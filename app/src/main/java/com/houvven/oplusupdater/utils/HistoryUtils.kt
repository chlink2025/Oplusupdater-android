package com.houvven.oplusupdater.utils

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object HistoryUtils {
    private const val PREF_NAME = "search_history"
    private const val KEY_HISTORY = "history_list"
    private const val MAX_HISTORY_SIZE = 10

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class HistoryItem(
        val otaVersion: String,
        val region: String,
        val model: String,
        val carrier: String
    )

    fun saveHistory(context: Context, item: HistoryItem) {
        val history = getHistory(context).toMutableList()
        history.removeAll { it == item }
        history.add(0, item)
        if (history.size > MAX_HISTORY_SIZE) {
            history.removeAt(history.lastIndex)
        }
        saveList(context, history)
    }

    fun getHistory(context: Context): List<HistoryItem> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<HistoryItem>>(jsonStr)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clearHistory(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit {
            remove(KEY_HISTORY)
        }
    }

    private fun saveList(context: Context, list: List<HistoryItem>) {
        val jsonStr = json.encodeToString(list)
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_HISTORY, jsonStr)
        }
    }
}
