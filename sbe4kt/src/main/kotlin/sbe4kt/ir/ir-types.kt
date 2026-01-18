package sbe4kt.ir

import com.google.devtools.ksp.symbol.KSClassDeclaration
import sbe4kt.util.packageName
import sbe4kt.util.simpleName

internal sealed interface IR {
    val sbeName: String
    val name: String
    val defaultValue: String
    val randomValue: String
    val size: Int
    val packageStr: String

    // encoding logic
    fun encodeField(encoder: String, field: String, value: String) = "$encoder.$field($value)"
    fun convertToSbe(value: String) = value
    fun howToEncodeFromInstance(encoder: String, field: String, instance: String) =
        encodeField(encoder, field, convertToSbe("$instance.$field"))

    fun howToEncodeFromField(encoder: String, field: String) = encodeField(encoder, field, convertToSbe(field))

    // decoding logic
    fun decodeField(decoder: String, field: String) = "$decoder.$field()"
    fun convertFromSbe(value: String) = value
    fun assignToInstanceField(instance: String, field: String, value: String) = "$instance.$field = $value"
    fun howToDecodeIntoInstance(decoder: String, field: String, instance: String) =
        assignToInstanceField(instance, field, convertFromSbe(decodeField(decoder, field)))

    fun howToDecodeFromField(decoder: String, field: String): String = convertFromSbe(decodeField(decoder, field))

    data object Boolean : IR {
        override val sbeName = "Bool"
        override val name = "Boolean"
        override val defaultValue = "false"
        override val randomValue = "Random.nextBoolean()"
        override val size = 1
        override val packageStr = ""
        override fun convertToSbe(value: String) = "if ($value) sbe4kt.Bool.T else sbe4kt.Bool.F"
        override fun convertFromSbe(value: String) = "$value == sbe4kt.Bool.T"
    }

    data object Char : IR {
        override val sbeName = "char"
        override val name = "Char"
        override val defaultValue = "'\\u0000'"
        override val randomValue = "Random.nextInt(32, 127).toChar()"
        override val size = 1
        override val packageStr = ""
        override fun convertToSbe(value: String) = "$value.code.toByte()"
        override fun convertFromSbe(value: String) = "$value.toInt().toChar()"
    }

    data object Int8 : IR {
        override val sbeName = "int8"
        override val name = "Byte"
        override val defaultValue = "0.toByte()"
        override val randomValue = "Random.nextInt().toByte()"
        override val size = 1
        override val packageStr = ""
    }

    data object Int16 : IR {
        override val sbeName = "int16"
        override val name = "Short"
        override val defaultValue = "0.toShort()"
        override val randomValue = "Random.nextInt().toShort()"
        override val size = 2
        override val packageStr = ""
    }

    data object Int32 : IR {
        override val sbeName = "int32"
        override val name = "Int"
        override val defaultValue = "0"
        override val randomValue = "Random.nextInt()"
        override val size = 4
        override val packageStr = ""
    }

    data object Int64 : IR {
        override val sbeName = "int64"
        override val name = "Long"
        override val defaultValue = "0L"
        override val randomValue = "Random.nextLong()"
        override val size = 8
        override val packageStr = ""
    }

    data object Float : IR {
        override val sbeName = "float"
        override val name = "Float"
        override val defaultValue = "0.0f"
        override val randomValue = "Random.nextFloat()"
        override val size = 4
        override val packageStr = ""
    }

    data object Double : IR {
        override val sbeName = "double"
        override val name = "Double"
        override val defaultValue = "0.0"
        override val randomValue = "Random.nextDouble()"
        override val size = 8
        override val packageStr = ""
    }


    data class Enum(
        val declaration: KSClassDeclaration,
    ) : IR {
        override val sbeName = "uint8"
        override val name: String = declaration.simpleName()

        override val packageStr = declaration.packageName()

        override val defaultValue = "$name.entries[0]"
        override val randomValue = "$name.entries[Random.nextInt($name.entries.size)]"
        override val size = 1

        override fun convertToSbe(value: String) = "$value.ordinal.toShort()"
        override fun convertFromSbe(value: String) = "$name.entries[$value.toInt()]"

    }

