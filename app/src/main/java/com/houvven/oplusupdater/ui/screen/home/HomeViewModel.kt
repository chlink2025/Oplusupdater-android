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

private val guidPattern = Regex("^[0-9a-fA-F]{64}$")

data class HomeFormState(
    val otaVersion: String = "",
    val model: String = "",
    val carrier: String = "",
    val otaRegion: OtaRegion = OtaRegion.CN,
    val updateMode: UpdateMode = UpdateMode.STABLE,
    val grayEnabled: Boolean = false,
    val antiEnabled: Boolean = false,
    val grayNewEnabled: Boolean = false,
    val genshinMode: GenshinMode = GenshinMode.OFF,
    val preEnabled: Boolean = false,
    val guid: String = "",
    val componentsInput: String = "",
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
            val supportsGray = supportsGrayOptions(region)
            state.copy(
                formState = state.formState.copy(
                    otaRegion = region,
                    model = deriveModel(state.formState.otaVersion, region),
                    carrier = defaultCarrier(region),
                    grayEnabled = state.formState.grayEnabled && supportsGray,
                    grayNewEnabled = state.formState.grayNewEnabled && supportsGray,
                )
            )
        }
    }

    fun onUpdateModeChange(updateMode: UpdateMode) {
        _uiState.update { state ->
            state.copy(
                formState = state.formState.copy(updateMode = updateMode)
            )
        }
    }

    fun onGrayEnabledChange(grayEnabled: Boolean) {
        _uiState.update { state ->
            state.copy(
                formState = state.formState.copy(grayEnabled = grayEnabled)
            )
        }
    }

    fun onAntiEnabledChange(antiEnabled: Boolean) {
        _uiState.update { state ->
            state.copy(formState = state.formState.copy(antiEnabled = antiEnabled))
        }
    }

    fun onGrayNewEnabledChange(grayNewEnabled: Boolean) {
        _uiState.update { state ->
            state.copy(formState = state.formState.copy(grayNewEnabled = grayNewEnabled))
        }
    }

    fun onGenshinModeChange(genshinMode: GenshinMode) {
        _uiState.update { state ->
            state.copy(formState = state.formState.copy(genshinMode = genshinMode))
        }
    }

    fun onPreEnabledChange(preEnabled: Boolean) {
        _uiState.update { state ->
            state.copy(formState = state.formState.copy(preEnabled = preEnabled))
        }
    }

    fun onGuidChange(guid: String) {
        _uiState.update { state ->
            state.copy(formState = state.formState.copy(guid = guid))
        }
    }

    fun onComponentsInputChange(componentsInput: String) {
        _uiState.update { state ->
            state.copy(formState = state.formState.copy(componentsInput = componentsInput))
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
            val legacyStrategy = runCatching { QueryStrategy.valueOf(item.queryStrategy) }.getOrDefault(QueryStrategy.NORMAL)
            val supportsGray = supportsGrayOptions(region)
            val grayEnabled = supportsGray && (item.grayEnabled || legacyStrategy == QueryStrategy.GRAY)
            val grayNewEnabled = supportsGray && (item.grayNewEnabled || legacyStrategy == QueryStrategy.GRAYNEW)
            val antiEnabled = item.antiEnabled || legacyStrategy == QueryStrategy.ANTI
            state.copy(
                formState = state.formState.copy(
                    otaVersion = item.otaVersion,
                    model = item.model,
                    carrier = item.carrier,
                    otaRegion = region,
                    updateMode = runCatching { UpdateMode.valueOf(item.updateMode) }.getOrDefault(UpdateMode.STABLE),
                    grayEnabled = grayEnabled,
                    antiEnabled = antiEnabled,
                    grayNewEnabled = grayNewEnabled,
                    genshinMode = runCatching { GenshinMode.valueOf(item.genshinMode) }.getOrDefault(GenshinMode.OFF),
                    preEnabled = item.preEnabled,
                    guid = item.guid.trim(),
                    componentsInput = item.componentsInput,
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
        if (!validateQueryArgs(formState)) {
            return
        }

        val args = QueryUpdateArgs().also {
            it.otaVersion = formState.otaVersion
            it.region = formState.otaRegion.name
            it.model = formState.model
            it.nvCarrier = formState.carrier
            it.mode = formState.updateMode.value
            it.guid = formState.guid.trim()
            it.anti = formState.antiEnabled
            it.gray = formState.grayEnabled
            it.grayNew = formState.grayNewEnabled
            it.pre = formState.preEnabled
            it.genshin = formState.genshinMode.value
            it.componentsInput = formState.componentsInput.trim()
        }
        val historyItem = HistoryUtils.HistoryItem(
            otaVersion = formState.otaVersion,
            region = formState.otaRegion.name,
            model = formState.model,
            carrier = formState.carrier,
            updateMode = formState.updateMode.name,
            queryStrategy = toLegacyQueryStrategy(formState).name,
            grayEnabled = formState.grayEnabled,
            antiEnabled = formState.antiEnabled,
            grayNewEnabled = formState.grayNewEnabled,
            genshinMode = formState.genshinMode.name,
            preEnabled = formState.preEnabled,
            guid = formState.guid.trim(),
            componentsInput = formState.componentsInput.trim(),
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
            updateMode = UpdateMode.STABLE,
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

    private fun validateQueryArgs(formState: HomeFormState): Boolean {
        val guid = formState.guid.trim()
        if (requiresGuidForQuery(formState.updateMode, formState.preEnabled) && guid.isEmpty()) {
            _messages.tryEmit(appContext.getString(R.string.guid_required_message))
            return false
        }
        if (guid.isNotEmpty() && !guidPattern.matches(guid)) {
            _messages.tryEmit(appContext.getString(R.string.guid_invalid_message))
            return false
        }
        return true
    }

    private fun toLegacyQueryStrategy(formState: HomeFormState): QueryStrategy {
        return when {
            formState.grayNewEnabled -> QueryStrategy.GRAYNEW
            formState.antiEnabled -> QueryStrategy.ANTI
            formState.grayEnabled -> QueryStrategy.GRAY
            else -> QueryStrategy.NORMAL
        }
    }
}
