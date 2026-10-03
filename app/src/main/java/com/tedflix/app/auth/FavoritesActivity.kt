package com.tedflix.app.auth

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.media3.common.util.UnstableApi
import com.tedflix.app.ContinueWatchingStore
import com.tedflix.app.MainActivity
import com.tedflix.app.TedflixLoading
import java.net.URL

@UnstableApi
class FavoritesActivity : Activity() {
    private lateinit var list: LinearLayout
    private lateinit var loadingPanel: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AuthSession.init(applicationContext)
        if (!AuthSession.hasToken()) { finish(); return }
        setContentView(buildScreen())
        loadFavorites()
    }

    private fun buildScreen(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(5, 6, 9)) }
        val scroll = ScrollView(this).apply { overScrollMode = View.OVER_SCROLL_NEVER }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(18), dp(20), dp(30)) }
        val toolbar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        toolbar.addView(button("‹", 30) { finish() }, LinearLayout.LayoutParams(dp(48), dp(52)))
        toolbar.addView(TextView(this).apply { text = "Favoritos"; textSize = 23f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD }, LinearLayout.LayoutParams(0, -2, 1f))
        toolbar.addView(button("↻", 24) { loadFavorites() }, LinearLayout.LayoutParams(dp(48), dp(52)))
        content.addView(toolbar, LinearLayout.LayoutParams(-1, dp(58)).apply { bottomMargin = dp(12) })
        content.addView(TextView(this).apply { text = "Seus títulos salvos"; textSize = 13f; setTextColor(Color.LTGRAY) }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(18) })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(list, LinearLayout.LayoutParams(-1, -2))
        scroll.addView(content)
        root.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        loadingPanel = TedflixLoading.create(this).apply { visibility = View.GONE }
        root.addView(loadingPanel, FrameLayout.LayoutParams(-1, -1))
        return root
    }

    private fun loadFavorites() {
        if (!::list.isInitialized) return
        TedflixLoading.show(loadingPanel, true)
        list.removeAllViews()
        Thread {
            val favoritesResult = AuthSession.listFavorites()
            val historyResult = AuthSession.continueWatching()
            val localProgress = ContinueWatchingStore.read(applicationContext)
            runOnUiThread {
                TedflixLoading.show(loadingPanel, false)
                if (!favoritesResult.ok) {
                    list.addView(message(favoritesResult.message.ifBlank { "Não foi possível carregar os favoritos." }))
                    return@runOnUiThread
                }
                val favorites = favoritesResult.value.orEmpty()
                if (favorites.isEmpty()) {
                    list.addView(message("Você ainda não adicionou nenhum favorito."))
                    return@runOnUiThread
                }
                val remoteById = historyResult.value.orEmpty().associateBy { it.filmeId }
                val remoteByTitle = historyResult.value.orEmpty().associateBy { it.titulo.trim().lowercase() }
                Thread {
                    val enriched = favorites.map { favorite ->
                        if (favorite.categoria.isNotBlank() && favorite.slug.isNotBlank() && favorite.thumb.isNotBlank()) {
                            favorite
                        } else {
                            val resolved = AuthSession.resolveCatalogItem(favorite.titulo, favorite.filmeId).value
                            if (resolved == null) favorite else favorite.copy(
                                thumb = favorite.thumb.ifBlank { resolved.thumb },
                                categoria = favorite.categoria.ifBlank { resolved.categoria },
                                slug = favorite.slug.ifBlank { resolved.slug },
                                tipo = favorite.tipo.ifBlank { resolved.tipo },
                            )
                        }
                    }
                    runOnUiThread {
                        enriched.forEach { favorite ->
                            val local = localProgress.firstOrNull { it.filmeId == favorite.filmeId || it.titulo.equals(favorite.titulo, ignoreCase = true) }
                            val remote = remoteById[favorite.filmeId] ?: remoteByTitle[favorite.titulo.trim().lowercase()]
                            addFavorite(favorite, local, remote)
                        }
                    }
                }.start()
            }
        }.start()
    }

    private fun addFavorite(
        favorite: AuthSession.Favorite,
        local: ContinueWatchingStore.Entry?,
        remote: AuthSession.HistoryItem?,
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = rounded(Color.rgb(18, 18, 20), dp(14), Color.argb(55, 255, 255, 255), dp(1))
            setOnClickListener { openFavorite(favorite, local) }
        }
        val image = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setBackgroundColor(Color.rgb(35, 35, 38)) }
        row.addView(image, LinearLayout.LayoutParams(dp(74), dp(104)))
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(8), 0) }
        info.addView(TextView(this).apply { text = favorite.titulo.ifBlank { "Título salvo" }; textSize = 16f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD })
        val context = local?.takeIf { it.serieSlug.isNotBlank() }?.let { "Série · ${it.serieSlug}" } ?: "Filme"
        info.addView(TextView(this).apply { text = context; textSize = 11f; setTextColor(Color.GRAY); setPadding(0, dp(6), 0, dp(6)) })

        val percent = local?.percent ?: 0
        if (percent > 0) {
            val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = percent
                progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(229, 9, 20))
                progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(75, 75, 80))
            }
            info.addView(bar, LinearLayout.LayoutParams(-1, dp(5)).apply { bottomMargin = dp(4) })
            info.addView(TextView(this).apply {
                text = "Continuar em ${formatMs(local?.positionMs ?: 0L)}"
                textSize = 11f
                setTextColor(Color.LTGRAY)
            })
        } else if (!remote?.tempo.isNullOrBlank()) {
            info.addView(TextView(this).apply {
                text = "Continuar em ${remote?.tempo}"
                textSize = 11f
                setTextColor(Color.LTGRAY)
            })
        }

        info.addView(button("Remover", 12) { removeFavorite(favorite) }.apply {
            background = rounded(Color.rgb(95, 15, 22), dp(8), Color.TRANSPARENT, 0)
        }, LinearLayout.LayoutParams(dp(92), dp(40)).apply { topMargin = dp(8) })
        row.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        if (favorite.thumb.isNotBlank()) Thread {
            try {
                val bitmap = URL(favorite.thumb).openStream().use { BitmapFactory.decodeStream(it) }
                runOnUiThread { if (bitmap != null) image.setImageBitmap(bitmap) }
            } catch (_: Throwable) {}
        }.start()
    }

    private fun openFavorite(favorite: AuthSession.Favorite, local: ContinueWatchingStore.Entry?) {
        val localIsSeries = local?.serieSlug?.isNotBlank() == true
        val category = favorite.categoria.ifBlank {
            local?.serieCategoria?.ifBlank { local.categoria }.orEmpty()
        }
        val slug = favorite.slug.ifBlank {
            if (localIsSeries) local?.serieSlug.orEmpty() else local?.slug.orEmpty()
        }
        val type = when {
            localIsSeries -> "serie"
            favorite.tipo.contains("séri", true) || favorite.tipo.contains("serie", true) -> "serie"
            else -> "filme"
        }
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_OPEN_FAVORITE_TITLE, favorite.titulo)
            putExtra(MainActivity.EXTRA_OPEN_FAVORITE_CATEGORY, category)
            putExtra(MainActivity.EXTRA_OPEN_FAVORITE_SLUG, slug)
            putExtra(MainActivity.EXTRA_OPEN_FAVORITE_TYPE, type)
        })
        finish()
    }

    private fun removeFavorite(favorite: AuthSession.Favorite) {
        TedflixLoading.show(loadingPanel, true)
        Thread {
            val result = AuthSession.toggleFavorite(favorite.filmeId, favorite.titulo, favorite.thumb, favorite.tipo.ifBlank { "movie" })
            runOnUiThread {
                TedflixLoading.show(loadingPanel, false)
                if (!result.ok) toast(result.message.ifBlank { "Não foi possível remover o favorito." }) else loadFavorites()
            }
        }.start()
    }

    private fun formatMs(value: Long): String {
        val total = (value / 1000L).coerceAtLeast(0L)
        return "%02d:%02d:%02d".format(total / 3600L, (total % 3600L) / 60L, total % 60L)
    }

    private fun message(text: String) = TextView(this).apply { this.text = text; textSize = 14f; setTextColor(Color.LTGRAY); gravity = Gravity.CENTER; setPadding(dp(18), dp(40), dp(18), dp(40)); background = rounded(Color.rgb(18, 18, 20), dp(14), Color.argb(55, 255, 255, 255), dp(1)) }
    private fun button(text: String, size: Int, action: () -> Unit) = Button(this).apply { this.text = text; textSize = size.toFloat(); isAllCaps = false; setTextColor(Color.WHITE); background = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT); setOnClickListener { action() } }
    private fun rounded(fill: Int, radius: Int, stroke: Int, width: Int) = android.graphics.drawable.GradientDrawable().apply { setColor(fill); cornerRadius = radius.toFloat(); setStroke(width, stroke) }
    private fun toast(message: String) = android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
