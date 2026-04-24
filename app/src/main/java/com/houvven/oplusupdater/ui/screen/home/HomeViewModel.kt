package com.houvven.oplusupdater.ui.screen.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.houvven.oplusupdater.R
import com.houvven.oplusupdater.utils.HistoryUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import updater.QueryUpdateArgs
import updater.ResponseResult
import updater.Updater

data class HomeUiState(
    val isQuerying: Boolean = false,
    val responseResult: ResponseResult? = null,
    val historyList: List<HistoryUtils.HistoryItem> = emptyList(),
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()

    init {
        refreshHistory()
    }

    fun refreshHistory() {
        _uiState.update { state ->
            state.copy(historyList = HistoryUtils.getHistory(appContext))
        }
    }

    fun clearHistory() {
        HistoryUtils.clearHistory(appContext)
        _uiState.update { state ->
            state.copy(historyList = emptyList())
        }
    }

    fun queryUpdate(args: QueryUpdateArgs, historyItem: HistoryUtils.HistoryItem) {
        if (_uiState.value.isQuerying) {
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            HistoryUtils.saveHistory(appContext, historyItem)
            val updatedHistory = HistoryUtils.getHistory(appContext)
            _uiState.update { state ->
                state.copy(
                    isQuerying = true,
                    responseResult = null,
                    historyList = updatedHistory,
                )
            }

            try {
                val result = Updater.queryUpdate(args)
                _uiState.update { state ->
                    state.copy(
                        isQuerying = false,
                        responseResult = result,
                    )
                }

                val message = when (result.responseCode.toInt()) {
                    200 -> appContext.getString(R.string.msg_query_success)
                    304 -> appContext.getString(R.string.msg_no_update_available)
                    else -> "code: ${result.responseCode}, ${result.errMsg}"
                }
                _messages.emit(message)
            } catch (e: Exception) {
                _uiState.update { state ->
                    state.copy(isQuerying = false)
                }
                _messages.emit(e.message ?: e.stackTraceToString())
            }
        }
    }
}
