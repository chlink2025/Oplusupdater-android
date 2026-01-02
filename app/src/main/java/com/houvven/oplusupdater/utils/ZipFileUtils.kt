package com.houvven.oplusupdater.utils

import java.nio.ByteBuffer
import java.nio.ByteOrder

object ZipFileUtils {
    private const val CENSIG = 0x02014b50L         // "PK\001\002"
    private const val LOCSIG = 0x04034b50L         // "PK\003\004"
    private const val ENDSIG = 0x06054b50L         // "PK\005\006"
    private const val ENDHDR = 22
    private const val ZIP64_ENDSIG = 0x06064b50L   // "PK\006\006"
    private const val ZIP64_LOCSIG = 0x07064b50L   // "PK\006\007"
    private const val ZIP64_LOCHDR = 20
    private const val ZIP64_MAGICVAL = 0xFFFFFFFFL

    data class FileInfo(
        val offset: Long,
        val size: Long,
    )

    fun locateCentralDirectory(bytes: ByteArray, fileLength: Long): FileInfo {
        val searchStartPos = bytes.size - ENDHDR
        var cenSize = -1L
        var cenOffset = -1L

        for (currentScanPos in searchStartPos downTo 0) {
            if (getIntLe(bytes, currentScanPos).toLong() and 0xFFFFFFFFL == ENDSIG) {
                val cenDirOffsetFieldPos = currentScanPos + 16
                val cenDirSizeFieldPos = currentScanPos + 12

                val offsetOfCentralDir = getIntLe(bytes, cenDirOffsetFieldPos).toLong() and 0xFFFFFFFFL
                val sizeOfCentralDir = getIntLe(bytes, cenDirSizeFieldPos).toLong() and 0xFFFFFFFFL

                if (offsetOfCentralDir == ZIP64_MAGICVAL || sizeOfCentralDir == ZIP64_MAGICVAL) {
                    val zip64LocatorPos = currentScanPos - ZIP64_LOCHDR
                    if (zip64LocatorPos >= 0 && (getIntLe(bytes, zip64LocatorPos).toLong() and 0xFFFFFFFFL) == ZIP64_LOCSIG) {
                        val zip64EocdRecordOffsetInFile = getLongLe(bytes, zip64LocatorPos + 8)
                        val zip64EocdRecordOffsetInBuffer = bytes.size - (fileLength - zip64EocdRecordOffsetInFile).toInt()
                        if (zip64EocdRecordOffsetInBuffer >= 0
                            && (zip64EocdRecordOffsetInBuffer + 56) <= bytes.size
                            && (getIntLe(bytes, zip64EocdRecordOffsetInBuffer).toLong() and 0xFFFFFFFFL) == ZIP64_ENDSIG
                        ) {
                            cenSize = getLongLe(bytes, zip64EocdRecordOffsetInBuffer + 40)
                            cenOffset = getLongLe(bytes, zip64EocdRecordOffsetInBuffer + 48)
                            break
                        }
                    }
                } else {
                    cenSize = sizeOfCentralDir
                    cenOffset = offsetOfCentralDir
                    break
                }
            }
        }
        return FileInfo(cenOffset, cenSize)
    }

    fun locateLocalFileHeader(bytes: ByteArray, fileName: String): Long {
        var pos = 0
        var localHeaderOffset = -1L

        while (pos + 46 <= bytes.size) {
            if ((getIntLe(bytes, pos).toLong() and 0xFFFFFFFFL) == CENSIG) {
                val fileNameLength = getShortLe(bytes, pos + 28).toInt() and 0xFFFF
                val extraFieldLength = getShortLe(bytes, pos + 30).toInt() and 0xFFFF
                val fileCommentLength = getShortLe(bytes, pos + 32).toInt() and 0xFFFF
                val relativeOffsetOfLocalHeader = getIntLe(bytes, pos + 42).toLong() and 0xFFFFFFFFL

                val fileNameStartPos = pos + 46
                if (fileNameStartPos + fileNameLength > bytes.size) break

                val currentFileName = String(bytes, fileNameStartPos, fileNameLength, Charsets.UTF_8)
                if (fileName == currentFileName) {
                    localHeaderOffset = relativeOffsetOfLocalHeader
                    break
                }
                pos = fileNameStartPos + fileNameLength + extraFieldLength + fileCommentLength
            } else {
                break
            }
        }
        return localHeaderOffset
    }

    fun locateLocalFileOffset(bytes: ByteArray): Long {
        if ((getIntLe(bytes, 0).toLong() and 0xFFFFFFFFL) == LOCSIG) {
            val fileNameLength = getShortLe(bytes, 26).toInt() and 0xFFFF
            val extraFieldLength = getShortLe(bytes, 28).toInt() and 0xFFFF
            return (30L + fileNameLength + extraFieldLength)
        }
        return -1L
    }

    private fun getIntLe(bytes: ByteArray, pos: Int): Int {
        if (pos + 4 > bytes.size) return 0
        return (bytes[pos].toInt() and 0xFF) or
                ((bytes[pos + 1].toInt() and 0xFF) shl 8) or
                ((bytes[pos + 2].toInt() and 0xFF) shl 16) or
                ((bytes[pos + 3].toInt() and 0xFF) shl 24)
    }

    private fun getShortLe(bytes: ByteArray, pos: Int): Short {
        if (pos + 2 > bytes.size) return 0
        return ((bytes[pos].toInt() and 0xFF) or
                ((bytes[pos + 1].toInt() and 0xFF) shl 8)).toShort()
    }

    private fun getLongLe(bytes: ByteArray, pos: Int): Long {
        if (pos + 8 > bytes.size) return 0L
        return (getIntLe(bytes, pos).toLong() and ZIP64_MAGICVAL) or
                (getIntLe(bytes, pos + 4).toLong() shl 32)
    }
}
