package com.tedflix.app

import android.app.Activity
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.tedflix.app.auth.AuthSession
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import org.json.JSONArray

@UnstableApi
class PlayerActivity : Activity() {
    companion object {
        const val EXTRA_CATEGORIA = "categoria"
        const val EXTRA_SLUG = "slug"
        const val EXTRA_TITULO = "titulo"
        const val EXTRA_NEXT_EPISODES = "next_episodes"
        const val EXTRA_FILME_ID = "filme_id"
        const val EXTRA_THUMB = "thumb"
        const val EXTRA_TIPO = "tipo"
        const val EXTRA_SERIE_CATEGORIA = "serie_categoria"
        const val EXTRA_SERIE_SLUG = "serie_slug"
        const val PREFS = "tedflix_preferences"
        const val BUFFER_KEY = "buffer"
        private const val API_BASE = AuthSession.MOVIE_API_BASE
        private const val STREAM_ORIGIN = "https://novelasflix.video"
        private const val STREAM_REFERER = "https://novelasflix.video/"
        private const val STREAM_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36"
        private const val TAG = "TedflixPlayer"
    }

    private lateinit var playerView: PlayerView
    private lateinit var overlay: FrameLayout
    private lateinit var topBar: View
    private lateinit var nextButton: Button
    private lateinit var progress: SeekBar
    private lateinit var currentTime: TextView
    private lateinit var durationTime: TextView
    private lateinit var playButton: Button
    private lateinit var titleView: TextView
    private lateinit var loading: ProgressBar
    private lateinit var loadingPanel: LinearLayout
    private var player: ExoPlayer? = null
    private var currentCategoria = ""
    private var currentSlug = ""
    private var currentTitle = ""
    private var currentFilmeId = ""
    private var currentThumb = ""
    private var currentTipo = ""
    private var currentSerieCategoria = ""
    private var currentSerieSlug = ""
    private val nextEpisodes = mutableListOf<NextEpisode>()
    private var controlsVisible = false
    private var playerReady = false
    private var activityDestroyed = false
    @Volatile private var lastStep = "Activity ainda não inicializada"
    @Volatile private var diagnosticUrl = ""
    private var validationThread: Thread? = null
    private var lastDuration = 0L
    private var positionRestored = false
    @Volatile private var lastRemoteSaveAt = 0L
    @Volatile private var remotePositionMs: Long? = null
    @Volatile private var remotePositionLoaded = false
    private var remotePositionApplied = false
    private val handler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { setControlsVisible(false) }
    private data class NextEpisode(
        val categoria: String,
        val slug: String,
        val titulo: String,
        val filmeId: String = "",
        val thumb: String = "",
        val tipo: String = "episodio",
        val serieCategoria: String = "",
        val serieSlug: String = "",
    )

