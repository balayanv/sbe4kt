package sbe4kt.kt

import com.google.devtools.ksp.processing.Dependencies
import sbe4kt.KSP.gen
import sbe4kt.KSP.ir
import sbe4kt.KSP.log
import sbe4kt.ir.IR

// Extension function to convert PascalCase to snake_case
fun String.toSnakeCase(): String {
    return this.replace(Regex("([a-z])([A-Z])"), "$1_$2")
        .replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1_$2")
}

private fun generateAllCompositeHelpers(compositeTypes: Set<IR.Composite>) {
   if (compositeTypes.isEmpty()) return

   val file = gen.createNewFile(
      Dependencies(false),
      "sbe4kt",
      "CommonExtensions"
   )

   file.bufferedWriter().use { writer ->
      // Collect all imports from all composite types
      val imports = mutableSetOf<String>()
      compositeTypes.forEach { composite ->
         composite.fields.forEach { field ->
            // Don't import types from the same package (sbe4kt)
            if (field.type.packageStr.isNotEmpty() && field.type.packageStr != "sbe4kt") {
               imports.add("${field.type.packageStr}.${field.type.name}")
            }
         }
      }

      val importsSection = if (imports.isNotEmpty()) {
         imports.sorted().joinToString("\n") { "import $it" } + "\n"
      } else {
         ""
      }

      val allHelpers = compositeTypes.joinToString("\n\n") { composite ->
         val className = composite.name

         val readMethods = composite.fields.joinToString("\n") { field ->
            val decodeLogic = field.type.convertFromSbe("${className.lowercase()}R.${field.name}()")
            "   fun ${field.name}() = $decodeLogic"
         }
         val writeMethods = composite.fields.joinToString("\n") { field ->
            val paramType = field.type.name
            val encodeLogic = field.type.convertToSbe(field.name)
            "   fun ${field.name}(${field.name}: $paramType) = apply { ${className.lowercase()}W.${field.name}($encodeLogic) }"
         }

         """class ${className}RW(
   private var ${className.lowercase()}W: ${className}Encoder,
   private var ${className.lowercase()}R: ${className}Decoder,
) {
$readMethods
$writeMethods
}

class ${className}R(
   private var ${className.lowercase()}R: ${className}Decoder,
) {
$readMethods
}

class ${className}W(
   private var ${className.lowercase()}W: ${className}Encoder,
) {
$writeMethods
}""".trimIndent()
      }

      writer.write(
         """
@file:Suppress("unused")

package sbe4kt

$importsSection
$allHelpers
""".trimIndent())
   }
}

internal fun generateKotlinExtensions() {
   log.info("Generating Kotlin extensions...")

   // First generate composite helpers
   val compositeTypes = mutableSetOf<IR.Composite>()
   ir.types.forEach { message ->
      message.fields.forEach { field ->
         if (field.type is IR.Composite) {
            compositeTypes.add(field.type)
         }
      }
   }

   // Generate all composite helpers in a single file
   generateAllCompositeHelpers(compositeTypes)

   ir.types.forEach { message ->
      log.info("Generating extensions for ${message.className}")
      generateKotlinExtensionsForMessage(message)
   }
}

