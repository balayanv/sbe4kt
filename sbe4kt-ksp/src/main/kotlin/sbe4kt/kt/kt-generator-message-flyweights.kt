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
   val className = message.name
   val sizeConstName = className.sbeSizeConstant()

   val fieldsDecOrder = message.fields.sortedBy { it.declarationOrder }
   val fieldsSbeOrder = message.fields.sortedBy { it.sbeOrder }
   val compositFields = fieldsDecOrder.filter { it.type is Composite }
   val nestedMessageFields = fieldsDecOrder.filter { it.type is Message }
   val unionFields = fieldsDecOrder.filter { it.type is Union }
   val simpleFields = fieldsDecOrder.filter { it.type !is Composite && it.type !is Message && it.type !is Union && it.type !is RepeatingGroup }

   // Composite FW fields
   val fwFieldDecls = compositFields.joinToString("\n") { "private val ${it.name}FW : ${it.type.name}RW" }

   // Composite accessor: re-wraps encoder/decoder on access
   val compositeFwAccessors = compositFields.joinToString("\n\n") {
      "fun ${it.name}(): ${it.type.name}RW {\n" +
      "   encoder.${it.name}()\n" +
      "   decoder.${it.name}()\n" +
      "   return ${it.name}FW\n" +
      "}"
   }

   // Simple field accessors: getter+setter grouped per field
   val simpleFieldAccessors = simpleFields.joinToString("\n\n") {
      "fun ${it.name}() = ${it.type.fwGetter("decoder", it.name)}\n" +
      "fun ${it.name}(${it.name}: ${it.type.name}) = apply { ${it.type.fwSetter("encoder", it.name)} }"
   }

   val groupFields = fieldsDecOrder.filter { it.type is RepeatingGroup }
   val groupFieldAccessors = generateGroupFwAccessors(groupFields)

   val unionFwGetters = generateUnionFwGetters(unionFields, className)
   val unionFwSetters = generateUnionFwSetters(unionFields, className)

   // encodeFrom body: delegates to own methods
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

   // decodeInto body: delegates to own methods
   val decodeIntoBody = fieldsSbeOrder.joinToString("\n") {
      when (it.type) {
         is Composite -> "${it.name}().decodeInto(target.${it.name})"
         is Message -> "decode${it.type.name}(target.${it.name}, encoder.buffer(), encoder.offset() + ${className}Encoder.${it.name}EncodingOffset())"
         is Union -> {
            val fieldOffset = "offset + MessageHeaderEncoder.ENCODED_LENGTH + ${className}Encoder.${it.name}EncodingOffset()"
            "target.${it.name} = ${it.name}()"
         }
         is RepeatingGroup -> "target.${it.name} = ${it.name}()"
         else -> "target.${it.name} = ${it.name}()"
      }
   }

   // Nested message FW declarations
   val nestedMessageFWDecls = nestedMessageFields.joinToString("\n") { "private val ${it.name}FW : ${it.type.name}RW" }
   val nestedMessageFwGetters = nestedMessageFields.joinToString("\n") { "fun ${it.name}FW() = ${it.name}FW" }

   // Constructor init for composite FWs
   val compositeFwInits = compositFields.joinToString("\n") { "${it.name}FW = ${it.type.name}RW(encoder.${it.name}(), decoder.${it.name}())" }

   return """
      |class ${className}RW{
      |   private val headerEncoder = MessageHeaderEncoder()
      |   private val headerDecoder = MessageHeaderDecoder()
      |
      |   private val encoder : ${className}Encoder
      |   private val decoder : ${className}Decoder
      |${if (fwFieldDecls.isNotEmpty()) "\n${fwFieldDecls.indent(3)}" else ""}
      |
      |   fun blockLength() = headerDecoder.blockLength()
      |   fun templateId() = headerDecoder.templateId()
      |   fun schemaId() = headerDecoder.schemaId()
      |   fun version() = headerDecoder.version()
      |
      |   constructor(encoder: ${className}Encoder, decoder: ${className}Decoder){
      |      this.encoder = encoder
      |      this.decoder = decoder
      |${compositeFwInits.indent(6)}
      |   }
      |
      |   constructor() : this(${if (message.hasVariableFields) "ExpandableArrayBuffer()" else "UnsafeBuffer(ByteArray($sizeConstName))"}, 0)
      |
      |   constructor(buffer: MutableDirectBuffer, offset: Int){
      |      this.encoder = ${className}Encoder()
      |      this.decoder = ${className}Decoder()
      |      wrap(buffer, offset)
      |${compositeFwInits.indent(6)}
      |   }
      |
      |   fun wrap(buffer: MutableDirectBuffer, offset: Int) = apply {
      |      encoder.wrapAndApplyHeader(buffer, offset, headerEncoder)
      |      decoder.wrapAndApplyHeader(buffer, offset, headerDecoder)
      |   }
      |
      |${compositeFwAccessors.indent(3)}
      |
      |${simpleFieldAccessors.indent(3)}
      |
      |${groupFieldAccessors.indent(3)}
      |${unionFwGetters.indent(3)}
      |${unionFwSetters.indent(3)}
      |
      |   fun encodeFrom(source: $className) : Int {
      |${encodeFromBody.indent(6)}
      |      return MessageHeaderDecoder.ENCODED_LENGTH + encoder.encodedLength()
      |   }
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

private fun generateGroupFwAccessors(groupFields: kotlin.collections.List<Field>): String {
   if (groupFields.isEmpty()) return ""
   return groupFields.joinToString("\n\n") { field ->
      val struct = (field.type as RepeatingGroup).elementType as Struct
      val fieldDecodes = struct.fields.sortedBy { it.sbeOrder }.joinToString(", ") { f ->
         "${f.name} = ${f.type.convertFromSbe("grpDec.${f.name}()")}"
      }

      val getter = when (field.type) {
         is Array -> buildString {
            appendLine("fun ${field.name}(): ${field.type.name} {")
            appendLine("   val grpDec = decoder.${field.name}()")
            appendLine("   return Array(grpDec.count()) {")
            appendLine("      grpDec.next()")
            appendLine("      ${struct.name}($fieldDecodes)")
            appendLine("   }")
            append("}")
         }
         is List -> buildString {
            appendLine("fun ${field.name}(): ${field.type.name} = buildList {")
            appendLine("   for (grpDec in decoder.${field.name}()) {")
            appendLine("      add(${struct.name}($fieldDecodes))")
            appendLine("   }")
            append("}")
         }
         else -> throw IllegalArgumentException("Unexpected RepeatingGroup subtype: ${field.type::class}")
      }

      val setter = buildString {
         appendLine("fun ${field.name}(${field.name}: ${field.type.name}) = apply {")
         appendLine("   val grp = encoder.${field.name}Count(${field.name}.size)")
         appendLine("   for (item in ${field.name}) {")
         appendLine("      grp.next()")
         for (f in struct.fields.sortedBy { it.sbeOrder }) {
            appendLine("      grp.${f.name}(${f.type.convertToSbe("item.${f.name}")})")
         }
         appendLine("   }")
         append("}")
      }

      "$getter\n$setter"
   }
}

private fun generateUnionFwGetters(unionFields: kotlin.collections.List<Field>, className: String): String {
   if (unionFields.isEmpty()) return ""
   return unionFields.joinToString("\n") { field ->
      val union = field.type as Union
      val fieldOffset = "encoder.offset() + ${className}Encoder.${field.name}EncodingOffset()"
      buildString {
         appendLine("fun ${field.name}(): ${union.name} {")
         appendLine("   val ${field.name}Header = MessageHeaderDecoder()")
         appendLine("   ${field.name}Header.wrap(encoder.buffer(), $fieldOffset)")
         appendLine("   return when (${field.name}Header.templateId()) {")
         for (type in union.allowedTypes) {
            appendLine("      ${type.name}Encoder.TEMPLATE_ID -> new${type.name}().also { decode${type.name}(it, encoder.buffer(), $fieldOffset) }")
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
