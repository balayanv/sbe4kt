package sbe4kt.util

import java.lang.ref.WeakReference
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

private class UnsafeLazy<T>(val supplier: () -> T) : ReadWriteProperty<Any?, T> {

   private var value: T? = null

   override fun getValue(thisRef: Any?, property: KProperty<*>): T {
      // as this is not null mutable lazy we don't need a separate boolean flag...
      // if its null then it was never initialized, its not possible to set it null
      // from outside
      if (value == null) {
         value = supplier.invoke()
      }
      @Suppress("UNCHECKED_CAST")
      return value as T
   }

   override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
      this.value = value
   }
}

/**
 * Regular variable backed lazy field, with no thread safety guarantees.
 */
fun <T> lazy(supplier: () -> T): ReadOnlyProperty<Any?, T> = UnsafeLazy(supplier)

/**
 * Regular variable backed lazy field, with no thread safety guarantees.
 */
fun <T> mutableLazy(supplier: () -> T): ReadWriteProperty<Any?, T> = UnsafeLazy(supplier)

private class VolatileLazy<T>(val supplier: () -> T) : ReadWriteProperty<Any?, T> {

   @Volatile
   private var value: T? = null

   override fun getValue(thisRef: Any?, property: KProperty<*>): T {
      // double checked locking
      if (value == null) {
         synchronized(this) {
            if (value == null) {
               value = supplier.invoke()
            }
         }
      }

      @Suppress("UNCHECKED_CAST")
      return value as T
   }

   override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
      this.value = value
   }
}

/**
 * Volatile backed lazy field with double-checked locking for initialization.
 */
fun <T> lazyVolatile(supplier: () -> T): ReadOnlyProperty<Any?, T> = VolatileLazy(supplier)

/**
 * Volatile backed lazy field with double-checked locking for initialization.
 */
fun <T> mutableLazyVolatile(supplier: () -> T): ReadWriteProperty<Any?, T> = VolatileLazy(supplier)

private class ThreadLocalLazy<T>(supplier: () -> T) : ReadWriteProperty<Any?, T> {

   val threadLocal: ThreadLocal<T> = ThreadLocal.withInitial(supplier)

   override fun getValue(thisRef: Any?, property: KProperty<*>): T {
      return threadLocal.get()
   }

   override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
      this.threadLocal.set(value)
   }
}

/**
 * ThreadLocal lazy field.
 */
fun <T> lazyThreadLocal(supplier: () -> T): ReadOnlyProperty<Any?, T> = ThreadLocalLazy(supplier)

/**
 * Thread local lazy field.
 */
fun <T> mutableLazyThreadLocal(supplier: () -> T): ReadWriteProperty<Any?, T> = ThreadLocalLazy(supplier)

private class WeakThreadLocalLazy<T>(val supplier: () -> T) : ReadWriteProperty<Any?, T> {

   val threadLocal: ThreadLocal<WeakReference<T>> = ThreadLocal.withInitial { WeakReference(supplier.invoke()) }

   override fun getValue(thisRef: Any?, property: KProperty<*>): T {
      val ref = threadLocal.get()
      var value = ref.get()

      if (value != null) {
         return value
      }

      value = supplier.invoke()
      this.threadLocal.set(WeakReference(value))
      return value
   }

   override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
      this.threadLocal.set(WeakReference(value))
   }
}

/**
 * ThreadLocal lazy field using a weak reference.
 * Useful if you don't want the value to stick around.
 */
fun <T> lazyWeakThreadLocal(supplier: () -> T): ReadOnlyProperty<Any?, T> = WeakThreadLocalLazy(supplier)

/**
 * ThreadLocal lazy field using a weak reference.
 * Useful if you don't want the value to stick around.
 */
fun <T> mutableWeakLazyThreadLocal(supplier: () -> T): ReadWriteProperty<Any?, T> = WeakThreadLocalLazy(supplier)