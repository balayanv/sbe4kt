//package sbe4kt.util
//
//class UnsafeLazy<out T>(val initializer: () -> T) : Lazy<T> {
//   override val value: T
//      get() = TODO("Not yet implemented")
//
//   override fun isInitialized(): Boolean {
//      TODO("Not yet implemented")
//   }
//}
//
//fun <T> lazy1(initializer: () -> T): Lazy<T> = UnsafeLazy(initializer)
//
//class Something {
//   val test by lazy { "Hello" }
//}
//
//fun main() {
//   val something = Something()
//   println(something.test)
//}