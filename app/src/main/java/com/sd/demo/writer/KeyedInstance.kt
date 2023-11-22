package com.sd.demo.writer

import java.util.concurrent.atomic.AtomicInteger

object KeyedInstance {
    private val _holder: MutableMap<Class<out AutoCloseable>, MutableMap<Any, Pair<AutoCloseable, AtomicInteger>>> = hashMapOf()

    fun <T : AutoCloseable> holder(clazz: Class<T>, key: Any, factory: () -> T): Holder<T> {
        val map = _holder[clazz] ?: hashMapOf<Any, Pair<AutoCloseable, AtomicInteger>>().also {
            _holder[clazz] = it
        }
        val pair = map[key] ?: kotlin.run {
            factory() to AtomicInteger(0)
        }.also {
            map[key] = it
        }
        return Holder(clazz, key, pair.first as T).also {
            pair.second.incrementAndGet()
        }
    }

    private fun decrementCount(clazz: Class<*>, key: Any) {
        val map = checkNotNull(_holder[clazz])
        val pair = checkNotNull(map[key])
        val count = pair.second.decrementAndGet()
        if (count <= 0) {
            map.remove(key)
            if (map.isEmpty()) _holder.remove(clazz)
            pair.first.close()
        }
    }

    class Holder<T : AutoCloseable>(
        private val clazz: Class<T>,
        private val key: Any,
        private val instance: T,
    ) {
        fun get(): T = instance

        protected fun finalize() {
            decrementCount(clazz, key)
        }
    }
}

