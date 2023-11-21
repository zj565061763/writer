package com.sd.lib.writer

import java.io.File

interface FWriter {
    fun write(data: ByteArray): Boolean

    fun flush()

    fun size(): Long

    fun close()

    companion object {
        @JvmStatic
        fun mmap(file: File): FWriter {
            if (file.isDirectory) error("file should not be a directory.")
            return MMapApi(file)
        }

        @JvmStatic
        fun file(file: File): FWriter {
            if (file.isDirectory) error("file should not be a directory.")
            return FileApi(file)
        }
    }
}

//---------- utils ----------

internal fun File?.fCreateFile(): Boolean {
    try {
        if (this == null) return false
        if (this.isFile) return true
        if (this.isDirectory) this.deleteRecursively()
        return this.parentFile.fMakeDirs() && this.createNewFile()
    } catch (e: Exception) {
        return false
    }
}

private fun File?.fMakeDirs(): Boolean {
    try {
        if (this == null) return false
        if (this.isDirectory) return true
        if (this.isFile) this.delete()
        return this.mkdirs()
    } catch (e: Exception) {
        return false
    }
}