private fun generateKotlinExtensionsForMessage(
   message: sbe4kt.ir.IrMessage
) {
   val packageName = message.packageName
   val className = message.className
   val fileName = "${className}Extensions"
   val file = gen.createNewFile(
      Dependencies(false, message.type.containingFile!!),
      packageName,
      fileName
   )

   file.bufferedWriter().use { writer ->
      // Collect imports for nested enums and value classes
      val imports = mutableSetOf<String>()

      message.fields.forEach { field ->
         field.addPackageIfNecessary(packageName, imports)
      }

      // Sort fields by declaration order for factory function
      val fieldsInDeclarationOrder = message.fields.sortedBy { it.declarationOrder }

      val defaultParams = fieldsInDeclarationOrder.joinToString(",\n") { field ->
         "   ${field.name}: ${field.type.name} = ${field.type.defaultValue}"
      }

      val randomParams = fieldsInDeclarationOrder.joinToString(",\n") { field ->
         "   ${field.name}: ${field.type.name} = ${field.type.randomValue}"
      }

      val assignments = fieldsInDeclarationOrder.joinToString(",\n") { field ->
         "     ${field.name} = ${field.name}"
      }

      // Create encoder/decoder variable names based on class name
      val encoderName = className.replaceFirstChar { it.lowercase() } + "Encoder"
      val decoderName = className.replaceFirstChar { it.lowercase() } + "Decoder"

      // Sort fields by SBE order for encoder (matches SBE schema field order)
      val sortedFieldsForEncoding = message.fields.sortedBy { it.sbeOrder }
      val encoderBody = sortedFieldsForEncoding.joinToString("\n") { field ->
         field.type.howToEncodeFromInstance(encoderName, field.name, "instance")
             .addPrefixToEachLine("   ")
      }

      // Sort fields by SBE order for decoder (matches SBE schema field order)
      val decoderBody = sortedFieldsForEncoding.joinToString("\n") { field ->
          field.type.howToDecodeIntoInstance(decoderName, field.name, "instance")
              .addPrefixToEachLine("   ")
      }

      // Use pre-computed blockLength from IR (computed in prepare())
      val totalSize = message.blockLength

      // Build layout display using pre-computed offsets from IR
      val sortedFields = message.fields.sortedBy { it.sbeOrder }
      // Prepare composite initialization code
      val compositeInitRW = if (sortedFieldsForEncoding.any { it.type is IR.Composite }) {
         sortedFieldsForEncoding.filter { it.type is IR.Composite }.joinToString("\n") { field ->
            "     $encoderName.${field.name}()\n     $decoderName.${field.name}()"
         }
      } else ""

      val compositeInitW = if (sortedFieldsForEncoding.any { it.type is IR.Composite }) {
         sortedFieldsForEncoding.filter { it.type is IR.Composite }.joinToString("\n") { field ->
            "     $encoderName.${field.name}()"
         }
      } else ""

      val compositeInitR = if (sortedFieldsForEncoding.any { it.type is IR.Composite }) {
         sortedFieldsForEncoding.filter { it.type is IR.Composite }.joinToString("\n") { field ->
            "     $decoderName.${field.name}()"
         }
      } else ""

      val layoutRows = mutableListOf<String>()

      sortedFields.forEachIndexed { index, field ->
         val displayOffset = field.offset + 8 // Add header size for display

         // Add padding row if there's a gap before this field
         if (index > 0 && message.usePadding) {
            val prevField = sortedFields[index - 1]
            val prevFieldEnd = prevField.offset + prevField.type.size + 8
            val paddingSize = displayOffset - prevFieldEnd
            if (paddingSize > 0) {
               layoutRows.add(".addRow(\"$prevFieldEnd\", \"[PADDING]\", \"\", \"$paddingSize\", \"\")")
            }
         } else if (index == 0 && message.usePadding && field.offset > 0) {
            // Padding before first field if it doesn't start at offset 0
            layoutRows.add(".addRow(\"8\", \"[PADDING]\", \"\", \"${field.offset}\", \"\")")
         }

         val displayTypeName = field.type.name
         layoutRows.add(".addRow(\"$displayOffset\", \"${field.name}\", \"${displayTypeName}\", \"${field.type.size}\", this.${field.name}.toString())")
      }

      // Add final padding if message needs it for alignment
      if (message.usePadding && sortedFields.isNotEmpty()) {
         val lastField = sortedFields.last()
         val lastFieldEnd = lastField.offset + lastField.type.size
         val finalPadding = message.blockLength - lastFieldEnd
         if (finalPadding > 0) {
            val finalPaddingOffset = lastFieldEnd + 8
            layoutRows.add(".addRow(\"$finalPaddingOffset\", \"[PADDING]\", \"\", \"$finalPadding\", \"\")")
         }
      }

      val printLayoutBodyWithHeader = layoutRows.joinToString("\n").addPrefixToEachLine("     ")


      val sizeConstName = "SBE_SIZE_${className.toSnakeCase().uppercase(java.util.Locale.ROOT)}"

      val importsSection = if (imports.isNotEmpty()) {
         imports.joinToString("\n") { "import $it" } + "\n"
      } else {
         ""
      }

      // For RW class, prepare composite helpers
      val compositeHelperFields = sortedFieldsForEncoding
         .filter { it.type is IR.Composite }
         .joinToString("\n") { field ->
            val compositeType = field.type as IR.Composite
            "   private var ${field.name}RW = ${compositeType.name}RW($encoderName.${field.name}(), $decoderName.${field.name}())"
         }
      val compositeHelperFieldsWithPrefix = if (compositeHelperFields.isNotEmpty()) "\n$compositeHelperFields\n" else ""

      // For W class, prepare write-only composite helpers
      val compositeWriteHelperFields = sortedFieldsForEncoding
         .filter { it.type is IR.Composite }
         .joinToString("\n") { field ->
            val compositeType = field.type as IR.Composite
            "   private var ${field.name}W = ${compositeType.name}W($encoderName.${field.name}())"
         }
      val compositeWriteHelperFieldsWithPrefix = if (compositeWriteHelperFields.isNotEmpty()) "\n$compositeWriteHelperFields\n" else ""

      // For R class, prepare read-only composite helpers
      val compositeReadHelperFields = sortedFieldsForEncoding
         .filter { it.type is IR.Composite }
         .joinToString("\n") { field ->
            val compositeType = field.type as IR.Composite
            "   private var ${field.name}R = ${compositeType.name}R($decoderName.${field.name}())"
         }
      val compositeReadHelperFieldsWithPrefix = if (compositeReadHelperFields.isNotEmpty()) "\n$compositeReadHelperFields\n" else ""

      val flyweightSetters = sortedFieldsForEncoding.joinToString("\n") { field ->
         if (field.type is IR.Composite) {
            // Composite fields don't have setters, accessed via helper
            ""
         } else {
            "   fun ${field.name}(${field.name}: ${field.type.name}) = apply { ${field.type.howToEncodeFromField(encoderName, field.name)} }"
         }
      }.replace(Regex("\n+"), "\n") // Clean up multiple empty lines

      val flyweightGetters = sortedFieldsForEncoding.joinToString("\n") { field ->
         if (field.type is IR.Composite) {
            // Return the helper for composite fields
            "   fun ${field.name}() = ${field.name}RW"
         } else {
            "   fun ${field.name}(): ${field.type.name} = ${field.type.howToDecodeFromField(decoderName, field.name)}"
         }
      }

      val flyweightWriteSetters = sortedFieldsForEncoding.joinToString("\n") { field ->
         if (field.type is IR.Composite) {
            // Return write helper for composite fields
            "   fun ${field.name}() = ${field.name}W"
         } else {
            "   fun ${field.name}(${field.name}: ${field.type.name}) = apply { ${field.type.howToEncodeFromField(encoderName, field.name)} }"
         }
      }.replace(Regex("\n+"), "\n") // Clean up multiple empty lines

      val flyweightReadGetters = sortedFieldsForEncoding.joinToString("\n") { field ->
         if (field.type is IR.Composite) {
            // Return read helper for composite fields
            "   fun ${field.name}() = ${field.name}R"
         } else {
            "   fun ${field.name}(): ${field.type.name} = ${field.type.howToDecodeFromField(decoderName, field.name)}"
         }
      }

      writer.write(
         $$"""
@file:Suppress("unused")

package $$packageName

import org.agrona.concurrent.UnsafeBuffer
import sbe4kt.$${className}Encoder
import sbe4kt.$${className}Decoder
import sbe4kt.MessageHeaderEncoder
import sbe4kt.MessageHeaderDecoder
import sbe4kt.util.CSV
import kotlin.random.Random
$${if (sortedFieldsForEncoding.any { it.type is IR.Composite }) "import sbe4kt.*" else ""}
$$importsSection
const val $$sizeConstName = MessageHeaderEncoder.ENCODED_LENGTH + $$totalSize

@JvmOverloads
fun new$$className(
$$defaultParams
): $$className {
   return $$className(
$$assignments
   )
}

@JvmOverloads
fun random$$className(
$$randomParams
): $$className {
   return $$className(
$$assignments
   )
}

fun encode$$className(instance: $$className, buffer: UnsafeBuffer, offset: Int = 0): Int {
   val headerEncoder = MessageHeaderEncoder()
   val $$encoderName = $${className}Encoder()
   $$encoderName.wrapAndApplyHeader(buffer, offset, headerEncoder)

$$encoderBody

   return MessageHeaderEncoder.ENCODED_LENGTH + $$encoderName.encodedLength()
}

fun decode$$className(instance: $$className, buffer: UnsafeBuffer, offset: Int = 0): Int {
   val headerDecoder = MessageHeaderDecoder()
   val $$decoderName = $${className}Decoder()

   headerDecoder.wrap(buffer, offset)

   $$decoderName.wrap(
      buffer,
      offset + MessageHeaderEncoder.ENCODED_LENGTH,
      headerDecoder.blockLength(),
      headerDecoder.version()
   )

$$decoderBody

   return MessageHeaderDecoder.ENCODED_LENGTH + $$decoderName.encodedLength()
}

fun $$className.encodeTo(buffer: UnsafeBuffer, offset: Int = 0): Int = encode$$className(this, buffer, offset)
fun $$className.decodeFrom(buffer: UnsafeBuffer, offset: Int = 0): Int = decode$$className(this, buffer, offset)

/**
 * Flyweight for using a buffer as a pojo, all reads and writes are direct with the buffer,
 * this means serialization time is truly 0 and there is no need for creating instances of
 * the actual message type (however convenince encode/decode methods are provided in case you do).
 */
class $${className}RW(
   var buffer: UnsafeBuffer = UnsafeBuffer(ByteArray($$sizeConstName)),
   var offset: Int = 0
) {
   private val headerEncoder = MessageHeaderEncoder()
   private val $$encoderName = $${className}Encoder()
   private val headerDecoder = MessageHeaderDecoder()
   private val $$decoderName = $${className}Decoder()
$$compositeHelperFieldsWithPrefix   
   init { prepareBuffer() }

   private fun prepareBuffer() {
      check(buffer.capacity() - offset >= $$sizeConstName) { "buffer has not enough capacity for '$$className' (need at least : $$$sizeConstName, actual : ${buffer.capacity()})" }
      $$encoderName.wrapAndApplyHeader(buffer, offset, headerEncoder)
      headerDecoder.wrap(buffer, offset)
      $$decoderName.wrap(buffer, offset + headerDecoder.encodedLength(), headerDecoder.blockLength(), headerDecoder.version())
$$compositeInitRW
   }

   fun wrap(buffer: UnsafeBuffer, offset: Int) = apply {
      this.buffer = buffer
      this.offset = offset
      prepareBuffer()
   }

$$flyweightGetters
$$flyweightSetters
   fun encode(instance: $$className) = apply {
$${encoderBody.addPrefixToEachLine("     ")}
   }

   fun decode(instance: $$className = new$$className()): $$className {
$${decoderBody.addPrefixToEachLine("     ")}
      return instance
   }
}

/**
 * Write only variant for cases when you only want to write and save few instructions when wrapping buffers frequently.
 */
class $${className}W(
   var buffer: UnsafeBuffer = UnsafeBuffer(ByteArray($$sizeConstName)),
   var offset: Int = 0
) {
   private val headerEncoder = MessageHeaderEncoder()
   private val $$encoderName = $${className}Encoder()
$$compositeWriteHelperFieldsWithPrefix   
   init { prepareBuffer() }

   private fun prepareBuffer() {
      check(buffer.capacity() - offset >= $$sizeConstName) { "buffer has not enough capacity for '$$className' (need at least : $$$sizeConstName, actual : ${buffer.capacity()})" }
      $$encoderName.wrapAndApplyHeader(buffer, offset, headerEncoder)
$$compositeInitW
   }

   fun wrap(buffer: UnsafeBuffer, offset: Int) = apply {
      this.buffer = buffer
      this.offset = offset
      prepareBuffer()
   }

$$flyweightWriteSetters
   fun encode(instance: $$className) = apply {
$${encoderBody.addPrefixToEachLine("     ")}
   }
}

/**
 * Read only variant for cases when you only want to read and save few instructions when wrapping buffers frequently.
 */
class $${className}R(
   var buffer: UnsafeBuffer = UnsafeBuffer(ByteArray($$sizeConstName)),
   var offset: Int = 0
) {
   private val headerDecoder = MessageHeaderDecoder()
   private val $$decoderName = $${className}Decoder()
$$compositeReadHelperFieldsWithPrefix   
   init { prepareBuffer() }

   private fun prepareBuffer() {
      check(buffer.capacity() - offset >= $$sizeConstName) { "buffer has not enough capacity for '$$className' (need at least : $$$sizeConstName, actual : ${buffer.capacity()})" }
      headerDecoder.wrap(buffer, offset)
      $$decoderName.wrap(buffer, offset + headerDecoder.encodedLength(), headerDecoder.blockLength(), headerDecoder.version())
$$compositeInitR
   }

   fun wrap(buffer: UnsafeBuffer, offset: Int) = apply {
      this.buffer = buffer
      this.offset = offset
      prepareBuffer()
   }

$$flyweightReadGetters
   fun decode(instance: $$className = new$$className()): $$className {
$${decoderBody.addPrefixToEachLine("     ")}
      return instance
   }
}

fun $$className.printSbeLayout() {
   val tempBuffer = UnsafeBuffer(ByteArray($$sizeConstName))
   this.encodeTo(tempBuffer)

   val header = MessageHeaderDecoder()
   header.wrap(tempBuffer, 0)
   val headerValue = "${header.blockLength()}|${header.templateId()}|${header.schemaId()}|${header.version()}"

   CSV()
      .info("$$className (totalSize = $$$sizeConstName bytes)")
      .addHeader("Offset", "Field", "Type", "Size", "Value")
      .addRow("0", "[HEADER]", "Header", "${MessageHeaderEncoder.ENCODED_LENGTH}", headerValue)
$$printLayoutBodyWithHeader
      .print()
}
""".trimIndent())
   }
}

fun String.addPrefixToEachLine(prefix: String): String {
    return lines().joinToString("\n") { prefix + it }
}
