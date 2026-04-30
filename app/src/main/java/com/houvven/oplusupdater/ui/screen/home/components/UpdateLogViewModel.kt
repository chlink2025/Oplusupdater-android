package com.houvven.oplusupdater.ui.screen.home.components

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class UpdateLogUiState(
    val responseHtml: String = "",
    val isLoading: Boolean = false,
    val loadError: String? = null,
)

class UpdateLogViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(UpdateLogUiState())
    val uiState = _uiState.asStateFlow()

    private var currentUrl: String? = null

    fun load(url: String, force: Boolean = false) {
        if (url.isBlank()) {
            currentUrl = null
            _uiState.value = UpdateLogUiState(loadError = "Empty update log URL")
            return
        }

        val state = _uiState.value
        if (!force && currentUrl == url && (state.isLoading || state.responseHtml.isNotBlank())) {
            return
        }

        currentUrl = url
        _uiState.value = UpdateLogUiState(isLoading = true)

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { fetchHtml(url) }
            }

            _uiState.value = result.fold(
                onSuccess = { html -> UpdateLogUiState(responseHtml = html) },
                onFailure = { error ->
                    UpdateLogUiState(loadError = error.message ?: "Unknown error")
                }
            )
        }
    }

    fun retry() {
        currentUrl?.let { load(it, force = true) }
    }

    fun reset() {
        currentUrl = null
        _uiState.value = UpdateLogUiState()
    }

    private fun fetchHtml(url: String): String {
        Log.d("UpdateLogViewModel", "startup get html")
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 10000
        }
        try {
            val responseCode = conn.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                error("HTTP $responseCode")
            }
            val html = conn.inputStream.bufferedReader().use { reader -> reader.readText() }
            if (html.isBlank()) {
                error("Empty response body")
            }
            return html
        } finally {
            conn.disconnect()
        }
    }
}
