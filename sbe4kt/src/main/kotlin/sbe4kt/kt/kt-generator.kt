package sbe4kt.kt

import com.google.devtools.ksp.processing.Dependencies
import sbe4kt.KSP.gen
import sbe4kt.KSP.ir
import sbe4kt.KSP.log
import sbe4kt.ir.IR

internal fun generateKotlinExtensions() {
   log.info("Generating Kotlin extensions...")
   ir.types.forEach { message ->
      log.info("Generating extensions for ${message.className}")
      generateKotlinExtensionsForMessage(message)
   }
}

private fun generateKotlinExtensionsForMessage(
   message: sbe4kt.ir.IrType
) {
   val packageName = message.packageName
   val className = message.className

   val file = gen.createNewFile(
      Dependencies(false, message.type.containingFile!!),
      packageName,
      "${className}Extensions"
   )

   file.bufferedWriter().use { writer ->
      // Sort fields by declaration order for factory function
      val fieldsInDeclarationOrder = message.fields.sortedBy { it.declarationOrder }

      val factoryParams = fieldsInDeclarationOrder.joinToString(",\n") { field ->
         "    ${field.name}: ${field.type.kotlinType} = ${field.type.defaultValue}"
      }

      val factoryAssignments = fieldsInDeclarationOrder.joinToString(",\n") { field ->
         "        ${field.name} = ${field.name}"
      }

      val assignments = fieldsInDeclarationOrder.joinToString(",\n") { field ->
         val randomValue = when (field.type) {
            is IR.Boolean -> "kotlin.random.Random.nextBoolean()"
            is IR.Int8 -> "kotlin.random.Random.nextInt().toByte()"
            is IR.Int16 -> "kotlin.random.Random.nextInt().toShort()"
            is IR.Int32 -> "kotlin.random.Random.nextInt()"
            is IR.Int64 -> "kotlin.random.Random.nextLong()"
            is IR.Float -> "kotlin.random.Random.nextFloat()"
            is IR.Double -> "kotlin.random.Random.nextDouble()"
            is IR.Char -> "kotlin.random.Random.nextInt(32, 127).toChar()"
         }
         "        ${field.name} = $randomValue"
      }

      val encoderBody = message.fields.joinToString("\n") { field ->
         when (field.type) {
            is IR.Boolean ->
               "    encoder.${field.name}(if (instance.${field.name}) 1.toShort() else 0.toShort())"
            is IR.Char ->
               "    encoder.${field.name}(instance.${field.name}.code.toByte())"
            else ->
               "    encoder.${field.name}(instance.${field.name})"
         }
      }

      val decoderBody = message.fields.joinToString("\n") { field ->
         when (field.type) {
            is IR.Boolean ->
               "    instance.${field.name} = decoder.${field.name}() > 0"
            is IR.Char ->
               "    instance.${field.name} = decoder.${field.name}().toInt().toChar()"
            else ->
               "    instance.${field.name} = decoder.${field.name}()"
         }
      }

      var currentOffset = 0
      
      val printLayoutBody = message.fields.joinToString("\n") { field ->
         var paddingLog = ""
         if (message.usePadding) {
            val padding = (8 - (currentOffset % 8)) % 8
            if (padding > 0) {
               paddingLog =
                  "        .addRow(\"$currentOffset\", \"PADDING\", \"$padding\", \"N/A\")\n"
               currentOffset += padding
            }
         }

         val size = field.type.size
         val name = field.name
         val offset = currentOffset
         currentOffset += size

         "$paddingLog        .addRow(\"$offset\", \"$name\", \"$size\", this.$name.toString())"
      }

      writer.write("""
package $packageName
 
 import org.agrona.concurrent.UnsafeBuffer
 import sbe4kt.${className}Encoder
 import sbe4kt.${className}Decoder
 import sbe4kt.MessageHeaderEncoder
 import sbe4kt.MessageHeaderDecoder

// Factory method
fun new$className(
$factoryParams
): $className {
    return $className(
$factoryAssignments
    )
}

// Random factory method
fun random$className(): $className {
    return $className(
$assignments
    )
}

// Encode method
fun encode$className(buffer: UnsafeBuffer, instance: $className): Int {
    val encoder = ${className}Encoder()
    val headerEncoder = MessageHeaderEncoder()

    encoder.wrapAndApplyHeader(buffer, 0, headerEncoder)

$encoderBody

    return MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength()
}

// Decode method
fun decode$className(buffer: UnsafeBuffer, instance: $className): Int {
    val decoder = ${className}Decoder()
    val headerDecoder = MessageHeaderDecoder()

    headerDecoder.wrap(buffer, 0)

    decoder.wrap(
        buffer,
        MessageHeaderEncoder.ENCODED_LENGTH,
        headerDecoder.blockLength(),
        headerDecoder.version()
    )

$decoderBody

    return MessageHeaderDecoder.ENCODED_LENGTH + decoder.encodedLength()
}

// Memory Layout Method
fun $className.printSbeLayout() {
    println("SBE Layout for $className:")
    sbe4kt.util.CSV()
        .addHeader("Offset", "Name", "Size", "Value")
$printLayoutBody
        .print()
}
""".trimIndent())
   }
}
