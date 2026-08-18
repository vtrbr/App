package com.tedflix.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Progresso local; a sincronização autenticada pode ser adicionada depois. */
object ContinueWatchingStore {
    private const val PREFS = "tedflix_continue_watching"
    private const val ITEMS = "items"
    private const val MAX_ITEMS = 30

    data class Entry(
        val categoria: String,
        val slug: String,
        val titulo: String,
        val positionMs: Long,
        val durationMs: Long,
        val updatedAt: Long,
    ) {
        val key: String get() = "$categoria:$slug"
        val percent: Int get() = if (durationMs > 0) ((positionMs * 100L) / durationMs).toInt().coerceIn(0, 99) else 0
    }

    fun save(context: Context, categoria: String, slug: String, titulo: String, positionMs: Long, durationMs: Long) {
        if (categoria.isBlank() || slug.isBlank() || positionMs < 10_000L || durationMs <= 0L) return
        if (positionMs >= (durationMs * 0.9f).toLong()) {
            remove(context, categoria, slug)
            return
        }
        val entries = read(context).filterNot { it.key == "$categoria:$slug" }.toMutableList()
        entries.add(0, Entry(categoria, slug, titulo.ifBlank { "Tedflix" }, positionMs, durationMs, System.currentTimeMillis()))
        write(context, entries.take(MAX_ITEMS))
    }

    fun remove(context: Context, categoria: String, slug: String) {
        write(context, read(context).filterNot { it.key == "$categoria:$slug" })
    }

    fun read(context: Context): List<Entry> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(ITEMS, "[]") ?: "[]"
        return try {
            val json = JSONArray(raw)
            buildList {
                for (i in 0 until json.length()) {
                    val item = json.optJSONObject(i) ?: continue
                    val categoria = item.optString("categoria")
                    val slug = item.optString("slug")
                    if (categoria.isBlank() || slug.isBlank()) continue
                    add(Entry(categoria, slug, item.optString("titulo", "Tedflix"), item.optLong("positionMs"), item.optLong("durationMs"), item.optLong("updatedAt")))
                }
            }.sortedByDescending { it.updatedAt }
        } catch (_: Throwable) { emptyList() }
    }

    fun toJson(context: Context): String = JSONArray().apply {
        read(context).forEach { item -> put(JSONObject().apply {
            put("categoria", item.categoria)
            put("slug", item.slug)
            put("titulo", item.titulo)
            put("positionMs", item.positionMs)
            put("durationMs", item.durationMs)
            put("percent", item.percent)
        }) }
    }.toString()

    private fun write(context: Context, entries: List<Entry>) {
        val json = JSONArray().apply {
            entries.forEach { item -> put(JSONObject().apply {
                put("categoria", item.categoria)
                put("slug", item.slug)
                put("titulo", item.titulo)
                put("positionMs", item.positionMs)
                put("durationMs", item.durationMs)
                put("updatedAt", item.updatedAt)
            }) }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(ITEMS, json.toString()).apply()
    }
}
