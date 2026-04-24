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

data class HomeFormState(
    val otaVersion: String = "",
    val model: String = "",
    val carrier: String = "",
    val otaRegion: OtaRegion = OtaRegion.CN,
    val updateMode: UpdateMode = UpdateMode.STABLE,
    val expandMoreParameters: Boolean = true,
)

data class HomeUiState(
    val formState: HomeFormState = HomeFormState(),
    val showAboutInfoDialog: Boolean = false,
    val isQuerying: Boolean = false,
    val responseResult: ResponseResult? = null,
    val historyList: List<HistoryUtils.HistoryItem> = emptyList(),
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext

    private val _uiState = MutableStateFlow(
        HomeUiState(formState = buildInitialFormState())
    )
    val uiState = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()

    init {
        refreshHistory()
    }

    fun onOtaVersionChange(otaVersion: String) {
        _uiState.update { state ->
            val region = state.formState.otaRegion
            state.copy(
                formState = state.formState.copy(
                    otaVersion = otaVersion,
                    model = deriveModel(otaVersion, region),
                )
            )
        }
    }

    fun onModelChange(model: String) {
        _uiState.update { state ->
            state.copy(formState = state.formState.copy(model = model))
        }
    }

    fun onCarrierChange(carrier: String) {
        _uiState.update { state ->
            state.copy(formState = state.formState.copy(carrier = carrier))
        }
    }

    fun onRegionChange(region: OtaRegion) {
        _uiState.update { state ->
            state.copy(
                formState = state.formState.copy(
                    otaRegion = region,
                    model = deriveModel(state.formState.otaVersion, region),
                    carrier = defaultCarrier(region),
                )
            )
        }
    }

    fun onUpdateModeChange(updateMode: UpdateMode) {
        _uiState.update { state ->
            state.copy(formState = state.formState.copy(updateMode = updateMode))
        }
    }

    fun toggleMoreParameters() {
        _uiState.update { state ->
            state.copy(
                formState = state.formState.copy(
                    expandMoreParameters = !state.formState.expandMoreParameters
                )
            )
        }
    }

    fun applyHistoryItem(item: HistoryUtils.HistoryItem) {
        _uiState.update { state ->
            val region = runCatching { OtaRegion.valueOf(item.region) }.getOrDefault(OtaRegion.CN)
            state.copy(
                formState = state.formState.copy(
                    otaVersion = item.otaVersion,
                    model = item.model,
                    carrier = item.carrier,
                    otaRegion = region,
                )
            )
        }
    }

    fun showAboutInfoDialog() {
        _uiState.update { state ->
            state.copy(showAboutInfoDialog = true)
        }
    }

    fun hideAboutInfoDialog() {
        _uiState.update { state ->
            state.copy(showAboutInfoDialog = false)
        }
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

    fun queryUpdate() {
        if (_uiState.value.isQuerying) {
            return
        }

        val formState = _uiState.value.formState
        val args = QueryUpdateArgs().also {
            it.otaVersion = formState.otaVersion
            it.region = formState.otaRegion.name
            it.model = formState.model
            it.nvCarrier = formState.carrier
            it.mode = formState.updateMode.value
        }
        val historyItem = HistoryUtils.HistoryItem(
            otaVersion = formState.otaVersion,
            region = formState.otaRegion.name,
            model = formState.model,
            carrier = formState.carrier,
        )

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

    private fun buildInitialFormState(): HomeFormState {
        val otaVersion = systemOtaVersion
        val region = OtaRegion.CN
        return HomeFormState(
            otaVersion = otaVersion,
            model = deriveModel(otaVersion, region),
            carrier = defaultCarrier(region),
            otaRegion = region,
        )
    }

    private fun deriveModel(otaVersion: String, region: OtaRegion): String {
        val baseModel = otaVersion.split("_").firstOrNull()?.takeIf { it.isNotBlank() } ?: return ""
        return when (region) {
            OtaRegion.EU -> baseModel + "EEA"
            OtaRegion.IN -> baseModel + "IN"
            else -> baseModel
        }
    }

    private fun defaultCarrier(region: OtaRegion): String {
        return Updater.getConfig(region.name, false).carrierID
    }
}
