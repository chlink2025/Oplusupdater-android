package com.houvven.oplusupdater.ui.screen.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.houvven.oplusupdater.R
import com.houvven.oplusupdater.utils.HistoryUtils
import top.yukonga.miuix.kmp.extra.SuperDropdown
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SearchHistoryView(
    historyList: List<HistoryUtils.HistoryItem>,
    onHistorySelect: (HistoryUtils.HistoryItem) -> Unit,
    onClearHistory: () -> Unit
) {
    AnimatedVisibility(
        visible = historyList.isNotEmpty(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MiuixTheme.colorScheme.surface)
        ) {
            val selectPlaceholder = stringResource(R.string.select_placeholder)
            val clearHistoryText = stringResource(R.string.clear_history)
            val items = listOf(selectPlaceholder) + historyList.map { "${it.otaVersion} | ${it.model}" } + clearHistoryText

            SuperDropdown(
                title = stringResource(R.string.search_history),
                items = items,
                selectedIndex = 0
            ) { index ->
                when (index) {
                    0 -> return@SuperDropdown
                    items.lastIndex -> onClearHistory()
                    else -> onHistorySelect(historyList[index - 1])
                }
            }
        }
    }
}
