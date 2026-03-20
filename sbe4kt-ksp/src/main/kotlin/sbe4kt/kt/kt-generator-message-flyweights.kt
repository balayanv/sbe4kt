package sbe4kt.kt

import sbe4kt.ir.ArrayListGroup
import sbe4kt.ir.Bool
import sbe4kt.ir.Composite
import sbe4kt.ir.Field
import sbe4kt.ir.IntArrayListGroup
import sbe4kt.ir.LongArrayListGroup
import sbe4kt.ir.Message
import sbe4kt.ir.Nullable
import sbe4kt.ir.RepeatingGroup
import sbe4kt.ir.Struct
import sbe4kt.ir.Union
import sbe4kt.ir.VarStringN
import sbe4kt.util.indent
import sbe4kt.util.sbeSizeConstant
import sbe4kt.util.trimTemplate

internal fun generateFlyweightsSection(message: Message): String {
   val reader = generateReaderFlyweight(message)
   val writer = generateWriterFlyweight(message)
   return "$reader\n\n$writer"
}

private fun generateFlagsReaderInnerClass(message: Message, isVariable: Boolean): String {
   val cf = message.compressedFlags ?: return ""
   val className = message.name
   val flagFields = message.flagFields
   val setName = cf.setName
   val visibility = if (isVariable) "private " else ""

   val getters = flagFields.joinToString("\n") { "fun ${it.name}() = decoder.${it.name}" }
   val decodeBody = flagFields.joinToString("\n") { "target.${it.name} = decoder.${it.name}" }

   return """
      |${visibility}class FlagsR(private val decoder: ${setName}Decoder) {
      |${getters.indent(3)}
      |
      |   fun decodeInto(target: $className) {
      |${decodeBody.indent(6)}
      |   }
      |}""".trimTemplate()
}

private fun generateFlagsWriterInnerClass(message: Message, isVariable: Boolean): String {
   val cf = message.compressedFlags ?: return ""
   val className = message.name
   val flagFields = message.flagFields
   val setName = cf.setName

   val visibility = if (isVariable) "private " else ""
   val setters = flagFields.joinToString("\n") { "fun ${it.name}(${it.name}: Boolean) = apply { encoder.${it.name}(${it.name}) }" }
   val encodeBody = flagFields.joinToString("\n") { "encoder.${it.name}(source.${it.name})" }

   return """
      |${visibility}class FlagsW(private val encoder: ${setName}Encoder) {
      |   fun clear() = apply { encoder.clear() }
      |${setters.indent(3)}
      |
      |   fun encodeFrom(source: $className) {
      |      encoder.clear()
      |${encodeBody.indent(6)}
      |   }
      |}""".trimTemplate()
}

