package sbe4kt.ir

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import sbe4kt.util.allowNullableTypes
import sbe4kt.util.compactFlags
import sbe4kt.util.getFieldIRs
import sbe4kt.util.id
import sbe4kt.util.packageName
import sbe4kt.util.sbeAnnotation
import sbe4kt.util.simpleName
import sbe4kt.util.usePadding

internal data class Message(
   val declaration: KSClassDeclaration
) : IR.Leaf, IR.Root{
   val annotation: KSAnnotation = declaration.sbeAnnotation()!!
   var id: Int = annotation.id()
   val usePadding: Boolean = annotation.usePadding()
   val allowNullableTypes: Boolean = annotation.allowNullableTypes()
   val compactFlags: Boolean = annotation.compactFlags()
   var blockLength: Int = -1
   val fields = declaration.getFieldIRs()


   override val name = declaration.simpleName()

   // When used as a nested field type in SBE schema, use ByteArrayN
   override val sbeName: String get() = "ByteArray$size"

   // When used as a nested field, include 8-byte header in size
   override val size: Int get() = 8 + blockLength

   override val defaultValue: String = "new${name}()"
   override val randomValue: String = "random${name}()"
   override val packageStr: String = declaration.packageName()

   override val imports: Set<String> = allImports().toSet()

   init {
      var currentOffset = 0

      fields
         .onEach { require(!it.isNullable || allowNullableTypes) { "nullable field '${it.name}' in message '${declaration.simpleName()}' is not allowed" } }
         .sortedByDescending { it.type.size }
         .forEachIndexed { index, field ->
            if (usePadding) {
               val padding = (8 - (currentOffset % 8)) % 8
               currentOffset += padding
            }
            field.offset = currentOffset
            field.sbeOrder = index + 1
            currentOffset += field.type.size
         }

      blockLength = if (usePadding) {
         val remainder = currentOffset % 8
         if (remainder == 0) currentOffset else currentOffset + (8 - remainder)
      } else {
         currentOffset
      }
   }

   // When used as a field in another message, handle as nested
   override fun howToEncodeFromSource(encoder: String, field: String, instance: String): String {
      // encoder is "signalEncoder" or "xyEncoder" etc -> "SignalEncoder" or "XYEncoder"
      val encoderClass = encoder.replaceFirstChar { it.uppercase() }
      return "encode${name}($instance.$field, buffer, offset + MessageHeaderEncoder.ENCODED_LENGTH + ${encoderClass}.${field}EncodingOffset())"
   }

   override fun howToDecodeIntoTarget(decoder: String, field: String, instance: String): String {
      // decoder is "signalDecoder" or "xyDecoder" etc -> "SignalDecoder" or "XYDecoder"
      val decoderClass = decoder.replaceFirstChar { it.uppercase() }
      return "decode${name}($instance.$field, buffer, offset + MessageHeaderEncoder.ENCODED_LENGTH + ${decoderClass}.${field}EncodingOffset())"
   }

   override fun fwSetter(encoder: String, field: String): String {
      return "${field}FW.encodeFrom($field)"
   }

   override fun fwGetter(decoder: String, field: String): String {
      return "new${name}().also { ${field}FW.decodeInto(it) }"
   }

   fun sourceFiles(): Array<KSFile> {
      // todo cleanup
      val dependentFiles = mutableSetOf(declaration.containingFile!!)
      fields.forEach { field ->
         when (val type = field.type) {
            is Struct -> type.declaration.containingFile?.let { dependentFiles.add(it) }
            is Message -> type.declaration.containingFile?.let { dependentFiles.add(it) }
            else -> {} // Primitive types don't have source files
         }
      }

      return dependentFiles.toTypedArray()
   }

   fun allImports(): Set<String> {
      val imports = mutableSetOf<String>()

      val packageName = packageStr
      imports.add("org.agrona.DirectBuffer")
      imports.add("org.agrona.MutableDirectBuffer")
      imports.add("org.agrona.concurrent.UnsafeBuffer")
      imports.add("sbe4kt.MessageHeaderEncoder")
      imports.add("sbe4kt.MessageHeaderDecoder")
      imports.add("sbe4kt.lib.CSV")
      imports.add("sbe4kt.${name}Encoder")
      imports.add("sbe4kt.${name}Decoder")
      fields.onEach { imports.addAll(it.type.imports) }

      // Add encoder/decoder imports for union composite types and their allowed types
      fields.filter { it.type is Union }.forEach { field ->
         val union = field.type as Union
         val baseName = union.sbeName.replaceFirstChar { it.uppercase() }
         imports.add("sbe4kt.${baseName}Encoder")
         imports.add("sbe4kt.${baseName}Decoder")
         // add imports for Message types (structs don't have standalone encoders/decoders)
         union.allowedTypes.filterIsInstance<Message>().forEach { allowedType ->
            imports.add("sbe4kt.${allowedType.name}Encoder")
            imports.add("sbe4kt.${allowedType.name}Decoder")
         }
      }

      return imports
         .filter { it.substringBeforeLast('.') != packageName && it != packageName }
         .sorted()
         .toSet()
   }
}