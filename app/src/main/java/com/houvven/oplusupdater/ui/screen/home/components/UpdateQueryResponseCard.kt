package com.houvven.oplusupdater.ui.screen.home.components

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.houvven.oplusupdater.R
import com.houvven.oplusupdater.domain.UpdateQueryResponse
import com.houvven.oplusupdater.utils.DownloadUrlResolver
import com.houvven.oplusupdater.utils.StorageUnitUtil
import com.houvven.oplusupdater.utils.toast
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
    val firstComponentUrl = components?.firstOrNull()?.componentPackets?.url
    val updateLogUrl = description?.panelUrl
    val resolvableComponentUrls = components
        ?.mapNotNull { it?.componentPackets?.url }
        ?.filter { it.contains("/downloadCheck") }
        .orEmpty()
    val responseCardViewModel: UpdateQueryResponseCardViewModel = viewModel()
    val responseCardUiState by responseCardViewModel.uiState.collectAsState()
    val updateLogViewModel: UpdateLogViewModel = viewModel()
    val updateLogUiState by updateLogViewModel.uiState.collectAsState()

    var showUpdateLogDialog by remember(updateLogUrl) { mutableStateOf(false) }
    val responseIdentity = listOf(
        versionName,
        realOtaVersion,
        otaVersion,
        updateLogUrl,
        firstComponentUrl,
        resolvableComponentUrls.joinToString("|")
    ).joinToString("::")

    LaunchedEffect(responseIdentity) {
        updateLogViewModel.reset()
        responseCardViewModel.bindResponse(
            firstComponentUrl = firstComponentUrl,
            resolvableComponentUrls = resolvableComponentUrls
        )
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
                summary = responseCardUiState.metadataSecurityPatch ?: securityPatch ?: securityPatchVendor
            )
            SuperArrowWrapper(
                title = stringResource(R.string.published_time),
                summary = responseCardUiState.buildTime ?: publishedTime?.let {
                    SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())
                        .format(Date(it))
                }
            )
            SuperArrowWrapper(
                title = stringResource(R.string.update_log),
                summary = updateLogUrl,
                onClick = {
                    updateLogUrl?.let(updateLogViewModel::load)
                    showUpdateLogDialog = true
                }
            )
        }

        components?.forEach { component ->
            val componentPackets = component?.componentPackets ?: return@forEach
            ComponentPacketCard(
                componentName = component.componentName,
                componentPackets = componentPackets,
                resolvedUrlInfo = componentPackets.url?.let(responseCardUiState.resolvedUrlInfoByOriginalUrl::get),
                systemVersion = realOtaVersion ?: otaVersion ?: "Unknown"
            )
        }
    }

    updateLogUrl?.let {
        UpdateLogDialog(
            show = showUpdateLogDialog,
            url = it,
            softwareVersion = versionName ?: stringResource(R.string.unknown_version),
            uiState = updateLogUiState,
            onRetryRequest = updateLogViewModel::retry,
            onDismissRequest = { showUpdateLogDialog = false }
        )
    }
}

@Composable
private fun ComponentPacketCard(
    componentName: String?,
    componentPackets: UpdateQueryResponse.Component.ComponentPackets,
    resolvedUrlInfo: DownloadUrlResolver.ResolvedUrl?,
    systemVersion: String,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val originalUrl = componentPackets.url
    val size = componentPackets.size?.toLongOrNull()?.let(StorageUnitUtil::formatSize)

    val finalDownloadUrl = when {
        resolvedUrlInfo != null -> resolvedUrlInfo?.getDownloadUrl()
        !originalUrl.isNullOrEmpty() -> originalUrl
        else -> null
    }
    val wasResolved = resolvedUrlInfo?.needsResolution == true && resolvedUrlInfo?.resolvedUrl != null

    Card {
        SuperArrowWrapper(
            title = stringResource(R.string.packet_name),
            summary = componentName,
            rightText = size
        )
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
        resolvedUrlInfo?.getFormattedExpiresTime()?.let { expiresTime ->
            SuperArrowWrapper(
                title = stringResource(R.string.release_time),
                summary = expiresTime
            )
        }
        if (resolvedUrlInfo?.needsResolution == true && resolvedUrlInfo?.error != null) {
            SuperArrowWrapper(
                title = stringResource(R.string.url_resolve_failed),
                summary = resolvedUrlInfo?.error
            )
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
        if (finalDownloadUrl != null) {
            PartitionListView(
                downloadUrl = finalDownloadUrl,
                systemVersion = systemVersion
            )
        }
    }
}
