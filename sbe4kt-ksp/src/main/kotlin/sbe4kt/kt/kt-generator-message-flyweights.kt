package sbe4kt.kt

import sbe4kt.ir.ArrayListGroup
import sbe4kt.ir.Composite
import sbe4kt.ir.Field
import sbe4kt.ir.IntArrayListGroup
import sbe4kt.ir.LongArrayListGroup
import sbe4kt.ir.Message
import sbe4kt.ir.RepeatingGroup
import sbe4kt.ir.Struct
import sbe4kt.ir.Union
import sbe4kt.util.indent
import sbe4kt.util.sbeSizeConstant
import sbe4kt.util.trimTemplate

internal fun generateFlyweightsSection(message: Message): String {
   val reader = generateReaderFlyweight(message)
   val writer = generateWriterFlyweight(message)
   return "$reader\n\n$writer"
}

private fun generateReaderFlyweight(message: Message): String {
   val className = message.name
   val sizeConstName = className.sbeSizeConstant()

   val fieldsDecOrder = message.fields.sortedBy { it.declarationOrder }
   val fieldsSbeOrder = message.fields.sortedBy { it.sbeOrder }
   val compositeFields = fieldsDecOrder.filter { it.type is Composite }
   val simpleFields = fieldsDecOrder.filter { it.type !is Composite && it.type !is Message && it.type !is Union && it.type !is RepeatingGroup }
   val unionFields = fieldsDecOrder.filter { it.type is Union }

   val isVariable = message.hasVariableFields

   val compositeFwDecls = if (!isVariable) compositeFields.joinToString("\n") { "private val ${it.name}FW: ${it.type.name}R" } else ""
   val compositeFwInits = if (!isVariable) compositeFields.joinToString("\n") { "${it.name}FW = ${it.type.name}R(decoder.${it.name}())" } else ""
   val compositeFwAccessors = if (!isVariable) compositeFields.joinToString("\n\n") {
      "fun ${it.name}(): ${it.type.name}R {\n" +
      "   decoder.${it.name}()\n" +
      "   return ${it.name}FW\n" +
      "}"
   } else ""

   val simpleFieldGetters = if (!isVariable) simpleFields.joinToString("\n\n") {
      "fun ${it.name}() = ${it.type.fwGetter("decoder", it.name)}"
   } else ""

   val unionGetters = if (!isVariable) generateUnionFwGetters(unionFields, className, "decoder") else ""

   val headerAccessors = if (!isVariable) """
      |   fun blockLength() = headerDecoder.blockLength()
      |   fun templateId() = headerDecoder.templateId()
      |   fun schemaId() = headerDecoder.schemaId()
      |   fun version() = headerDecoder.version()""".trimMargin() else ""

   val decodeIntoBody = fieldsSbeOrder.joinToString("\n") {
      when (it.type) {
         is Composite -> if (!isVariable) {
            "${it.name}FW.decodeInto(target.${it.name})"
         } else {
            val composite = it.type as Composite
            composite.fields.sortedBy { f -> f.sbeOrder }.joinToString("\n") { f ->
               f.type.assignToInstanceField("target.${it.name}", f.name, f.type.convertFromSbe("decoder.${it.name}().${f.name}()"))
            }
         }
         is Message -> "decode${it.type.name}(target.${it.name}, decoder.buffer(), decoder.offset() + ${className}Encoder.${it.name}EncodingOffset())"
         is Union -> if (!isVariable) {
            "target.${it.name} = ${it.name}()"
         } else {
            val union = it.type as Union
            val fieldOffset = "decoder.offset() + ${className}Encoder.${it.name}EncodingOffset()"
            buildString {
               appendLine("run {")
               appendLine("   val ${it.name}Header = MessageHeaderDecoder()")
               appendLine("   ${it.name}Header.wrap(decoder.buffer(), $fieldOffset)")
               appendLine("   target.${it.name} = when (${it.name}Header.templateId()) {")
               for (type in union.allowedTypes) {
                  appendLine("      ${type.name}Encoder.TEMPLATE_ID -> new${type.name}().also { decode${type.name}(it, decoder.buffer(), $fieldOffset) }")
               }
               appendLine("      else -> throw IllegalArgumentException(\"Unknown templateId in union field '${it.name}': \${${it.name}Header.templateId()}\")")
               appendLine("   }")
               append("}")
            }
         }
         is IntArrayListGroup -> buildString {
            appendLine("run {")
            appendLine("   target.${it.name}.clear()")
            appendLine("   for (grpDec in decoder.${it.name}()) {")
            appendLine("      target.${it.name}.addInt(grpDec.value())")
            appendLine("   }")
            append("}")
         }
         is LongArrayListGroup -> buildString {
            appendLine("run {")
            appendLine("   target.${it.name}.clear()")
            appendLine("   for (grpDec in decoder.${it.name}()) {")
            appendLine("      target.${it.name}.addLong(grpDec.value())")
            appendLine("   }")
            append("}")
         }
         is ArrayListGroup -> {
            val elemType = it.type.elementType
            val valueExpr = when (elemType) {
               is Struct -> {
                  val fieldDecodes = elemType.fields.sortedBy { f -> f.sbeOrder }.joinToString(", ") { f ->
                     "${f.name} = ${f.type.convertFromSbe("grpDec.${f.name}()")}"
                  }
                  "${elemType.name}($fieldDecodes)"
               }
               else -> elemType.convertFromSbe("grpDec.value()")
            }
            buildString {
               appendLine("run {")
               appendLine("   target.${it.name}.clear()")
               appendLine("   for (grpDec in decoder.${it.name}()) {")
               appendLine("      target.${it.name}.add($valueExpr)")
               appendLine("   }")
               append("}")
            }
         }
         else -> it.type.assignToInstanceField("target", it.name, it.type.fwGetter("decoder", it.name))
      }
   }

   return """
      |class ${className}R{
      |   private val headerDecoder = MessageHeaderDecoder()
      |   private val decoder : ${className}Decoder
      |${if (compositeFwDecls.isNotEmpty()) "\n${compositeFwDecls.indent(3)}" else ""}
      |
      |${headerAccessors}
      |
      |   constructor() : this(${if (isVariable) "ExpandableArrayBuffer()" else "UnsafeBuffer(ByteArray($sizeConstName))"}, 0)
      |
      |   constructor(buffer: DirectBuffer, offset: Int){
      |      this.decoder = ${className}Decoder()
      |      wrap(buffer, offset)
      |${compositeFwInits.indent(6)}
      |   }
      |
      |   fun wrap(buffer: DirectBuffer, offset: Int) = apply {
      |      decoder.wrapAndApplyHeader(buffer, offset, headerDecoder)
      |   }
      |
      |${compositeFwAccessors.indent(3)}
      |
      |${simpleFieldGetters.indent(3)}
      |
      |${unionGetters.indent(3)}
      |
      |   fun decodeInto(target: $className = new$className()): Int {
      |${decodeIntoBody.indent(6)}
      |      return MessageHeaderDecoder.ENCODED_LENGTH + decoder.encodedLength()
      |   }
      |
      |   fun size() = $sizeConstName
      |}
      |""".trimTemplate()
}

