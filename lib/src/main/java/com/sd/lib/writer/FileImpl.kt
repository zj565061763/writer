package com.sd.lib.writer

import android.util.Log
import com.sd.lib.closeable.FCloseableInstance
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

internal class FileApi(
    private val file: File,
) : FWriter {

    private val _holder = FCloseableInstance.key(file.absolutePath) { FileImpl(file) }
    private val impl get() = _holder.instance

    override fun write(data: ByteArray): Boolean {
        return impl.write(data)
    }

    override fun flush() {
        impl.flush()
    }

    override fun limit(size: Long) {
        impl.limit(size)
    }

    override fun size(): Long {
        return impl.size()
    }

    override fun close() {
        impl.close()
    }
}

private class FileImpl(
    private val file: File,
    private val debug: Boolean = false,
) : FWriter, AutoCloseable {

    private var _limit: Long = 0
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
        } finally {
            checkLimit()
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

    override fun limit(size: Long) {
        _limit = size
        checkLimit()
    }

    @Synchronized
    override fun size(): Long {
        return _output?.written?.toLong() ?: file.length()
    }

    @Synchronized
    override fun close() {
        try {
            _output?.close()
            logMsg { "close ${this@FileImpl}" }
        } catch (e: Exception) {
            logMsg { "close error:$e ${this@FileImpl}" }
        } finally {
            _output = null
        }
    }

    private fun checkLimit() {
        if (_limit > 0 && size() > _limit) {
            close()
            file.deleteRecursively()
        }
    }

    private fun getOutput(): CounterOutputStream? {
        val output = _output
        return if (output == null) {
            createOutput()
        } else {
            if (file.isFile) output else createOutput()
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
}