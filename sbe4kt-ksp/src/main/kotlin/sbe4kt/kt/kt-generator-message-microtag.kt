package sbe4kt.kt

import sbe4kt.ir.Message

/**
 * Generates progressive timestamp tagging methods for MicroTag4 fields.
 *
 * For each field annotated with @META(tags = [anchorField, tag1, tag2, ...]):
 *   - record<TagN>(time: TimeId)  — stores the microsecond delta from the previous stage
 *   - getTime<TagN>()             — reconstructs the absolute timestamp by replaying deltas from the anchor
 */
internal fun generateMicroTagSection(message: Message): String {
   val className = message.name
   val taggedFields = message.fields.filter { it.tags.isNotEmpty() }
   if (taggedFields.isEmpty()) return ""

   return buildString {
      for (field in taggedFields) {
         val anchor = field.tags[0]
         val tagNames = field.tags.drop(1)
         val fieldName = field.name

         // record<TagN> methods
         for ((index, tagName) in tagNames.withIndex()) {
            val slotN = index + 1
            val prevExpr = if (index == 0) "$anchor.us()" else "getTime${tagNames[index - 1]}().us()"
            appendLine("fun $className.record$tagName(time: TimeId) = apply { $fieldName = $fieldName.set$slotN(time.us() - $prevExpr) }")
         }

         appendLine()

         // getTime<TagN> methods
         for ((index, tagName) in tagNames.withIndex()) {
            val slotN = index + 1
            val prevExpr = if (index == 0) anchor else "getTime${tagNames[index - 1]}()"
            appendLine("fun $className.getTime$tagName() = $prevExpr.add($fieldName.get$slotN())")
         }
      }
   }.trimEnd()
}
