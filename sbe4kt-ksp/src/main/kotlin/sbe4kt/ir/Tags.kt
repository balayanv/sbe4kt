package sbe4kt.ir

import kotlin.collections.List

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
      val rawExpr = "$codecName.$fieldName(buffer, offset)"
      val lines = mutableListOf<String>()
      lines.add("fun $fieldName() = ${convertFromSbe(rawExpr)}")
      lines.add("fun $fieldName($fieldName: $name) = apply { $codecName.$fieldName(buffer, offset, ${convertToSbe(fieldName)}) }")

      for ((i, tag) in milestones.withIndex()) {
         val slot = i + 1
         val prevExpr = if (i == 0) "$anchor().us()" else "getTime${milestones[i - 1]}().us()"
         lines.add("fun record$tag(time: TimeId) = apply { $fieldName(${fieldName}().set$slot(time.us() - $prevExpr)) }")
      }

      for ((i, tag) in milestones.withIndex()) {
         val slot = i + 1
         val prevExpr = if (i == 0) "$anchor()" else "getTime${milestones[i - 1]}()"
         lines.add("fun getTime$tag() = $prevExpr.add(${fieldName}().get$slot())")
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
