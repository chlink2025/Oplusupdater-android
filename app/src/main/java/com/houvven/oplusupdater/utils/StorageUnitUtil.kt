package com.houvven.oplusupdater.utils

import android.annotation.SuppressLint

object StorageUnitUtil {

    private const val KB = 1024L
    private const val MB = KB * 1024
    private const val GB = MB * 1024
    private const val TB = GB * 1024

    @SuppressLint("DefaultLocale")
    @JvmStatic
    fun formatSize(size: Long): String {
        return when {
            size >= TB -> String.format("%.2f TB", size.toDouble() / TB)
            size >= GB -> String.format("%.2f GB", size.toDouble() / GB)
            size >= MB -> String.format("%.2f MB", size.toDouble() / MB)
            size >= KB -> String.format("%.2f KB", size.toDouble() / KB)
            else -> "$size B"
        }
    }
}