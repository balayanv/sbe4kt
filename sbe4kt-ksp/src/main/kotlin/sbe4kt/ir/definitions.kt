package sbe4kt.ir

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSTypeAlias
import sbe4kt.util.*
import sbe4kt.util.isStruct
import sbe4kt.util.requireNonConflictingSettings
import sbe4kt.util.sbeAnnotation
import sbe4kt.migration.PrevMessage
import sbe4kt.migration.PreviousSchema
import sbe4kt.migration.SchemaComparator
import sbe4kt.migration.SchemaEvolution


internal class DeferredTypeException(message: String) : RuntimeException(message)

/**
 * High level interfaces representing IR.
 */
internal sealed interface IR {

   sealed interface Root

   sealed interface Leaf {
      val sbeName: String
      val name: String
      val defaultValue: String
      val randomValue: String
      val size: Int
      val packageStr: String
      val imports: Set<String>

      fun encodeField(encoder: String, field: String, value: String) = "$encoder.$field($value)"
      fun decodeField(decoder: String, field: String) = "$decoder.$field()"

      fun convertToSbe(value: String) = value
      fun convertFromSbe(value: String) = value


      fun assignToInstanceField(instance: String, field: String, value: String) = "$instance.$field = $value"

      fun fwSetter(encoder: String, field: String) = encodeField(encoder, field, convertToSbe(field))
      fun fwGetter(decoder: String, field: String): String = convertFromSbe(decodeField(decoder, field))
   }
}

internal data class Schema(
   val annotatedTypes: kotlin.collections.List<KSClassDeclaration>,
   val previousSchema: PreviousSchema? = null,
) {
   val structs: ArrayList<Struct> = ArrayList()
   val messages: ArrayList<Message> = ArrayList()
   val deprecatedMessages: ArrayList<PrevMessage> = ArrayList()
   val byteArraySizes = HashSet<Int>()
   val fixedStrings = HashSet<StringN>()
   var evolution: SchemaEvolution? = null

   val ids = HashSet<Int>()
   var id = 1

   init {
      // seed IDs from previous schema to prevent reuse of retired message IDs
      previousSchema?.allMessageIds?.let { ids.addAll(it) }

      for (type: KSClassDeclaration in annotatedTypes) {
         type.requireMutableDataClass()
         val annotation = type.sbeAnnotation()!!
         annotation.requireNonConflictingSettings()

         if (annotation.isStruct()) {
            structs.add(Struct(type))
         } else {
            val prevMessage = previousSchema?.messages?.get(type.simpleName())
            val message = Message(type, prevMessage)
            messages.add(message)

            // if message existed in previous schema, preserve its ID
            if (prevMessage != null && message.id == -1) {
               message.id = prevMessage.id
            }
            if (message.id != -1) ids.add(message.id)
         }
      }

      // auto-assign IDs after all explicit and preserved IDs are known
      for (message in messages) {
         if (message.id == -1) {
            message.id = getNextAvailableId()
            ids.add(message.id)
         }
      }

      messages.sortBy { it.id }

      for (message in messages) {
         for (field in message.fields) {
            extractSpecialTypes(field.type)
         }
      }

      for (struct in structs) {
         for (field in struct.fields) {
            extractSpecialTypes(field.type)
         }
      }

      // carry forward deprecated messages from previous schema
      if (previousSchema != null) {
         val currentMessageNames = messages.map { it.name }.toSet()
         for ((name, prevMsg) in previousSchema.messages) {
            if (name !in currentMessageNames) {
               // newly removed or already deprecated — carry forward
               deprecatedMessages.add(prevMsg)
            }
         }

         evolution = SchemaComparator.compare(this, previousSchema)
      }
   }

   private fun extractSpecialTypes(type: IR.Leaf) {
      when (type) {
         is ByteArrayN -> byteArraySizes.add(type.length)
         is StringN -> fixedStrings.add(type)
         is Value -> extractSpecialTypes(type.underlyingType)
         is Message -> byteArraySizes.add(type.size) // Nested messages use ByteArrayN
         is Union -> {
            byteArraySizes.add(type.maxSize)
            type.allowedTypes.forEach { extractSpecialTypes(it) }
         }
         is RepeatingGroup -> extractSpecialTypes(type.elementType)
         else -> {} // primitives, structs don't contain special types to extract
      }
   }

   private fun getNextAvailableId(): Int {
      while (ids.contains(id)) id++
      return id
   }
}


