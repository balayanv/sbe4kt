package sbe4kt.migration

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList

internal object SbeSchemaParser {

   fun parse(file: File): PreviousSchema? {
      if (!file.exists()) return null
      val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
      doc.documentElement.normalize()
      return parseSchema(doc)
   }

   private fun parseSchema(doc: Document): PreviousSchema {
      val root = doc.documentElement
      val schemaId = root.getAttribute("id").toIntOrNull() ?: 1
      val version = root.getAttribute("version").toIntOrNull() ?: 0

      val enums = parseEnums(doc)
      val composites = parseComposites(doc)
      val messages = parseMessages(doc)

      return PreviousSchema(
         schemaId = schemaId,
         version = version,
         messages = messages,
         enums = enums,
         composites = composites,
      )
   }

   private fun parseMessages(doc: Document): Map<String, PrevMessage> {
      val result = LinkedHashMap<String, PrevMessage>()
      val messageNodes = doc.getElementsByTagName("sbe:message")
      for (i in 0 until messageNodes.length) {
         val elem = messageNodes.item(i) as Element
         val name = elem.getAttribute("name")
         val id = elem.getAttribute("id").toInt()
         val blockLength = elem.getAttribute("blockLength").toIntOrNull()

         val deprecated = elem.getAttribute("deprecated").toIntOrNull() ?: 0
         val fields = parseFields(elem)
         result[name] = PrevMessage(name = name, id = id, blockLength = blockLength, fields = fields, deprecated = deprecated)
      }
      return result
   }

   private fun parseFields(messageElem: Element): List<PrevField> {
      val fields = mutableListOf<PrevField>()
      val fieldNodes = messageElem.getElementsByTagName("field")
      for (i in 0 until fieldNodes.length) {
         val elem = fieldNodes.item(i) as Element
         // only direct children (not nested message fields)
         if (elem.parentNode != messageElem) continue

         fields.add(PrevField(
            name = elem.getAttribute("name"),
            id = elem.getAttribute("id").toInt(),
            type = elem.getAttribute("type"),
            offset = elem.getAttribute("offset").toIntOrNull(),
            sbeOrder = i + 1,
            sinceVersion = elem.getAttribute("sinceVersion").toIntOrNull(),
            deprecated = elem.getAttribute("deprecated").ifEmpty { null },
            presence = elem.getAttribute("presence").ifEmpty { null },
         ))
      }
      return fields
   }

   private fun parseEnums(doc: Document): Map<String, PrevEnum> {
      val result = LinkedHashMap<String, PrevEnum>()
      val enumNodes = doc.getElementsByTagName("enum")
      for (i in 0 until enumNodes.length) {
         val elem = enumNodes.item(i) as Element
         val name = elem.getAttribute("name")
         val encodingType = elem.getAttribute("encodingType")

         val values = mutableListOf<PrevEnumValue>()
         val validValues = elem.getElementsByTagName("validValue")
         for (j in 0 until validValues.length) {
            val valElem = validValues.item(j) as Element
            values.add(PrevEnumValue(
               name = valElem.getAttribute("name"),
               value = valElem.textContent.trim().toInt(),
            ))
         }
         result[name] = PrevEnum(name = name, encodingType = encodingType, values = values)
      }
      return result
   }

   private fun parseComposites(doc: Document): Map<String, PrevComposite> {
      val result = LinkedHashMap<String, PrevComposite>()
      val compositeNodes = doc.getElementsByTagName("composite")
      for (i in 0 until compositeNodes.length) {
         val elem = compositeNodes.item(i) as Element
         val name = elem.getAttribute("name")
         if (name == "messageHeader") continue

         val fields = mutableListOf<PrevCompositeField>()
         val childNodes = elem.childNodes
         for (j in 0 until childNodes.length) {
            val child = childNodes.item(j)
            if (child !is Element) continue
            if (child.tagName != "type" && child.tagName != "ref") continue
            fields.add(PrevCompositeField(
               name = child.getAttribute("name"),
               type = if (child.tagName == "type") child.getAttribute("primitiveType") else child.getAttribute("type"),
               offset = child.getAttribute("offset").toIntOrNull(),
            ))
         }
         result[name] = PrevComposite(name = name, fields = fields)
      }
      return result
   }

   private fun String.toIntOrNull(): Int? = if (isNullOrBlank()) null else try { toInt() } catch (_: NumberFormatException) { null }
}
