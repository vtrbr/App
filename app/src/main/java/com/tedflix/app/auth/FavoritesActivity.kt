package com.tedflix.app.auth

import android.app.Activity
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
import java.net.URL

@UnstableApi
class FavoritesActivity : Activity() {
    private lateinit var list: LinearLayout
    private lateinit var progress: ProgressBar

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
        progress = ProgressBar(this).apply { visibility = View.GONE; indeterminateTintList = android.content.res.ColorStateList.valueOf(Color.rgb(229, 9, 20)) }
        root.addView(progress, FrameLayout.LayoutParams(dp(42), dp(42), Gravity.CENTER))
        return root
    }

    private fun loadFavorites() {
        if (!::list.isInitialized) return
        progress.visibility = View.VISIBLE
        list.removeAllViews()
        Thread {
            val result = AuthSession.listFavorites()
            runOnUiThread {
                progress.visibility = View.GONE
                if (!result.ok) { list.addView(message(result.message.ifBlank { "Não foi possível carregar os favoritos." })); return@runOnUiThread }
                val favorites = result.value.orEmpty()
                if (favorites.isEmpty()) list.addView(message("Você ainda não adicionou nenhum favorito."))
                else favorites.forEach { addFavorite(it) }
            }
        }.start()
    }

    private fun addFavorite(favorite: AuthSession.Favorite) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = rounded(Color.rgb(18, 18, 20), dp(14), Color.argb(55, 255, 255, 255), dp(1))
        }
        val image = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setBackgroundColor(Color.rgb(35, 35, 38)) }
        row.addView(image, LinearLayout.LayoutParams(dp(74), dp(104)))
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(8), 0) }
        info.addView(TextView(this).apply { text = favorite.titulo.ifBlank { "Título salvo" }; textSize = 16f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD })
        info.addView(TextView(this).apply { text = favorite.filmeId; textSize = 11f; setTextColor(Color.GRAY); setPadding(0, dp(6), 0, dp(10)) })
        info.addView(button("Remover", 12) { removeFavorite(favorite) }.apply { background = rounded(Color.rgb(95, 15, 22), dp(8), Color.TRANSPARENT, 0) }, LinearLayout.LayoutParams(dp(92), dp(40)))
        row.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        if (favorite.thumb.isNotBlank()) Thread {
            try {
                val bitmap = URL(favorite.thumb).openStream().use { BitmapFactory.decodeStream(it) }
                runOnUiThread { if (bitmap != null) image.setImageBitmap(bitmap) }
            } catch (_: Throwable) {}
        }.start()
    }

    private fun removeFavorite(favorite: AuthSession.Favorite) {
        progress.visibility = View.VISIBLE
        Thread {
            val result = AuthSession.toggleFavorite(favorite.filmeId, favorite.titulo, favorite.thumb)
            runOnUiThread {
                progress.visibility = View.GONE
                if (!result.ok) toast(result.message.ifBlank { "Não foi possível remover o favorito." }) else loadFavorites()
            }
        }.start()
    }

    private fun message(text: String) = TextView(this).apply { this.text = text; textSize = 14f; setTextColor(Color.LTGRAY); gravity = Gravity.CENTER; setPadding(dp(18), dp(40), dp(18), dp(40)); background = rounded(Color.rgb(18, 18, 20), dp(14), Color.argb(55, 255, 255, 255), dp(1)) }
    private fun button(text: String, size: Int, action: () -> Unit) = Button(this).apply { this.text = text; textSize = size.toFloat(); isAllCaps = false; setTextColor(Color.WHITE); background = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT); setOnClickListener { action() } }
    private fun rounded(fill: Int, radius: Int, stroke: Int, width: Int) = android.graphics.drawable.GradientDrawable().apply { setColor(fill); cornerRadius = radius.toFloat(); setStroke(width, stroke) }
    private fun toast(message: String) = android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
