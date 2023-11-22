package com.sd.demo.writer

import java.util.concurrent.atomic.AtomicInteger

object AutoCloseableInstance {
    private val _holder: MutableMap<Class<out AutoCloseable>, MutableMap<Any, Pair<AutoCloseable, AtomicInteger>>> = hashMapOf()

    fun <T : AutoCloseable> key(clazz: Class<T>, key: Any, factory: () -> T): Holder<T> {
        synchronized(this@AutoCloseableInstance) {
            val map = _holder[clazz] ?: hashMapOf<Any, Pair<AutoCloseable, AtomicInteger>>().also {
                _holder[clazz] = it
            }
            val pair = map[key] ?: (factory() to AtomicInteger(0)).also {
                map[key] = it
            }
            val finalize = AutoCloseable { decrementCount(clazz, key) }
            return Holder(instance = pair.first as T, finalize = finalize).also {
                pair.second.incrementAndGet()
            }
        }
    }

    private fun <T : AutoCloseable> decrementCount(clazz: Class<T>, key: Any) {
        synchronized(this@AutoCloseableInstance) {
            val map = checkNotNull(_holder[clazz])
            val pair = checkNotNull(map[key])
            val count = pair.second.decrementAndGet()
            if (count > 0) {
                null
            } else {
                map.remove(key)
                if (map.isEmpty()) _holder.remove(clazz)
                pair.first
            }
        }?.close()
    }

    class Holder<T : AutoCloseable>(
        private val instance: T,
        private val finalize: AutoCloseable,
    ) {
        fun get(): T = instance

        protected fun finalize() = finalize.close()
    }
}