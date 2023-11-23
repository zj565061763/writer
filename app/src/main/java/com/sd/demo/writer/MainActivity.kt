package com.sd.demo.writer

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.sd.demo.writer.databinding.ActivityMainBinding
import com.sd.lib.writer.FWriter
import kotlin.time.measureTime

class MainActivity : AppCompatActivity() {
    private val _binding by lazy { ActivityMainBinding.inflate(layoutInflater) }

    private val _memory: FWriter by lazy {
        MemoryWriter()
    }

    private val _mmap: FWriter by lazy {
        FWriter.mmap(filesDir.resolve("mmap.log"))
    }

    private val _file: FWriter by lazy {
        FWriter.file(filesDir.resolve("file.log"))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(_binding.root)

        _binding.btnMemory.setOnClickListener {
            testPerformance(_memory, "memory")
        }

        _binding.btnMmap.setOnClickListener {
            testPerformance(_mmap, "mmap")
        }

        _binding.btnFile.setOnClickListener {
            testPerformance(_file, "file")
        }
    }
}

private fun testPerformance(
    writer: FWriter,
    tag: String,
    repeat: Int = 1_0000,
    logLength: Int = 500,
) {
    val log = "1".repeat(logLength)
    measureTime {
        repeat(repeat) {
            writer.write(log.toByteArray())
        }
    }.let {
        logMsg { "$tag time:${it.inWholeMilliseconds} size:${writer.size()}" }
    }
}

inline fun logMsg(block: () -> Any) {
    Log.i("writer-demo", block().toString())
}