internal data class Field(
   val prop: KSPropertyDeclaration,
   val declarationOrder: Int = 0,
) {
   val name: String = prop.simpleName.asString().requireNotReserveWord()
   val type: IR.Leaf = prop.toLeaf()
   val isNullable: Boolean = prop.type.resolve().isMarkedNullable
   val tags: kotlin.collections.List<String> = prop.metaAnnotation().tags()
   val removeAfter: Int = prop.metaAnnotation().removeAfter()

   var id: Int = -1
   var offset: Int = -1
   var sbeOrder: Int = -1
}

// recursive IR resolution

internal fun KSPropertyDeclaration.toLeaf(): IR.Leaf {
   val meta = this.metaAnnotation()
   val size = meta.size()
   val oneOf = meta.oneOf()

   val resolvedType = this.type.resolve()
   val declaration = resolvedType.declaration

   if (oneOf.isNotEmpty()) {
      require(declaration.qualifiedName() == "kotlin.Any") { "Union field '${simpleName.asString()}' must be of type Any, got ${declaration.qualifiedName()}" }
      val allowedTypes = oneOf.map { typeDecl ->
         require(typeDecl.sbeAnnotation() != null) { "Union type '${typeDecl.simpleName()}' must be annotated with @SBE" }
         require(!typeDecl.sbeAnnotation().isStruct()) { "Union types must be @SBE messages, not structs: '${typeDecl.simpleName()}'" }
         typeDecl.requireMutableDataClass()
         Message(typeDecl)
      }
      return Union(allowedTypes, simpleName.asString())
   }

   if (declaration is KSClassDeclaration && declaration.isSealed()) {
      val sealedSubclasses = declaration.getSealedSubclasses().toList()
      if (sealedSubclasses.isEmpty()) throw DeferredTypeException("Sealed type '${declaration.simpleName()}' has no subclasses yet, deferring")
      val allowedTypes = sealedSubclasses.map { subclass ->
         require(subclass.sbeAnnotation() != null) { "Sealed subclass '${subclass.simpleName()}' must be annotated with @SBE" }
         require(!subclass.sbeAnnotation().isStruct()) { "Union types must be @SBE messages, not structs: '${subclass.simpleName()}'" }
         subclass.requireMutableDataClass()
         Message(subclass)
      }
      return Union(
         allowedTypes = allowedTypes,
         fieldName = simpleName.asString(),
         typeName = declaration.simpleName(),
         typePackage = declaration.packageName.asString()
      )
   }

   // Reusability violations — reject immutable types with clear messages
   if (declaration.qualifiedName() == "kotlin.String" || declaration.qualifiedName() == "kotlin.CharSequence") {
      throw IllegalArgumentException(
         "Field '${simpleName.asString()}': String is not reusable. Use StringBuilder instead. " +
         "For fixed-size: @META(size=N) var field: StringBuilder. " +
         "For variable-length: var field: StringBuilder"
      )
   }

   if (declaration.qualifiedName() == "kotlin.Array") {
      throw IllegalArgumentException(
         "Field '${simpleName.asString()}': Array<T> is not reusable. Use ArrayList<T> for objects, " +
         "IntArrayList/LongArrayList for primitives."
      )
   }

   if (declaration.qualifiedName() == "kotlin.collections.List" ||
       declaration.qualifiedName() == "kotlin.collections.MutableList") {
      throw IllegalArgumentException(
         "Field '${simpleName.asString()}': List<T> is not reusable. Use ArrayList<T> for objects, " +
         "IntArrayList/LongArrayList for primitives."
      )
   }

   if (size > 0) {
      if (declaration.qualifiedName() == "kotlin.ByteArray") {
         require(size > 0) { "ByteArray size must be positive, got $size" }
         return ByteArrayN(size)
      }
      if (declaration.qualifiedName() == "kotlin.text.StringBuilder" ||
          declaration.qualifiedName() == "java.lang.StringBuilder") {
         require(size > 0) { "StringBuilder size must be positive, got $size" }
         val encoding = meta.encoding()
         return StringN(size, encoding)
      }
   }

   // ArrayList<T> → ArrayListGroup (SBE repeating group, object types only)
   if (declaration.qualifiedName() == "java.util.ArrayList") {
      val typeArg = resolvedType.arguments.first().type!!.resolve().declaration
      val qualName = typeArg.qualifiedName()

      // Reject boxed primitives — use IntArrayList/LongArrayList instead
      val boxedPrimitives = setOf("kotlin.Int", "kotlin.Long", "kotlin.Float", "kotlin.Double",
         "kotlin.Short", "kotlin.Byte", "kotlin.Boolean", "kotlin.Char",
         "java.lang.Integer", "java.lang.Long", "java.lang.Float", "java.lang.Double",
         "java.lang.Short", "java.lang.Byte", "java.lang.Boolean", "java.lang.Character")
      if (qualName in boxedPrimitives) {
         val hint = when (qualName) {
            "kotlin.Int", "java.lang.Integer" -> " Use IntArrayList instead."
            "kotlin.Long", "java.lang.Long" -> " Use LongArrayList instead."
            else -> " Primitive type '${typeArg.simpleName()}' is not supported in repeating groups."
         }
         throw IllegalArgumentException(
            "Field '${simpleName.asString()}': ArrayList<${typeArg.simpleName()}> causes boxing.$hint"
         )
      }

      val elementType = resolveGroupElementType(typeArg, meta)
      return ArrayListGroup(elementType)
   }

   // StringBuilder — reusable string type (variable-length when no size annotation)
   if (declaration.qualifiedName() == "kotlin.text.StringBuilder" ||
       declaration.qualifiedName() == "java.lang.StringBuilder") {
      val encoding = meta.encoding()
      return VarStringN(encoding)
   }

   // Agrona primitive collections — reusable, unboxed
   if (declaration.qualifiedName() == "org.agrona.collections.IntArrayList") {
      return IntArrayListGroup
   }

   if (declaration.qualifiedName() == "org.agrona.collections.LongArrayList") {
      return LongArrayListGroup
   }

   val tags = meta.tags()
   if (tags.isNotEmpty()) {
      val resolved = recursiveResolveLeaf(declaration)
      require(resolved is Value) { "tags field must be a value class, got ${resolved::class.simpleName}" }
      return Tags(resolved, tags)
   }

   return recursiveResolveLeaf(declaration)
}