    data class ValueClass(
        val declaration: KSClassDeclaration,
        val underlyingType: IR
    ) : IR {
        override val sbeName = underlyingType.sbeName
        override val name = declaration.simpleName()
        override val defaultValue = "${name}(${underlyingType.defaultValue})"
        override val randomValue = "${name}(${underlyingType.randomValue})"
        override val size = underlyingType.size
        override val packageStr = declaration.packageName()
        val innerName = declaration.primaryConstructor!!.parameters.first().name!!.getShortName()

        override fun convertToSbe(value: String) = underlyingType.convertToSbe("$value.$innerName")
        override fun convertFromSbe(value: String) = "$name(${underlyingType.convertFromSbe(value)})"
    }

    /**
     * Fixed-size CharSequence type for encoding strings with a known maximum length.
     * Uses US-ASCII encoding (1 byte per character).
     *
     * @param fixedSize The fixed size in bytes
     * @param kotlinTypeName The original Kotlin type name (String or CharSequence)
     */
    data class FixedCharSeq(val fixedSize: Int, val kotlinTypeName: String = "String") : IR {
        override val sbeName = "CharSeq$fixedSize"
        override val name = kotlinTypeName  // Use original type for factory functions
        override val defaultValue = "\"\""
        override val randomValue = "generateRandomAscii($fixedSize)"
        override val size = fixedSize
        override val packageStr = ""

        // SBE encoder has a built-in method that accepts CharSequence directly
        override fun howToEncodeFromInstance(encoder: String, field: String, instance: String): String {
            return "$encoder.$field($instance.$field)"
        }

        // SBE decoder has a built-in method that returns String directly
        override fun howToDecodeIntoInstance(decoder: String, field: String, instance: String): String {
            return "$instance.$field = $decoder.$field()"
        }

        // SBE encoder has a built-in method that accepts CharSequence directly
        override fun howToEncodeFromField(encoder: String, field: String): String {
            return "$encoder.$field($field)"
        }

        override fun howToDecodeFromField(decoder: String, field: String): String {
            // This returns the flyweight view - handled specially in kt-generator
            return "${field}View"
        }
    }

    data class Composite(
        val declaration: KSClassDeclaration,
        val fields: List<CompositeField>
    ) : IR {

        data class CompositeField(
            val name: String,
            val type: IR
        )

        override val sbeName = declaration.simpleName()
        override val name = declaration.simpleName()
        override val size = fields.sumOf { it.type.size }
        override val packageStr = declaration.packageName()
        override val defaultValue = "$name(${fields.joinToString(", ") { "${it.name} = ${it.type.defaultValue}" }})"
        override val randomValue = "$name(${fields.joinToString(", ") {
            val randomVal = it.type.randomValue
            // Replace kotlin.random.Random references in nested composite types
            "${it.name} = ${randomVal.replace("kotlin.random.Random", "Random")}"
        }})"

        override fun howToEncodeFromInstance(encoder: String, field: String, instance: String): String {
            return fields.joinToString("\n") { subField ->
                val value = subField.type.convertToSbe("$instance.$field.${subField.name}")
                "$encoder.$field().${subField.name}($value)"
            }
        }

        override fun howToDecodeIntoInstance(decoder: String, field: String, instance: String): String {
            return fields.joinToString("\n") { subField ->
                val decodedValue = subField.type.convertFromSbe("$decoder.$field().${subField.name}()")
                "$instance.$field.${subField.name} = $decodedValue"
            }
        }

        override fun howToEncodeFromField(encoder: String, field: String): String {
            return fields.joinToString("; ") { subField ->
                val value = subField.type.convertToSbe("$field.${subField.name}")
                "$encoder.$field().${subField.name}($value)"
            }
        }

        override fun howToDecodeFromField(decoder: String, field: String): String {
            val fieldDecodes = fields.joinToString(", ") { subField ->
                val decodedValue = subField.type.convertFromSbe("$decoder.$field().${subField.name}()")
                "${subField.name} = $decodedValue"
            }
            return "${name}($fieldDecodes)"
        }
    }
}