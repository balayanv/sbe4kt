package sbe4kt.util

typealias Header = String
typealias Headers = ArrayList<String>

typealias Value = String
typealias Row = ArrayList<Value>
typealias Rows = ArrayList<Row>

class CSV(
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
}


fun main() {
   CSV()
      .addHeader("header1")
      .addHeader("header2")
      .addHeader("header3")
      .addRow("value1", "value2", "value3")
      .addRow("value4", "value5", "value6")
      .print()

}