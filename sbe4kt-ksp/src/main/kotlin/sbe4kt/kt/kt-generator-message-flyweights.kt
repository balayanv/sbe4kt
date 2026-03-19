package sbe4kt.kt

import sbe4kt.ir.Array
import sbe4kt.ir.Composite
import sbe4kt.ir.Field
import sbe4kt.ir.List
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
   val groupFields = fieldsDecOrder.filter { it.type is RepeatingGroup }
   val unionFields = fieldsDecOrder.filter { it.type is Union }

   val compositeFwDecls = compositeFields.joinToString("\n") { "private val ${it.name}FW: ${it.type.name}R" }
   val compositeFwInits = compositeFields.joinToString("\n") { "${it.name}FW = ${it.type.name}R(decoder.${it.name}())" }
   val compositeFwAccessors = compositeFields.joinToString("\n\n") {
      "fun ${it.name}(): ${it.type.name}R {\n" +
      "   decoder.${it.name}()\n" +
      "   return ${it.name}FW\n" +
      "}"
   }

   val simpleFieldGetters = simpleFields.joinToString("\n\n") {
      "fun ${it.name}() = ${it.type.fwGetter("decoder", it.name)}"
   }

   val groupFieldGetters = generateGroupFwGetters(groupFields)
   val unionGetters = generateUnionFwGetters(unionFields, className, "decoder")

   val decodeIntoBody = fieldsSbeOrder.joinToString("\n") {
      when (it.type) {
         is Composite -> "${it.name}().decodeInto(target.${it.name})"
         is Message -> "decode${it.type.name}(target.${it.name}, decoder.buffer(), decoder.offset() + ${className}Encoder.${it.name}EncodingOffset())"
         is Union -> "target.${it.name} = ${it.name}()"
         is RepeatingGroup -> "target.${it.name} = ${it.name}()"
         else -> "target.${it.name} = ${it.name}()"
      }
   }

   return """
      |class ${className}R{
      |   private val headerDecoder = MessageHeaderDecoder()
      |   private val decoder : ${className}Decoder
      |${if (compositeFwDecls.isNotEmpty()) "\n${compositeFwDecls.indent(3)}" else ""}
      |
      |   fun blockLength() = headerDecoder.blockLength()
      |   fun templateId() = headerDecoder.templateId()
      |   fun schemaId() = headerDecoder.schemaId()
      |   fun version() = headerDecoder.version()
      |
      |   constructor(encoder: ${className}Encoder, decoder: ${className}Decoder){
      |      this.decoder = decoder
      |${compositeFwInits.indent(6)}
      |   }
      |
      |   constructor() : this(${if (message.hasVariableFields) "ExpandableArrayBuffer()" else "UnsafeBuffer(ByteArray($sizeConstName))"}, 0)
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
      |${groupFieldGetters.indent(3)}
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
   val groupFields = fieldsDecOrder.filter { it.type is RepeatingGroup }
   val unionFields = fieldsDecOrder.filter { it.type is Union }

   val compositeFwDecls = compositeFields.joinToString("\n") { "private val ${it.name}FW: ${it.type.name}W" }
   val compositeFwInits = compositeFields.joinToString("\n") { "${it.name}FW = ${it.type.name}W(encoder.${it.name}())" }
   val compositeFwAccessors = compositeFields.joinToString("\n\n") {
      "fun ${it.name}(): ${it.type.name}W {\n" +
      "   encoder.${it.name}()\n" +
      "   return ${it.name}FW\n" +
      "}"
   }

   val simpleFieldSetters = simpleFields.joinToString("\n\n") {
      "fun ${it.name}(${it.name}: ${it.type.name}) = apply { ${it.type.fwSetter("encoder", it.name)} }"
   }

   val groupFieldSetters = generateGroupFwSetters(groupFields)
   val unionSetters = generateUnionFwSetters(unionFields, className)

   val encodeFromBody = fieldsSbeOrder.joinToString("\n") {
      when (it.type) {
         is Composite -> "${it.name}().encodeFrom(source.${it.name})"
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
         is RepeatingGroup -> "${it.name}(source.${it.name})"
         else -> "${it.name}(source.${it.name})"
      }
   }

   return """
      |class ${className}W{
      |   private val headerEncoder = MessageHeaderEncoder()
      |   private val encoder : ${className}Encoder
      |${if (compositeFwDecls.isNotEmpty()) "\n${compositeFwDecls.indent(3)}" else ""}
      |
      |   constructor(encoder: ${className}Encoder, decoder: ${className}Decoder){
      |      this.encoder = encoder
      |${compositeFwInits.indent(6)}
      |   }
      |
      |   constructor() : this(${if (message.hasVariableFields) "ExpandableArrayBuffer()" else "UnsafeBuffer(ByteArray($sizeConstName))"}, 0)
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
      |${groupFieldSetters.indent(3)}
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

private fun generateGroupFwGetters(groupFields: kotlin.collections.List<Field>): String {
   if (groupFields.isEmpty()) return ""
   return groupFields.joinToString("\n\n") { field ->
      val elemType = (field.type as RepeatingGroup).elementType
      val valueExpr = when (elemType) {
         is Struct -> {
            val fieldDecodes = elemType.fields.sortedBy { it.sbeOrder }.joinToString(", ") { f ->
               "${f.name} = ${f.type.convertFromSbe("grpDec.${f.name}()")}"
            }
            "${elemType.name}($fieldDecodes)"
         }
         else -> elemType.convertFromSbe("grpDec.value()")
      }
      when (field.type) {
         is Array -> buildString {
            appendLine("fun ${field.name}(): ${field.type.name} {")
            appendLine("   val grpDec = decoder.${field.name}()")
            appendLine("   return Array(grpDec.count()) {")
            appendLine("      grpDec.next()")
            appendLine("      $valueExpr")
            appendLine("   }")
            append("}")
         }
         is List -> buildString {
            appendLine("fun ${field.name}(): ${field.type.name} = buildList {")
            appendLine("   for (grpDec in decoder.${field.name}()) {")
            appendLine("      add($valueExpr)")
            appendLine("   }")
            append("}")
         }
         else -> throw IllegalArgumentException("Unexpected RepeatingGroup subtype: ${field.type::class}")
      }
   }
}

private fun generateGroupFwSetters(groupFields: kotlin.collections.List<Field>): String {
   if (groupFields.isEmpty()) return ""
   return groupFields.joinToString("\n\n") { field ->
      val elemType = (field.type as RepeatingGroup).elementType
      buildString {
         appendLine("fun ${field.name}(${field.name}: ${field.type.name}) = apply {")
         appendLine("   val grp = encoder.${field.name}Count(${field.name}.size)")
         appendLine("   for (item in ${field.name}) {")
         appendLine("      grp.next()")
         when (elemType) {
            is Struct -> {
               for (f in elemType.fields.sortedBy { it.sbeOrder }) {
                  appendLine("      grp.${f.name}(${f.type.convertToSbe("item.${f.name}")})")
               }
            }
            else -> appendLine("      grp.value(${elemType.convertToSbe("item")})")
         }
         appendLine("   }")
         append("}")
      }
   }
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
