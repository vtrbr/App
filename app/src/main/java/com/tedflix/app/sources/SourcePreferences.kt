package com.tedflix.app.sources

import android.content.Context

class SourcePreferences(context: Context) {
    private val prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun read(): Selection {
        val mode = runCatching {
            SourceSelectionMode.valueOf(prefs.getString(KEY_MODE, SourceSelectionMode.PRIMARY.name).orEmpty())
        }.getOrDefault(SourceSelectionMode.PRIMARY)
        val sourceId = prefs.getString(KEY_SOURCE_ID, SourceRegistry.PRIMARY_ID).orEmpty()
        return Selection(mode = mode, sourceId = sourceId)
    }

    fun save(selection: Selection) {
        prefs.edit()
            .putString(KEY_MODE, selection.mode.name)
            .putString(KEY_SOURCE_ID, selection.sourceId)
            .apply()
    }

    data class Selection(
        val mode: SourceSelectionMode,
        val sourceId: String,
    )

    private companion object {
        const val FILE_NAME = "tedflix_sources"
        const val KEY_MODE = "selection_mode"
        const val KEY_SOURCE_ID = "source_id"
    }
}
