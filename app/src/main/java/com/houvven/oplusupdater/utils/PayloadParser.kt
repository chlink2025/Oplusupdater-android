package com.houvven.oplusupdater.utils

import chromeos_update_engine.UpdateMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.io.IOUtils
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.Channels
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Utility for parsing and extracting partitions from payload.bin.
 * Ported from Payload-Dumper-Compose project.
 */
object PayloadParser {

    private const val MAGIC_VALUE = "CrAU"
    private const val FORMAT_VERSION = 2L

    private val mutex by lazy { Mutex() }

    data class PartitionInfo(
        val name: String,
        val size: Long,
        val rawSize: Long = 0,
        val sha256: String = "",
        var isDownloading: Boolean = false,
        var progress: Float = 0f
    )

    data class PayloadHeader(
        val fileFormatVersion: Long,
        val manifestSize: Long,
        val metadataSignatureSize: Int
    )

    data class Payload(
        val fileName: String,
        val header: PayloadHeader,
        val deltaArchiveManifest: UpdateMetadata.DeltaArchiveManifest,
        val dataOffset: Long,
        val blockSize: Int,
        val archiveSize: Long,
        val isLocal: Boolean
    )

    /**
     * Initialize payload from HTTP URL.
     */
    private suspend fun initPayloadFromHttp(
        fileName: String,
        payloadOffset: Long
    ): Payload {
        HttpRangeUtil.seek(payloadOffset)
        
        val magicBytes = ByteArray(4)
        HttpRangeUtil.read(magicBytes)
        if (String(magicBytes, StandardCharsets.UTF_8) != MAGIC_VALUE) {
            throw Exception("Invalid magic value")
        }

        val fileFormatVersionBytes = ByteArray(8)
        HttpRangeUtil.read(fileFormatVersionBytes)
        val fileFormatVersion = ByteBuffer.wrap(fileFormatVersionBytes).order(ByteOrder.BIG_ENDIAN).long
        if (fileFormatVersion != FORMAT_VERSION) {
            throw Exception("Unsupported file format version: $fileFormatVersion")
        }

        val manifestSizeBytes = ByteArray(8)
        HttpRangeUtil.read(manifestSizeBytes)
        val manifestSize = ByteBuffer.wrap(manifestSizeBytes).order(ByteOrder.BIG_ENDIAN).long

        val metadataSignatureSizeBytes = ByteArray(4)
        HttpRangeUtil.read(metadataSignatureSizeBytes)
        val metadataSignatureSize = ByteBuffer.wrap(metadataSignatureSizeBytes).order(ByteOrder.BIG_ENDIAN).int

        val manifest = ByteArray(manifestSize.toInt())
        HttpRangeUtil.read(manifest)
        
        val metadataSignatureMessage = ByteArray(metadataSignatureSize)
        HttpRangeUtil.read(metadataSignatureMessage)
        
        val deltaArchiveManifest = UpdateMetadata.DeltaArchiveManifest.parseFrom(manifest)
        val dataOffset = HttpRangeUtil.position()
        val payloadHeader = PayloadHeader(fileFormatVersion, manifestSize, metadataSignatureSize)
        
        return Payload(
            fileName,
            payloadHeader,
            deltaArchiveManifest,
            dataOffset,
            deltaArchiveManifest.blockSize,
            HttpRangeUtil.length(),
            false
        )
    }

    /**
     * Get payload offset within a ZIP file from URL.
     */
    suspend fun getPayloadOffset(url: String): Long {
        var payloadOffset = -1L
        val endBytes = ByteArray(4096)
        val fileName = "payload.bin"
        
        if (url.endsWith(".bin")) {
            payloadOffset = 0
        } else if (url.startsWith("https://") || url.startsWith("http://")) {
            HttpRangeUtil.init(url)
            HttpRangeUtil.seek(HttpRangeUtil.length() - 4096)
            HttpRangeUtil.read(endBytes)
            
            val centralDirectoryInfo = ZipFileUtils.locateCentralDirectory(endBytes, HttpRangeUtil.length())
            HttpRangeUtil.seek(centralDirectoryInfo.offset)
            
            val centralDirectory = ByteArray(centralDirectoryInfo.size.toInt())
            HttpRangeUtil.read(centralDirectory)
            
            val localHeaderOffset = ZipFileUtils.locateLocalFileHeader(centralDirectory, fileName)
            val localHeaderBytes = ByteArray(256)
            HttpRangeUtil.seek(localHeaderOffset)
            HttpRangeUtil.read(localHeaderBytes)
            
            payloadOffset = ZipFileUtils.locateLocalFileOffset(localHeaderBytes) + localHeaderOffset
        }
        return payloadOffset
    }

