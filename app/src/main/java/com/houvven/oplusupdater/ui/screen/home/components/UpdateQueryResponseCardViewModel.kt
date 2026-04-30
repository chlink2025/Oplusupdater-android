package com.houvven.oplusupdater.ui.screen.home.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.houvven.oplusupdater.utils.DownloadUrlResolver
import com.houvven.oplusupdater.utils.MetadataUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class UpdateQueryResponseCardUiState(
    val buildTime: String? = null,
    val metadataSecurityPatch: String? = null,
    val resolvedUrlInfoByOriginalUrl: Map<String, DownloadUrlResolver.ResolvedUrl> = emptyMap(),
)

class UpdateQueryResponseCardViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(UpdateQueryResponseCardUiState())
    val uiState = _uiState.asStateFlow()

    private var currentRequestId = 0

    fun bindResponse(
        firstComponentUrl: String?,
        resolvableComponentUrls: List<String>
    ) {
        currentRequestId += 1
        val requestId = currentRequestId
        _uiState.value = UpdateQueryResponseCardUiState()

        firstComponentUrl
            ?.takeIf { it.isNotBlank() }
            ?.let { url ->
                viewModelScope.launch {
                    val metadataState = withContext(Dispatchers.IO) {
                        runCatching { loadMetadataState(url) }.getOrNull()
                    } ?: return@launch

                    if (requestId != currentRequestId) {
                        return@launch
                    }

                    _uiState.update { state ->
                        state.copy(
                            buildTime = metadataState.buildTime,
                            metadataSecurityPatch = metadataState.metadataSecurityPatch
                        )
                    }
                }
            }

        resolvableComponentUrls
            .distinct()
            .forEach { originalUrl ->
                viewModelScope.launch {
                    val resolvedUrlInfo = withContext(Dispatchers.IO) {
                        runCatching { DownloadUrlResolver.resolveUrl(originalUrl) }.getOrElse {
                            it.printStackTrace()
                            DownloadUrlResolver.ResolvedUrl(
                                originalUrl = originalUrl,
                                resolvedUrl = null,
                                needsResolution = true,
                                error = it.message
                            )
                        }
                    }

                    if (requestId != currentRequestId) {
                        return@launch
                    }

                    _uiState.update { state ->
                        state.copy(
                            resolvedUrlInfoByOriginalUrl = state.resolvedUrlInfoByOriginalUrl + (originalUrl to resolvedUrlInfo)
                        )
                    }
                }
            }
    }

    private suspend fun loadMetadataState(firstComponentUrl: String): UpdateQueryResponseCardUiState {
        val resolved = DownloadUrlResolver.resolveUrl(firstComponentUrl)
        val urlToUse = resolved.getDownloadUrl()
        val metadata = MetadataUtils.getMetadata(urlToUse)

        if (metadata.isEmpty()) {
            return UpdateQueryResponseCardUiState()
        }

        val buildTime = MetadataUtils.getMetadataValue(metadata, "post-timestamp=")
            .takeIf { it.isNotEmpty() }
            ?.let { timestamp ->
                runCatching {
                    val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())
                    sdf.format(Date(timestamp.toLong() * 1000))
                }.getOrNull()
            }

        val metadataSecurityPatch = MetadataUtils.getMetadataValue(metadata, "post-security-patch-level=")
            .takeIf { it.isNotEmpty() }

        return UpdateQueryResponseCardUiState(
            buildTime = buildTime,
            metadataSecurityPatch = metadataSecurityPatch
        )
    }
}
