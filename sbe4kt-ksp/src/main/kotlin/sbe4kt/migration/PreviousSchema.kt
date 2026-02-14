package sbe4kt.migration

internal data class PreviousSchema(
   val schemaId: Int,
   val version: Int,
   val messages: Map<String, PrevMessage>,
   val enums: Map<String, PrevEnum>,
   val composites: Map<String, PrevComposite>,
) {
   val allMessageIds: Set<Int> = messages.values.map { it.id }.toSet()
}

internal data class PrevMessage(
   val name: String,
   val id: Int,
   val blockLength: Int?,
   val fields: List<PrevField>,
   val deprecated: Int = 0,
) {
   val isDeprecated: Boolean get() = deprecated > 0
   val fieldsByName: Map<String, PrevField> = fields.associateBy { it.name }
   val allFieldIds: Set<Int> = fields.map { it.id }.toSet()
   val maxFieldId: Int = fields.maxOfOrNull { it.id } ?: 0
   val maxSbeOrder: Int = fields.size
}

internal data class PrevField(
   val name: String,
   val id: Int,
   val type: String,
   val offset: Int?,
   val sbeOrder: Int,
   val sinceVersion: Int?,
   val deprecated: String?,
   val presence: String?,
)

internal data class PrevEnum(
   val name: String,
   val encodingType: String,
   val values: List<PrevEnumValue>,
)

internal data class PrevEnumValue(val name: String, val value: Int)

internal data class PrevComposite(
   val name: String,
   val fields: List<PrevCompositeField>,
)

internal data class PrevCompositeField(val name: String, val type: String, val offset: Int?)
