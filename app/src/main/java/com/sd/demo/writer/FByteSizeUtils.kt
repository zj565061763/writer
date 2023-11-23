package com.sd.demo.writer

import java.io.File
import java.text.DecimalFormat

const val FByteKB = 1024L
const val FByteMB = 1024L * FByteKB
const val FByteGB = 1024L * FByteMB
const val FByteTB = 1024L * FByteGB

/**
 * 返回文件或者文件夹的大小（byte）
 */
fun File?.fSize(): Long {
    if (this == null) return 0
    return if (this.isFile) {
        this.length()
    } else if (this.isDirectory) {
        this.walkBottomUp().fold(0) { acc, it ->
            acc + (if (it.isFile) it.length() else 0)
        }
    } else 0
}

/**
 * 返回格式化的字符串
 */
@JvmOverloads
fun Long.fFormatByteSize(df: DecimalFormat = DecimalFormat("#.0")): String {
    return when {
        this <= 0 -> df.format(0.0) + "B"
        this < FByteKB -> df.format(this.toDouble()) + "B"
        this < FByteMB -> df.format(this.toDouble() / FByteKB) + "KB"
        this < FByteGB -> df.format(this.toDouble() / FByteMB) + "MB"
        this < FByteTB -> df.format(this.toDouble() / FByteGB) + "GB"
        else -> df.format(this.toDouble() / FByteTB) + "TB"
    }
}