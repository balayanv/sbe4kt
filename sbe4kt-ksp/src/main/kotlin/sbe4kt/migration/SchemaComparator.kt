package sbe4kt.migration

import sbe4kt.ir.Enum
import sbe4kt.ir.Schema

internal data class SchemaEvolution(
   val newVersion: Int,
   val hasChanges: Boolean,
   val breakingChanges: List<BreakingChange>,
   val warnings: List<String>,
   val newFields: Map<String, Set<String>>,
   val removedFields: Map<String, List<PrevField>>,
   val newMessages: Set<String>,
   val removedMessages: Set<String>,
) {
   val hasErrors: Boolean = breakingChanges.any { it.severity == Severity.ERROR }
}

internal data class BreakingChange(val message: String, val severity: Severity)

internal enum class Severity { ERROR, WARNING }

internal object SchemaComparator {

   fun compare(current: Schema, previous: PreviousSchema): SchemaEvolution {
      val breakingChanges = mutableListOf<BreakingChange>()
      val warnings = mutableListOf<String>()
      val newFields = mutableMapOf<String, Set<String>>()
      val removedFields = mutableMapOf<String, List<PrevField>>()

      val currentMessageNames = current.messages.map { it.name }.toSet()
      val previousMessageNames = previous.messages.keys

      val newMessages = currentMessageNames - previousMessageNames
      val removedMessages = previousMessageNames - currentMessageNames

      for (removedName in removedMessages) {
         warnings.add("Message '$removedName' (id=${previous.messages[removedName]!!.id}) was removed")
      }

      for (message in current.messages) {
         val prev = previous.messages[message.name] ?: continue

         val currentFieldNames = message.fields.map { it.name }.toSet()
         val previousFieldNames = prev.fields.map { it.name }.toSet()

         val added = currentFieldNames - previousFieldNames
         val removed = previousFieldNames - currentFieldNames

         if (added.isNotEmpty()) {
            newFields[message.name] = added
         }

         if (removed.isNotEmpty()) {
            val newlyRemoved = prev.fields.filter { it.name in removed && it.deprecated == null }
            removedFields[message.name] = newlyRemoved
            for (field in newlyRemoved) {
               breakingChanges.add(BreakingChange(
                  "Field '${field.name}' was removed from message '${message.name}'. " +
                  "Removing fields is not backward compatible. Use @META(removeAfter=N) to schedule deprecation.",
                  Severity.ERROR,
               ))
            }
         }

         // check removeAfter constraints
         val currentVersion = previous.version + 1
         for (field in message.fields) {
            if (field.removeAfter > 0 && field.removeAfter <= currentVersion) {
               breakingChanges.add(BreakingChange(
                  "Field '${field.name}' in message '${message.name}' has @META(removeAfter=${field.removeAfter}) " +
                  "but schema version is already $currentVersion. " +
                  "Either remove the annotation, increase removeAfter, or remove the field from the source type.",
                  Severity.ERROR,
               ))
            }
         }

         for (field in message.fields) {
            val prevField = prev.fieldsByName[field.name] ?: continue
            if (field.type.sbeName != prevField.type) {
               breakingChanges.add(BreakingChange(
                  "Field '${field.name}' in message '${message.name}' changed type: '${prevField.type}' -> '${field.type.sbeName}'",
                  Severity.ERROR,
               ))
            }
         }
      }

      checkComposites(current, previous, breakingChanges)
      checkEnums(current, previous, breakingChanges)

      val hasChanges = newMessages.isNotEmpty()
         || removedMessages.isNotEmpty()
         || newFields.isNotEmpty()
         || removedFields.isNotEmpty()

      val newVersion = if (hasChanges) previous.version + 1 else previous.version

      return SchemaEvolution(
         newVersion = newVersion,
         hasChanges = hasChanges,
         breakingChanges = breakingChanges,
         warnings = warnings,
         newFields = newFields,
         removedFields = removedFields,
         newMessages = newMessages,
         removedMessages = removedMessages,
      )
   }

   private fun checkComposites(current: Schema, previous: PreviousSchema, errors: MutableList<BreakingChange>) {
      for (struct in current.structs) {
         val prev = previous.composites[struct.name] ?: continue

         val currentFields = struct.fields.sortedBy { it.sbeOrder }.map { it.name to it.type.sbeName }
         val previousFields = prev.fields.map { it.name to it.type }

         if (currentFields != previousFields) {
            errors.add(BreakingChange(
               "Composite '${struct.name}' was modified. Composites cannot evolve in SBE. " +
               "Previous: ${previousFields.map { "${it.first}:${it.second}" }}, " +
               "Current: ${currentFields.map { "${it.first}:${it.second}" }}",
               Severity.ERROR,
            ))
         }
      }
   }

   private fun checkEnums(current: Schema, previous: PreviousSchema, errors: MutableList<BreakingChange>) {
      val currentEnums = mutableMapOf<String, List<Pair<String, Int>>>()
      fun collectEnums(type: sbe4kt.ir.IR.Leaf) {
         if (type is Enum) {
            val entries = type.declaration.declarations
               .filterIsInstance<com.google.devtools.ksp.symbol.KSClassDeclaration>()
               .filter { it.classKind == com.google.devtools.ksp.symbol.ClassKind.ENUM_ENTRY }
               .mapIndexed { index, entry -> entry.simpleName.asString() to index }
               .toList()
            currentEnums[type.name] = entries
         }
      }

      for (message in current.messages) {
         for (field in message.fields) collectEnums(field.type)
      }
      for (struct in current.structs) {
         for (field in struct.fields) collectEnums(field.type)
      }

      for ((enumName, prevEnum) in previous.enums) {
         val currentEntries = currentEnums[enumName] ?: continue
         val currentByName = currentEntries.toMap()

         for (prevValue in prevEnum.values) {
            val currentOrdinal = currentByName[prevValue.name]
            if (currentOrdinal == null) {
               errors.add(BreakingChange(
                  "Enum value '${prevValue.name}' was removed from enum '$enumName'",
                  Severity.ERROR,
               ))
            } else if (currentOrdinal != prevValue.value) {
               errors.add(BreakingChange(
                  "Enum value '${prevValue.name}' in '$enumName' changed ordinal: ${prevValue.value} -> $currentOrdinal",
                  Severity.ERROR,
               ))
            }
         }
      }
   }
}