private fun generateWriterFlyweight(message: Message): String {
   val className = message.name
   val sizeConstName = className.sbeSizeConstant()

   val fieldsDecOrder = message.fields.sortedBy { it.declarationOrder }
   val fieldsSbeOrder = message.fields.sortedBy { it.sbeOrder }
   val compositeFields = fieldsDecOrder.filter { it.type is Composite }
   val simpleFields = fieldsDecOrder.filter { it.type !is Composite && it.type !is Message && it.type !is Union && it.type !is RepeatingGroup }
   val unionFields = fieldsDecOrder.filter { it.type is Union }

   val isVariable = message.hasVariableFields

   val compositeFwDecls = if (!isVariable) compositeFields.joinToString("\n") { "private val ${it.name}FW: ${it.type.name}W" } else ""
   val compositeFwInits = if (!isVariable) compositeFields.joinToString("\n") { "${it.name}FW = ${it.type.name}W(encoder.${it.name}())" } else ""
   val compositeFwAccessors = if (!isVariable) compositeFields.joinToString("\n\n") {
      "fun ${it.name}(): ${it.type.name}W {\n" +
      "   encoder.${it.name}()\n" +
      "   return ${it.name}FW\n" +
      "}"
   } else ""

   val simpleFieldSetters = if (!isVariable) simpleFields.joinToString("\n\n") {
      "fun ${it.name}(${it.name}: ${it.type.name}) = apply { ${it.type.fwSetter("encoder", it.name)} }"
   } else ""

   val unionSetters = if (!isVariable) generateUnionFwSetters(unionFields, className) else ""

   val encodeFromBody = fieldsSbeOrder.joinToString("\n") {
      when (it.type) {
         is Composite -> if (!isVariable) {
            "${it.name}FW.encodeFrom(source.${it.name})"
         } else {
            val composite = it.type as Composite
            composite.fields.sortedBy { f -> f.sbeOrder }.joinToString("\n") { f ->
               "encoder.${it.name}().${f.name}(${f.type.convertToSbe("source.${it.name}.${f.name}")})"
            }
         }
         is Message -> "encode${it.type.name}(source.${it.name}, encoder.buffer(), encoder.offset() + ${className}Encoder.${it.name}EncodingOffset())"
         is Union -> {
            val union = it.type
            val fieldOffset = "encoder.offset() + ${className}Encoder.${it.name}EncodingOffset()"
            buildString {
               appendLine("when (source.${it.name}) {")
               for (type in union.allowedTypes) {
                  appendLine("   is ${type.name} -> encode${type.name}(source.${it.name} as ${type.name}, encoder.buffer(), $fieldOffset)")
               }
               appendLine("   else -> throw IllegalArgumentException(\"Unexpected type in union field '${it.name}'\")")
               append("}")
            }
         }
         is IntArrayListGroup -> buildString {
            appendLine("run {")
            appendLine("   val grp = encoder.${it.name}Count(source.${it.name}.size)")
            appendLine("   for (i in 0 until source.${it.name}.size) {")
            appendLine("      grp.next()")
            appendLine("      grp.value(source.${it.name}.getInt(i))")
            appendLine("   }")
            append("}")
         }
         is LongArrayListGroup -> buildString {
            appendLine("run {")
            appendLine("   val grp = encoder.${it.name}Count(source.${it.name}.size)")
            appendLine("   for (i in 0 until source.${it.name}.size) {")
            appendLine("      grp.next()")
            appendLine("      grp.value(source.${it.name}.getLong(i))")
            appendLine("   }")
            append("}")
         }
         is ArrayListGroup -> {
            val elemType = it.type.elementType
            buildString {
               appendLine("run {")
               appendLine("   val grp = encoder.${it.name}Count(source.${it.name}.size)")
               appendLine("   for (item in source.${it.name}) {")
               appendLine("      grp.next()")
               when (elemType) {
                  is Struct -> {
                     for (f in elemType.fields.sortedBy { sf -> sf.sbeOrder }) {
                        appendLine("      grp.${f.name}(${f.type.convertToSbe("item.${f.name}")})")
                     }
                  }
                  else -> appendLine("      grp.value(${elemType.convertToSbe("item")})")
               }
               appendLine("   }")
               append("}")
            }
         }
         else -> it.type.encodeField("encoder", it.name, it.type.convertToSbe("source.${it.name}"))
      }
   }

   return """
      |class ${className}W{
      |   private val headerEncoder = MessageHeaderEncoder()
      |   private val encoder : ${className}Encoder
      |${if (compositeFwDecls.isNotEmpty()) "\n${compositeFwDecls.indent(3)}" else ""}
      |
      |   constructor() : this(${if (isVariable) "ExpandableArrayBuffer()" else "UnsafeBuffer(ByteArray($sizeConstName))"}, 0)
      |
      |   constructor(buffer: MutableDirectBuffer, offset: Int){
      |      this.encoder = ${className}Encoder()
      |      wrap(buffer, offset)
      |${compositeFwInits.indent(6)}
      |   }
      |
      |   fun wrap(buffer: MutableDirectBuffer, offset: Int) = apply {
      |      encoder.wrapAndApplyHeader(buffer, offset, headerEncoder)
      |   }
      |
      |${compositeFwAccessors.indent(3)}
      |
      |${simpleFieldSetters.indent(3)}
      |
      |${unionSetters.indent(3)}
      |
      |   fun encodeFrom(source: $className) : Int {
      |${encodeFromBody.indent(6)}
      |      return MessageHeaderDecoder.ENCODED_LENGTH + encoder.encodedLength()
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
