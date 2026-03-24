@file:Suppress("unused")

package sbe4kt.lib

import kotlin.random.Random

inline fun <reified E : Enum<E>> randomValue(): E {
   val values = enumValues<E>()
   return values[Random.nextInt(values.size)]
}

inline fun <reified E : Enum<E>> defaultValue(): E {
   return enumValues<E>()[0]
}

class CSV(
   val rows: MutableList<Row> = ArrayList(),
) {
   data class Row(
      val values: ArrayList<String> = ArrayList(),
      var metadata: String = ""
   )

   fun addHeader(vararg header: String) = apply {
      val row = Row(metadata = "header")
      row.values.addAll(header)
      rows.add(0, row)
   }

   fun addRow(vararg value: String, metadata: String = "") = apply {
      val row = Row()
      row.values.addAll(value)
      row.metadata = metadata
      rows.add(row)
   }

   fun print() {
      val headerRow = rows.firstOrNull { it.metadata == "header" } ?: return
      val dataRows = rows.filter { it.metadata != "header" }

      val columnWidths = IntArray(headerRow.values.size) { index ->
         var max = headerRow.values[index].length
         dataRows.forEach { row ->
            if (index < row.values.size) {
               max = maxOf(max, row.values[index].length)
            }
         }
         max + 2
      }

      // Print top border
      print("┌")
      columnWidths.forEachIndexed { index, width ->
         print("─".repeat(width))
         if (index < columnWidths.size - 1) print("┬") else print("┐")
      }
      println()

      // Print header
      print("│")
      columnWidths.forEachIndexed { index, width ->
         val value = headerRow.values[index]
         val padding = width - value.length
         print(" " + value + " ".repeat(padding - 1))
         print("│")
      }
      println()

      // Print header separator
      print("├")
      columnWidths.forEachIndexed { index, width ->
         print("─".repeat(width))
         if (index < columnWidths.size - 1) print("┼") else print("┤")
      }
      println()

      // Print data rows
      dataRows.forEach { row ->
         if (row.metadata.endsWith("separator")) {
            print("├")
            columnWidths.forEachIndexed { index, width ->
               print("─".repeat(width))
               if (index < columnWidths.size - 1) print("┼") else print("┤")
            }
            println()
            return@forEach
         }
         print("│")
         columnWidths.forEachIndexed { index, width ->
            val value = if (index < row.values.size) row.values[index] else ""
            val padding = width - value.length
            print(" " + value + " ".repeat(padding - 1))
            print("│")
         }
         println()
      }

      // Print bottom border
      print("└")
      columnWidths.forEachIndexed { index, width ->
         print("─".repeat(width))
         if (index < columnWidths.size - 1) print("┴") else print("┘")
      }
      println()
   }

   fun separator(metadata: String = "separator") = apply{
      addRow(metadata = metadata)
   }
}

fun CharSet.randomStr(length: Int) = (1..length).map { this.chars.random() }.joinToString("")
fun Random.nextStr(length: Int) = (1..length).map { nextChar() }.joinToString("")
fun Random.nextChar() = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"[nextInt(62)]
fun Random.stringBuilder(): StringBuilder {
   val cap = Random.nextInt(4, 18)
   val sb = StringBuilder(cap)
   repeat(cap) { sb.append(Random.nextChar()) }
   return sb
}
fun Random.nextShort() = this.nextInt().toShort()

fun Boolean.convertToByte(): Byte = if (this) 1.toByte() else 0.toByte()
fun Byte.convertToBoolean(): Boolean = this.toInt() != 0

fun Long.toBinaryString(chunkSize: Int = 8) = this.toULong().toString(2).padStart(64, '0').reversed().chunked(chunkSize).reversed().joinToString(" ")

fun branchlessMin(a : Int, b : Int) : Int = b + ((a - b) and ((a - b) shr 31))
fun branchlessMax(a : Int, b : Int) : Int = a - ((a - b) and ((a - b) shr 31))

data class SbeLayoutRow(
   val offset: Int,
   val field: String,
   val type: String,
   val sbeType: String,
   val size: Int,
   val value: String,
   val metadata: String = "",
)

fun List<SbeLayoutRow>.toCSV(): CSV {
   val csv = CSV()
   csv.addHeader("Offset", "Field", "Type", "SBE Type", "Size", "Value")
   for (row in this) {
      when (row.metadata) {
         "separator" -> csv.separator()
         else -> csv.addRow(
            row.offset.toString(),
            row.field,
            row.type,
            row.sbeType,
            if (row.size > 0) row.size.toString() else if (row.size < 0) "1/${-row.size}" else "",
            row.value,
            metadata = row.metadata
         )
      }
   }
   val endOffset = if (isEmpty()) 0 else filter { it.metadata != "separator" }.maxOfOrNull { it.offset + maxOf(it.size, 0) } ?: 0
   csv.addRow(endOffset.toString(), "[END]", "", "", "", "")
   return csv
}