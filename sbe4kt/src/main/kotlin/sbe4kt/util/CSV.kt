package sbe4kt.util

typealias Header = String
typealias Headers = ArrayList<String>

typealias Value = String
typealias Row = ArrayList<Value>
typealias Rows = ArrayList<Row>

class CSV(
   var info: String = "",
   val header: Headers = ArrayList(),
   val rows: Rows = ArrayList(),
) {
   fun addHeader(vararg header: Header) = apply {
      this.header.addAll(header)
   }

   fun addRow(vararg value: Value) = apply {
      val row = ArrayList<Value>()
      row.addAll(value)
      rows.add(row)
   }

   fun print() {
      if (header.isEmpty()) return

      val columnWidths = IntArray(header.size) { index ->
         var max = header[index].length
         rows.forEach { row ->
            if (index < row.size) {
               max = maxOf(max, row[index].length)
            }
         }
         max + 2 // Add some padding
      }

      // Print info string as one big row before header if present
      if (info.isNotEmpty()) {
         val totalWidth = columnWidths.sum() + (columnWidths.size - 1) // Total width including separators

         // Print info top border
         print("┌")
         print("─".repeat(totalWidth))
         print("┐")
         println()

         // Print info row
         print("│")
         val infoMaxWidth = totalWidth - 2 // Account for padding
         val infoPadding = maxOf(0, totalWidth - info.length - 2)
         print(" ")
         print(info.take(infoMaxWidth)) // Truncate if too long
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
         val value = header[index]
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

      // Print rows
      rows.forEach { row ->
         print("│")
         columnWidths.forEachIndexed { index, width ->
            val value = if (index < row.size) row[index] else ""
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

   fun info(info: String) = apply {
      this.info = info
   }
}