private fun generateReaderFlyweight(message: Message): String {
   val className = message.name
   val sizeConstName = className.sbeSizeConstant()

   val allFields = message.fields
   val fieldsDecOrder = allFields.sortedBy { it.declarationOrder }
   val fieldsSbeOrder = allFields.filter { it.sbeOrder >= 0 }.sortedBy { it.sbeOrder }
   val compositeFields = fieldsDecOrder.filter { it.type is Composite }
   val simpleFields = fieldsDecOrder.filter { it.type !is Composite && it.type !is Message && it.type !is Union && it.type !is RepeatingGroup && it.type.size >= 0 }
   val unionFields = fieldsDecOrder.filter { it.type is Union }

   val isVariable = message.hasVariableFields

   val cf = message.compressedFlags
   val flagsFwDecl = if (cf != null) "private val flagsFW: FlagsR" else ""
   val flagsFwInit = if (cf != null) "flagsFW = FlagsR(decoder.flags())" else ""

   val compositeFwDecls = compositeFields.joinToString("\n") { "private val ${it.name}FW: ${it.type.name}R" }
   val compositeFwInits = compositeFields.joinToString("\n") { "${it.name}FW = ${it.type.name}R(decoder.${it.name}())" }
   val compositeFwAccessors = compositeFields.joinToString("\n\n") {
      val visibility = if (isVariable) "private " else ""
      "${visibility}fun ${it.name}() = ${it.name}FW"
   }

   val flagsAccessor = if (cf != null) {
      val visibility = if (isVariable) "private " else ""
      "${visibility}fun flags() = flagsFW"
   } else ""

   // wrap() positions all flyweights after wrapAndApplyHeader
   val wrapPositioning = buildList {
      compositeFields.forEach { add("decoder.${it.name}()") }
      if (cf != null) add("decoder.flags()")
   }.joinToString("\n")

   val simpleFieldGetters = if (!isVariable) simpleFields.joinToString("\n\n") {
      val type = it.type
      val ctx = if (type is Bool && type.isCompressed) "flagsFW" else "decoder"
      if (type is Nullable) {
         val inner = type.innerType
         "fun ${it.name}(): ${type.name} = if (decoder.${it.nullBoolName}().convertToBoolean()) ${inner.fwGetter(ctx, it.name)} else null"
      } else {
         "fun ${it.name}() = ${type.fwGetter(ctx, it.name)}"
      }
   } else ""

   val unionGetters = if (!isVariable) generateUnionFwGetters(unionFields, className, "decoder") else ""

   val headerAccessors = if (!isVariable) """
      |   fun blockLength() = headerDecoder.blockLength()
      |   fun templateId() = headerDecoder.templateId()
      |   fun schemaId() = headerDecoder.schemaId()
      |   fun version() = headerDecoder.version()""".trimMargin() else ""

   // Filter out compressed Bool fields — handled by flags().decodeInto() at cf.sbeOrder
   val decodeFields = fieldsSbeOrder.filter { it.type !is Bool || !(it.type as Bool).isCompressed }
   val decodeIntoBody = buildString {
      var flagsEmitted = false
      for (field in decodeFields) {
         if (cf != null && !flagsEmitted && field.sbeOrder > cf.sbeOrder) {
            appendLine("flags().decodeInto(target)")
            flagsEmitted = true
         }
         val type = field.type
         val line = when (type) {
            is Composite -> "${field.name}().decodeInto(target.${field.name})"
            is Message -> "decode${type.name}(target.${field.name}, decoder.buffer(), decoder.offset() + ${className}Encoder.${field.name}EncodingOffset())"
            is Union -> if (!isVariable) {
               "target.${field.name} = ${field.name}()"
            } else {
               val fieldOffset = "decoder.offset() + ${className}Encoder.${field.name}EncodingOffset()"
               buildString {
                  appendLine("val ${field.name}Header = MessageHeaderDecoder()")
                  appendLine("${field.name}Header.wrap(decoder.buffer(), $fieldOffset)")
                  appendLine("target.${field.name} = when (${field.name}Header.templateId()) {")
                  for (t in type.allowedTypes) {
                     appendLine("   ${t.name}Encoder.TEMPLATE_ID -> new${t.name}().also { decode${t.name}(it, decoder.buffer(), $fieldOffset) }")
                  }
                  appendLine("   else -> throw IllegalArgumentException(\"Unknown templateId in union field '${field.name}': \${${field.name}Header.templateId()}\")")
                  append("}")
               }
            }
            is IntArrayListGroup -> buildString {
               val decVar = "${field.name}Dec"
               appendLine("target.${field.name}.clear()")
               appendLine("for ($decVar in decoder.${field.name}()) {")
               appendLine("   target.${field.name}.addInt($decVar.value())")
               append("}")
            }
            is LongArrayListGroup -> buildString {
               val decVar = "${field.name}Dec"
               appendLine("target.${field.name}.clear()")
               appendLine("for ($decVar in decoder.${field.name}()) {")
               appendLine("   target.${field.name}.addLong($decVar.value())")
               append("}")
            }
            is ArrayListGroup -> {
               val elemType = type.elementType
               val decVar = "${field.name}Dec"
               val valueExpr = when (elemType) {
                  is Struct -> {
                     val fieldDecodes = elemType.fields.sortedBy { f -> f.sbeOrder }.joinToString(", ") { f ->
                        "${f.name} = ${f.type.convertFromSbe("$decVar.${f.name}()")}"
                     }
                     "${elemType.name}($fieldDecodes)"
                  }
                  else -> elemType.convertFromSbe("$decVar.value()")
               }
               buildString {
                  appendLine("target.${field.name}.clear()")
                  appendLine("for ($decVar in decoder.${field.name}()) {")
                  appendLine("   target.${field.name}.add($valueExpr)")
                  append("}")
               }
            }
            is Nullable -> {
               val inner = type.innerType
               val valueExpr = if (inner is VarStringN) {
                  val cap = field.name.replaceFirstChar { c -> c.uppercase() }
                  "StringBuilder().also { decoder.get$cap(it) }"
               } else {
                  inner.convertFromSbe(inner.decodeField("decoder", field.name))
               }
               "target.${field.name} = if (decoder.${field.nullBoolName}().convertToBoolean()) $valueExpr else null"
            }
            is VarStringN -> {
               val cap = field.name.replaceFirstChar { c -> c.uppercase() }
               "decoder.get$cap(target.${field.name}.clear())"
            }
            else -> type.assignToInstanceField("target", field.name, type.fwGetter("decoder", field.name))
         }
         appendLine(line)
      }
      if (cf != null && !flagsEmitted) appendLine("flags().decodeInto(target)")
   }.trimEnd()

   val flagsInnerClass = generateFlagsReaderInnerClass(message, isVariable)

   return """
      |class ${className}R{
      |${if (flagsInnerClass.isNotEmpty()) "\n${flagsInnerClass.indent(3)}\n" else ""}
      |   private val headerDecoder = MessageHeaderDecoder()
      |   private val decoder = ${className}Decoder()
      |${if (compositeFwDecls.isNotEmpty()) "\n${compositeFwDecls.indent(3)}" else ""}
      |${if (flagsFwDecl.isNotEmpty()) "\n${flagsFwDecl.indent(3)}" else ""}
      |
      |${headerAccessors}
      |
      |   constructor() : this(${if (isVariable) "ExpandableArrayBuffer()" else "UnsafeBuffer(ByteArray($sizeConstName))"}, 0)
      |
      |   constructor(buffer: DirectBuffer, offset: Int = 0){
      |      wrap(buffer, offset)
      |${compositeFwInits.indent(6)}
      |${if (flagsFwInit.isNotEmpty()) flagsFwInit.indent(6) else ""}
      |   }
      |
      |   fun wrap(buffer: DirectBuffer, offset: Int = 0) = apply {
      |      decoder.wrapAndApplyHeader(buffer, offset, headerDecoder)
      |${if (wrapPositioning.isNotEmpty()) wrapPositioning.indent(6) else ""}
      |   }
      |
      |${compositeFwAccessors.indent(3)}
      |
      |${if (flagsAccessor.isNotEmpty()) flagsAccessor.indent(3) else ""}
      |
      |${simpleFieldGetters.indent(3)}
      |
      |${unionGetters.indent(3)}
      |
      |   fun decodeInto(target: $className = new$className()): Int {
      |${decodeIntoBody.indent(6)}
      |      return ${if (isVariable) "MessageHeaderDecoder.ENCODED_LENGTH + decoder.encodedLength()" else sizeConstName}
      |   }
      |
      |   fun size() = $sizeConstName
      |}
      |""".trimTemplate()
}

