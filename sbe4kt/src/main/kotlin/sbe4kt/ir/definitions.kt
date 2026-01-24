package sbe4kt.ir

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSTypeAlias
import sbe4kt.util.*
import sbe4kt.util.isStruct
import sbe4kt.util.requireNonConflictingSettings
import sbe4kt.util.sbeAnnotation


/**
 * High level interfaces representing IR.
 */
internal sealed interface IR {

   sealed interface Root {

   }

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

      fun howToEncodeFromSource(encoder: String, field: String, instance: String) = encodeField(encoder, field, convertToSbe("$instance.$field"))
      fun howToDecodeIntoTarget(decoder: String, field: String, instance: String) = assignToInstanceField(instance, field, convertFromSbe(decodeField(decoder, field)))

      fun assignToInstanceField(instance: String, field: String, value: String) = "$instance.$field = $value"

      fun fwSetter(encoder: String, field: String) = encodeField(encoder, field, convertToSbe(field))
      fun fwGetter(decoder: String, field: String): String = convertFromSbe(decodeField(decoder, field))
   }
}

internal data class Schema(
   val annotatedTypes: List<KSClassDeclaration>,
) {
   val enums: ArrayList<Enum> = ArrayList()
   val structs: ArrayList<Struct> = ArrayList()
   val messages: ArrayList<Message> = ArrayList()
   val unionFields: ArrayList<Union> = ArrayList()
   val byteArraySizes = HashSet<Int>()
   val fixedStrings = HashSet<StringN>()

   val ids = HashSet<Int>()
   var id = 1

   init {
      for (type: KSClassDeclaration in annotatedTypes) {
         type.requireMutableDataClass()
         val annotation = type.sbeAnnotation()!!
         annotation.requireNonConflictingSettings()

         if (annotation.isStruct()) {
            structs.add(Struct(type))
         } else {
            val message = Message(type)
            messages.add(message)
            if (message.id != -1) ids.add(message.id)
         }
      }

      // auto-assign IDs after all explicit IDs are known
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
   }

   private fun extractSpecialTypes(type: IR.Leaf) {
      when (type) {
         is Enum -> enums.add(type)
         is ByteArrayN -> byteArraySizes.add(type.length)
         is StringN -> fixedStrings.add(type)
         is Value -> extractSpecialTypes(type.underlyingType)
         is Message -> byteArraySizes.add(type.size) // Nested messages use ByteArrayN
         is Union -> {
            unionFields.add(type)
            byteArraySizes.add(type.maxSize)
            type.allowedTypes.forEach { extractSpecialTypes(it) }
         }
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
   val isNullable: kotlin.Boolean = prop.type.resolve().isMarkedNullable
   val tags: List<String> = prop.metaAnnotation().tags()

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
         typeDecl.requireMutableDataClass()
         if (typeDecl.sbeAnnotation().isStruct()) {
            Struct(typeDecl)
         } else {
            Message(typeDecl)
         }
      }
      return Union(allowedTypes, simpleName.asString())
   }

   if (size > 0) {
      if (declaration.qualifiedName() == "kotlin.ByteArray") {
         require(size > 0) { "ByteArray size must be positive, got $size" }
         return ByteArrayN(size)
      }
      if (declaration.qualifiedName() == "kotlin.String") {
         require(size > 0) { "String size must be positive, got $size" }
         val encoding = meta.encoding()
         return StringN(size, encoding)
      }
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
      "kotlin.Boolean" -> Boolean
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

