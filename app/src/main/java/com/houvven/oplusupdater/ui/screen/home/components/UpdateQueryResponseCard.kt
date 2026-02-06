package com.houvven.oplusupdater.ui.screen.home.components

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.houvven.oplusupdater.R
import com.houvven.oplusupdater.domain.UpdateQueryResponse
import com.houvven.oplusupdater.utils.DownloadUrlResolver
import com.houvven.oplusupdater.utils.StorageUnitUtil
import com.houvven.oplusupdater.utils.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import top.yukonga.miuix.kmp.basic.BasicComponentColors
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.extra.RightActionColors
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperArrowDefaults
import updater.ResponseResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun UpdateQueryResponseCard(
    response: ResponseResult,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    if (response.responseCode.toInt() != 200) {
        Card {
            SuperArrowWrapper(
                title = stringResource(R.string.status),
                summary = response.responseCode.toString()
            )
            SuperArrowWrapper(
                title = stringResource(R.string.message),
                summary = response.errMsg
            )
        }
        return
    }

    runCatching {
        val json = Json { ignoreUnknownKeys = true }
        json.decodeFromString<UpdateQueryResponse>(response.decryptedBodyBytes.decodeToString())
    }.onSuccess {
        UpdateQueryResponseCardContent(
            modifier = modifier,
            response = it
        )
    }.onFailure {
        it.message?.let(context::toast)
    }
}

@Composable
private fun SuperArrowWrapper(
    title: String,
    modifier: Modifier = Modifier,
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    summary: String? = null,
    summaryColor: BasicComponentColors = BasicComponentDefaults.summaryColor(),
    leftAction: @Composable (() -> Unit)? = null,
    rightText: String? = null,
    rightActionColor: RightActionColors = SuperArrowDefaults.rightActionColors(),
    onClick: (() -> Unit)? = null,
) {
    val defaultActionColors = RightActionColors(
        color = Color.Transparent,
        disabledColor = Color.Transparent,
    )

    if (!summary.isNullOrBlank()) {
        SuperArrow(
            title = title,
            modifier = modifier,
            titleColor = titleColor,
            summary = summary,
            summaryColor = summaryColor,
            leftAction = leftAction,
            rightText = rightText,
            rightActionColor = if (onClick == null) defaultActionColors else rightActionColor,
            onClick = onClick,
        )
    }
}

