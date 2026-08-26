package com.tedflix.app

import android.app.Activity
import android.app.AlertDialog
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

@UnstableApi
class LivePlayerActivity : Activity() {
    companion object {
        const val EXTRA_CHANNEL_ID = "live_channel_id"
        const val EXTRA_TITLE = "live_title"
        const val EXTRA_LOGO = "live_logo"
        const val EXTRA_SERVERS = "live_servers"
        const val EXTRA_PROGRAM = "live_program"
        const val EXTRA_CATEGORY = "live_category"
        private const val TAG = "TedflixLivePlayer"
        private const val MOBILE_USER_AGENT = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36"
        private const val LIVE_BEHIND_THRESHOLD_MS = 15_000L
        private const val STREAM_TIMEOUT_MS = 15_000L
        private const val RESOLVE_CONNECT_TIMEOUT_MS = 6_000
        private const val RESOLVE_READ_TIMEOUT_MS = 9_000
        private const val MAX_GLOBO_AUTO_FAILOVERS = 4
        private const val MAX_EMBED_HTML_CHARS = 1_200_000
    }

    private data class Server(
        val provider: String,
        val quality: String,
        val url: String,
    )

    private lateinit var videoContainer: FrameLayout
    private lateinit var chromeTop: View
    private lateinit var chromeBottom: View
    private lateinit var statusView: TextView
    private lateinit var titleView: TextView
    private lateinit var logoView: ImageView
    private lateinit var serverButton: Button
    private lateinit var liveButton: Button
    private lateinit var retryButton: Button
    private var loading: ProgressBar? = null
    private var nativePlayer: ExoPlayer? = null
    private var nativePlayerView: PlayerView? = null
    private var resolveThread: Thread? = null
    private var chromeVisible = true
    private var streamReady = false
    private var loadGeneration = 0L
    private val attemptedServerIndices = mutableSetOf<Int>()
    private var stoppedForBackground = false
    private val chromeHandler = Handler(Looper.getMainLooper())
    private val autoHideChrome = Runnable { setChromeVisible(false) }
    private val liveCheck = object : Runnable {
        override fun run() {
            nativePlayer?.let { updateLiveButton(it) }
            if (nativePlayer != null && !isFinishing && !isDestroyedCompat()) {
                chromeHandler.postDelayed(this, 2_000L)
            }
        }
    }
    private var streamTimeout: Runnable? = null
    private val servers = mutableListOf<Server>()
    private var selectedIndex = 0

    private data class NativeStream(
        val url: String,
        val referer: String,
        val origin: String,
    )

    override fun onStart() {
        super.onStart()
        if (stoppedForBackground && !isFinishing && !isDestroyedCompat()) {
            stoppedForBackground = false
            loadSelectedServer()
        }
    }

    override fun onPause() {
        pauseAllPlayback()
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        if (!isFinishing) {
            stoppedForBackground = true
            releaseNativePlayer()
        }
    }

    override fun onDestroy() {
        chromeHandler.removeCallbacksAndMessages(null)
        pauseAllPlayback()
        releaseNativePlayer()
        super.onDestroy()
    }

    private fun pauseAllPlayback() {
        nativePlayer?.playWhenReady = false
        nativePlayer?.pause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        parseIntent()
        if (servers.isEmpty()) return
        buildUi()
        enterImmersiveMode()
        loadSelectedServer()
    }

