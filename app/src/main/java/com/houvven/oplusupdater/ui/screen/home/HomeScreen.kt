package com.houvven.oplusupdater.ui.screen.home

import android.annotation.SuppressLint
import androidx.annotation.Keep
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.houvven.oplusupdater.R
import com.houvven.oplusupdater.ui.screen.home.components.AboutInfoDialog
import com.houvven.oplusupdater.ui.screen.home.components.UpdateQueryResponseCard
import com.houvven.oplusupdater.utils.toast
import kotlinx.coroutines.flow.collectLatest
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.extra.SuperDropdown
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Keep
enum class OtaRegion(
    @StringRes val strRes: Int,
) {
    CN(R.string.china),
    EU(R.string.europe),
    IN(R.string.india),
    SG(R.string.singapore),
    RU(R.string.russia),
    TR(R.string.turkey),
    TH(R.string.thailand),
    GL(R.string.global)
}

@Keep
enum class UpdateMode(
    @StringRes val strRes: Int,
    val value: String
) {
    STABLE(R.string.update_mode_stable, "manual"),
    TASTE(R.string.update_mode_taste, "taste")
}

@Keep
enum class QueryStrategy(
    @StringRes val strRes: Int,
) {
    NORMAL(R.string.query_strategy_normal),
    GRAY(R.string.query_strategy_gray),
    ANTI(R.string.query_strategy_anti),
    GRAYNEW(R.string.query_strategy_graynew),
}

@Keep
enum class GenshinMode(
    @StringRes val strRes: Int,
    val value: String
) {
    OFF(R.string.genshin_off, "0"),
    YS(R.string.genshin_ys, "1"),
    OVT(R.string.genshin_ovt, "2"),
}

fun availableQueryStrategies(region: OtaRegion): List<QueryStrategy> {
    return when (region) {
        OtaRegion.CN -> QueryStrategy.entries
        else -> listOf(QueryStrategy.NORMAL, QueryStrategy.ANTI)
    }
}

fun availableUpdateModes(strategy: QueryStrategy): List<UpdateMode> {
    return when (strategy) {
        QueryStrategy.ANTI -> listOf(UpdateMode.TASTE)
        QueryStrategy.GRAYNEW -> listOf(UpdateMode.STABLE)
        else -> UpdateMode.entries
    }
}

fun requiresGuidForQuery(strategy: QueryStrategy, mode: UpdateMode, preEnabled: Boolean): Boolean {
    if (preEnabled) {
        return true
    }
    return strategy != QueryStrategy.ANTI &&
        strategy != QueryStrategy.GRAYNEW &&
        mode == UpdateMode.TASTE
}

@delegate:SuppressLint("PrivateApi")
val systemOtaVersion: String by lazy {
    val clazz = Class.forName("android.os.SystemProperties")
    val method = clazz.getMethod("get", String::class.java, String::class.java)
    method.invoke(clazz, "ro.build.version.ota", "") as String
}