private fun generateWriterFlyweight(message: Message): String {
   val className = message.name
   val sizeConstName = className.sbeSizeConstant()

   val allFields = message.fields
   val fieldsDecOrder = allFields.sortedBy { it.declarationOrder }
   val fieldsSbeOrder = allFields.filter { it.sbeOrder >= 0 }.sortedBy { it.sbeOrder }
   val compositeFields = fieldsDecOrder.filter { it.type is Composite }
   val simpleFields = fieldsDecOrder.filter { it.type !is Composite && it.type !is Message && it.type !is Union && it.type !is RepeatingGroup && it.type.size >= 0 }
   val unionFields = fieldsDecOrder.filter { it.type is Union }

   val isVariable = message.hasVariableFields

   val cf = message.compressedFlags
   val flagsFwDecl = if (cf != null) "private val flagsFW: FlagsW" else ""
   val flagsFwInit = if (cf != null) "flagsFW = FlagsW(encoder.flags())" else ""

   val compositeFwDecls = compositeFields.joinToString("\n") { "private val ${it.name}FW: ${it.type.name}W" }
   val compositeFwInits = compositeFields.joinToString("\n") { "${it.name}FW = ${it.type.name}W(encoder.${it.name}())" }
   val compositeFwAccessors = compositeFields.joinToString("\n\n") {
      val visibility = if (isVariable) "private " else ""
      "${visibility}fun ${it.name}() = ${it.name}FW"
   }

   val flagsAccessor = if (cf != null) {
      val visibility = if (isVariable) "private " else ""
      "${visibility}fun flags() = flagsFW"
   } else ""

   // wrap() positions all flyweights after wrapAndApplyHeader
   val wrapPositioning = buildList {
      compositeFields.forEach { add("encoder.${it.name}()") }
      if (cf != null) add("encoder.flags()")
   }.joinToString("\n")

   val simpleFieldSetters = if (!isVariable) simpleFields.joinToString("\n\n") {
      val type = it.type
      val ctx = if (type is Bool && type.isCompressed) "flagsFW" else "encoder"
      if (type is Nullable) {
         val inner = type.innerType
         "fun ${it.name}(${it.name}: ${type.name}) = apply {\n" +
         "   if (${it.name} != null) { ${inner.fwSetter(ctx, it.name)}; encoder.${it.nullBoolName}(true.convertToByte()) }\n" +
         "   else { encoder.${it.nullBoolName}(false.convertToByte()) }\n" +
         "}"
      } else {
         "fun ${it.name}(${it.name}: ${type.name}) = apply { ${type.fwSetter(ctx, it.name)} }"
      }
   } else ""

   val unionSetters = if (!isVariable) generateUnionFwSetters(unionFields, className) else ""

   // Filter out compressed Bool fields — handled by flags().encodeFrom() at cf.sbeOrder
   val encodeFields = fieldsSbeOrder.filter { it.type !is Bool || !(it.type as Bool).isCompressed }
   val encodeFromBody = buildString {
      var flagsEmitted = false
      for (field in encodeFields) {
         if (cf != null && !flagsEmitted && field.sbeOrder > cf.sbeOrder) {
            appendLine("flags().encodeFrom(source)")
            flagsEmitted = true
         }
         val type = field.type
         val line = when (type) {
            is Composite -> "${field.name}().encodeFrom(source.${field.name})"
            is Message -> "encode${type.name}(source.${field.name}, encoder.buffer(), encoder.offset() + ${className}Encoder.${field.name}EncodingOffset())"
            is Union -> {
               val fieldOffset = "encoder.offset() + ${className}Encoder.${field.name}EncodingOffset()"
               buildString {
                  appendLine("when (source.${field.name}) {")
                  for (t in type.allowedTypes) {
                     appendLine("   is ${t.name} -> encode${t.name}(source.${field.name} as ${t.name}, encoder.buffer(), $fieldOffset)")
                  }
                  appendLine("   else -> throw IllegalArgumentException(\"Unexpected type in union field '${field.name}'\")")
                  append("}")
               }
            }
            is IntArrayListGroup -> buildString {
               val grpVar = "${field.name}Grp"
               appendLine("val $grpVar = encoder.${field.name}Count(source.${field.name}.size)")
               appendLine("for (i in 0 until source.${field.name}.size) {")
               appendLine("   $grpVar.next()")
               appendLine("   $grpVar.value(source.${field.name}.getInt(i))")
               append("}")
            }
            is LongArrayListGroup -> buildString {
               val grpVar = "${field.name}Grp"
               appendLine("val $grpVar = encoder.${field.name}Count(source.${field.name}.size)")
               appendLine("for (i in 0 until source.${field.name}.size) {")
               appendLine("   $grpVar.next()")
               appendLine("   $grpVar.value(source.${field.name}.getLong(i))")
               append("}")
            }
            is ArrayListGroup -> {
               val elemType = type.elementType
               val grpVar = "${field.name}Grp"
               buildString {
                  appendLine("val $grpVar = encoder.${field.name}Count(source.${field.name}.size)")
                  appendLine("for (item in source.${field.name}) {")
                  appendLine("   $grpVar.next()")
                  when (elemType) {
                     is Struct -> {
                        for (f in elemType.fields.sortedBy { sf -> sf.sbeOrder }) {
                           appendLine("   $grpVar.${f.name}(${f.type.convertToSbe("item.${f.name}")})")
                        }
                     }
                     else -> appendLine("   $grpVar.value(${elemType.convertToSbe("item")})")
                  }
                  append("}")
               }
            }
            is Nullable -> {
               val inner = type.innerType
               buildString {
                  appendLine("if (source.${field.name} != null) {")
                  appendLine("   ${inner.encodeField("encoder", field.name, inner.convertToSbe("source.${field.name}!!"))}")
                  appendLine("   encoder.${field.nullBoolName}(true.convertToByte())")
                  appendLine("} else {")
                  appendLine("   encoder.${field.nullBoolName}(false.convertToByte())")
                  append("}")
               }
            }
            else -> type.encodeField("encoder", field.name, type.convertToSbe("source.${field.name}"))
         }
         appendLine(line)
      }
      if (cf != null && !flagsEmitted) appendLine("flags().encodeFrom(source)")
   }.trimEnd()

   val flagsInnerClass = generateFlagsWriterInnerClass(message, isVariable)

   return """
      |class ${className}W{
      |${if (flagsInnerClass.isNotEmpty()) "\n${flagsInnerClass.indent(3)}\n" else ""}
      |   private val headerEncoder = MessageHeaderEncoder()
      |   private val encoder = ${className}Encoder()
      |${if (compositeFwDecls.isNotEmpty()) "\n${compositeFwDecls.indent(3)}" else ""}
      |${if (flagsFwDecl.isNotEmpty()) "\n${flagsFwDecl.indent(3)}" else ""}
      |
      |   constructor() : this(${if (isVariable) "ExpandableArrayBuffer()" else "UnsafeBuffer(ByteArray($sizeConstName))"}, 0)
      |
      |   constructor(buffer: MutableDirectBuffer, offset: Int = 0){
      |      wrap(buffer, offset)
      |${compositeFwInits.indent(6)}
      |${if (flagsFwInit.isNotEmpty()) flagsFwInit.indent(6) else ""}
      |   }
      |
      |   fun wrap(buffer: MutableDirectBuffer, offset: Int = 0) = apply {
      |      encoder.wrapAndApplyHeader(buffer, offset, headerEncoder)
      |${if (wrapPositioning.isNotEmpty()) wrapPositioning.indent(6) else ""}
      |   }
      |
      |${compositeFwAccessors.indent(3)}
      |
      |${if (flagsAccessor.isNotEmpty()) flagsAccessor.indent(3) else ""}
      |
      |${simpleFieldSetters.indent(3)}
      |
      |${unionSetters.indent(3)}
      |
      |   fun encodeFrom(source: $className) : Int {
      |${encodeFromBody.indent(6)}
      |      return ${if (isVariable) "MessageHeaderDecoder.ENCODED_LENGTH + encoder.encodedLength()" else sizeConstName}
      |   }
      |
      |   fun size() = $sizeConstName
      |}
      |""".trimTemplate()
}

