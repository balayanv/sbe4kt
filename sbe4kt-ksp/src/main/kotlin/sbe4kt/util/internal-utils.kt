package sbe4kt.util

import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.symbol.ClassKind.ENUM_CLASS
import com.google.devtools.ksp.symbol.Modifier.INLINE
import com.google.devtools.ksp.symbol.Modifier.VALUE
import sbe4kt.KSP.log
import sbe4kt.META.Encoding
import sbe4kt.ir.Field

// validation

private val reserved = setOf(
   "abstract", "continue", "for", "new", "switch",
   "assert", "default", "goto", "package", "synchronized",
   "boolean", "do", "if", "private", "this",
   "break", "double", "implements", "protected", "throw",
   "byte", "else", "import", "public", "throws",
   "case", "enum", "instanceof", "return", "transient",
   "catch", "extends", "int", "short", "try",
   "char", "final", "interface", "static", "void",
   "class", "finally", "long", "strictfp", "volatile",
   "const", "float", "native", "super", "while",
   "true", "false", "null",


   // SBE specific type names which might clash if used as field names
   "int8", "int16", "int32", "int64",
   "uint8", "uint16", "uint32", "uint64",
   "float", "float64"
)


internal fun String.requireNotReserveWord() = apply { require(!reserved.contains(this)) { "do not use reserve words as property names : $this" } }

internal fun String.toSnakeCase() = this.replace(Regex("([a-z])([A-Z])"), "$1_$2").replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1_$2")
internal fun String.sbeSizeConstant() = "SBE_SIZE_${this.toSnakeCase().uppercase(java.util.Locale.ROOT)}"

internal fun KSClassDeclaration.requireMutableDataClass() = apply {
   log.info("Validating ${this.containingFile?.filePath}")

   if (!this.modifiers.contains(Modifier.DATA)) {
      log.error("must be mutable data classes", this)
      throw IllegalStateException()
   }

   this.getAllProperties().forEach { prop ->
      if (!prop.isMutable) {
         log.error("${this.simpleName.asString()}.${prop.simpleName.asString()} is not mutable", prop)
         throw IllegalStateException()
      }
   }
}

internal fun KSAnnotation.requireNonConflictingSettings() = apply {
   val isStruct = this.isStruct()
   val usePadding = this.usePadding()
   val allowVarLengthFields = this.allowVarLengthFields()

   require(!(usePadding && allowVarLengthFields)) { "`usePadding` and `allowVarLengthFields` cannot both be true" }
   require(!(isStruct && allowVarLengthFields)) { "`isStruct` and `allowVarLengthFields` cannot both be true" }
}

// readability

internal fun KSDeclaration.qualifiedName() = qualifiedName!!.asString()
internal fun KSDeclaration.simpleName() = simpleName.asString()
internal fun KSClassDeclaration.isValueClass() = modifiers.contains(INLINE) || modifiers.contains(VALUE)
internal fun KSClassDeclaration.isEnum() = classKind == ENUM_CLASS
internal fun KSClassDeclaration.isSealed() = modifiers.contains(Modifier.SEALED)
internal fun KSClassDeclaration.implementsCharSequence(): Boolean {
   // todo maybe this is too broad, we could create a marker interface that extends CharSequence...
   return superTypes.any {
      val resolved = it.resolve()
      resolved.declaration.qualifiedName?.asString() == "kotlin.CharSequence"
   }
}

internal fun KSClassDeclaration.packageName(): String = packageName.asString()

internal fun KSClassDeclaration.importName(): String {
   val parts = mutableListOf(simpleName.asString())
   var current: KSDeclaration? = parentDeclaration
   while (current is KSClassDeclaration) {
      parts.add(0, current.simpleName.asString())
      current = current.parentDeclaration
   }
   val pkg = packageName.asString()
   return if (pkg.isNotEmpty()) "$pkg.${parts.joinToString(".")}" else parts.joinToString(".")
}

internal fun KSClassDeclaration.getFieldIRs() = this.getAllProperties()
   .mapIndexed { declarationIndex, prop -> Field(prop, declarationIndex) }
   .onEachIndexed { index, field -> field.id = index + 1 }
   .toMutableList()

internal fun KSClassDeclaration.sbeAnnotation() = this.annotations.find { it.shortName.asString() == "SBE" }
internal fun KSAnnotation?.id() = supplyOrDefaultTo(-1) { this!!.arguments.find { it.name!!.asString() == "id" }!!.value as Int }
internal fun KSAnnotation?.usePadding() = supplyOrDefaultTo(false) { this!!.arguments.find { it.name!!.asString() == "usePadding" }!!.value as Boolean }
internal fun KSAnnotation?.isStruct() = supplyOrDefaultTo(false) { this!!.arguments.find { it.name!!.asString() == "isStruct" }!!.value as Boolean }
internal fun KSAnnotation?.allowNullableTypes() = supplyOrDefaultTo(false) { this!!.arguments.find { it.name!!.asString() == "allowNullableTypes" }!!.value as Boolean }
internal fun KSAnnotation?.compactFlags() = supplyOrDefaultTo(-1) { this!!.arguments.find { it.name!!.asString() == "compactFlags" }!!.value as Int }
internal fun KSPropertyDeclaration.metaAnnotation() = this.annotations.find { it.shortName.asString() == "META" }
internal fun KSAnnotation?.allowVarLengthFields() = supplyOrDefaultTo(false) { this!!.arguments.find { it.name!!.asString() == "allowVarLengthFields" }!!.value as Boolean }
internal fun KSAnnotation?.size() = supplyOrDefaultTo(-1) { this!!.arguments.find { it.name!!.asString() == "size" }!!.value as Int }
internal fun KSAnnotation?.removeAfter() = supplyOrDefaultTo(-1) { this!!.arguments.find { it.name!!.asString() == "removeAfter" }!!.value as Int }

// this is fragile but i dont know what the heck is the api to actually extract the Enum value of the annotation argument...
internal fun KSAnnotation?.encoding() = supplyOrDefaultTo(Encoding.ASCII) { Encoding.valueOf(this!!.arguments.find { it.name!!.asString() == "encoding" }!!.value.toString().removePrefix("Encoding.")) }

internal fun KSAnnotation?.tags(): List<String> {
   if (this == null) return emptyList()
   val arg = arguments.find { it.name!!.asString() == "tags" } ?: return emptyList()
   @Suppress("UNCHECKED_CAST")
   val value = arg.value as? ArrayList<String> ?: return emptyList()
   return value.toList()
}

internal fun KSAnnotation?.oneOf(): List<KSClassDeclaration> {
   if (this == null) return emptyList()
   val arg = arguments.find { it.name!!.asString() == "oneOf" } ?: return emptyList()
   @Suppress("UNCHECKED_CAST")
   val value = arg.value as? ArrayList<KSType> ?: return emptyList()
   if (value.isEmpty()) return emptyList()
   return value.mapNotNull { it.declaration as? KSClassDeclaration }
}

// misc

internal fun String.indent(count: Int): String {
   return if (this.isEmpty()) ""
   else lines().joinToString("\n") { " ".repeat(count) + it }
}

internal fun <T> supplyOrDefaultTo(default: T, supplier: () -> T): T {
   return try {
      supplier.invoke()
   } catch (_: Throwable) {
      default
   }
}

internal fun String.trimTemplate() = this.trimMargin().replace(Regex("\n{3,}"), "\n\n")