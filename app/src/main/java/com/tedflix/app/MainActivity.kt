package com.tedflix.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.media3.common.util.UnstableApi

@UnstableApi
class MainActivity : Activity() {
    private lateinit var webView: WebView
    private var playerWasOpened = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(5, 6, 9)
        window.navigationBarColor = Color.rgb(5, 6, 9)

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
            }
            addJavascriptInterface(AndroidPlayerBridge(this@MainActivity), "AndroidPlayer")
            loadUrl("file:///android_asset/tedflix/index.html")
        }

        setContentView(webView)
    }

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
        fun openPlayer(categoria: String, slug: String, titulo: String) {
            activity.runOnUiThread {
                activity.playerWasOpened = true
                val intent = Intent(activity, PlayerActivity::class.java).apply {
                    putExtra(PlayerActivity.EXTRA_CATEGORIA, categoria)
                    putExtra(PlayerActivity.EXTRA_SLUG, slug)
                    putExtra(PlayerActivity.EXTRA_TITULO, titulo)
                }
                activity.startActivity(intent)
            }
        }

        @JavascriptInterface
        fun setBuffer(buffer: String) {
            activity.getSharedPreferences(PlayerActivity.PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(PlayerActivity.BUFFER_KEY, buffer)
                .apply()
        }
    }
}
