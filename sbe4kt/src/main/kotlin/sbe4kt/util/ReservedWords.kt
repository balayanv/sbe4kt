package sbe4kt.util

private val reserved = setOf(
   "abstract", "continue", "for", "new", "switch",
   "assert", "default", "goto", "package", "synchronized",
   "boolean", "do", "if", "private", "this",
   "break", "double", "implements", "protected", "throw",
   "byte", "else", "import", "public", "throws",
   "case", "enum", "instanceof", "return", "transient",
   "catch", "extends", "int", "short", "try",
   "char", "final", "interface", "static", "void",
   "class", "finally", "long", "strictfp", "volatile",
   "const", "float", "native", "super", "while",
   "true", "false", "null",


   // SBE specific type names which might clash if used as field names
   "int8", "int16", "int32", "int64",
   "uint8", "uint16", "uint32", "uint64",
   "float", "float64"
)

internal fun getSbeFieldName(name: String) = if (reserved.contains(name)) "${name}Value" else name
