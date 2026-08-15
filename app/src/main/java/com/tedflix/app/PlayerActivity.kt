package com.tedflix.app

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.Window
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
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

@UnstableApi
class PlayerActivity : Activity() {
    companion object {
        const val EXTRA_CATEGORIA = "categoria"
        const val EXTRA_SLUG = "slug"
        const val EXTRA_TITULO = "titulo"
        const val PREFS = "tedflix_preferences"
        const val BUFFER_KEY = "buffer"
        private const val API_BASE = "https://ted.cryptitys.site/api"
        private const val STREAM_ORIGIN = "https://novelasflix.video"
        private const val STREAM_REFERER = "https://novelasflix.video/"
        private const val STREAM_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36"
        private const val TAG = "TedflixPlayer"
    }

    private lateinit var playerView: PlayerView
    private lateinit var overlay: FrameLayout
    private lateinit var progress: SeekBar
    private lateinit var currentTime: TextView
    private lateinit var durationTime: TextView
    private lateinit var playButton: Button
    private lateinit var titleView: TextView
    private lateinit var loading: ProgressBar
    private var player: ExoPlayer? = null
    private var controlsVisible = true
    private var activityDestroyed = false
    @Volatile private var lastStep = "Activity ainda não inicializada"
    @Volatile private var diagnosticUrl = ""
    private var validationThread: Thread? = null
    private var lastDuration = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { setControlsVisible(false) }
    private val progressRunnable = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, 500L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        markStep("PlayerActivity criada")
        try {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            enterImmersiveMode()
            buildUi()
            // Aguarda a Activity terminar o primeiro layout antes de criar o player.
            // Isso evita corrida com a troca obrigatória para a orientação horizontal.
            window.decorView.post {
                if (!activityDestroyed && !isFinishing && !isDestroyedCompat()) {
                    initializePlayer()
                }
            }
        } catch (error: Throwable) {
            Log.e(TAG, "Falha ao criar a interface do player", error)
            showStartupError()
        }
    }

    private fun enterImmersiveMode() {
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.hide(WindowInsets.Type.systemBars())
            window.insetsController?.systemBarsBehavior =
                android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
        }
    }

    private fun buildUi() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        playerView = PlayerView(this).apply {
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
            setBackgroundColor(Color.BLACK)
            setOnClickListener { toggleControls() }
        }
        root.addView(playerView, FrameLayout.LayoutParams(-1, -1))

        overlay = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            isClickable = false
        }
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        buildTopBar()
        buildCenterControls()
        buildBottomControls()

        loading = ProgressBar(this).apply { visibility = View.VISIBLE }
        overlay.addView(loading, centeredParams(54, 54))
        setContentView(root)
        setControlsVisible(true)
    }

    private fun buildTopBar() {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(22), dp(14), dp(22), dp(14))
            background = gradient(Color.argb(185, 0, 0, 0), 0f, 0f, 0f, 1f)
            isClickable = true
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
        val reload = controlButton("↻", 28).apply {
            contentDescription = "Recarregar"
            setOnClickListener { reloadStream() }
        }
        bar.addView(reload, LinearLayout.LayoutParams(dp(54), dp(54)))
        val params = FrameLayout.LayoutParams(-1, dp(82), Gravity.TOP)
        overlay.addView(bar, params)
    }

    private fun buildCenterControls() {
        val center = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(18), 0, dp(18), 0)
            isClickable = true
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
            background = gradient(Color.argb(210, 0, 0, 0), 1f, 0f, 0f, 0f)
            isClickable = true
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
            background = gradient(Color.argb(120, 35, 35, 35), 1f, 1f, 1f, 1f)
            setOnClickListener { action() }
        }
        row.addView(button, LinearLayout.LayoutParams(0, dp(46), 1f).apply {
            setMargins(dp(4), 0, dp(4), 0)
        })
    }

    private fun initializePlayer() {
        try {
            markStep("Intent recebido")
            val categoria = intent.getStringExtra(EXTRA_CATEGORIA).orEmpty()
            val slug = intent.getStringExtra(EXTRA_SLUG).orEmpty()
            val titulo = intent.getStringExtra(EXTRA_TITULO).orEmpty()
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
                    loading.visibility = if (state == Player.STATE_BUFFERING || state == Player.STATE_IDLE) View.VISIBLE else View.GONE
                    if (state == Player.STATE_READY) {
                        markStep("Player pronto: STATE_READY")
                        loading.visibility = View.GONE
                        scheduleHide()
                    }
                    if (state == Player.STATE_ENDED) setControlsVisible(true)
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (activityDestroyed) return
                    playButton.text = if (isPlaying) "Ⅱ" else "▶"
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
            handler.post(progressRunnable)
        } catch (error: Throwable) {
            Log.e(TAG, "Falha ao criar Media3/HlsMediaSource", error)
            player?.release()
            player = null
            if (::playerView.isInitialized) playerView.player = null
            showDiagnosticScreen("Inicialização Media3/HlsMediaSource", error)
        }
    }

    private fun streamRequestProperties() = mapOf(
        "Origin" to STREAM_ORIGIN,
        "Referer" to STREAM_REFERER,
        "Accept" to "*/*",
        "Accept-Language" to "pt-BR,pt;q=0.9",
        "Cache-Control" to "no-cache",
        "Pragma" to "no-cache",
    )

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
        loading.visibility = View.VISIBLE
        player?.seekTo(0)
        player?.prepare()
        player?.playWhenReady = true
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
        android.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(labels.toTypedArray()) { _, which -> selected(labels[which]); setControlsVisible(true) }
            .show()
    }

    private fun showError(message: String) {
        if (::loading.isInitialized) loading.visibility = View.GONE
        toast(message)
        if (::overlay.isInitialized) setControlsVisible(true)
    }

    private fun showStartupError() {
        showDiagnosticScreen(
            "Criação da interface",
            IllegalStateException("A interface do player não pôde ser criada")
        )
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
            appendLine("Activity: ${javaClass.simpleName}")
            appendLine("Última etapa registrada: $lastStep")
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
        overlay.children().forEach { child -> child.visibility = if (visible) View.VISIBLE else View.INVISIBLE }
        if (visible) scheduleHide()
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

    private fun roundButton(textValue: String, size: Int) = controlButton(textValue, size).apply {
        background = gradient(Color.argb(140, 45, 45, 45), 1f, 1f, 1f, 1f)
    }

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
