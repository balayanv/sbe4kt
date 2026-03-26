package sbe4kt.ir

internal data class Tags(
   val valueType: Value,
   val tagNames: List<String>,
) : IR.Leaf {
   val anchor = tagNames[0]
   val milestones = tagNames.drop(1)

   override val sbeName = valueType.sbeName
   override val name = valueType.name
   override val size = valueType.size
   override val defaultValue = valueType.defaultValue
   override val randomValue = valueType.randomValue
   override val packageStr = valueType.packageStr

   override val imports by lazy {
      buildSet {
         addAll(valueType.imports)
         add("sbe4kt.lib.TimeId")
         add("sbe4kt.lib.us")
      }
   }

   override fun convertToSbe(value: String) = valueType.convertToSbe(value)
   override fun convertFromSbe(value: String) = valueType.convertFromSbe(value)

   fun generateFlyweightAccessors(fieldName: String, codecName: String): String {
      val fieldCap = fieldName.replaceFirstChar { it.uppercase() }
      val rawExpr = "$codecName.read${fieldCap}(buffer, offset)"
      val lines = mutableListOf<String>()
      lines.add("fun write${fieldCap}($fieldName: $name) = apply { $codecName.write${fieldCap}(buffer, offset, ${convertToSbe(fieldName)}) }")
      lines.add("fun read${fieldCap}() = ${convertFromSbe(rawExpr)}")

      val anchorCap = anchor.replaceFirstChar { it.uppercase() }
      for ((i, tag) in milestones.withIndex()) {
         val slot = i + 1
         val prevExpr = if (i == 0) "read${anchorCap}().us()" else "readTime${milestones[i - 1]}().us()"
         lines.add("fun record$tag(time: TimeId) = apply { write${fieldCap}(read${fieldCap}().set$slot(time.us() - $prevExpr)) }")
      }

      for ((i, tag) in milestones.withIndex()) {
         val slot = i + 1
         val prevExpr = if (i == 0) "read${anchorCap}()" else "readTime${milestones[i - 1]}()"
         lines.add("fun readTime$tag() = $prevExpr.add(read${fieldCap}().get$slot())")
      }

      return lines.joinToString("\n")
   }

   fun generateDataClassExtensions(className: String, fieldName: String): String {
      val lines = mutableListOf<String>()

      for ((i, tag) in milestones.withIndex()) {
         val slot = i + 1
         val prevExpr = if (i == 0) "$anchor.us()" else "getTime${milestones[i - 1]}().us()"
         lines.add("fun $className.record$tag(time: TimeId) = apply { $fieldName = $fieldName.set$slot(time.us() - $prevExpr) }")
      }

      lines.add("")

      for ((i, tag) in milestones.withIndex()) {
         val slot = i + 1
         val prevExpr = if (i == 0) anchor else "getTime${milestones[i - 1]}()"
         lines.add("fun $className.getTime$tag() = $prevExpr.add($fieldName.get$slot())")
      }

      return lines.joinToString("\n")
   }
}