    /**
     * Initialize and parse payload from URL.
     */
    suspend fun initPayload(url: String): Payload {
        val payloadOffset = getPayloadOffset(url)
        if (payloadOffset == -1L) {
            throw Exception("Invalid payload offset")
        }
        return initPayloadFromHttp(HttpRangeUtil.getFileName(), payloadOffset)
    }

    /**
     * Get list of partitions from payload.
     */
    fun getPartitionInfoList(payload: Payload): List<PartitionInfo> {
        return payload.deltaArchiveManifest.partitionsList.map { partition ->
            val operations = partition.operationsList
            val rawSize = if (operations.isNotEmpty()) {
                val lastOp = operations[operations.size - 1]
                (lastOp.dataOffset + lastOp.dataLength) - operations[0].dataOffset
            } else 0L
            
            PartitionInfo(
                name = partition.partitionName,
                size = partition.newPartitionInfo.size,
                rawSize = rawSize,
                sha256 = partition.newPartitionInfo.hash.toByteArray().toHexString()
            )
        }
    }

    /**
     * Extract a single partition to a file with hash verification.
     */
    suspend fun extractPartition(
        partitionName: String,
        payload: Payload,
        outputDir: String,
        onProgressUpdate: (Long, Long) -> Unit
    ): ExtractResult {
        val metadataPartition = payload.deltaArchiveManifest.partitionsList
            .find { it.partitionName == partitionName }
            ?: throw Exception("Partition not found: $partitionName")

        return withContext(Dispatchers.IO) {
            val downloadDir = Paths.get(outputDir)
            if (!Files.exists(downloadDir)) {
                Files.createDirectories(downloadDir)
            }

            val outputFile = File("$outputDir/${metadataPartition.partitionName}.img")
            RandomAccessFile(outputFile, "rw").use { partOutput ->
                val totalSize = metadataPartition.newPartitionInfo.size
                var processedBytes = 0L

                metadataPartition.operationsList.forEach { operation ->
                    extractOperationFromHttp(
                        operation,
                        partOutput,
                        payload.blockSize,
                        payload.dataOffset
                    )
                    processedBytes = partOutput.channel.position()
                    onProgressUpdate(processedBytes, totalSize)
                }
            }
            
            // Verify hash
            val expectedHash = metadataPartition.newPartitionInfo.hash.toByteArray().toHexString()
            val actualHash = calculateFileSha256(outputFile)
            
            ExtractResult(
                success = expectedHash == actualHash,
                expectedHash = expectedHash,
                actualHash = actualHash,
                filePath = outputFile.absolutePath
            )
        }
    }
    
    /**
     * Calculate SHA256 hash of a file.
     */
    private fun calculateFileSha256(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().toHexString()
    }
    
    data class ExtractResult(
        val success: Boolean,
        val expectedHash: String,
        val actualHash: String,
        val filePath: String
    )

    private suspend fun extractOperationFromHttp(
        op: UpdateMetadata.InstallOperation,
        partOutput: RandomAccessFile,
        blockSize: Int,
        offset: Long
    ) {
        mutex.withLock {
            HttpRangeUtil.seek(offset + op.dataOffset)
            withContext(Dispatchers.IO) {
                partOutput.seek(op.dstExtentsList[0].startBlock * blockSize)
            }

            when (op.type) {
                UpdateMetadata.InstallOperation.Type.REPLACE_XZ -> {
                    val data = ByteArray(op.dataLength.toInt())
                    HttpRangeUtil.read(data)
                    XZCompressorInputStream(ByteArrayInputStream(data)).use { xzStream ->
                        IOUtils.copy(xzStream, FileOutputStream(partOutput.fd))
                    }
                }

                UpdateMetadata.InstallOperation.Type.REPLACE_BZ -> {
                    val data = ByteArray(op.dataLength.toInt())
                    HttpRangeUtil.read(data)
                    BZip2CompressorInputStream(BufferedInputStream(ByteArrayInputStream(data))).use { bzStream ->
                        IOUtils.copy(bzStream, FileOutputStream(partOutput.fd))
                    }
                }

                UpdateMetadata.InstallOperation.Type.REPLACE -> {
                    val data = ByteArray(op.dataLength.toInt())
                    HttpRangeUtil.read(data)
                    withContext(Dispatchers.IO) {
                        partOutput.write(data)
                    }
                }

                UpdateMetadata.InstallOperation.Type.ZERO -> {
                    val data = ByteArray(op.dataLength.toInt()) { 0x00 }
                    withContext(Dispatchers.IO) {
                        partOutput.write(data)
                    }
                }

                else -> {
                    throw Exception("Unsupported operation type: ${op.type}")
                }
            }
        }
    }

    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }
}
