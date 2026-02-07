package misc

fun <T : Any> T?.log() = apply { println(this) }