@Composable
fun HomeScreen() {
    val homeViewModel: HomeViewModel = viewModel()
    val uiState by homeViewModel.uiState.collectAsState()
    val formState = uiState.formState
    val strategyOptions = availableQueryStrategies(formState.otaRegion)
    val modeOptions = availableUpdateModes(formState.queryStrategy)
    val guidRequired = requiresGuidForQuery(
        strategy = formState.queryStrategy,
        mode = formState.updateMode,
        preEnabled = formState.preEnabled
    )
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    LaunchedEffect(homeViewModel) {
        homeViewModel.messages.collectLatest {
            context.toast(it)
        }
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.app_name),
                actions = {
                    IconButton(onClick = {
                        homeViewModel.showAboutInfoDialog()
                    }) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null
                        )
                    }
                }
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextField(
                value = formState.otaVersion,
                onValueChange = { homeViewModel.onOtaVersionChange(it.trim()) },
                label = stringResource(R.string.ota_version),
                trailingIcon = {
                    IconButton(
                        onClick = { homeViewModel.toggleMoreParameters() }
                    ) {
                        val icon = if (formState.expandMoreParameters) {
                            Icons.Outlined.ExpandLess
                        } else {
                            Icons.Outlined.ExpandMore
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                    }
                }
            )

            AnimatedVisibility(visible = formState.expandMoreParameters) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextField(
                            value = formState.model,
                            onValueChange = { homeViewModel.onModelChange(it.trim()) },
                            modifier = Modifier
                                .weight(1f),
                            label = stringResource(R.string.model)
                        )
                        TextField(
                            value = formState.carrier,
                            onValueChange = { homeViewModel.onCarrierChange(it.trim()) },
                            modifier = Modifier
                                .weight(1f),
                            label = stringResource(R.string.carrier)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MiuixTheme.colorScheme.surface)
                        ) {
                            SuperDropdown(
                                title = stringResource(R.string.genshin),
                                items = GenshinMode.entries.map { stringResource(it.strRes) },
                                selectedIndex = GenshinMode.entries.indexOf(formState.genshinMode)
                            ) {
                                homeViewModel.onGenshinModeChange(GenshinMode.entries[it])
                            }
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MiuixTheme.colorScheme.surface)
                        ) {
                            SuperDropdown(
                                title = stringResource(R.string.preview_query),
                                items = listOf(
                                    stringResource(R.string.preview_disabled),
                                    stringResource(R.string.preview_enabled)
                                ),
                                selectedIndex = if (formState.preEnabled) 1 else 0
                            ) {
                                homeViewModel.onPreEnabledChange(it == 1)
                            }
                        }
                    }

                    TextField(
                        value = formState.guid,
                        onValueChange = { homeViewModel.onGuidChange(it.trim()) },
                        label = stringResource(R.string.guid)
                    )

                    if (guidRequired) {
                        Text(
                            text = stringResource(R.string.guid_required_hint),
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            fontSize = 12.sp
                        )
                    }

                    TextField(
                        value = formState.componentsInput,
                        onValueChange = { homeViewModel.onComponentsInputChange(it) },
                        label = stringResource(R.string.components)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MiuixTheme.colorScheme.surface)
            ) {
                SuperDropdown(
                    title = stringResource(R.string.region),
                    items = OtaRegion.entries.map { stringResource(it.strRes) },
                    selectedIndex = OtaRegion.entries.indexOf(formState.otaRegion)
                ) {
                    homeViewModel.onRegionChange(OtaRegion.entries[it])
                }
            }

            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MiuixTheme.colorScheme.surface)
            ) {
                SuperDropdown(
                    title = stringResource(R.string.query_strategy),
                    items = strategyOptions.map { stringResource(it.strRes) },
                    selectedIndex = strategyOptions.indexOf(formState.queryStrategy).coerceAtLeast(0)
                ) {
                    homeViewModel.onQueryStrategyChange(strategyOptions[it])
                }
            }

            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MiuixTheme.colorScheme.surface)
            ) {
                SuperDropdown(
                    title = stringResource(R.string.update_mode),
                    items = modeOptions.map { stringResource(it.strRes) },
                    selectedIndex = modeOptions.indexOf(formState.updateMode).coerceAtLeast(0)
                ) {
                    homeViewModel.onUpdateModeChange(modeOptions[it])
                }
            }
            
            // Search History View - placed before query button
            com.houvven.oplusupdater.ui.screen.home.components.SearchHistoryView(
                historyList = uiState.historyList,
                onHistorySelect = { item ->
                    homeViewModel.applyHistoryItem(item)
                },
                onClearHistory = {
                    homeViewModel.clearHistory()
                }
            )

            Button(
                onClick = {
                    focusManager.clearFocus()
                    homeViewModel.queryUpdate()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                enabled = formState.otaVersion.isNotBlank(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                if (uiState.isQuerying) {
                    InfiniteProgressIndicator(
                        color = MiuixTheme.colorScheme.onPrimary,
                        size = 20.dp
                    )
                } else {
                    Text(
                        text = stringResource(R.string.query),
                        color = MiuixTheme.colorScheme.onPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }

            AnimatedVisibility(visible = uiState.responseResult != null) {
                uiState.responseResult?.let { UpdateQueryResponseCard(it) }
            }

            if (uiState.showAboutInfoDialog) {
                AboutInfoDialog(
                    onDismissRequest = { homeViewModel.hideAboutInfoDialog() }
                )
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
private fun HomePreview() {
    HomeScreen()
}
