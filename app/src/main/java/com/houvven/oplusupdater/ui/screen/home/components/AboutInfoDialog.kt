package com.houvven.oplusupdater.ui.screen.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.houvven.oplusupdater.utils.startUrl
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AboutInfoDialog(
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            onDismissRequest()
                        }
                    )
                },
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .padding(8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MiuixTheme.colorScheme.surface)
            ) {
                SuperArrow(
                    title = "Author",
                    summary = "Houvven",
                )
                SuperArrow(
                    title = "Version",
                    summary = "${packageInfo.versionName}(${packageInfo.versionCode})"
                )
                SuperArrow(
                    title = "GitHub",
                    summary = "https://github.com/houvven/OplusUpdater-android",
                    onClick = {
                        context.startUrl("https://github.com/houvven/OplusUpdater-android")
                    }
                )
            }
        }
    }
}