package com.sd.demo.writer

import com.sd.lib.writer.FWriter
import java.nio.ByteBuffer

class MemoryWriter : FWriter {

    private val _buffer by lazy {
        ByteBuffer.allocate(1024 * 1024)
    }

    override fun write(data: ByteArray): Boolean {
        if (_buffer.remaining() < data.size) {
            _buffer.clear()
        }
        _buffer.put(data)
        return true
    }

    override fun flush() {
    }

    override fun size(): Long {
        return _buffer.position().toLong()
    }

    override fun close() {
    }
}