@Composable
private fun UpdateQueryResponseCardContent(
    modifier: Modifier = Modifier,
    response: UpdateQueryResponse,
) = with(response) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()

    var showUpdateLogDialog by remember { mutableStateOf(false) }
    
    // Metadata parsing state
    var buildTime by remember { mutableStateOf<String?>(null) }
    var metadataSecurityPatch by remember { mutableStateOf<String?>(null) }
    
    // URL resolution state
    var resolvedUrlInfo by remember { mutableStateOf<DownloadUrlResolver.ResolvedUrl?>(null) }

    // Fetch metadata from remote ZIP
    LaunchedEffect(response) {
        val url = response.components?.firstOrNull()?.componentPackets?.url
        if (!url.isNullOrEmpty()) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    // Resolve URL first if needed
                    val resolved = DownloadUrlResolver.resolveUrl(url)
                    resolvedUrlInfo = resolved
                    
                    // Use resolved URL for metadata fetching
                    val urlToUse = resolved.getDownloadUrl()
                    val metadata = com.houvven.oplusupdater.utils.MetadataUtils.getMetadata(urlToUse)
                    if (metadata.isNotEmpty()) {
                        val timestamp = com.houvven.oplusupdater.utils.MetadataUtils.getMetadataValue(metadata, "post-timestamp=")
                        if (timestamp.isNotEmpty()) {
                            runCatching {
                                val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())
                                buildTime = sdf.format(Date(timestamp.toLong() * 1000))
                            }
                        }
                        val patchLevel = com.houvven.oplusupdater.utils.MetadataUtils.getMetadataValue(metadata, "post-security-patch-level=")
                        if (patchLevel.isNotEmpty()) {
                            metadataSecurityPatch = patchLevel
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card {
            SuperArrowWrapper(
                title = stringResource(R.string.version_type),
                summary = "$versionTypeH5 ($status)"
            )
            SuperArrowWrapper(
                title = stringResource(R.string.version_name),
                summary = versionName
            )
            (realOtaVersion ?: otaVersion)?.let {
                SuperArrowWrapper(
                    title = stringResource(R.string.ota_version),
                    summary = it
                )
            }
            SuperArrowWrapper(
                title = stringResource(R.string.android_version),
                summary = realAndroidVersion ?: androidVersion
            )
            SuperArrowWrapper(
                title = stringResource(R.string.os_version),
                summary = realOsVersion ?: colorOSVersion ?: osVersion
            )
            SuperArrowWrapper(
                title = stringResource(R.string.security_patch),
                summary = metadataSecurityPatch ?: securityPatch ?: securityPatchVendor
            )
            SuperArrowWrapper(
                title = stringResource(R.string.published_time),
                summary = buildTime ?: publishedTime?.let {
                    SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())
                        .format(Date(it))
                }
            )
            SuperArrowWrapper(
                title = stringResource(R.string.update_log),
                summary = description?.panelUrl,
                onClick = {
                    showUpdateLogDialog = true
                }
            )
        }

        components?.forEach { component ->
            val componentPackets = component?.componentPackets ?: return@forEach
            Card {
                val size = componentPackets.size?.toLongOrNull()?.let(StorageUnitUtil::formatSize)

                SuperArrowWrapper(
                    title = stringResource(R.string.packet_name),
                    summary = component.componentName,
                    rightText = size
                )
                componentPackets.manualUrl?.let {
                    // Get the final download URL (resolved if needed, otherwise original)
                    val finalDownloadUrl = resolvedUrlInfo?.getDownloadUrl() ?: componentPackets.url
                    val originalUrl = componentPackets.url
                    val wasResolved = resolvedUrlInfo?.needsResolution == true && resolvedUrlInfo?.resolvedUrl != null
                    
                    // Show release time if available (only for resolved URLs)
                    resolvedUrlInfo?.getFormattedExpiresTime()?.let { expiresTime ->
                        SuperArrowWrapper(
                            title = stringResource(R.string.release_time),
                            summary = expiresTime
                        )
                    }
                    
                    // Show original URL first if resolution was needed
                    if (wasResolved) {
                        SuperArrowWrapper(
                            title = stringResource(R.string.original_url),
                            summary = originalUrl,
                            onClick = {
                                val urlToCopy = originalUrl ?: return@SuperArrowWrapper
                                coroutineScope.launch {
                                    clipboard.setClipEntry(ClipData.newPlainText("original_url", urlToCopy).toClipEntry())
                                }
                                context.toast(R.string.copied)
                            }
                        )
                    }
                    
                    // Always show the final download URL (resolved or original)
                    SuperArrowWrapper(
                        title = stringResource(R.string.packet_url),
                        summary = finalDownloadUrl,
                        onClick = {
                            val urlToCopy = finalDownloadUrl ?: return@SuperArrowWrapper
                            coroutineScope.launch {
                                clipboard.setClipEntry(ClipData.newPlainText("url", urlToCopy).toClipEntry())
                            }
                            context.toast(R.string.copied)
                        }
                    )
                    
                    // Show error if resolution failed
                    if (resolvedUrlInfo?.needsResolution == true && resolvedUrlInfo?.error != null) {
                        SuperArrowWrapper(
                            title = stringResource(R.string.url_resolve_failed),
                            summary = resolvedUrlInfo?.error
                        )
                    }
                }
                componentPackets.md5?.let {
                    SuperArrowWrapper(
                        title = stringResource(R.string.packet_md5),
                        summary = componentPackets.md5,
                        onClick = {
                            coroutineScope.launch {
                                clipboard.setClipEntry(ClipData.newPlainText(it, it).toClipEntry())
                            }
                            context.toast(R.string.copied)
                        }
                    )
                }
            }
        }
        
        // Partition List View - Parse payload.bin to show partitions
        // Only show after URL resolution is complete (if needed)
        val originalUrl = components?.firstOrNull()?.componentPackets?.url
        val needsResolve = originalUrl?.contains("/downloadCheck") == true
        val actualDownloadUrl = when {
            resolvedUrlInfo != null -> resolvedUrlInfo?.getDownloadUrl()
            !needsResolve && originalUrl != null -> originalUrl
            else -> null // Still resolving, wait
        }
        if (actualDownloadUrl != null) {
            PartitionListView(
                downloadUrl = actualDownloadUrl,
                systemVersion = realOtaVersion ?: otaVersion ?: "Unknown"
            )
        }
    }

    description?.panelUrl?.let {
        UpdateLogDialog(
            show = showUpdateLogDialog,
            url = it,
            softwareVersion = versionName ?: stringResource(R.string.unknown_version),
            onDismissRequest = { showUpdateLogDialog = false }
        )
    }
}