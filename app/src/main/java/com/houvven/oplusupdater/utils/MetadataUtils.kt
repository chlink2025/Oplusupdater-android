package com.houvven.oplusupdater.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.min

object MetadataUtils {
    private const val METADATA_PATH = "META-INF/com/android/metadata"
    private const val CHUNK_SIZE = 1024
    private const val END_BYTES_SIZE = 4096
    private const val LOCAL_HEADER_SIZE = 256
    private const val TIMEOUT_MS = 20000L

    suspend fun getMetadata(url: String): String = withContext(Dispatchers.IO) {
        try {
            withTimeout(TIMEOUT_MS) {
                extractMetadata(url)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    fun getMetadataValue(metadata: String, prefix: String): String =
        metadata.lineSequence().firstOrNull { it.startsWith(prefix) }?.substringAfter(prefix).orEmpty()

    private suspend fun extractMetadata(url: String): String {
        val fileLength = getFileLength(url) ?: return ""
        if (fileLength == 0L) return ""

        val actualEndBytesSize = min(fileLength, END_BYTES_SIZE.toLong()).toInt()
        val endBytes = readRange(url, fileLength - actualEndBytesSize, actualEndBytesSize) ?: return ""

        val centralDirectoryInfo = ZipFileUtils.locateCentralDirectory(endBytes, fileLength)
        if (centralDirectoryInfo.offset == -1L || centralDirectoryInfo.size == -1L ||
            centralDirectoryInfo.offset < 0 || centralDirectoryInfo.size <= 0 ||
            centralDirectoryInfo.offset + centralDirectoryInfo.size > fileLength
        ) return ""

        val centralDirectory = readRange(url, centralDirectoryInfo.offset, centralDirectoryInfo.size.toInt()) ?: return ""

        val localHeaderOffset = ZipFileUtils.locateLocalFileHeader(centralDirectory, METADATA_PATH)
        if (localHeaderOffset == -1L || localHeaderOffset < 0 || localHeaderOffset >= fileLength) return ""

        val maxBytesForLocalHeader = min(fileLength - localHeaderOffset, LOCAL_HEADER_SIZE.toLong()).toInt()

        if (maxBytesForLocalHeader < 30) return ""
        val localHeaderBytes = readRange(url, localHeaderOffset, maxBytesForLocalHeader) ?: return ""

        val metadataInternalOffset = ZipFileUtils.locateLocalFileOffset(localHeaderBytes)
        if (metadataInternalOffset == -1L || metadataInternalOffset > maxBytesForLocalHeader) return ""

        val metadataActualOffset = localHeaderOffset + metadataInternalOffset
        // Note: This assumes metadata is not compressed, which is usually true for this file.
        // If compressed, we'd need to read compressed size and inflate.
        // ZipFileUtils doesn't have getUncompressedSize logic exposed easily, let's add it or assume.
        // In KMP version, it had getUncompressedSize extension. Let's implement it here inline or add to ZipFileUtils.
        // For now, let's read a reasonable chunk or try to parse size from local header.
        
        // Re-implementing getUncompressedSize logic from KMP here for safety
        val metadataSize = getUncompressedSize(localHeaderBytes)

        if (metadataSize < 0 || metadataActualOffset + metadataSize > fileLength) return ""

        return readContent(url, metadataActualOffset, metadataSize) ?: ""
    }
    
    private fun getUncompressedSize(bytes: ByteArray): Int {
        if (bytes.size < 22 + 4) return -1
        return (bytes[22].toInt() and 0xff) or
                ((bytes[23].toInt() and 0xff) shl 8) or
                ((bytes[24].toInt() and 0xff) shl 16) or
                ((bytes[25].toInt() and 0xff) shl 24)
    }

    private fun getFileLength(urlStr: String): Long? {
        return try {
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.requestMethod = "HEAD"
            conn.setRequestProperty("Range", "bytes=0-0")
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            
            val contentLength = conn.contentLengthLong
            // Some servers might ignore Range for HEAD and return full length, which is what we want.
            // If they return 1 (for bytes=0-0), we need to check Content-Range.
            
            val contentRange = conn.getHeaderField("Content-Range")
            if (contentRange != null) {
                // bytes 0-0/123456
                val parts = contentRange.split("/")
                if (parts.size > 1) {
                    return parts[1].toLongOrNull()
                }
            }
            
            if (contentLength > 1) return contentLength
            
            // Fallback: if HEAD didn't give full length, we might need another way or just trust Content-Length if it's large.
            // But for "bytes=0-0", Content-Length should be 1.
            
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun readRange(urlStr: String, start: Long, size: Int): ByteArray? {
        if (size == 0) return ByteArray(0)
        if (size < 0 || start < 0) return null

        return try {
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Range", "bytes=$start-${start + size - 1}")
            conn.connectTimeout = 5000
            conn.readTimeout = 10000
            
            conn.inputStream.use { it.readBytes() }
        } catch (e: Exception) {
            null
        }
    }

    private fun readContent(urlStr: String, offset: Long, size: Int): String? {
        if (size == 0) return ""
        if (size < 0 || offset < 0) return null

        val contentBytes = ByteArray(size)
        var totalBytesRead = 0
        
        // Simple implementation: read in one go if possible, or chunks. 
        // KMP used chunks, we can do the same for stability.
        
        return try {
             while (totalBytesRead < size) {
                val remaining = size - totalBytesRead
                val currentChunkSize = min(CHUNK_SIZE, remaining)
                
                val chunk = readRange(urlStr, offset + totalBytesRead, currentChunkSize) ?: return null
                System.arraycopy(chunk, 0, contentBytes, totalBytesRead, chunk.size)
                totalBytesRead += chunk.size
             }
             contentBytes.decodeToString()
        } catch (e: Exception) {
            null
        }
    }
}
