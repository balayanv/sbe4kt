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

   fun info(info: String) = apply {
      val row = Row(metadata = "info")
      row.values.add(info)
      rows.add(0, row)
   }

   fun addHeader(vararg header: String) = apply {
      val row = Row(metadata = "header")
      row.values.addAll(header)
      val insertIndex = if (rows.firstOrNull()?.metadata == "info") 1 else 0
      rows.add(insertIndex, row)
   }

   fun addRow(vararg value: String, metadata: String = "") = apply {
      val row = Row()
      row.values.addAll(value)
      row.metadata = metadata
      rows.add(row)
   }

   fun print() {
      val headerRow = rows.firstOrNull { it.metadata == "header" } ?: return
      val infoRow = rows.firstOrNull { it.metadata == "info" }
      val dataRows = rows.filter { it.metadata != "header" && it.metadata != "info" }

      val columnWidths = IntArray(headerRow.values.size) { index ->
         var max = headerRow.values[index].length
         dataRows.forEach { row ->
            if (index < row.values.size) {
               max = maxOf(max, row.values[index].length)
            }
         }
         max + 2 // Add some padding
      }

      // Print info string as one big row before header if present
      if (infoRow != null) {
         val infoText = infoRow.values.firstOrNull() ?: ""
         val totalWidth = columnWidths.sum() + (columnWidths.size - 1)

         // Print info top border
         print("┌")
         print("─".repeat(totalWidth))
         print("┐")
         println()

         // Print info row
         print("│")
         val infoMaxWidth = totalWidth - 2
         val infoPadding = maxOf(0, totalWidth - infoText.length - 2)
         print(" ")
         print(infoText.take(infoMaxWidth))
         print(" ".repeat(infoPadding))
         print(" ")
         print("│")
         println()

         // Print info separator
         print("├")
         columnWidths.forEachIndexed { index, width ->
            print("─".repeat(width))
            if (index < columnWidths.size - 1) print("┬") else print("┤")
         }
         println()
      } else {
         // Print top border without info
         print("┌")
         columnWidths.forEachIndexed { index, width ->
            print("─".repeat(width))
            if (index < columnWidths.size - 1) print("┬") else print("┐")
         }
         println()
      }

      // Print header
      print("│")
      columnWidths.forEachIndexed { index, width ->
         val value = headerRow.values[index]
         val padding = width - value.length
         print(" " + value + " ".repeat(padding - 1))
         print("│")
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
fun Random.nextShort() = this.nextInt().toShort()

fun Boolean.convertToByte(): Byte = if (this) 1.toByte() else 0.toByte()
fun Byte.convertToBoolean(): Boolean = this.toInt() != 0

fun Long.toBinaryString(chunkSize: Int = 8) = this.toULong().toString(2).padStart(64, '0').reversed().chunked(chunkSize).reversed().joinToString(" ")

fun branchlessMin(a : Int, b : Int) : Int = b + ((a - b) and ((a - b) shr 31))
fun branchlessMax(a : Int, b : Int) : Int = a - ((a - b) and ((a - b) shr 31))