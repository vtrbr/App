package com.tedflix.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.util.UnstableApi
import com.tedflix.app.auth.AuthActivity
import com.tedflix.app.auth.AuthSession
import com.tedflix.app.auth.FavoritesActivity
import com.tedflix.app.auth.NotificationActivity
import com.tedflix.app.NotificationHelper
import com.tedflix.app.requestNotificationPermissionIfNeeded

@UnstableApi
class MainActivity : Activity() {
    companion object {
        const val EXTRA_OPEN_FAVORITE_TITLE = "open_favorite_title"
        const val EXTRA_OPEN_FAVORITE_CATEGORY = "open_favorite_category"
        const val EXTRA_OPEN_FAVORITE_SLUG = "open_favorite_slug"
        const val EXTRA_OPEN_FAVORITE_TYPE = "open_favorite_type"
    }

    private lateinit var webView: WebView
    private var playerWasOpened = false
    @Volatile private var authRedirectInProgress = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(5, 6, 9)
        window.navigationBarColor = Color.rgb(5, 6, 9)
        AuthSession.init(applicationContext)
        if (!AuthSession.hasToken()) {
            openLogin()
            return
        }

        NotificationHelper.createChannel(this)
        requestNotificationPermissionIfNeeded(this)
        publishUnreadNotifications()
        val previousCrash = TedflixApplication.consumeLastCrash(this)
        if (!previousCrash.isNullOrBlank()) {
            showCrashRecovery(previousCrash)
            return
        }
        showProfileChooser()
    }

    private fun publishUnreadNotifications() {
        Thread {
            val result = AuthSession.notifications()
            if (!result.ok) return@Thread
            result.value.orEmpty().filterNot { it.read }.forEachIndexed { index, item ->
                runOnUiThread { NotificationHelper.show(this, 5000 + index, item.title, item.body) }
            }
        }.start()
    }

    private fun showProfileChooser() {
        val name = AuthSession.cachedUser()?.username?.ifBlank { "Meu perfil" } ?: "Meu perfil"
        val root = android.widget.FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(5, 6, 9))
        }
        val banner = ImageView(this).apply {
            setImageResource(com.tedflix.app.R.drawable.tedflix_auth_banner)
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0.82f
        }
        root.addView(banner, android.widget.FrameLayout.LayoutParams(-1, -1))
        val shade = View(this).apply {
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.argb(120, 5, 6, 9), Color.rgb(5, 6, 9)),
            )
        }
        root.addView(shade, android.widget.FrameLayout.LayoutParams(-1, -1))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(40), dp(24), dp(34))
        }
        val logo = ImageView(this).apply {
            setImageResource(com.tedflix.app.R.drawable.tedflix_auth_logo)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            contentDescription = "Tedflix"
        }
        content.addView(logo, LinearLayout.LayoutParams(dp(150), dp(54)).apply { bottomMargin = dp(42) })
        content.addView(TextView(this).apply {
            text = "Quem está assistindo?"
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(28) })

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(12), dp(12), dp(12), dp(16))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.argb(210, 18, 19, 24))
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), Color.argb(90, 255, 255, 255))
            }
            elevation = dp(8).toFloat()
        }
        val avatar = ImageView(this).apply {
            setImageResource(com.tedflix.app.R.drawable.tedflix_auth_banner)
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = name
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setStroke(dp(2), Color.rgb(229, 9, 20))
            }
            clipToOutline = true
            setOnClickListener {
                content.visibility = View.GONE
                shade.alpha = 0.98f
                val loader = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    addView(ProgressBar(this@MainActivity).apply {
                        indeterminateTintList = android.content.res.ColorStateList.valueOf(Color.rgb(229, 9, 20))
                    }, LinearLayout.LayoutParams(dp(48), dp(48)))
                    addView(TextView(this@MainActivity).apply {
                        text = "Carregando seu perfil..."
                        textSize = 14f
                        setTextColor(Color.WHITE)
                        gravity = Gravity.CENTER
                    }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
                }
                root.addView(loader, android.widget.FrameLayout.LayoutParams(-1, -1))
                root.postDelayed({ setupWebView() }, 320L)
            }
        }
        card.addView(avatar, LinearLayout.LayoutParams(dp(142), dp(174)))
        card.addView(TextView(this).apply {
            text = name
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        content.addView(card, LinearLayout.LayoutParams(dp(190), -2))
        content.addView(TextView(this).apply {
            text = "Toque no seu perfil para entrar"
            textSize = 13f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(22) })
        root.addView(content, android.widget.FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView = WebView(this).apply {
            setBackgroundColor(Color.rgb(5, 6, 9))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            @Suppress("DEPRECATION")
            settings.allowUniversalAccessFromFileURLs = true
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    return request.url.toString().startsWith("file:///android_asset/").not()
                }

                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): android.webkit.WebResourceResponse? {
                    val response = AuthSession.proxyMovieRequest(request)
                    if (response != null && response.statusCode in 401..403) {
                        handleSessionExpired()
                    }
                    return if (response != null) AuthSession.toWebResourceResponse(response)
                    else super.shouldInterceptRequest(view, request)
                }
            }
            addJavascriptInterface(AndroidPlayerBridge(this@MainActivity), "AndroidPlayer")
            val favoriteTitle = intent.getStringExtra(EXTRA_OPEN_FAVORITE_TITLE).orEmpty()
            val favoriteCategory = intent.getStringExtra(EXTRA_OPEN_FAVORITE_CATEGORY).orEmpty()
            val favoriteSlug = intent.getStringExtra(EXTRA_OPEN_FAVORITE_SLUG).orEmpty()
            val favoriteType = intent.getStringExtra(EXTRA_OPEN_FAVORITE_TYPE).orEmpty().lowercase().let { if (it.contains("séri") || it.contains("serie")) "serie" else "filme" }
            val route = when {
                favoriteCategory.isNotBlank() && favoriteSlug.isNotBlank() -> "#/titulo/$favoriteType/${Uri.encode(favoriteCategory)}/${Uri.encode(favoriteSlug)}"
                favoriteTitle.isNotBlank() -> "#/favorito?titulo=${Uri.encode(favoriteTitle)}"
                else -> ""
            }
            loadUrl("file:///android_asset/tedflix/index.html$route")
        }
        setContentView(webView)
    }

    private fun handleSessionExpired() {
        if (authRedirectInProgress) return
        authRedirectInProgress = true
        runOnUiThread {
            AuthSession.clear()
            Toast.makeText(this, "Sua sessão expirou. Entre novamente.", Toast.LENGTH_LONG).show()
            openLogin()
        }
    }

    private fun openLogin() {
        startActivity(Intent(this, AuthActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        finish()
    }

    private fun showCrashRecovery(report: String) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
            setBackgroundColor(Color.BLACK)
        }
        val title = TextView(this).apply {
            text = "Tedflix capturou um erro"
            textSize = 22f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        root.addView(title, LinearLayout.LayoutParams(-1, -2))
        val explanation = TextView(this).apply {
            text = "O erro abaixo foi salvo antes do encerramento. Envie esta tela para identificar a causa exata."
            textSize = 14f
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(12), 0, dp(12))
        }
        root.addView(explanation, LinearLayout.LayoutParams(-1, -2))
        val details = TextView(this).apply {
            text = report
            textSize = 12f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(0, dp(8), 0, dp(16))
        }
        root.addView(ScrollView(this).apply {
            isFillViewport = true
            addView(details)
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        val continueButton = Button(this).apply {
            text = "Continuar para o Tedflix"
            setAllCaps(false)
            setOnClickListener { setupWebView() }
        }
        root.addView(continueButton, LinearLayout.LayoutParams(-1, dp(54)))
        setContentView(root)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized && playerWasOpened) {
            playerWasOpened = false
            webView.postDelayed({
                webView.evaluateJavascript("if (location.hash.indexOf('#/assistir/') === 0) history.back();", null)
            }, 120)
        }
    }

    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.removeJavascriptInterface("AndroidPlayer")
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }

    private class AndroidPlayerBridge(private val activity: MainActivity) {
        @JavascriptInterface
        fun openPlayer(
            categoria: String?,
            slug: String?,
            titulo: String?,
            filaJson: String?,
            filmeId: String? = null,
            thumb: String? = null,
            tipo: String? = null,
            serieCategoria: String? = null,
            serieSlug: String? = null,
        ) {
            val categoriaValue = categoria.orEmpty().trim()
            val slugValue = slug.orEmpty().trim()
            val tituloValue = titulo.orEmpty().trim()
            activity.runOnUiThread {
                if (!AuthSession.hasToken()) {
                    activity.startActivity(Intent(activity, AuthActivity::class.java))
                    return@runOnUiThread
                }
                if (categoriaValue.isBlank() || slugValue.isBlank()) {
                    Toast.makeText(activity, "Título inválido.", Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                try {
                    val intent = Intent(activity, PlayerActivity::class.java).apply {
                        putExtra(PlayerActivity.EXTRA_CATEGORIA, categoriaValue)
                        putExtra(PlayerActivity.EXTRA_SLUG, slugValue)
                        putExtra(PlayerActivity.EXTRA_TITULO, tituloValue)
                        putExtra(PlayerActivity.EXTRA_NEXT_EPISODES, filaJson.orEmpty())
                        putExtra(PlayerActivity.EXTRA_FILME_ID, filmeId.orEmpty().trim().ifBlank { slugValue })
                        putExtra(PlayerActivity.EXTRA_THUMB, thumb.orEmpty().trim())
                        putExtra(PlayerActivity.EXTRA_TIPO, tipo.orEmpty().trim())
                        putExtra(PlayerActivity.EXTRA_SERIE_CATEGORIA, serieCategoria.orEmpty().trim())
                        putExtra(PlayerActivity.EXTRA_SERIE_SLUG, serieSlug.orEmpty().trim())
                    }
                    activity.playerWasOpened = true
                    activity.startActivity(intent)
                } catch (error: Throwable) {
                    Log.e("TedflixMain", "Falha ao abrir o PlayerActivity", error)
                    activity.playerWasOpened = false
                    Toast.makeText(activity, "Não foi possível abrir o player.", Toast.LENGTH_LONG).show()
                }
            }
        }

        @JavascriptInterface
        fun getProfileName(): String = AuthSession.cachedUser()?.username?.ifBlank { "Meu perfil" } ?: "Meu perfil"

        @JavascriptInterface
        fun openNotifications() {
            activity.runOnUiThread {
                try {
                    activity.startActivity(Intent(activity, NotificationActivity::class.java))
                } catch (error: Throwable) {
                    Log.e("TedflixMain", "Falha ao abrir notificações", error)
                    Toast.makeText(activity, "Não foi possível abrir as notificações.", Toast.LENGTH_LONG).show()
                }
            }
        }

        @JavascriptInterface
        fun openFavorites() {
            activity.runOnUiThread {
                if (!AuthSession.hasToken()) {
                    activity.startActivity(Intent(activity, AuthActivity::class.java))
                    return@runOnUiThread
                }
                try {
                    activity.startActivity(Intent(activity, FavoritesActivity::class.java))
                } catch (error: Throwable) {
                    Log.e("TedflixMain", "Falha ao abrir favoritos", error)
                    Toast.makeText(activity, "Não foi possível abrir os favoritos.", Toast.LENGTH_LONG).show()
                }
            }
        }

        @JavascriptInterface
        fun getContinueWatching(): String {
            return try {
                val local = org.json.JSONArray(ContinueWatchingStore.toJson(activity))
                if (!AuthSession.hasToken()) return local.toString()
                val remote = AuthSession.continueWatching().value.orEmpty()
                for (i in 0 until local.length()) {
                    val item = local.optJSONObject(i) ?: continue
                    val match = remote.firstOrNull { it.filmeId == item.optString("filmeId").ifBlank { item.optString("slug") } || it.filmeId == item.optString("slug") }
                    if (match != null) {
                        if (match.thumb.isNotBlank()) item.put("thumb", match.thumb)
                        if (match.tempo.isNotBlank()) item.put("tempo", match.tempo)
                        item.put("remote", true)
                    }
                }
                local.toString()
            } catch (error: Throwable) {
                Log.w("TedflixMain", "Falha ao combinar histórico remoto", error)
                try { ContinueWatchingStore.toJson(activity) } catch (_: Throwable) { "[]" }
            }
        }

        @JavascriptInterface
        fun getFavorites(): String {
            return try {
                val result = AuthSession.listFavorites()
                if (!result.ok) "{\"success\":false,\"error\":${org.json.JSONObject.quote(result.message)}}"
                else org.json.JSONObject().put("success", true).put("favoritos", org.json.JSONArray().apply {
                    result.value.orEmpty().forEach { item ->
                        put(org.json.JSONObject().apply {
                            put("filmeId", item.filmeId)
                            put("titulo", item.titulo)
                            put("thumb", item.thumb)
                            put("adicionadoEm", item.adicionadoEm)
                        })
                    }
                }).toString()
            } catch (error: Throwable) {
                Log.w("TedflixMain", "Falha ao ler favoritos", error)
                "{\"success\":false,\"error\":\"Não foi possível carregar favoritos.\"}"
            }
        }

        @JavascriptInterface
        fun toggleFavorite(filmeId: String?, titulo: String?, thumb: String?): String {
            return try {
                val result = AuthSession.toggleFavorite(filmeId.orEmpty(), titulo.orEmpty(), thumb.orEmpty())
                org.json.JSONObject().apply {
                    put("success", result.ok)
                    if (result.value != null) put("favorito", result.value)
                    if (result.message.isNotBlank()) put("error", result.message)
                }.toString()
            } catch (error: Throwable) {
                Log.w("TedflixMain", "Falha ao alternar favorito", error)
                "{\"success\":false,\"favorito\":false,\"error\":\"Não foi possível atualizar o favorito.\"}"
            }
        }

        @JavascriptInterface
        fun openSources() {
            activity.runOnUiThread {
                try {
                    activity.startActivity(Intent(activity, com.tedflix.app.sources.SourceSettingsActivity::class.java))
                } catch (error: Throwable) {
                    Log.e("TedflixMain", "Falha ao abrir as fontes", error)
                    Toast.makeText(activity, "Não foi possível abrir as fontes.", Toast.LENGTH_LONG).show()
                }
            }
        }

        @JavascriptInterface
        fun openAccount() {
            activity.runOnUiThread {
                if (!activity::webView.isInitialized) return@runOnUiThread
                activity.webView.evaluateJavascript("location.hash = '#/config';", null)
            }
        }

        @JavascriptInterface
        fun getAccountStatus(): String {
            return try {
                val status = AuthSession.status()
                if (!status.ok) "{\"success\":false,\"error\":${org.json.JSONObject.quote(status.message)}}"
                else org.json.JSONObject().put("success", true).put("status", status.value ?: org.json.JSONObject()).toString()
            } catch (error: Throwable) {
                "{\"success\":false,\"error\":\"Não foi possível carregar o status da conta.\"}"
            }
        }

        @JavascriptInterface
        fun updateProfileName(username: String?): String {
            return try {
                val result = AuthSession.updateUsername(username.orEmpty())
                org.json.JSONObject().put("success", result.ok).apply {
                    if (result.message.isNotBlank()) put("error", result.message)
                }.toString()
            } catch (_: Throwable) {
                "{\"success\":false,\"error\":\"Não foi possível salvar o nome.\"}"
            }
        }

        @JavascriptInterface
        fun changeProfilePassword(currentPassword: String?, newPassword: String?): String {
            return try {
                val result = AuthSession.changePassword(currentPassword.orEmpty(), newPassword.orEmpty())
                org.json.JSONObject().put("success", result.ok).apply {
                    if (result.message.isNotBlank()) put("error", result.message)
                }.toString()
            } catch (_: Throwable) {
                "{\"success\":false,\"error\":\"Não foi possível alterar a senha.\"}"
            }
        }

        @JavascriptInterface
        fun logoutFromSettings(): String {
            return try {
                val result = AuthSession.logout()
                if (result.ok) activity.runOnUiThread { activity.openLogin() }
                org.json.JSONObject().put("success", result.ok).apply {
                    if (result.message.isNotBlank()) put("error", result.message)
                }.toString()
            } catch (_: Throwable) {
                "{\"success\":false,\"error\":\"Não foi possível sair da conta.\"}"
            }
        }

        @JavascriptInterface
        fun setBuffer(buffer: String?) {
            activity.getSharedPreferences(PlayerActivity.PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(PlayerActivity.BUFFER_KEY, buffer.orEmpty())
                .apply()
        }
    }
}