    private fun parseIntent() {
        val raw = intent.getStringExtra(EXTRA_SERVERS).orEmpty()
        try {
            val array = JSONArray(raw)
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val url = item.optString("embed_url").trim()
                val parsed = runCatching { Uri.parse(url) }.getOrNull()
                val scheme = parsed?.scheme.orEmpty().lowercase()
                if (url.isNotBlank() && (scheme == "https" || scheme == "http")) {
                    servers += Server(
                        provider = item.optString("provider").ifBlank { "Servidor ${index + 1}" },
                        quality = item.optString("quality").ifBlank { "Automático" },
                        url = url,
                    )
                }
            }
        } catch (error: Throwable) {
            android.util.Log.w(TAG, "Lista de servidores inválida", error)
        }
        if (servers.isEmpty()) {
            Toast.makeText(this, "Nenhum servidor de transmissão disponível.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
    }

    private fun buildUi() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        videoContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }
        // A reprodução usa somente o ExoPlayer nativo; nenhum WebView de provedor
        // é criado, exibido ou deixado em segundo plano.
        root.addView(videoContainer, FrameLayout.LayoutParams(-1, -1))
        retryButton = controlButton("Tentar novamente", 13).apply {
            visibility = View.GONE
            setTextColor(Color.WHITE)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.rgb(219, 42, 56))
                cornerRadius = dp(14).toFloat()
            }
            setOnClickListener { loadSelectedServer() }
        }
        root.addView(retryButton, FrameLayout.LayoutParams(dp(210), dp(50), Gravity.CENTER))

        val topOverlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), 0)
            setBackgroundColor(Color.TRANSPARENT)
        }
        val top = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.argb(170, 0, 0, 0))
                cornerRadius = dp(16).toFloat()
            }
        }
        logoView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = "Logo do canal"
        }
        top.addView(logoView, LinearLayout.LayoutParams(dp(54), dp(50)).apply { rightMargin = dp(2) })
        loadChannelLogo()
        val back = controlButton("‹", 34).apply {
            contentDescription = "Voltar"
            setOnClickListener { finish() }
        }
        top.addView(back, LinearLayout.LayoutParams(dp(50), dp(50)))
        titleView = TextView(this).apply {
            text = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "Canal ao vivo" }
            textSize = 19f
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = false
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(12), 0, dp(12), 0)
        }
        top.addView(titleView, LinearLayout.LayoutParams(0, dp(50), 1f))
        serverButton = controlButton(serverLabel(), 12).apply {
            contentDescription = "Selecionar servidor"
            setTextColor(Color.WHITE)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.argb(180, 35, 35, 42))
                cornerRadius = dp(12).toFloat()
            }
            setOnClickListener { chooseServer() }
        }
        top.addView(serverButton, LinearLayout.LayoutParams(dp(174), dp(50)).apply { leftMargin = dp(6) })
        val reload = controlButton("↻", 28).apply {
            contentDescription = "Recarregar transmissão"
            setOnClickListener { loadSelectedServer() }
        }
        top.addView(reload, LinearLayout.LayoutParams(dp(50), dp(50)))
        topOverlay.addView(top, LinearLayout.LayoutParams(-1, dp(66)))
        chromeTop = topOverlay
        root.addView(topOverlay, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        val bottom = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(14), 0)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.argb(170, 0, 0, 0))
                cornerRadius = dp(14).toFloat()
            }
        }
        liveButton = controlButton("AO VIVO", 12).apply {
            visibility = View.GONE
            setTextColor(Color.WHITE)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.rgb(219, 42, 56))
                cornerRadius = dp(12).toFloat()
            }
            setOnClickListener { goToLive() }
        }
        bottom.addView(liveButton, LinearLayout.LayoutParams(dp(122), dp(44)).apply {
            rightMargin = dp(12)
        })
        statusView = TextView(this).apply {
            text = "Preparando transmissão autorizada..."
            textSize = 13f
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(Color.WHITE)
            setShadowLayer(8f, 0f, 2f, Color.BLACK)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        bottom.addView(statusView, LinearLayout.LayoutParams(0, dp(48), 1f))
        val program = intent.getStringExtra(EXTRA_PROGRAM).orEmpty()
        if (program.isNotBlank()) {
            bottom.addView(TextView(this).apply {
                text = "Agora: $program"
                textSize = 12f
                gravity = Gravity.CENTER_VERTICAL
                setTextColor(Color.LTGRAY)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setShadowLayer(8f, 0f, 2f, Color.BLACK)
            }, LinearLayout.LayoutParams(dp(430), dp(48)))
        }
        chromeBottom = bottom
        root.addView(bottom, FrameLayout.LayoutParams(-1, dp(48), Gravity.BOTTOM).apply {
            leftMargin = dp(14)
            rightMargin = dp(14)
            bottomMargin = dp(14)
        })

        retryButton.visibility = View.GONE
        liveButton.visibility = View.GONE
        setChromeVisible(true)
        chromeHandler.removeCallbacks(autoHideChrome)
        chromeHandler.postDelayed(autoHideChrome, 5_000L)
        loading = ProgressBar(this).apply {
            indeterminateTintList = android.content.res.ColorStateList.valueOf(Color.rgb(229, 28, 42))
        }
        root.addView(loading, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER))
        setContentView(root)
    }

    private fun loadSelectedServer() {
        loadSelectedServer(resetAttempts = true)
    }

    private fun loadSelectedServer(resetAttempts: Boolean) {
        if (servers.isEmpty()) return
        if (resetAttempts) attemptedServerIndices.clear()
        attemptedServerIndices += selectedIndex
        val generation = ++loadGeneration
        resolveThread?.interrupt()
        releaseNativePlayer()
        retryButton.visibility = View.GONE
        liveButton.visibility = View.GONE
        val server = servers[selectedIndex]
        serverButton.text = serverLabel()
        loading?.visibility = View.VISIBLE
        showStatus("Procurando vídeo ${serverLabel()}...")
        startStreamTimeout(generation)
        resolveThread = Thread {
            val nativeStream = resolveNativeStream(server.url)
            runOnUiThread {
                if (generation != loadGeneration || isFinishing || isDestroyedCompat()) return@runOnUiThread
                if (nativeStream != null && playNativeStream(nativeStream)) {
                    showStatus("Carregando vídeo ${serverLabel()}...")
                } else {
                    tryNextServerOrShowError("Nenhum servidor respondeu com vídeo ao vivo.")
                }
            }
        }.apply {
            name = "TedflixLiveStreamResolver"
            start()
        }
    }

    private fun startStreamTimeout(generation: Long) {
        streamTimeout?.let(chromeHandler::removeCallbacks)
        val timeout = Runnable {
            if (generation != loadGeneration || streamReady || isFinishing || isDestroyedCompat()) return@Runnable
            tryNextServerOrShowError("Nenhum servidor respondeu dentro do tempo esperado.")
        }
        streamTimeout = timeout
        chromeHandler.postDelayed(timeout, STREAM_TIMEOUT_MS)
    }

    private fun tryNextServerOrShowError(message: String) {
        if (isFinishing || isDestroyedCompat()) return
        val isGlobo = isGloboFamily()
        val withinAutomaticBudget = !isGlobo || attemptedServerIndices.size < MAX_GLOBO_AUTO_FAILOVERS
        val next = if (withinAutomaticBudget) {
            servers.indices.firstOrNull { it !in attemptedServerIndices }
        } else {
            null
        }
        if (next != null) {
            selectedIndex = next
            serverButton.text = serverLabel()
            showStatus("Tentando ${serverLabel()}...")
            loadSelectedServer(resetAttempts = false)
        } else {
            val finalMessage = if (isGlobo) {
                "A Globo não forneceu um HLS autorizado nesta região. Escolha outra região ou tente novamente."
            } else {
                message
            }
            showStreamUnavailable(finalMessage)
        }
    }

    private fun isGloboFamily(): Boolean = servers.any { server ->
        server.provider.contains("globo", ignoreCase = true) ||
            server.url.contains("globo", ignoreCase = true)
    }

    private fun showStreamUnavailable(message: String) {
        // Invalida qualquer resolver que ainda esteja retornando em segundo plano.
        loadGeneration++
        resolveThread?.interrupt()
        releaseNativePlayer()
        loading?.visibility = View.GONE
        retryButton.visibility = View.VISIBLE
        liveButton.visibility = View.GONE
        setChromeVisible(true)
        showStatus(message)
    }

    private fun releaseNativePlayer() {
        streamTimeout?.let(chromeHandler::removeCallbacks)
        streamTimeout = null
        chromeHandler.removeCallbacks(liveCheck)
        nativePlayer?.playWhenReady = false
        nativePlayer?.stop()
        nativePlayer?.release()
        nativePlayer = null
        streamReady = false
        nativePlayerView?.let { view ->
            if (::videoContainer.isInitialized) videoContainer.removeView(view)
        }
        nativePlayerView = null
        if (::liveButton.isInitialized) liveButton.visibility = View.GONE
    }

    private fun playNativeStream(stream: NativeStream): Boolean {
        if (!::videoContainer.isInitialized) return false
        releaseNativePlayer()
        val view = PlayerView(this).apply {
            useController = false
            // Mostrar o quadro inteiro; quando a proporção for diferente, as
            // margens ficam pretas em vez de cortar conteúdo do canal.
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setShutterBackgroundColor(Color.BLACK)
            setBackgroundColor(Color.BLACK)
            setOnTouchListener { _, event ->
                if (event.action == android.view.MotionEvent.ACTION_UP) toggleChrome()
                true
            }
        }
        videoContainer.addView(view, FrameLayout.LayoutParams(-1, -1))
        nativePlayerView = view
        val requestProperties = mapOf(
            "Origin" to stream.origin,
            "Referer" to stream.referer,
            "Accept" to "*/*",
            "Accept-Language" to "pt-BR,pt;q=0.9",
            "Cache-Control" to "no-cache",
        )
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(requestProperties)
            .setUserAgent(MOBILE_USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
        val mediaItem = MediaItem.Builder()
            .setUri(stream.url)
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .build()
        val exo = ExoPlayer.Builder(this).build()
        nativePlayer = exo
        view.player = exo
        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> {
                        streamReady = true
                        streamTimeout?.let(chromeHandler::removeCallbacks)
                        streamTimeout = null
                        loading?.visibility = View.GONE
                        retryButton.visibility = View.GONE
                        updateLiveButton(exo)
                        showStatus("Ao vivo • ${serverLabel()}")
                    }
                    Player.STATE_BUFFERING -> {
                        if (!streamReady) {
                            loading?.visibility = View.VISIBLE
                            showStatus("Conectando ao vivo ${serverLabel()}...")
                        }
                    }
                    Player.STATE_ENDED -> {
                        loading?.visibility = View.GONE
                        liveButton.visibility = View.GONE
                        showStreamUnavailable("A transmissão terminou ou ficou indisponível.")
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                android.util.Log.e(TAG, "Falha no manifesto HLS nativo: ${error.errorCodeName}", error)
                tryNextServerOrShowError("Não foi possível atualizar esta transmissão. Tente novamente.")
            }
        })
        exo.setMediaSource(HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem))
        exo.prepare()
        exo.playWhenReady = true
        chromeHandler.removeCallbacks(liveCheck)
        chromeHandler.post(liveCheck)
        return true
    }

    private fun updateLiveButton(player: ExoPlayer) {
        if (!::liveButton.isInitialized || !streamReady) return
        val behind = player.currentLiveOffset
        val isDelayed = player.isCurrentWindowLive && behind != C.TIME_UNSET && behind > LIVE_BEHIND_THRESHOLD_MS
        liveButton.visibility = if (isDelayed) View.VISIBLE else View.GONE
        if (isDelayed) {
            liveButton.text = "AO VIVO"
            liveButton.contentDescription = "Voltar ao vivo"
        }
    }

    private fun goToLive() {
        nativePlayer?.let { player ->
            player.seekToDefaultPosition()
            player.playWhenReady = true
            liveButton.visibility = View.GONE
            loading?.visibility = View.VISIBLE
            showStatus("Voltando ao ao vivo...")
        }
    }

    private fun resolveNativeStream(embedUrl: String): NativeStream? {
        val outer = fetchText(embedUrl, "https://reidoscanais.st/") ?: return null
        val iframe = Regex("""<(?:iframe|frame)\b[^>]*(?:src|data-src|data-url)\s*=\s*[\x22\x27]([^\x22\x27]+)[\x22\x27]""", RegexOption.IGNORE_CASE)
            .find(outer)?.groupValues?.getOrNull(1)
            ?.replace("&amp;", "&")
            ?.let { raw ->
                Uri.parse(raw).let { parsed ->
                    if (parsed.isAbsolute) raw else Uri.parse(embedUrl).buildUpon().path(raw).build().toString()
                }
            }
            ?: return null
        val iframeHtml = fetchText(iframe, embedUrl) ?: return null
        val normalized = iframeHtml.replace("\\/", "/").replace("&amp;", "&")
        val manifest = Regex("""https?://[^\x22\x27\s<>]+\.m3u8(?:\?[^\x22\x27\s<>]*)?""", RegexOption.IGNORE_CASE)
            .find(normalized)?.value
            ?.replace("\\u0026", "&")
        if (manifest == null && normalized.contains("playback.video.globo.com/v5/video-session", ignoreCase = true)) {
            // O player Globo expõe uma sessão protegida, não um manifesto HLS
            // público. Não tentar fabricar token, contornar login ou DRM.
            android.util.Log.i(TAG, "Globo sem HLS direto; mantendo failover autorizado: $embedUrl")
            return null
        }
        if (manifest == null) return null
        val parsed = Uri.parse(iframe)
        val origin = buildString {
            append(parsed.scheme.orEmpty())
            append("://")
            append(parsed.host.orEmpty())
            if (parsed.port > 0) append(":${parsed.port}")
        }
        return NativeStream(manifest, iframe, origin)
    }

    private fun fetchText(url: String, referer: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = RESOLVE_CONNECT_TIMEOUT_MS
                readTimeout = RESOLVE_READ_TIMEOUT_MS
                instanceFollowRedirects = true
                useCaches = false
                setRequestProperty("User-Agent", MOBILE_USER_AGENT)
                setRequestProperty("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
                setRequestProperty("Referer", referer)
            }
            if (connection.responseCode !in 200..299) return null
            BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8)).use { reader ->
                val html = StringBuilder(minOf(MAX_EMBED_HTML_CHARS, 32_768))
                val buffer = CharArray(8_192)
                while (html.length < MAX_EMBED_HTML_CHARS) {
                    val amount = reader.read(buffer, 0, minOf(buffer.size, MAX_EMBED_HTML_CHARS - html.length))
                    if (amount <= 0) break
                    html.append(buffer, 0, amount)
                }
                html.toString()
            }
        } catch (error: Throwable) {
            android.util.Log.w(TAG, "Falha ao analisar embed para HLS nativo: $url", error)
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun loadChannelLogo() {
        val logoUrl = intent.getStringExtra(EXTRA_LOGO).orEmpty().trim()
        if (logoUrl.isBlank()) return
        Thread {
            try {
                val bitmap = URL(logoUrl).openStream().use { BitmapFactory.decodeStream(it) }
                runOnUiThread {
                    if (::logoView.isInitialized && bitmap != null && !isFinishing && !isDestroyedCompat()) {
                        logoView.setImageBitmap(bitmap)
                    }
                }
            } catch (error: Throwable) {
                android.util.Log.w(TAG, "Não foi possível carregar a logo do canal", error)
            }
        }.start()
    }

    private fun toggleChrome() {
        setChromeVisible(!chromeVisible)
    }

    private fun setChromeVisible(visible: Boolean) {
        if (!::chromeTop.isInitialized || !::chromeBottom.isInitialized) return
        chromeVisible = visible
        chromeTop.visibility = if (visible) View.VISIBLE else View.GONE
        chromeBottom.visibility = if (visible) View.VISIBLE else View.GONE
        chromeHandler.removeCallbacks(autoHideChrome)
        if (visible) chromeHandler.postDelayed(autoHideChrome, 5_000L)
    }

    private fun chooseServer() {
        val labels = servers.mapIndexed { index, server ->
            val marker = if (index == selectedIndex) "✓ " else ""
            "$marker${server.provider} · ${server.quality}"
        }
        AlertDialog.Builder(this, R.style.TedflixDialog)
            .setTitle("Escolha o servidor")
            .setItems(labels.toTypedArray()) { _, which ->
                if (which != selectedIndex) {
                    selectedIndex = which
                    serverButton.text = serverLabel()
                    loadSelectedServer()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun serverLabel(): String {
        val server = servers.getOrNull(selectedIndex) ?: return "Servidor"
        return "${server.provider} · ${server.quality}"
    }

    private fun showStatus(message: String) {
        if (::statusView.isInitialized) statusView.text = message
    }

    private fun enterImmersiveMode() {
        val decorView = window.decorView
        @Suppress("DEPRECATION")
        val legacyFlags = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            decorView.windowInsetsController?.let {
                it.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                it.hide(WindowInsets.Type.systemBars())
            } ?: run {
                @Suppress("DEPRECATION")
                decorView.systemUiVisibility = legacyFlags
            }
        } else {
            @Suppress("DEPRECATION")
            decorView.systemUiVisibility = legacyFlags
        }
    }

    override fun onBackPressed() {
        finish()
    }

    private fun controlButton(textValue: String, size: Int) = Button(this).apply {
        text = textValue
        textSize = size.toFloat()
        setTextColor(Color.WHITE)
        setAllCaps(false)
        setPadding(0, 0, 0, 0)
        background = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
    }

    private fun isDestroyedCompat(): Boolean = android.os.Build.VERSION.SDK_INT >= 17 && isDestroyed

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
