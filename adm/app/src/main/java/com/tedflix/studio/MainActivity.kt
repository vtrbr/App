package com.tedflix.studio

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.View
import com.tedflix.studio.auth.StudioSession
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var session: StudioSession

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = StudioSession(this)
        webView = WebView(this)
        configureWebView()
        setContentView(webView)
        webView.loadUrl("file:///android_asset/studio/index.html")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            builtInZoomControls = false
            displayZoomControls = false
            setSupportZoom(false)
            textZoom = 100
            useWideViewPort = false
            loadWithOverviewMode = false
            mediaPlaybackRequiresUserGesture = true
        }
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
        webView.isScrollbarFadingEnabled = true
        webView.overScrollMode = View.OVER_SCROLL_NEVER
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return request.url.scheme != "file"
            }
        }
        webView.addJavascriptInterface(StudioBridge(), "AndroidStudio")
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    inner class StudioBridge {
        @JavascriptInterface
        fun sessionState(): String {
            val admin = session.admin()
            return JSONObject().apply {
                put("loggedIn", session.isLoggedIn())
                put("admin", admin ?: JSONObject.NULL)
            }.toString()
        }

        @JavascriptInterface
        fun login(email: String, password: String): String {
            val payload = JSONObject().apply {
                put("email", email.trim())
                put("password", password)
            }
            val response = requestInternal("POST", "/admin/login", payload.toString(), includeAuth = false)
            if (response.optBoolean("ok") && response.optJSONObject("body")?.optBoolean("success") == true) {
                val body = response.optJSONObject("body") ?: JSONObject()
                val token = body.optString("token")
                val admin = body.optJSONObject("admin") ?: JSONObject()
                if (token.isNotBlank()) session.save(token, admin)
                body.remove("token")
                response.put("body", body)
            }
            return response.toString()
        }

        @JavascriptInterface
        fun logout() {
            session.clear()
        }

        @JavascriptInterface
        fun apiRequest(method: String, path: String, body: String): String {
            val normalized = if (path.startsWith("/")) path else "/$path"
            if (!normalized.startsWith("/admin/")) {
                return errorResponse(403, "Rota não permitida no Studio").toString()
            }
            return requestInternal(method.uppercase(), normalized, body, includeAuth = true).toString()
        }
    }

    private fun requestInternal(method: String, path: String, body: String?, includeAuth: Boolean): JSONObject {
        return try {
            val connection = (URL(AUTH_BASE + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 12_000
                readTimeout = 20_000
                useCaches = false
                doInput = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("User-Agent", "TedflixStudio-Android/1.0")
                if (includeAuth) session.token()?.let { setRequestProperty("Authorization", "Bearer $it") }
                if (method in setOf("POST", "PATCH", "PUT", "DELETE")) doOutput = true
            }
            if (method in setOf("POST", "PATCH", "PUT", "DELETE") && body != null) {
                OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { it.write(body) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..399) connection.inputStream else connection.errorStream
            val responseText = stream?.let { BufferedReader(InputStreamReader(BufferedInputStream(it), StandardCharsets.UTF_8)).use { reader -> reader.readText() } } ?: ""
            val parsed = runCatching { JSONObject(responseText) }.getOrElse { JSONObject().put("raw", responseText) }
            JSONObject().apply {
                put("ok", status in 200..299)
                put("status", status)
                put("body", parsed)
            }
        } catch (error: Exception) {
            errorResponse(0, error.message ?: "Falha de conexão")
        }
    }

    private fun errorResponse(status: Int, message: String): JSONObject = JSONObject().apply {
        put("ok", false)
        put("status", status)
        put("body", JSONObject().put("error", message))
    }

    companion object {
        private const val AUTH_BASE = "https://authted.onrender.com"
    }
}
