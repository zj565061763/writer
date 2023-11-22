package com.sd.lib.writer

import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max

internal class MMapApi(
    private val file: File,
) : FWriter {

    private val impl: FWriter
        get() = MMapImpl.getInstance(file)

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
        MMapImpl.incrementCount(file)
    }

    protected fun finalize() {
        MMapImpl.decrementCount(file)
    }
}

private const val HEADER_SIZE: Long = 32
private const val BUFFER_SIZE: Long = 512 * 1024

private class MMapImpl private constructor(
    private val file: File,
    private val debug: Boolean = false,
) : FWriter {

    private var _raf: RandomAccessFile? = null
    private var _buffer: MappedByteBuffer? = null
    private var _headerBuffer: MappedByteBuffer? = null

    private var _size: Long = -1

    @Synchronized
    override fun write(data: ByteArray): Boolean {
        val buffer = getBuffer(data.size) ?: return false
        return writeBuffer(buffer, data)
    }

    @Synchronized
    override fun flush() {
        try {
            _buffer?.force()
            _headerBuffer?.force()
        } catch (e: Exception) {
            logMsg { "flush error:$e ${this@MMapImpl}" }
        }
    }

    @Synchronized
    override fun size(): Long {
        getBuffer(0)
        return _size
    }

    @Synchronized
    override fun close() {
        try {
            _buffer?.force()
            _headerBuffer?.force()
            _raf?.close()
            logMsg { "close ${this@MMapImpl}" }
        } catch (e: Exception) {
            logMsg { "close error:$e ${this@MMapImpl}" }
        } finally {
            _buffer = null
            _headerBuffer = null
            _raf = null
            _size = -1
        }
    }

    private fun writeBuffer(buffer: MappedByteBuffer, data: ByteArray): Boolean {
        check(_buffer === buffer)
        return try {
            buffer.put(data)
            true
        } catch (e: Exception) {
            logMsg { "write error:$e ${this@MMapImpl}" }
            close()
            false
        }.also {
            if (it) {
                _size += data.size
                setHeaderRemaining(buffer.remaining().toLong())
            }
        }
    }

    private fun getBuffer(appendSize: Int): MappedByteBuffer? {
        val buffer = _buffer ?: createBuffer(
            remaining = getHeaderRemaining(),
            bufferSize = max(BUFFER_SIZE, appendSize.toLong()),
        ) ?: return null

        check(_buffer === buffer)
        val remaining = buffer.remaining()

        return if (remaining >= appendSize) {
            buffer
        } else {
            logMsg { "remaining:$remaining file exist:${file.exists()} ${this@MMapImpl}" }

//            buffer.force()
            if (file.exists()) {
                _buffer = null
            } else {
                close()
            }

            createBuffer(
                remaining = remaining.toLong(),
                bufferSize = max(BUFFER_SIZE, appendSize.toLong()),
            )
        }
    }

    private fun createBuffer(
        remaining: Long,
        bufferSize: Long,
    ): MappedByteBuffer? {
        check(_buffer == null)
        val raf = getRaf() ?: return null
        val position = (raf.length() - remaining.coerceAtLeast(0)).coerceAtLeast(HEADER_SIZE)
        return try {
            raf.channel.map(FileChannel.MapMode.READ_WRITE, position, bufferSize)?.also {
                _buffer = it
                logMsg { "create buffer:$it ${Integer.toHexString(it.hashCode())} ${this@MMapImpl}" }
            }
        } catch (e: Exception) {
            logMsg { "create buffer error:$e" }
            null
        }?.also {
            if (_size == -1L) {
                _size = position
            }
            if (position == HEADER_SIZE) {
                writeBuffer(it, getLineSeparator().toByteArray())
            } else {
                setHeaderRemaining(it.remaining().toLong())
            }
        }
    }

    private fun getHeaderRemaining(): Long {
        val buffer = getHeaderBuffer() ?: return 0
        return try {
            buffer.getLong(0)
        } catch (e: Exception) {
            logMsg { "get remaining error:$e ${this@MMapImpl}" }
            0
        }
    }

    private fun setHeaderRemaining(remaining: Long) {
        require(remaining >= 0)
        val buffer = getHeaderBuffer() ?: return
        try {
            buffer.putLong(0, remaining)
        } catch (e: Exception) {
            logMsg { "set remaining error:$e ${this@MMapImpl}" }
        }
    }

    private fun getHeaderBuffer(): MappedByteBuffer? {
        _headerBuffer?.let { return it }
        val raf = getRaf() ?: return null
        return try {
            raf.channel.map(FileChannel.MapMode.READ_WRITE, 0, HEADER_SIZE)?.also {
                _headerBuffer = it
            }
        } catch (e: Exception) {
            logMsg { "create header buffer error:$e" }
            null
        }
    }

    private fun getRaf(): RandomAccessFile? {
        _raf?.let { return it }
        if (!file.fCreateFile()) {
            logMsg { "create raf failed ${this@MMapImpl}" }
            return null
        }
        return try {
            RandomAccessFile(file, "rw").also {
                _raf = it
            }
        } catch (e: Exception) {
            logMsg { "create raf error:$e ${this@MMapImpl}" }
            null
        }
    }

    private inline fun logMsg(block: () -> Any) {
        if (debug) {
            Log.i(MMapImpl::class.java.simpleName, block().toString())
        }
    }

    companion object {
        private val _holder: MutableMap<String, Pair<MMapImpl, AtomicInteger>> = hashMapOf()

        //---------- instance ----------

        fun getInstance(file: File): FWriter {
            return synchronized(this@Companion) {
                val path = file.absolutePath
                _holder[path]?.first ?: (MMapImpl(file) to AtomicInteger(0)).let {
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

private fun getLineSeparator(): String {
    val line = try {
        System.getProperty("line.separator")
    } catch (e: Exception) {
        null
    }
    return if (line.isNullOrEmpty()) "\n" else line
}