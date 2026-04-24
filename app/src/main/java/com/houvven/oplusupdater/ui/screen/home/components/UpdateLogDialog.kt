package com.houvven.oplusupdater.ui.screen.home.components

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.annotation.Keep
import androidx.compose.animation.AnimatedVisibility as ComposeAnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import com.houvven.oplusupdater.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.BackHandler
import java.net.HttpURLConnection
import java.net.URL


@SuppressLint("SetJavaScriptEnabled")
@Composable
fun UpdateLogDialog(
    show: Boolean,
    url: String,
    softwareVersion: String,
    onDismissRequest: () -> Unit
) {
    val isDarkTheme = isSystemInDarkTheme()
    var responseHtml by rememberSaveable { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var reloadTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(show, url, reloadTrigger) {
        if (!show) {
            return@LaunchedEffect
        }

        isLoading = true
        loadError = null
        responseHtml = ""

        val result = withContext(Dispatchers.IO) {
            runCatching {
                Log.d("UpdateLogDialog", "startup get html")
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
                    html
                } finally {
                    conn.disconnect()
                }
            }
        }

        result.onSuccess { html ->
            responseHtml = html
        }.onFailure {
            loadError = it.message ?: "Unknown error"
        }
        isLoading = false
    }

    Popup(
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(excludeFromSystemGesture = false)
    ) {
        ComposeAnimatedVisibility(
            visible = show,
            enter = slideInVertically(
                initialOffsetY = { fullHeight -> fullHeight },
                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { fullHeight -> fullHeight },
                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
            ) + fadeOut()
        ) {
            BackHandler(show, onDismissRequest)

            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                        .background(if (isDarkTheme) Color(0xFF404040) else Color(0xFFFAFAFA)),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 26.dp)
                            .padding(top = 24.dp)
                            .zIndex(1f),
                    ) {
                        Text(
                            text = stringResource(R.string.software_version),
                            style = TextStyle(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.W500,
                                color = MiuixTheme.colorScheme.onSurface
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = softwareVersion,
                            style = TextStyle(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.W500,
                                color = if (isDarkTheme) Color.Gray else Color.DarkGray
                            )
                        )

                        Box(
                            modifier = Modifier
                                .padding(vertical = 16.dp)
                                .height((0.5).dp)
                                .fillMaxWidth()
                                .background(MiuixTheme.colorScheme.dividerLine)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 26.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (responseHtml.isNotBlank() && !isLoading && loadError == null) {
                            AndroidView(
                                factory = { context ->
                                    WebView(context).apply {
                                        setBackgroundColor(Color.Transparent.toArgb())
                                        // @formatter:off
                                        addJavascriptInterface(object { @JavascriptInterface @Keep @Suppress("unused") fun isNight(): Boolean = isDarkTheme }, "HeytapTheme")
                                        // @formatter:on
                                        settings.javaScriptEnabled = true
                                        settings.javaScriptCanOpenWindowsAutomatically = false
                                        settings.allowFileAccess = false
                                        settings.allowContentAccess = false
                                    }
                                },
                                update = {
                                    it.loadDataWithBaseURL(url, responseHtml, "text/html", "utf-8", null)
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        if (isLoading || loadError != null || responseHtml.isBlank()) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (isLoading) {
                                    InfiniteProgressIndicator(
                                        color = MiuixTheme.colorScheme.primary,
                                        size = 24.dp
                                    )
                                    Text(
                                        text = stringResource(R.string.update_log_loading),
                                        color = MiuixTheme.colorScheme.onSurface
                                    )
                                } else if (loadError != null) {
                                    Text(
                                        text = stringResource(R.string.update_log_load_failed),
                                        color = MiuixTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = loadError.orEmpty(),
                                        color = if (isDarkTheme) Color.Gray else Color.DarkGray,
                                        fontSize = 12.sp
                                    )
                                    Button(
                                        onClick = { reloadTrigger++ },
                                        colors = ButtonDefaults.buttonColorsPrimary()
                                    ) {
                                        Text(
                                            text = stringResource(R.string.retry),
                                            color = MiuixTheme.colorScheme.onPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    backgroundColor = MiuixTheme.colorScheme.secondaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}
