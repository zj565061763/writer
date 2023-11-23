package com.sd.lib.writer

import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicInteger

internal class FileApi(
    private val file: File,
) : FWriter {

    private val impl: FWriter
        get() = FileImpl.getInstance(file)

    override fun write(data: ByteArray): Boolean {
        return impl.write(data)
    }

    override fun flush() {
        impl.flush()
    }

    override fun size(): Long {
        return impl.size()
    }

    override fun close() {
        impl.close()
    }

    init {
        FileImpl.incrementCount(file)
    }

    protected fun finalize() {
        FileImpl.decrementCount(file)
    }
}

private class FileImpl private constructor(
    private val file: File,
    private val debug: Boolean = false,
) : FWriter {
    private var _output: CounterOutputStream? = null

    @Synchronized
    override fun write(data: ByteArray): Boolean {
        val output = getOutput() ?: return false
        return try {
            output.write(data)
            output.flush()
            true
        } catch (e: Exception) {
            logMsg { "write error:$e ${this@FileImpl}" }
            close()
            false
        }
    }

    @Synchronized
    override fun flush() {
        try {
            _output?.flush()
        } catch (e: Exception) {
            logMsg { "flush error:$e ${this@FileImpl}" }
        }
    }

    @Synchronized
    override fun size(): Long {
        return (getOutput()?.written ?: 0).toLong()
    }

    @Synchronized
    override fun close() {
        try {
            _output?.flush()
            _output?.close()
            logMsg { "close ${this@FileImpl}" }
        } catch (e: Exception) {
            logMsg { "close error:$e ${this@FileImpl}" }
        } finally {
            _output = null
        }
    }

    private fun getOutput(): CounterOutputStream? {
        val output = _output
        return if (output == null) {
            createOutput()
        } else {
            if (file.exists()) output else createOutput()
        }
    }

    private fun createOutput(): CounterOutputStream? {
        close()
        if (!file.fCreateFile()) {
            logMsg { "create file failed ${this@FileImpl}" }
            return null
        }
        return try {
            FileOutputStream(file, true)
                .buffered()
                .let { CounterOutputStream(it, file.length().toInt()) }
                .also {
                    _output = it
                    logMsg { "create output:$it ${this@FileImpl}" }
                }
        } catch (e: Exception) {
            logMsg { "create file error:$e ${this@FileImpl}" }
            null
        }
    }

    private class CounterOutputStream(output: OutputStream, length: Int) : OutputStream() {
        private val _output = output
        private var _written = length

        val written: Int get() = _written

        override fun write(b: Int) {
            _output.write(b)
            _written++
        }

        override fun write(buff: ByteArray) {
            _output.write(buff)
            _written += buff.size
        }

        override fun write(buff: ByteArray, off: Int, len: Int) {
            _output.write(buff, off, len)
            _written += len
        }

        override fun flush() {
            _output.flush()
        }

        override fun close() {
            _output.close()
        }
    }

    private inline fun logMsg(block: () -> Any) {
        if (debug) {
            Log.i(FileImpl::class.java.simpleName, block().toString())
        }
    }

    companion object {
        private val _holder: MutableMap<String, Pair<FileImpl, AtomicInteger>> = hashMapOf()

        //---------- instance ----------

        fun getInstance(file: File): FWriter {
            return synchronized(this@Companion) {
                val path = file.absolutePath
                _holder[path]?.first ?: (FileImpl(file) to AtomicInteger(0)).let {
                    _holder[path] = it
                    it.first
                }
            }
        }

        private fun removeInstance(file: File) {
            synchronized(this@Companion) {
                val path = file.absolutePath
                _holder.remove(path)
            }?.let {
                check(it.second.get() <= 0)
                it.first.close()
            }
        }

        //---------- count ----------

        fun incrementCount(file: File) {
            synchronized(this@Companion) {
                getInstance(file)
                val path = file.absolutePath
                val counter = checkNotNull(_holder[path]?.second) { "There is no instance bound to $path" }
                counter.incrementAndGet()
            }
        }

        fun decrementCount(file: File) {
            synchronized(this@Companion) {
                val path = file.absolutePath
                val counter = checkNotNull(_holder[path]?.second) { "There is no instance bound to $path" }
                counter.decrementAndGet().let {
                    if (it <= 0) {
                        removeInstance(file)
                    }
                }
            }
        }
    }
}