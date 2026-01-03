package com.houvven.oplusupdater.ui.screen.home.components

import android.content.ClipData
import android.os.Environment
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.houvven.oplusupdater.utils.PayloadParser
import com.houvven.oplusupdater.utils.StorageUnitUtil
import com.houvven.oplusupdater.utils.toast
import androidx.compose.ui.res.stringResource
import com.houvven.oplusupdater.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File

@Composable
fun PartitionListView(
    downloadUrl: String?,
    systemVersion: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    
    var isLoading by remember { mutableStateOf(false) }
    var payload by remember { mutableStateOf<PayloadParser.Payload?>(null) }
    var partitions by remember { mutableStateOf<List<PayloadParser.PartitionInfo>?>(null) }
    var isExpanded by remember { mutableStateOf(false) }
    
    // Download state
    var downloadingPartition by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }

    Column(modifier = modifier) {
        // Parse Payload Button
        if (downloadUrl != null && partitions == null && !isLoading) {
            Button(
                onClick = {
                    isLoading = true
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val parsedPayload = PayloadParser.initPayload(downloadUrl)
                            payload = parsedPayload
                            partitions = PayloadParser.getPartitionInfoList(parsedPayload)
                            isExpanded = true
                        } catch (e: Exception) {
                            e.printStackTrace()
                            launch(Dispatchers.Main) {
                                context.toast("${context.getString(R.string.parse_failed)}${e.message}")
                            }
                        } finally {
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.parse_payload),
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.primary
                )
            }
        }

        // Loading indicator
        if (isLoading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                InfiniteProgressIndicator(
                    color = MiuixTheme.colorScheme.primary,
                    size = 24.dp
                )
                Text(
                    text = stringResource(R.string.parsing_payload),
                    modifier = Modifier.padding(start = 12.dp),
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }

        // Partition list
        AnimatedVisibility(
            visible = partitions != null && isExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${stringResource(R.string.partition_list)} (${partitions?.size ?: 0})",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        
                        // Copy all partitions info
                        IconButton(
                            onClick = {
                                val allInfo = partitions?.joinToString("\n") { p ->
                                    "${p.name}: ${StorageUnitUtil.formatSize(p.size)}"
                                } ?: ""
                                coroutineScope.launch {
                                    clipboard.setClipEntry(
                                        ClipData.newPlainText("Partitions", allInfo).toClipEntry()
                                    )
                                }
                                context.toast(context.getString(R.string.copy_all_partitions))
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = "Copy All",
                                tint = MiuixTheme.colorScheme.primary
                            )
                        }
                    }
                    
                    // Download tip
                    Text(
                        text = stringResource(R.string.download_tip),
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    partitions?.forEach { partition ->
                        PartitionItem(
                            partition = partition,
                            isDownloading = downloadingPartition == partition.name,
                            progress = if (downloadingPartition == partition.name) downloadProgress else 0f,
                            onDownloadClick = {
                                if (downloadingPartition != null) {
                                    context.toast(context.getString(R.string.download_wait))
                                    return@PartitionItem
                                }
                                
                                val currentPayload = payload
                                if (currentPayload == null) {
                                    context.toast(context.getString(R.string.parse_first))
                                    return@PartitionItem
                                }
                                
                                downloadingPartition = partition.name
                                downloadProgress = 0f
                                
                                coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        val downloadDir = Environment.getExternalStoragePublicDirectory(
                                            Environment.DIRECTORY_DOWNLOADS
                                        ).absolutePath + "/OplusUpdater/" + systemVersion
                                        
                                        val result = PayloadParser.extractPartition(
                                            partitionName = partition.name,
                                            payload = currentPayload,
                                            outputDir = downloadDir,
                                            onProgressUpdate = { current, total ->
                                                downloadProgress = if (total > 0) {
                                                    current.toFloat() / total.toFloat()
                                                } else 0f
                                            }
                                        )
                                        
                                        launch(Dispatchers.Main) {
                                            if (result.success) {
                                                val rootPath = Environment.getExternalStorageDirectory().absolutePath
                                                val displayPath = downloadDir.replace("$rootPath/", "")
                                                context.toast("${context.getString(R.string.save_success)}\n$displayPath/${partition.name}.img")
                                            } else {
                                                context.toast("${context.getString(R.string.save_verify_failed)}${partition.name}.img\n${context.getString(R.string.expected)}${result.expectedHash.take(16)}...\n${context.getString(R.string.actual)}${result.actualHash.take(16)}...")
                                            }
                                        }
                                    } catch (e: Exception) {
                                        launch(Dispatchers.Main) {
                                            context.toast("${context.getString(R.string.download_failed)}${e.message}")
                                        }
                                        e.printStackTrace()
                                    } finally {
                                        downloadingPartition = null
                                        downloadProgress = 0f
                                    }
                                }
                            },
                            onCopyClick = {
                                coroutineScope.launch {
                                    clipboard.setClipEntry(
                                        ClipData.newPlainText("Partition", partition.name).toClipEntry()
                                    )
                                }
                                context.toast("${context.getString(R.string.copied_partition)}${partition.name}")
                            }
                        )
                    }
                }
            }
        }

        // Toggle button if partitions loaded
        if (partitions != null) {
            Button(
                onClick = { isExpanded = !isExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isExpanded) stringResource(R.string.collapse_list) else "${stringResource(R.string.expand_list)} (${partitions?.size ?: 0})",
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun PartitionItem(
    partition: PayloadParser.PartitionInfo,
    isDownloading: Boolean,
    progress: Float,
    onDownloadClick: () -> Unit,
    onCopyClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onCopyClick() }
            .padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Memory,
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp)
                        .padding(end = 8.dp),
                    tint = MiuixTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = partition.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    if (partition.size > 0) {
                        Text(
                            text = StorageUnitUtil.formatSize(partition.size),
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
            
            if (isDownloading) {
                InfiniteProgressIndicator(
                    color = MiuixTheme.colorScheme.primary,
                    size = 24.dp
                )
            } else {
                IconButton(
                    onClick = onDownloadClick
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Download,
                        contentDescription = "Download",
                        tint = MiuixTheme.colorScheme.primary
                    )
                }
            }
        }
        
        // Progress bar
        if (isDownloading && progress > 0) {
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
        }
    }
}