    private val progressRunnable = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, 500L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        markStep("PlayerActivity criada")
        loadEpisodeContext()
        try {
            // A orientação landscape já é declarada no Manifesto. Repetir a troca aqui
            // durante o primeiro onCreate pode provocar uma recriação enquanto a UI nasce.
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            markStep("iniciando criação da interface")
            buildUi()
            // A DecorView só é garantidamente criada depois de setContentView.
            // O modo imersivo e o player são iniciados no próximo ciclo do layout.
            window.decorView.post {
                if (!activityDestroyed && !isFinishing && !isDestroyedCompat()) {
                    enterImmersiveMode()
                    if (!activityDestroyed && !isFinishing && !isDestroyedCompat()) {
                        initializePlayer()
                    }
                }
            }
        } catch (error: Throwable) {
            Log.e(TAG, "Falha ao criar a interface do player", error)
            showStartupError(error)
        }
    }

    private fun enterImmersiveMode() {
        if (!isActivityAlive()) return
        try {
            window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
            val decorView = window.decorView
            @Suppress("DEPRECATION")
            val legacyFlags = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val controller = decorView.windowInsetsController
                if (controller != null) {
                    controller.systemBarsBehavior =
                        android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    controller.hide(WindowInsets.Type.systemBars())
                } else {
                    @Suppress("DEPRECATION")
                    decorView.systemUiVisibility = legacyFlags
                    Log.w(TAG, "WindowInsetsController ainda nulo; usando flags legadas")
                }
            } else {
                @Suppress("DEPRECATION")
                decorView.systemUiVisibility = legacyFlags
            }
            markStep("modo imersivo aplicado")
        } catch (error: Throwable) {
            // O modo imersivo é visual e não pode impedir a criação do player.
            Log.w(TAG, "Não foi possível aplicar modo imersivo; continuando com a UI", error)
            try {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
            } catch (fallbackError: Throwable) {
                Log.w(TAG, "Fallback do modo imersivo também falhou", fallbackError)
            }
        }
    }

    private fun loadEpisodeContext() {
        currentCategoria = intent.getStringExtra(EXTRA_CATEGORIA).orEmpty().trim()
        currentSlug = intent.getStringExtra(EXTRA_SLUG).orEmpty().trim()
        currentTitle = intent.getStringExtra(EXTRA_TITULO).orEmpty().trim().ifBlank { "Tedflix" }
        currentFilmeId = intent.getStringExtra(EXTRA_FILME_ID).orEmpty().trim().ifBlank { currentSlug }
        currentThumb = intent.getStringExtra(EXTRA_THUMB).orEmpty().trim()
        currentTipo = intent.getStringExtra(EXTRA_TIPO).orEmpty().trim()
        currentSerieCategoria = intent.getStringExtra(EXTRA_SERIE_CATEGORIA).orEmpty().trim()
        currentSerieSlug = intent.getStringExtra(EXTRA_SERIE_SLUG).orEmpty().trim()
        loadRemoteProgress()
        nextEpisodes.clear()
        val raw = intent.getStringExtra(EXTRA_NEXT_EPISODES).orEmpty().trim()
        if (raw.isBlank() || raw == "[]") return
        try {
            val array = JSONArray(raw)
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val categoria = item.optString("categoria").trim()
                val slug = item.optString("slug").trim()
                val titulo = item.optString("titulo").trim().ifBlank { "Próximo episódio" }
                val filmeId = item.optString("filmeId").trim().ifBlank { slug }
                val thumb = item.optString("thumb").trim()
                val tipo = item.optString("tipo").trim().ifBlank { "episodio" }
                // A fila criada pela página da série pode não repetir o contexto em
                // cada item; nesse caso, herdamos o contexto do episódio atual.
                val serieCategoria = item.optString("serieCategoria").trim()
                    .ifBlank { currentSerieCategoria }
                val serieSlug = item.optString("serieSlug").trim()
                    .ifBlank { currentSerieSlug }
                if (categoria.isNotBlank() && slug.isNotBlank()) {
                    nextEpisodes += NextEpisode(
                        categoria,
                        slug,
                        titulo,
                        filmeId,
                        thumb,
                        tipo,
                        serieCategoria,
                        serieSlug,
                    )
                }
            }
        } catch (error: Throwable) {
            Log.w(TAG, "Fila de próximos episódios inválida; continuando sem avanço automático", error)
        }
    }

    private fun updateNextEpisodeButton() {
        if (::nextButton.isInitialized) {
            nextButton.visibility = if (nextEpisodes.isNotEmpty() && playerReady) View.VISIBLE else View.GONE
        }
    }

    private fun playNextEpisode() {
        if (!isActivityAlive() || nextEpisodes.isEmpty()) return
        val next = nextEpisodes.removeAt(0)
        currentCategoria = next.categoria
        currentSlug = next.slug
        currentTitle = next.titulo
        currentFilmeId = next.filmeId.ifBlank { currentSlug }
        currentThumb = next.thumb
        currentTipo = next.tipo.ifBlank { "episodio" }
        currentSerieCategoria = next.serieCategoria.ifBlank { currentSerieCategoria }
        currentSerieSlug = next.serieSlug.ifBlank { currentSerieSlug }
        remotePositionMs = null
        remotePositionLoaded = false
        remotePositionApplied = false
        lastRemoteSaveAt = 0L
        loadRemoteProgress()
        titleView.text = currentTitle
        playerReady = false
        updateNextEpisodeButton()
        setLoadingVisible(true)
        setControlsVisible(false)
        handler.removeCallbacks(progressRunnable)
        playerView.player = null
        player?.release()
        player = null
        initializePlayer()
    }

    private fun buildUi() {
        markStep("criando raiz da interface")
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        markStep("criando PlayerView")
        playerView = PlayerView(this).apply {
            useController = false
            // Preenche a área total do player; o vídeo pode cortar somente a sobra
            // mínima de proporção, como no player original do app.
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            // O spinner interno ALWAYS continuava aparecendo mesmo com o vídeo pronto.
            // O loader customizado abaixo será controlado exclusivamente por STATE_READY.
            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
            setBackgroundColor(Color.BLACK)
            setOnClickListener { toggleControls() }
        }
        root.addView(playerView, FrameLayout.LayoutParams(-1, -1))
        markStep("PlayerView criada")

        overlay = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            isClickable = true
            // O toque em área livre alterna: fecha os controles quando abertos
            // e permite reabri-los quando já estão ocultos.
            setOnClickListener { if (playerReady) toggleControls() }
        }
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        markStep("overlay criado")
        buildTopBar()
        markStep("barra superior criada")
        buildCenterControls()
        markStep("controles centrais criados")
        buildBottomControls()
        markStep("controles inferiores criados")

        loadingPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            // O carregamento segue o mesmo visual limpo dos controles:
            // sem cartão, fundo escuro ou sombra por trás do spinner.
            elevation = 0f
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        loading = ProgressBar(this).apply {
            visibility = View.VISIBLE
            indeterminateTintList = android.content.res.ColorStateList.valueOf(Color.rgb(229, 28, 42))
        }
        loadingPanel.addView(loading, LinearLayout.LayoutParams(dp(42), dp(42)))
        loadingPanel.addView(TextView(this).apply {
            text = "Carregando vídeo..."
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(10) })
        overlay.addView(loadingPanel, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        markStep("loading criado")
        setContentView(root)
        markStep("setContentView concluído")
        // Enquanto o manifesto e o primeiro frame não estão prontos, somente o
        // loader fica visível. Os controles entram após STATE_READY.
        setControlsVisible(false)
        markStep("interface pronta")
    }

    private fun buildTopBar() {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(22), dp(14), dp(22), dp(14))
            // Overlay transparente: título e ações ficam diretamente sobre o vídeo,
            // sem a faixa escura atrás do cabeçalho.
            setBackgroundColor(Color.TRANSPARENT)
            isClickable = true
            setOnClickListener { if (playerReady) setControlsVisible(false) }
        }
        val back = controlButton("‹", 34).apply {
            contentDescription = "Voltar"
            setOnClickListener { finish() }
        }
        bar.addView(back, LinearLayout.LayoutParams(dp(54), dp(54)))
        titleView = TextView(this).apply {
            text = intent.getStringExtra(EXTRA_TITULO).orEmpty().ifBlank { "Tedflix" }
            setTextColor(Color.WHITE)
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(16), 0, dp(16), 0)
        }
        bar.addView(titleView, LinearLayout.LayoutParams(0, -2, 1f))
        nextButton = controlButton("Próximo ›", 13).apply {
            contentDescription = "Próximo episódio"
            visibility = View.GONE
            setOnClickListener { playNextEpisode() }
        }
        bar.addView(nextButton, LinearLayout.LayoutParams(dp(112), dp(54)))
        updateNextEpisodeButton()
        val reload = controlButton("↻", 28).apply {
            contentDescription = "Recarregar"
            setOnClickListener { reloadStream() }
        }
        bar.addView(reload, LinearLayout.LayoutParams(dp(54), dp(54)))
        topBar = bar
        val params = FrameLayout.LayoutParams(-1, dp(82), Gravity.TOP)
        overlay.addView(bar, params)
    }

    private fun buildCenterControls() {
        val center = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(18), 0, dp(18), 0)
            isClickable = true
            setOnClickListener { if (playerReady) toggleControls() }
        }
        val back10 = roundButton("↺\n10", 16).apply {
            contentDescription = "Retroceder 10 segundos"
            setOnClickListener { seekBy(-10_000L) }
        }
        val space = LinearLayout.LayoutParams(dp(92), dp(92)).apply {
            setMargins(dp(18), 0, dp(18), 0)
        }
        center.addView(back10, LinearLayout.LayoutParams(dp(80), dp(80)))
        playButton = roundButton("▶", 31).apply {
            contentDescription = "Reproduzir ou pausar"
            setOnClickListener { togglePlay() }
        }
        center.addView(playButton, space)
        val forward10 = roundButton("10\n↻", 16).apply {
            contentDescription = "Avançar 10 segundos"
            setOnClickListener { seekBy(10_000L) }
        }
        center.addView(forward10, LinearLayout.LayoutParams(dp(80), dp(80)))
        val params = FrameLayout.LayoutParams(-1, dp(150), Gravity.CENTER)
        overlay.addView(center, params)
    }

    private fun buildBottomControls() {
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), dp(20))
            // Controles inferiores sem painel/badge escuro, no estilo Netflix.
            setBackgroundColor(Color.TRANSPARENT)
            isClickable = true
            setOnClickListener { if (playerReady) toggleControls() }
        }
        val seekRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        currentTime = timeText("0:00")
        durationTime = timeText("0:00")
        seekRow.addView(currentTime, LinearLayout.LayoutParams(dp(68), -2))
        progress = SeekBar(this).apply {
            max = 1000
            progress = 0
            setPadding(0, 0, 0, 0)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, value: Int, fromUser: Boolean) {
                    if (fromUser && lastDuration > 0) currentTime.text = formatTime(lastDuration * value / 1000L)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) { handler.removeCallbacks(hideRunnable) }
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    player?.seekTo(lastDuration * (seekBar?.progress ?: 0) / 1000L)
                    scheduleHide()
                }
            })
        }
        seekRow.addView(progress, LinearLayout.LayoutParams(0, dp(34), 1f))
        seekRow.addView(durationTime, LinearLayout.LayoutParams(dp(72), -2))
        bottom.addView(seekRow, LinearLayout.LayoutParams(-1, dp(42)))

        val menuRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        addMenuButton(menuRow, "🔊  Áudio") { showAudioMenu() }
        addMenuButton(menuRow, "▣  Legendas") { showSubtitleMenu() }
        addMenuButton(menuRow, "⚙  Qualidade") { showQualityMenu() }
        addMenuButton(menuRow, "◷  Velocidade") { showSpeedMenu() }
        bottom.addView(menuRow, LinearLayout.LayoutParams(-1, dp(52)))
        overlay.addView(bottom, FrameLayout.LayoutParams(-1, dp(126), Gravity.BOTTOM))
    }

    private fun addMenuButton(row: LinearLayout, label: String, action: () -> Unit) {
        val button = Button(this).apply {
            text = label
            textSize = 14f
            setTextColor(Color.WHITE)
            setAllCaps(false)
            setPadding(dp(10), 0, dp(10), 0)
            // Ação leve sobre o vídeo, sem cápsula escura atrás do texto.
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { action() }
        }
        row.addView(button, LinearLayout.LayoutParams(0, dp(46), 1f).apply {
            setMargins(dp(4), 0, dp(4), 0)
        })
    }

    private fun initializePlayer() {
        try {
            markStep("Intent recebido")
            val categoria = currentCategoria
            val slug = currentSlug
            val titulo = currentTitle
            if (!AuthSession.hasToken()) {
                showDiagnosticScreen(
                    "Sessão ausente",
                    IllegalStateException("Faça login para reproduzir este conteúdo."),
                )
                return
            }
            if (categoria.isBlank() || slug.isBlank()) {
                showDiagnosticScreen(
                    "Intent/extras inválidos",
                    IllegalArgumentException("categoria ou slug vazio"),
                    "categoria='$categoria'\nslug='$slug'\ntitulo='$titulo'"
                )
                return
            }

            val streamUrl = "$API_BASE/filme-player/${Uri.encode(categoria)}/${Uri.encode(slug)}"
            diagnosticUrl = streamUrl
            markStep("streamUrl montada")
            val buffer = getSharedPreferences(PREFS, MODE_PRIVATE).getString(BUFFER_KEY, "equilibrado")
            val requestProperties = streamRequestProperties()
            validationThread?.interrupt()
            validationThread = Thread {
                var connection: HttpURLConnection? = null
                try {
                    markStep("validando manifesto HTTP")
                    connection = (URL(streamUrl).openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 15_000
                        readTimeout = 15_000
                        instanceFollowRedirects = true
                        requestProperties.forEach { (key, value) -> setRequestProperty(key, value) }
                    }
                    val status = connection.responseCode
                    val contentType = connection.contentType ?: "não informado"
                    val source = if (status in 200..299) connection.inputStream else connection.errorStream
                    val preview = readPreview(source)
                    val content = preview.trimStart().removePrefix("\uFEFF").trimStart()
                    val details = "HTTP status: $status\nContent-Type: $contentType\nPrévia:\n${preview.take(1200)}"
                    if (status !in 200..299) {
                        throw StreamValidationException("A API/CDN respondeu HTTP $status", details)
                    }
                    if (!content.startsWith("#EXTM3U")) {
                        throw StreamValidationException("A resposta não é uma playlist HLS (#EXTM3U ausente)", details)
                    }
                    markStep("URL validada: HTTP $status / $contentType")
                    runOnUiThread {
                        if (!isActivityAlive()) return@runOnUiThread
                        createExoPlayer(streamUrl, buffer, requestProperties)
                    }
                } catch (error: Throwable) {
                    if (error is InterruptedException || Thread.currentThread().isInterrupted) return@Thread
                    Log.e(TAG, "Falha no preflight do manifesto", error)
                    runOnUiThread {
                        if (isActivityAlive()) showDiagnosticScreen("Validação do manifesto", error)
                    }
                } finally {
                    connection?.disconnect()
                }
            }.apply {
                name = "TedflixManifestValidation"
                start()
            }
        } catch (error: Throwable) {
            Log.e(TAG, "Falha antes de inicializar o player", error)
            showDiagnosticScreen("Preparação do player", error)
        }
    }

    private fun createExoPlayer(
        streamUrl: String,
        buffer: String?,
        requestProperties: Map<String, String>,
    ) {
        try {
            playerReady = false
            positionRestored = false
            loading.visibility = View.VISIBLE
            setControlsVisible(false)
            markStep("criando MediaItem")
            val mediaItem = MediaItem.Builder()
                .setUri(streamUrl)
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .build()
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(bufferMin(buffer), bufferMax(buffer), 2_500, 5_000)
                .build()
            val dataSourceFactory = DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(requestProperties)
                .setUserAgent(STREAM_USER_AGENT)
                .setAllowCrossProtocolRedirects(true)
            markStep("criando HlsMediaSource")
            val mediaSource = HlsMediaSource.Factory(dataSourceFactory)
                .createMediaSource(mediaItem)
            markStep("criando ExoPlayer")
            val exo = ExoPlayer.Builder(this)
                .setLoadControl(loadControl)
                .build()
            player = exo
            playerView.player = exo
            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (activityDestroyed) return
                    if (state == Player.STATE_READY) {
                        playerReady = true
                        restoreSavedPosition(exo)
                        applyRemotePositionIfReady(exo)
                        updateNextEpisodeButton()
                        markStep("Player pronto: STATE_READY")
                        setLoadingVisible(false)
                        setControlsVisible(true)
                        scheduleHide()
                    } else if (state == Player.STATE_BUFFERING || state == Player.STATE_IDLE) {
                        // Durante a leitura do manifesto ou dos segmentos, o painel
                        // central reaparece sem remover a barra de voltar.
                        setLoadingVisible(true)
                        if (!playerReady) setControlsVisible(false)
                    }
                    if (state == Player.STATE_ENDED) {
                        setLoadingVisible(false)
                        setControlsVisible(true)
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (activityDestroyed) return
                    playButton.text = if (isPlaying) "Ⅱ" else "▶"
                    if (!playerReady) return
                    if (isPlaying) scheduleHide() else setControlsVisible(true)
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (activityDestroyed) return
                    markStep("erro de playback: ${error.errorCodeName}")
                    Log.e(TAG, "Falha ao carregar HLS: ${error.errorCodeName}", error)
                    showDiagnosticScreen("Playback Media3/HTTP", error)
                }
            })
            exo.setMediaSource(mediaSource)
            markStep("preparando ExoPlayer")
            exo.prepare()
            exo.playWhenReady = true
            markStep("reprodução solicitada")
            handler.removeCallbacks(progressRunnable)
            handler.post(progressRunnable)
        } catch (error: Throwable) {
            Log.e(TAG, "Falha ao criar Media3/HlsMediaSource", error)
            player?.release()
            player = null
            if (::playerView.isInitialized) playerView.player = null
            showDiagnosticScreen("Inicialização Media3/HlsMediaSource", error)
        }
    }

    private fun streamRequestProperties(): Map<String, String> = buildMap {
        put("Origin", STREAM_ORIGIN)
        put("Referer", STREAM_REFERER)
        put("Accept", "*/*")
        put("Accept-Language", "pt-BR,pt;q=0.9")
        put("Cache-Control", "no-cache")
        put("Pragma", "no-cache")
        AuthSession.token()?.takeIf { it.isNotBlank() }?.let { token ->
            put("Authorization", "Bearer $token")
        }
    }

    private fun readPreview(input: InputStream?): String {
        if (input == null) return "(resposta sem corpo)"
        return try {
            input.bufferedReader(Charsets.UTF_8).use { reader ->
                val chars = CharArray(8192)
                val count = reader.read(chars)
                if (count <= 0) "(corpo vazio)" else String(chars, 0, count)
            }
        } catch (error: Throwable) {
            "(não foi possível ler o corpo: ${error.message})"
        }
    }

    private fun isActivityAlive() = !activityDestroyed && !isFinishing && !isDestroyedCompat()

    private fun markStep(step: String) {
        lastStep = step
        Log.i(TAG, "[TEDFLIX] $step")
    }

    private class StreamValidationException(message: String, val details: String) : IllegalStateException(message)

    private fun reloadStream() {
        if (!isActivityAlive()) return
        playerReady = false
        positionRestored = false
        remotePositionApplied = false
        setLoadingVisible(true)
        setControlsVisible(false)
        player?.let { exo ->
            try {
                exo.stop()
                exo.seekTo(0)
                exo.prepare()
                exo.playWhenReady = true
            } catch (error: Throwable) {
                Log.e(TAG, "Falha ao recarregar o stream", error)
                showDiagnosticScreen("Recarregamento do stream", error)
            }
        }
    }

    private fun togglePlay() {
        player?.let { if (it.isPlaying) it.pause() else it.play() }
        setControlsVisible(true)
    }

    private fun seekBy(delta: Long) {
        player?.let { it.seekTo((it.currentPosition + delta).coerceIn(0L, it.duration.takeIf { d -> d > 0 } ?: Long.MAX_VALUE)) }
        setControlsVisible(true)
    }

    private fun updateProgress() {
        if (!::progress.isInitialized || !::currentTime.isInitialized || !::durationTime.isInitialized) return
        val exo = player ?: return
        lastDuration = exo.duration.takeIf { it > 0 } ?: 0L
        val current = exo.currentPosition.coerceAtLeast(0L)
        progress.progress = if (lastDuration > 0) ((current * 1000L) / lastDuration).toInt().coerceIn(0, 1000) else 0
        currentTime.text = formatTime(current)
        durationTime.text = formatTime(lastDuration)
    }

    private fun showQualityMenu() {
        val options = linkedMapOf("Automático" to Int.MAX_VALUE)
        player?.currentTracks?.groups?.filter { it.type == C.TRACK_TYPE_VIDEO }?.forEach { group ->
            for (i in 0 until group.length) {
                val height = group.getTrackFormat(i).height
                if (height > 0) options["${height}p"] = height
            }
        }
        choose("Qualidade", options.keys.toList()) { label ->
            val height = options[label] ?: Int.MAX_VALUE
            player?.let { exo ->
                exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                    .setMaxVideoSize(Int.MAX_VALUE, height)
                    .build()
            }
        }
    }

    private fun showAudioMenu() {
        val tracks = collectTracks(C.TRACK_TYPE_AUDIO)
        if (tracks.isEmpty()) {
            toast("Apenas uma faixa de áudio disponível")
            return
        }
        choose("Áudio", listOf("Automático") + tracks.map { it.label }) { label ->
            if (label == "Automático") {
                player?.let { exo ->
                    exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                        .setPreferredAudioLanguage(null).build()
                }
            } else {
                val track = tracks.firstOrNull { it.label == label }
                player?.let { exo ->
                    exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                        .setPreferredAudioLanguage(track?.format?.language).build()
                }
            }
        }
    }

    private fun showSubtitleMenu() {
        val tracks = collectTracks(C.TRACK_TYPE_TEXT)
        choose("Legendas", listOf("Desativadas") + tracks.map { it.label }) { label ->
            val builder = player?.trackSelectionParameters?.buildUpon() ?: return@choose
            if (label == "Desativadas") builder.setIgnoredTextSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            else builder.setPreferredTextLanguage(tracks.firstOrNull { it.label == label }?.format?.language)
            player?.trackSelectionParameters = builder.build()
        }
    }

    private fun showSpeedMenu() {
        val speeds = listOf("0,75x" to 0.75f, "Normal" to 1f, "1,25x" to 1.25f, "1,5x" to 1.5f, "2x" to 2f)
        choose("Velocidade", speeds.map { it.first }) { label ->
            player?.setPlaybackParameters(PlaybackParameters(speeds.firstOrNull { it.first == label }?.second ?: 1f))
        }
    }

    private data class TrackOption(val label: String, val format: Format)

    private fun collectTracks(type: Int): List<TrackOption> {
        val result = mutableListOf<TrackOption>()
        player?.currentTracks?.groups?.filter { it.type == type }?.forEach { group ->
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                val label = format.label ?: format.language ?: "Faixa ${result.size + 1}"
                if (result.none { it.label == label }) result += TrackOption(label, format)
            }
        }
        return result
    }

    private fun choose(title: String, labels: List<String>, selected: (String) -> Unit) {
        val dialog = android.app.AlertDialog.Builder(this, R.style.TedflixDialog)
            .setTitle(title)
            .setItems(labels.toTypedArray()) { _, which ->
                selected(labels[which])
                setControlsVisible(false)
            }
            .create()
        dialog.setCanceledOnTouchOutside(true)
        dialog.setOnCancelListener { setControlsVisible(false) }
        dialog.show()
    }

    private fun restoreSavedPosition(exo: ExoPlayer) {
        if (positionRestored) return
        positionRestored = true
        val saved = ContinueWatchingStore.read(this).firstOrNull {
            it.categoria == currentCategoria && it.slug == currentSlug
        } ?: return
        val duration = exo.duration.takeIf { it > 0L } ?: saved.durationMs
        val target = saved.positionMs.coerceIn(0L, (duration - 1_000L).coerceAtLeast(0L))
        if (target >= 10_000L) {
            exo.seekTo(target)
            toast("Continuando de ${formatTime(target)}")
            Log.d(TAG, "Progresso restaurado: ${target}ms para ${saved.key}")
        }
    }

    private fun loadRemoteProgress() {
        if (!AuthSession.hasToken() || currentFilmeId.isBlank()) return
        val filmeId = currentFilmeId
        val categoria = currentCategoria
        val slug = currentSlug
        Thread {
            val result = AuthSession.continueWatching()
            val match = result.value.orEmpty().firstOrNull { item ->
                item.filmeId == filmeId ||
                    item.slug == slug ||
                    (item.categoria == categoria && item.slug == slug)
            }
            remotePositionMs = match?.tempo?.let { parseRemoteTime(it) }
            remotePositionLoaded = true
            runOnUiThread { player?.let { applyRemotePositionIfReady(it) } }
        }.apply { name = "TedflixRemoteProgressLoad"; start() }
    }

    private fun parseRemoteTime(raw: String): Long? {
        val parts = raw.trim().split(":")
        if (parts.size !in 2..3) return raw.toLongOrNull()?.times(1_000L)
        return try {
            val values = parts.map { it.toLong() }
            val seconds = if (values.size == 3) values[0] * 3_600L + values[1] * 60L + values[2] else values[0] * 60L + values[1]
            seconds * 1_000L
        } catch (_: Throwable) { null }
    }

    private fun applyRemotePositionIfReady(exo: ExoPlayer) {
        if (remotePositionApplied || !remotePositionLoaded) return
        val target = remotePositionMs ?: return
        val duration = exo.duration.takeIf { it > 0L } ?: return
        val safeTarget = target.coerceIn(0L, (duration - 1_000L).coerceAtLeast(0L))
        if (safeTarget >= 10_000L) {
            exo.seekTo(safeTarget)
            remotePositionApplied = true
            toast("Continuando de ${formatTime(safeTarget)}")
            Log.d(TAG, "Histórico remoto restaurado: ${safeTarget}ms para $currentFilmeId")
        }
    }

    private fun saveRemoteProgress(positionMs: Long) {
        if (!AuthSession.hasToken() || currentFilmeId.isBlank()) return
        val now = System.currentTimeMillis()
        if (now - lastRemoteSaveAt < 4_000L) return
        lastRemoteSaveAt = now
        val tempo = formatRemoteTime(positionMs)
        val filmeId = currentFilmeId
        val titulo = currentTitle
        val thumb = currentThumb
        val categoria = currentCategoria
        val slug = currentSlug
        val tipo = currentTipo
        val serieCategoria = currentSerieCategoria
        val serieSlug = currentSerieSlug
        Thread {
            val result = AuthSession.saveProgress(
                filmeId = filmeId,
                titulo = titulo,
                tempo = tempo,
                thumb = thumb,
                categoria = categoria,
                slug = slug,
                tipo = tipo,
                serieCategoria = serieCategoria,
                serieSlug = serieSlug,
            )
            if (!result.ok) Log.w(TAG, "Falha ao sincronizar histórico remoto: ${result.message}")
            else Log.d(TAG, "Histórico remoto sincronizado para $filmeId em $tempo")
        }.apply { name = "TedflixRemoteProgress"; start() }
    }

    private fun formatRemoteTime(positionMs: Long): String {
        val totalSeconds = (positionMs / 1_000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun savePlaybackProgress() {
        val exo = player ?: return
        if (!playerReady) return
        val duration = exo.duration
        val position = exo.currentPosition
        if (duration <= 0L || position <= 0L) return
        ContinueWatchingStore.save(
            context = this,
            categoria = currentCategoria,
            slug = currentSlug,
            titulo = currentTitle,
            positionMs = position,
            filmeId = currentFilmeId,
            durationMs = duration,
            thumb = currentThumb,
            tipo = currentTipo,
            serieCategoria = currentSerieCategoria,
            serieSlug = currentSerieSlug,
        )
        saveRemoteProgress(position)
        Log.d(TAG, "Progresso salvo: ${position}ms/${duration}ms para $currentCategoria:$currentSlug")
    }

    override fun onPause() {
        savePlaybackProgress()
        super.onPause()
    }

    override fun onStop() {
        savePlaybackProgress()
        handler.removeCallbacks(progressRunnable)
        super.onStop()
    }

    private fun showError(message: String) {
        setLoadingVisible(false)
        toast(message)
        if (::overlay.isInitialized) setControlsVisible(true)
    }

    private fun showStartupError(error: Throwable) {
        showDiagnosticScreen("Criação da interface", error)
    }

    private fun showDiagnosticScreen(
        stage: String,
        error: Throwable,
        extra: String? = null,
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread { showDiagnosticScreen(stage, error, extra) }
            return
        }
        if (activityDestroyed || isFinishing || isDestroyedCompat()) return
        val failedStep = lastStep
        val activityName = this@PlayerActivity.javaClass.simpleName
        markStep("diagnóstico exibido: $stage")
        validationThread?.interrupt()
        validationThread = null
        player?.release()
        player = null
        val stack = Log.getStackTraceString(error).take(8_000)
        val cause = generateSequence(error as Throwable?) { it.cause }
            .toList().drop(1).firstOrNull()?.let { "${it.javaClass.name}: ${it.message ?: "(sem mensagem)"}" }
            ?: "(sem causa encadeada)"
        val details = buildString {
            appendLine("TEDFLIX — DIAGNÓSTICO DE REPRODUÇÃO")
            appendLine()
            appendLine("Etapa: $stage")
            appendLine("Activity: $activityName")
            appendLine("Última etapa registrada: $failedStep")
            appendLine("Tipo: ${error.javaClass.name}")
            appendLine("Mensagem: ${error.message ?: "(sem mensagem)"}")
            appendLine("Causa: $cause")
            appendLine("Stream URL: ${diagnosticUrl.ifBlank { "(não montada)" }}")
            if (!extra.isNullOrBlank()) {
                appendLine()
                appendLine(extra)
            }
            if (error is StreamValidationException) {
                appendLine()
                appendLine(error.details)
            }
            appendLine()
            appendLine("Stack trace:")
            append(stack)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            setBackgroundColor(Color.BLACK)
        }
        val heading = TextView(this).apply {
            text = "Erro ao abrir o player"
            textSize = 22f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        root.addView(heading, LinearLayout.LayoutParams(-1, -2))
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(TextView(this@PlayerActivity).apply {
                text = details
                textSize = 12f
                setTextColor(Color.WHITE)
                typeface = android.graphics.Typeface.MONOSPACE
                setPadding(0, dp(18), 0, dp(18))
                setTextIsSelectable(true)
            })
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val retry = controlButton("Tentar novamente", 14).apply {
            setOnClickListener {
                activityDestroyed = false
                buildUi()
                window.decorView.post { if (isActivityAlive()) initializePlayer() }
            }
        }
        val back = controlButton("Voltar", 14).apply { setOnClickListener { finish() } }
        actions.addView(retry, LinearLayout.LayoutParams(0, dp(52), 1f))
        actions.addView(back, LinearLayout.LayoutParams(0, dp(52), 1f))
        root.addView(actions, LinearLayout.LayoutParams(-1, dp(64)))
        setContentView(root)
    }

    private fun setControlsVisible(visible: Boolean) {
        if (!::overlay.isInitialized) return
        controlsVisible = visible
        overlay.alpha = 1f
        overlay.children().forEach { child ->
            // O loader possui ciclo próprio e não pode ser escondido quando os
            // controles são fechados antes do primeiro STATE_READY.
            when {
                child === loading || child === loadingPanel -> {
                    child.visibility = if (!playerReady || loadingPanel.visibility == View.VISIBLE) View.VISIBLE else View.GONE
                }
                child === topBar -> {
                    // Durante o loading o Voltar permanece acessível. Depois que
                    // o player está pronto, a barra segue a mesma regra dos demais
                    // controles e desaparece ao tocar em uma área vazia.
                    child.visibility = if (!playerReady || visible) View.VISIBLE else View.INVISIBLE
                }
                else -> child.visibility = if (visible) View.VISIBLE else View.INVISIBLE
            }
        }
        if (visible) scheduleHide()
    }

    private fun setLoadingVisible(visible: Boolean) {
        if (::loading.isInitialized) loading.visibility = if (visible) View.VISIBLE else View.GONE
        if (::loadingPanel.isInitialized) loadingPanel.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun scheduleHide() {
        handler.removeCallbacks(hideRunnable)
        if (player?.isPlaying == true) handler.postDelayed(hideRunnable, 4_000L)
    }

    private fun toggleControls() {
        setControlsVisible(!controlsVisible)
    }

    override fun onBackPressed() {
        finish()
    }

    override fun onDestroy() {
        activityDestroyed = true
        validationThread?.interrupt()
        validationThread = null
        handler.removeCallbacks(progressRunnable)
        handler.removeCallbacks(hideRunnable)
        if (::playerView.isInitialized) playerView.player = null
        player?.release()
        player = null
        super.onDestroy()
    }

    private fun controlButton(textValue: String, size: Int) = Button(this).apply {
        text = textValue
        textSize = size.toFloat()
        setTextColor(Color.WHITE)
        setAllCaps(false)
        setPadding(0, 0, 0, 0)
        background = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
    }

    private fun roundButton(textValue: String, size: Int) = controlButton(textValue, size)

    private fun timeText(textValue: String) = TextView(this).apply {
        text = textValue
        textSize = 14f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
    }

    private fun centeredParams(width: Int, height: Int) = FrameLayout.LayoutParams(dp(width), dp(height), Gravity.CENTER)

    private fun bufferMin(buffer: String?) = when (buffer) {
        "economico" -> 15_000
        "generoso" -> 90_000
        else -> 30_000
    }

    private fun bufferMax(buffer: String?) = when (buffer) {
        "economico" -> 30_000
        "generoso" -> 180_000
        else -> 60_000
    }

    private fun formatTime(milliseconds: Long): String {
        val totalSeconds = (milliseconds / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0) String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        else String.format(Locale.US, "%d:%02d", minutes, seconds)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun gradient(color: Int, tl: Float, tr: Float, br: Float, bl: Float) =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadii = floatArrayOf(dp(18).toFloat(), dp(18).toFloat(), dp(18).toFloat(), dp(18).toFloat(), dp(18).toFloat(), dp(18).toFloat(), dp(18).toFloat(), dp(18).toFloat())
        }

    private fun toast(message: String) {
        if (!isFinishing && !isDestroyedCompat()) {
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun isDestroyedCompat(): Boolean = android.os.Build.VERSION.SDK_INT >= 17 && isDestroyed
}

private fun FrameLayout.children(): Sequence<View> = sequence {
    for (index in 0 until childCount) yield(getChildAt(index))
}
