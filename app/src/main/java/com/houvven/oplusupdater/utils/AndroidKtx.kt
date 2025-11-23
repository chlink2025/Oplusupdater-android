package com.houvven.oplusupdater.utils

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri

fun Context.toast(msg: String, length: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, msg, length).show()
}

fun Context.toast(msg: Int, length: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, msg, length).show()
}

fun Context.startUrl(url: String) {
    startActivity(Intent(Intent.ACTION_VIEW).apply { data = url.toUri() })
}