private fun generateUnionFwGetters(unionFields: kotlin.collections.List<Field>, className: String, bufferSource: String): String {
   if (unionFields.isEmpty()) return ""
   return unionFields.joinToString("\n") { field ->
      val union = field.type as Union
      val fieldOffset = "$bufferSource.offset() + ${className}Encoder.${field.name}EncodingOffset()"
      buildString {
         appendLine("fun ${field.name}(): ${union.name} {")
         appendLine("   val ${field.name}Header = MessageHeaderDecoder()")
         appendLine("   ${field.name}Header.wrap($bufferSource.buffer(), $fieldOffset)")
         appendLine("   return when (${field.name}Header.templateId()) {")
         for (type in union.allowedTypes) {
            appendLine("      ${type.name}Encoder.TEMPLATE_ID -> new${type.name}().also { decode${type.name}(it, $bufferSource.buffer(), $fieldOffset) }")
         }
         appendLine("      else -> throw IllegalArgumentException(\"Unknown templateId in union field '${field.name}': \${${field.name}Header.templateId()}\")")
         appendLine("   }")
         append("}")
      }
   }
}

private fun generateUnionFwSetters(unionFields: kotlin.collections.List<Field>, className: String): String {
   if (unionFields.isEmpty()) return ""
   return unionFields.joinToString("\n") { field ->
      val union = field.type as Union
      val fieldOffset = "encoder.offset() + ${className}Encoder.${field.name}EncodingOffset()"
      buildString {
         appendLine("fun ${field.name}(${field.name}: ${union.name}) = apply {")
         appendLine("   when (${field.name}) {")
         for (type in union.allowedTypes) {
            appendLine("      is ${type.name} -> encode${type.name}(${field.name}, encoder.buffer(), $fieldOffset)")
         }
         appendLine("      else -> throw IllegalArgumentException(\"Unexpected type in union field '${field.name}': \${${field.name}::class.qualifiedName}\")")
         appendLine("   }")
         append("}")
      }
   }
}