private fun recursiveResolveLeaf(declaration: KSDeclaration): IR.Leaf {

   if (declaration is KSTypeAlias) {
      return recursiveResolveLeaf(declaration.type.resolve().declaration)
   }

   if (declaration is KSClassDeclaration) {
      if (declaration.isValueClass()) {
         val first = declaration.primaryConstructor!!.parameters.first()
         val underlyingType = recursiveResolveLeaf(first.type.resolve().declaration)

         if (underlyingType == Int64 && declaration.implementsCharSequence()) {
            return Str64Value(declaration)
         }

         return Value(declaration, underlyingType)
      }

      if (declaration.isEnum()) {
         return Enum(declaration)
      }

      if (declaration.sbeAnnotation().isStruct()) {
         declaration.requireMutableDataClass()
         return Struct(declaration)
      }

      // Check if this is an @SBE annotated message type (for nesting)
      if (declaration.sbeAnnotation() != null && !declaration.sbeAnnotation().isStruct()) {
         declaration.requireMutableDataClass()
         return Message(declaration)
      }
   }

   return when (declaration.qualifiedName()) {
      "kotlin.Boolean" -> Bool
      "kotlin.Char" -> Char
      "kotlin.Byte" -> Int8
      "kotlin.Short" -> Int16
      "kotlin.Int" -> Int32
      "kotlin.Long" -> Int64
      "kotlin.Float" -> Float
      "kotlin.Double" -> Double
      else -> throw IllegalArgumentException("Unsupported type: ${declaration.qualifiedName()}")
   }
}

private fun resolveGroupElementType(declaration: KSDeclaration, meta: KSAnnotation?): IR.Leaf {
   if (declaration.qualifiedName() == "kotlin.text.StringBuilder" ||
       declaration.qualifiedName() == "java.lang.StringBuilder") {
      val encoding = meta.encoding()
      return VarStringN(encoding)
   }
   return recursiveResolveLeaf(declaration)
}

