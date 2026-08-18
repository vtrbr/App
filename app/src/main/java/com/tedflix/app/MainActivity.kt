package com.tedflix.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
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
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.util.UnstableApi
import com.tedflix.app.auth.AccountActivity
import com.tedflix.app.auth.AuthActivity
import com.tedflix.app.auth.AuthSession

@UnstableApi
class MainActivity : Activity() {
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

        val previousCrash = TedflixApplication.consumeLastCrash(this)
        if (!previousCrash.isNullOrBlank()) {
            showCrashRecovery(previousCrash)
            return
        }
        setupWebView()
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
            loadUrl("file:///android_asset/tedflix/index.html")
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
        fun openPlayer(categoria: String?, slug: String?, titulo: String?, filaJson: String?) {
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
        fun getContinueWatching(): String {
            return try {
                ContinueWatchingStore.toJson(activity)
            } catch (error: Throwable) {
                Log.w("TedflixMain", "Falha ao ler progresso local", error)
                "[]"
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
                try {
                    activity.startActivity(Intent(activity, AccountActivity::class.java))
                } catch (error: Throwable) {
                    Log.e("TedflixMain", "Falha ao abrir a conta", error)
                    Toast.makeText(activity, "Não foi possível abrir a conta.", Toast.LENGTH_LONG).show()
                }
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
