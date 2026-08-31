package com.tedflix.app

import android.content.Context
import com.tedflix.app.auth.AuthSession
import org.json.JSONArray
import org.json.JSONObject

/** Progresso local; a sincronização autenticada pode ser adicionada depois. */
object ContinueWatchingStore {
    private const val LEGACY_PREFS = "tedflix_continue_watching"
    private const val ITEMS = "items"

    private fun prefs(context: Context): android.content.SharedPreferences {
        val profileId = AuthSession.activeProfileId().trim()
        if (profileId.isBlank()) return context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        val scopedName = "${LEGACY_PREFS}_$profileId"
        val scoped = context.getSharedPreferences(scopedName, Context.MODE_PRIVATE)
        if (!scoped.contains(ITEMS)) {
            val legacy = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
            if (legacy.contains(ITEMS)) {
                scoped.edit().putString(ITEMS, legacy.getString(ITEMS, "[]")).apply()
            }
        }
        return scoped
    }
    private const val MAX_ITEMS = 30

    data class Entry(
        val categoria: String,
        val slug: String,
        val titulo: String,
        val filmeId: String = "",
        val positionMs: Long,
        val durationMs: Long,
        val updatedAt: Long,
        val thumb: String = "",
        val tipo: String = "",
        val serieCategoria: String = "",
        val serieSlug: String = "",
    ) {
        val key: String get() = "$categoria:$slug"
        val percent: Int get() = if (durationMs > 0) ((positionMs * 100L) / durationMs).toInt().coerceIn(0, 99) else 0
    }

    fun save(
        context: Context,
        categoria: String,
        slug: String,
        titulo: String,
        positionMs: Long,
        durationMs: Long,
        filmeId: String = "",
        thumb: String = "",
        tipo: String = "",
        serieCategoria: String = "",
        serieSlug: String = "",
    ) {
        val categoriaSegura = categoria.ifBlank { if (tipo.contains("sér", true) || tipo.contains("ser", true)) "series" else "filmes" }
        val slugSeguro = slug.ifBlank { filmeId }
        if (slugSeguro.isBlank() || positionMs <= 0L || durationMs <= 0L) return
        if (positionMs >= (durationMs * 0.9f).toLong()) {
            remove(context, categoriaSegura, slugSeguro)
            return
        }
        val entries = read(context).filterNot { it.key == "$categoriaSegura:$slugSeguro" }.toMutableList()
        entries.add(0, Entry(categoriaSegura, slugSeguro, titulo.ifBlank { "Tedflix" }, filmeId.ifBlank { slugSeguro }, positionMs, durationMs, System.currentTimeMillis(), thumb, tipo, serieCategoria, serieSlug))
        write(context, entries.take(MAX_ITEMS))
    }

    fun remove(context: Context, categoria: String, slug: String) {
        write(context, read(context).filterNot { it.key == "$categoria:$slug" })
    }

    fun read(context: Context): List<Entry> {
        val raw = prefs(context).getString(ITEMS, "[]") ?: "[]"
        return try {
            val json = JSONArray(raw)
            buildList {
                for (i in 0 until json.length()) {
                    val item = json.optJSONObject(i) ?: continue
                    val categoria = item.optString("categoria")
                    val slug = item.optString("slug")
                    if (categoria.isBlank() || slug.isBlank()) continue
                    add(Entry(categoria, slug, item.optString("titulo", "Tedflix"), item.optString("filmeId", slug), item.optLong("positionMs"), item.optLong("durationMs"), item.optLong("updatedAt"), item.optString("thumb"), item.optString("tipo"), item.optString("serieCategoria"), item.optString("serieSlug")))
                }
            }.sortedByDescending { it.updatedAt }
        } catch (_: Throwable) { emptyList() }
    }

    fun toJson(context: Context): String = JSONArray().apply {
        read(context).forEach { item -> put(JSONObject().apply {
            put("categoria", item.categoria)
            put("slug", item.slug)
            put("titulo", item.titulo)
            put("filmeId", item.filmeId)
            put("positionMs", item.positionMs)
            put("durationMs", item.durationMs)
            put("percent", item.percent)
            put("thumb", item.thumb)
            put("tipo", item.tipo)
            put("serieCategoria", item.serieCategoria)
            put("serieSlug", item.serieSlug)
        }) }
    }.toString()

    private fun write(context: Context, entries: List<Entry>) {
        val json = JSONArray().apply {
            entries.forEach { item -> put(JSONObject().apply {
                put("categoria", item.categoria)
                put("slug", item.slug)
                put("titulo", item.titulo)
                put("filmeId", item.filmeId)
                put("positionMs", item.positionMs)
                put("durationMs", item.durationMs)
                put("updatedAt", item.updatedAt)
                put("thumb", item.thumb)
                put("tipo", item.tipo)
                put("serieCategoria", item.serieCategoria)
                put("serieSlug", item.serieSlug)
            }) }
        }
        prefs(context).edit().putString(ITEMS, json.toString()).apply()
    }
}
