package com.tedflix.app

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.util.UnstableApi
import com.tedflix.app.auth.AuthActivity
import com.tedflix.app.auth.AuthSession
import com.tedflix.app.auth.FavoritesActivity
import java.net.HttpURLConnection
import java.net.URL

@UnstableApi
class MainActivity : Activity() {
    companion object {
        const val EXTRA_OPEN_FAVORITE_TITLE = "open_favorite_title"
        const val EXTRA_OPEN_FAVORITE_CATEGORY = "open_favorite_category"
        const val EXTRA_OPEN_FAVORITE_SLUG = "open_favorite_slug"
        const val EXTRA_OPEN_FAVORITE_TYPE = "open_favorite_type"
        private const val PROFILE_PREFS = "tedflix_local_profiles"
        private val DEFAULT_AVATAR_SEEDS = listOf(
            "tedflix-avatar-01", "tedflix-avatar-02", "tedflix-avatar-03",
            "tedflix-avatar-04", "tedflix-avatar-05", "tedflix-avatar-06",
            "tedflix-avatar-07", "tedflix-avatar-08", "tedflix-avatar-09",
            "tedflix-avatar-10", "tedflix-avatar-11", "tedflix-avatar-12",
            "tedflix-avatar-13", "tedflix-avatar-14", "tedflix-avatar-15",
        )
    }

    private data class LocalProfile(
        val id: String,
        val name: String,
        val avatarSeed: String,
        val avatarStyle: String = "fun-emoji",
        val username: String = "",
        val isKids: Boolean = false,
    )

    private lateinit var webView: WebView
    private var playerWasOpened = false
    @Volatile private var authRedirectInProgress = false
    @Volatile private var historyRefreshInFlight = false
    @Volatile private var remoteHistoryProfileId = ""
    @Volatile private var remoteHistoryCache: List<AuthSession.HistoryItem> = emptyList()
    @Volatile private var favoriteCache: List<AuthSession.Favorite> = emptyList()
    @Volatile private var favoriteCount = 0

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(5, 6, 9)
        window.navigationBarColor = Color.rgb(5, 6, 9)
        AuthSession.init(applicationContext)
        // O token corrente pode ter sido perdido sem que os perfis locais tenham
        // sido removidos. Restaure o perfil ativo antes de abrir o seletor/WebView.
        AuthSession.restoreActiveProfileIfNeeded()

        val previousCrash = TedflixApplication.consumeLastCrash(this)
        if (!previousCrash.isNullOrBlank()) {
            showCrashRecovery(previousCrash)
            return
        }
        if (AuthSession.hasToken()) {
            Thread {
                val profilesResult = AuthSession.refreshProfiles()
                runOnUiThread {
                    if (profilesResult.statusCode == 401 || !AuthSession.hasToken()) {
                        AuthSession.clear()
                        startActivity(Intent(this, AuthActivity::class.java))
                        finish()
                    } else if (profilesResult.ok) {
                        showProfileChooser()
                    } else {
                        showProfileLoadError(profilesResult.message.ifBlank { "Não foi possível carregar os perfis." })
                    }
                }
            }.apply { name = "TedflixLoadRemoteProfiles"; start() }
        } else {
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
        }
    }

    private fun showProfileLoadError(message: String) {
        AlertDialog.Builder(this, R.style.TedflixDialog)
            .setTitle("Não foi possível carregar os perfis")
            .setMessage(message)
            .setNegativeButton("Sair") { _, _ ->
                AuthSession.clear()
                startActivity(Intent(this, AuthActivity::class.java))
                finish()
            }
            .setPositiveButton("Tentar novamente") { _, _ -> recreate() }
            .setCancelable(false)
            .show()
    }

    private fun refreshHeaderBadges() {
        // A tela de seleção ainda não possui perfil ativo; não consulte
        // /profiles/:id/favorites nesse momento, pois isso gera o falso aviso
        // de conexão mostrado sobre o seletor de perfis.
        if (!AuthSession.hasToken() || AuthSession.activeProfileId().isBlank()) return
        Thread {
            val favorites = AuthSession.listFavorites()
            if (favorites.ok) favoriteCache = favorites.value.orEmpty()
            favoriteCount = favoriteCache.size
            publishBadgeCounts()
        }.apply { name = "TedflixHeaderBadges"; start() }
    }

    private fun publishBadgeCounts() {
        if (!::webView.isInitialized || isFinishing || isDestroyed) return
        val favorites = favoriteCount.coerceAtLeast(0)
        webView.post {
            if (isFinishing || isDestroyed) return@post
            webView.evaluateJavascript(
                """(() => {
                    const applyBadge = (id, value) => {
                        const badge = document.getElementById(id);
                        if (!badge) return;
                        const count = Number(value) || 0;
                        badge.textContent = count > 99 ? '99+' : String(count);
                        badge.classList.toggle('visible', count > 0);
                        badge.setAttribute('aria-hidden', count > 0 ? 'false' : 'true');
                    };
                    applyBadge('fav-badge', $favorites);
                })();""".trimIndent(),
                null,
            )
        }
    }

    private fun showProfileChooser() {
        val profiles = loadProfiles()
        val selectedId = AuthSession.activeProfileId().ifBlank { profiles.firstOrNull()?.id.orEmpty() }
        val root = android.widget.FrameLayout(this).apply { setBackgroundColor(Color.rgb(5, 6, 9)) }
        val banner = ImageView(this).apply {
            setImageResource(com.tedflix.app.R.drawable.tedflix_auth_banner)
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0.84f
        }
        root.addView(banner, android.widget.FrameLayout.LayoutParams(-1, -1))
        val shade = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.argb(138, 5, 6, 9), Color.argb(232, 5, 6, 9), Color.argb(248, 5, 6, 9)),
            )
        }
        root.addView(shade, android.widget.FrameLayout.LayoutParams(-1, -1))

        val scroll = ScrollView(this).apply { isFillViewport = true }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(28), dp(20), dp(28))
        }
        content.addView(ImageView(this).apply {
            setImageResource(com.tedflix.app.R.drawable.tedflix_auth_logo)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            contentDescription = "Tedflix"
        }, LinearLayout.LayoutParams(dp(150), dp(52)).apply { bottomMargin = dp(22) })
        content.addView(TextView(this).apply {
            text = "Quem está assistindo?"
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(-1, -2))
        content.addView(TextView(this).apply {
            text = "Escolha seu avatar para continuar"
            textSize = 16f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8); bottomMargin = dp(24) })

        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        profiles.chunked(3).forEach { rowProfiles ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            rowProfiles.forEach { profile ->
                row.addView(createProfileTile(profile, profile.id == selectedId) {
                    selectProfileAndOpen(root, content, shade, profile)
                }, LinearLayout.LayoutParams(0, dp(142), 1f).apply {
                    leftMargin = dp(3); rightMargin = dp(3); bottomMargin = dp(14)
                })
            }
            repeat(3 - rowProfiles.size) { row.addView(View(this), LinearLayout.LayoutParams(0, dp(142), 1f)) }
            grid.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
        content.addView(grid, LinearLayout.LayoutParams(-1, -2))
        content.addView(TextView(this).apply {
            text = if (profiles.isEmpty()) "Nenhum perfil encontrado\n＋ Criar perfil" else "＋\nAdicionar perfil"
            textSize = 16f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(8))
            setOnClickListener { showAddProfileDialog() }
        }, LinearLayout.LayoutParams(-1, dp(92)))
        content.addView(Button(this).apply {
            text = "Gerenciar perfis"
            setAllCaps(false)
            textSize = 16f
            setTextColor(Color.LTGRAY)
            background = GradientDrawable().apply {
                setColor(Color.argb(80, 8, 8, 12)); cornerRadius = dp(10).toFloat()
                setStroke(dp(1), Color.argb(115, 255, 255, 255))
            }
            setOnClickListener { showManageProfilesDialog() }
        }, LinearLayout.LayoutParams(dp(300), dp(54)))
        scroll.addView(content)
        root.addView(scroll, android.widget.FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
    }

    private fun createProfileTile(profile: LocalProfile, selected: Boolean, onClick: () -> Unit): View {
        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            isClickable = true; isFocusable = true; setOnClickListener { onClick() }
        }
        val frame = android.widget.FrameLayout(this)
        val avatar = ImageView(this).apply {
            tag = profile.avatarSeed; scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = profile.name; clipToOutline = true
            outlineProvider = ViewOutlineProvider.BACKGROUND
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL; setColor(Color.argb(92, 20, 20, 25))
                setStroke(if (selected) dp(3) else dp(1), if (selected) Color.rgb(229, 9, 20) else Color.argb(90, 255, 255, 255))
            }
        }
        frame.addView(avatar, android.widget.FrameLayout.LayoutParams(dp(96), dp(96), Gravity.CENTER))
        if (selected) frame.addView(TextView(this).apply {
            text = "✓"; textSize = 16f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.rgb(229, 9, 20)) }
        }, android.widget.FrameLayout.LayoutParams(dp(28), dp(28), Gravity.TOP or Gravity.END).apply {
            topMargin = dp(6)
            rightMargin = dp(2)
        })
        cell.addView(frame, LinearLayout.LayoutParams(dp(104), dp(106)).apply { topMargin = dp(6) })
        cell.addView(TextView(this).apply {
            text = profile.name; textSize = 14f; setTextColor(if (selected) Color.WHITE else Color.LTGRAY)
            gravity = Gravity.CENTER; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            if (selected) typeface = android.graphics.Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(-1, dp(28)))
        loadDiceBearAvatar(avatar, profile.avatarSeed, profile.avatarStyle)
        return cell
    }

    private fun selectProfileAndOpen(root: android.widget.FrameLayout, content: LinearLayout, shade: View, profile: LocalProfile) {
        content.alpha = 0.65f
        Thread {
            val activated = AuthSession.activateProfile(profile.id)
            runOnUiThread {
                content.alpha = 1f
                if (!activated.ok) {
                    Toast.makeText(this, activated.message.ifBlank { "Não foi possível selecionar este perfil." }, Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                refreshHeaderBadges()
                getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE).edit().putString("selected_profile_id", profile.id).apply()
                content.visibility = View.GONE; shade.alpha = 0.98f
                val loader = TedflixLoading.create(this@MainActivity)
                root.addView(loader, android.widget.FrameLayout.LayoutParams(-1, -1))
                root.postDelayed({ setupWebView() }, 320L)
            }
        }.apply { name = "TedflixSelectProfile"; start() }
    }

    private fun loadProfiles(): MutableList<LocalProfile> {
        val accountName = AuthSession.cachedUser()?.username.orEmpty()
        AuthSession.ensureCurrentProfile(accountName, DEFAULT_AVATAR_SEEDS.first())
        return AuthSession.profiles().map { profile ->
            LocalProfile(profile.id, profile.name, profile.avatarSeed, profile.avatarStyle, profile.username, profile.isKids)
        }.toMutableList()
    }

    private fun updateProfile(profile: LocalProfile, name: String = profile.name, avatarSeed: String = profile.avatarSeed) {
        AuthSession.updateProfile(profile.id, name, avatarSeed)
    }

    private fun avatarUrl(seed: String, style: String = "fun-emoji"): String = "https://api.dicebear.com/10.x/${Uri.encode(style)}/png?seed=${Uri.encode(seed)}&size=256"

    private fun loadDiceBearAvatar(image: ImageView, seed: String, style: String = "fun-emoji") {
        Thread {
            try {
                val connection = URL(avatarUrl(seed, style)).openConnection() as HttpURLConnection
                connection.connectTimeout = 8_000; connection.readTimeout = 8_000; connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) Tedflix/1.0")
                connection.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                val bitmap = if (connection.responseCode in 200..299) {
                    connection.inputStream.use { BitmapFactory.decodeStream(it) }
                } else null
                connection.disconnect()
                if (bitmap != null) runOnUiThread { if (image.tag == seed) image.setImageBitmap(bitmap) }
            } catch (error: Throwable) { Log.w("TedflixProfiles", "Falha ao carregar avatar DiceBear $seed", error) }
        }.apply { name = "TedflixAvatar-$seed"; start() }
    }

    private fun showAddProfileDialog() {
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(8), dp(24), 0) }
        val selectedSeed = arrayOf(DEFAULT_AVATAR_SEEDS[loadProfiles().size % DEFAULT_AVATAR_SEEDS.size])
        val selectedStyle = arrayOf("fun-emoji")
        val preview = ImageView(this).apply {
            tag = selectedSeed[0]
            scaleType = ImageView.ScaleType.FIT_CENTER
            clipToOutline = true
            outlineProvider = ViewOutlineProvider.BACKGROUND
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(24, 26, 34))
                setStroke(dp(1), Color.rgb(70, 73, 84))
            }
        }
        panel.addView(preview, LinearLayout.LayoutParams(dp(96), dp(96)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(8) })
        loadDiceBearAvatar(preview, selectedSeed[0])
        panel.addView(Button(this).apply {
            text = "Escolher outro avatar"; setAllCaps(false)
            setOnClickListener { showAvatarPicker(selectedSeed[0]) { seed -> selectedSeed[0] = seed; loadDiceBearAvatar(preview, seed, selectedStyle[0]) } }
        }, LinearLayout.LayoutParams(-1, dp(46)))
        val nameInput = EditText(this).apply { hint = "Nome do perfil"; setSingleLine(true) }
        panel.addView(nameInput, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(8) })
        val usernameInput = EditText(this).apply { hint = "Username (opcional)"; setSingleLine(true) }
        panel.addView(usernameInput, LinearLayout.LayoutParams(-1, dp(54)))
        val styleSpinner = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("fun-emoji", "clay", "adventurer-neutral")); onItemSelectedListener = object : AdapterView.OnItemSelectedListener { override fun onNothingSelected(parent: AdapterView<*>?) = Unit; override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { selectedStyle[0] = parent?.getItemAtPosition(position)?.toString().orEmpty().ifBlank { "fun-emoji" }; loadDiceBearAvatar(preview, selectedSeed[0], selectedStyle[0]) } } }
        panel.addView(styleSpinner, LinearLayout.LayoutParams(-1, dp(52)))
        val kidsSwitch = Switch(this).apply { text = "Perfil infantil"; setTextColor(Color.WHITE); isChecked = false }
        panel.addView(kidsSwitch, LinearLayout.LayoutParams(-1, dp(52)))
        val dialog = AlertDialog.Builder(this, R.style.TedflixDialog).setTitle("Criar perfil").setView(ScrollView(this).apply { addView(panel) }).setNegativeButton("Cancelar", null).setPositiveButton("Criar perfil", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = nameInput.text.toString().trim()
                when {
                    name.isBlank() -> nameInput.error = "Informe um nome"
                    else -> {
                        val action = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        action.isEnabled = false
                        action.text = "Criando..."
                        Thread {
                            val result = AuthSession.createRemoteProfile(name, kidsSwitch.isChecked, selectedStyle[0], selectedSeed[0], usernameInput.text.toString().trim())
                            runOnUiThread {
                                if (result.ok) {
                                    dialog.dismiss()
                                    showProfileChooser()
                                    Toast.makeText(this, "Perfil adicionado. Selecione-o para entrar.", Toast.LENGTH_SHORT).show()
                                } else {
                                    action.isEnabled = true
                                    action.text = "Validar e adicionar"
                                    Toast.makeText(this, result.message.ifBlank { "Não foi possível criar o perfil." }, Toast.LENGTH_LONG).show()
                                }
                            }
                        }.apply { this.name = "TedflixCreateProfile"; start() }
                    }
                }
            }
        }
        dialog.show()
    }

    private fun showAvatarPicker(currentSeed: String, onPick: (String) -> Unit) {
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), 0, dp(18), 0) }
        var picker: AlertDialog? = null
        DEFAULT_AVATAR_SEEDS.chunked(4).forEach { seeds ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            seeds.forEach { seed ->
                val avatar = ImageView(this).apply {
                    tag = seed
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    clipToOutline = true
                    outlineProvider = ViewOutlineProvider.BACKGROUND
                    setPadding(dp(4), dp(4), dp(4), dp(4))
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.rgb(24, 26, 34))
                        setStroke(if (seed == currentSeed) dp(3) else dp(1), if (seed == currentSeed) Color.rgb(229, 9, 20) else Color.rgb(70, 73, 84))
                    }
                    setOnClickListener { onPick(seed); picker?.dismiss() }
                }
                loadDiceBearAvatar(avatar, seed)
                row.addView(avatar, LinearLayout.LayoutParams(dp(68), dp(68)).apply { leftMargin = dp(5); rightMargin = dp(5); bottomMargin = dp(10) })
            }
            grid.addView(row)
        }
        picker = AlertDialog.Builder(this, R.style.TedflixDialog).setTitle("Escolha seu avatar").setView(ScrollView(this).apply { addView(grid) }).setNegativeButton("Cancelar", null).create()
        picker.show()
    }

    private fun showManageProfilesDialog() {
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), 0, dp(16), 0) }
        val profiles = loadProfiles()
        profiles.forEach { profile ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(6), 0, dp(6)) }
            val avatar = ImageView(this).apply {
                tag = profile.avatarSeed
                scaleType = ImageView.ScaleType.FIT_CENTER
                clipToOutline = true
                outlineProvider = ViewOutlineProvider.BACKGROUND
                setPadding(dp(3), dp(3), dp(3), dp(3))
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.rgb(24, 26, 34)) }
            }
            loadDiceBearAvatar(avatar, profile.avatarSeed)
            row.addView(avatar, LinearLayout.LayoutParams(dp(54), dp(54)))
            row.addView(TextView(this).apply { text = profile.name; textSize = 15f; setTextColor(Color.WHITE); setPadding(dp(10), 0, dp(4), 0) }, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(Button(this).apply { text = "Editar"; setAllCaps(false); setOnClickListener { showEditProfileDialog(profile) } }, LinearLayout.LayoutParams(dp(76), dp(44)))
            if (profiles.size > 1) row.addView(Button(this).apply { text = "Excluir"; setAllCaps(false); setOnClickListener { confirmDeleteProfile(profile) } }, LinearLayout.LayoutParams(dp(82), dp(44)))
            panel.addView(row)
        }
        AlertDialog.Builder(this, R.style.TedflixDialog).setTitle("Gerenciar perfis").setView(ScrollView(this).apply { addView(panel) }).setNegativeButton("Fechar", null).setPositiveButton("Adicionar perfil") { _, _ -> showAddProfileDialog() }.show()
    }

    private fun showEditProfileDialog(profile: LocalProfile) {
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), 0, dp(22), 0) }
        val nameInput = EditText(this).apply { setSingleLine(true); setText(profile.name); hint = "Nome do perfil" }
        val usernameInput = EditText(this).apply { setSingleLine(true); setText(profile.username); hint = "Username (opcional)" }
        val styleSpinner = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("fun-emoji", "clay", "adventurer-neutral")); setSelection(listOf("fun-emoji", "clay", "adventurer-neutral").indexOf(profile.avatarStyle).coerceAtLeast(0)) }
        val kidsSwitch = Switch(this).apply { text = "Perfil infantil"; setTextColor(Color.WHITE); isChecked = profile.isKids }
        panel.addView(nameInput, LinearLayout.LayoutParams(-1, dp(54)))
        panel.addView(usernameInput, LinearLayout.LayoutParams(-1, dp(54)))
        panel.addView(styleSpinner, LinearLayout.LayoutParams(-1, dp(52)))
        panel.addView(kidsSwitch, LinearLayout.LayoutParams(-1, dp(52)))
        AlertDialog.Builder(this, R.style.TedflixDialog).setTitle("Editar perfil").setView(panel).setNegativeButton("Cancelar", null).setPositiveButton("Salvar") { _, _ ->
            val name = nameInput.text.toString().trim().ifBlank { profile.name }
            val style = styleSpinner.selectedItem?.toString().orEmpty().ifBlank { profile.avatarStyle }
            val result = AuthSession.updateProfileFull(profile.id, name, usernameInput.text.toString().trim(), style, profile.avatarSeed, kidsSwitch.isChecked)
            if (!result.ok) Toast.makeText(this, result.message.ifBlank { "Não foi possível atualizar o perfil." }, Toast.LENGTH_LONG).show()
            showProfileChooser()
        }.setNeutralButton("Alterar avatar") { _, _ -> showAvatarPicker(profile.avatarSeed) { seed ->
            updateProfile(profile, avatarSeed = seed)
            showProfileChooser()
        } }.show()
    }

    private fun confirmDeleteProfile(profile: LocalProfile) {
        val profiles = loadProfiles()
        if (profiles.size <= 1) {
            Toast.makeText(this, "O último perfil não pode ser excluído.", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this, R.style.TedflixDialog).setTitle("Excluir perfil?").setMessage("Tem certeza que deseja excluir ${profile.name}?").setNegativeButton("Cancelar", null).setPositiveButton("Excluir") { _, _ ->
            if (AuthSession.deleteProfile(profile.id)) {
                val remaining = loadProfiles()
                getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE).edit()
                    .putString("selected_profile_id", AuthSession.activeProfileId().ifBlank { remaining.firstOrNull()?.id.orEmpty() }).apply()
            }
            showManageProfilesDialog()
        }.show()
    }

    private fun currentProfileName(): String {
        val activeId = AuthSession.activeProfileId()
        return AuthSession.profiles().firstOrNull { it.id == activeId }?.name
            ?: AuthSession.cachedUser()?.username?.ifBlank { "Meu perfil" }
            ?: "Meu perfil"
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView = WebView(this).apply {
            setBackgroundColor(Color.rgb(5, 6, 9))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.blockNetworkImage = false
            // A interface é de aplicativo: não permitir pinch-to-zoom nem
            // os controles de escala do WebView.
            @Suppress("DEPRECATION")
            settings.setSupportZoom(false)
            @Suppress("DEPRECATION")
            settings.builtInZoomControls = false
            @Suppress("DEPRECATION")
            settings.displayZoomControls = false
            @Suppress("DEPRECATION")
            settings.useWideViewPort = false
            @Suppress("DEPRECATION")
            settings.loadWithOverviewMode = false
            settings.textZoom = 100
            @Suppress("DEPRECATION")
            // O HTML e os assets são empacotados no APK; o modo padrão evita
            // recarregar tudo a cada retorno sem afetar as requisições /api.
            settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
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
                    // WebView/Android falha de forma seletiva ao buscar alguns
                    // WebP longos do CDN. Baixar pelo Android com User-Agent e
                    // Referer corretos elimina capas quebradas sem alterar a API.
                    val image = AuthSession.proxyExternalImageRequest(request)
                    if (image != null) return image
                    val response = AuthSession.proxyMovieRequest(request)
                    // Não transformar uma falha de autorização do servidor de filmes
                    // em logout automático. O token pode continuar válido no auth;
                    // o fetch do WebView recebe o status e a tela mostra o erro real.
                    return if (response != null) AuthSession.toWebResourceResponse(response)
                    else super.shouldInterceptRequest(view, request)
                }

                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    publishBadgeCounts()
                }
            }
            addJavascriptInterface(AndroidPlayerBridge(this@MainActivity), "AndroidPlayer")
            loadUrl("file:///android_asset/tedflix/index.html${routeFromIntent(intent)}")
        }
        setContentView(webView)
    }

    private fun routeFromIntent(sourceIntent: Intent): String {
        val favoriteTitle = sourceIntent.getStringExtra(EXTRA_OPEN_FAVORITE_TITLE).orEmpty().trim()
        val favoriteCategory = sourceIntent.getStringExtra(EXTRA_OPEN_FAVORITE_CATEGORY).orEmpty().trim()
        val favoriteSlug = sourceIntent.getStringExtra(EXTRA_OPEN_FAVORITE_SLUG).orEmpty().trim()
        val favoriteType = sourceIntent.getStringExtra(EXTRA_OPEN_FAVORITE_TYPE).orEmpty().lowercase().let {
            if (it.contains("séri") || it.contains("serie")) "serie" else "filme"
        }
        return when {
            favoriteCategory.isNotBlank() && favoriteSlug.isNotBlank() ->
                "#/titulo/$favoriteType/${Uri.encode(favoriteCategory)}/${Uri.encode(favoriteSlug)}"
            favoriteTitle.isNotBlank() -> "#/favorito?titulo=${Uri.encode(favoriteTitle)}"
            else -> ""
        }
    }

    private fun loadRouteFromIntent(sourceIntent: Intent) {
        webView.loadUrl("file:///android_asset/tedflix/index.html${routeFromIntent(sourceIntent)}")
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent == null) return
        setIntent(intent)
        if (!::webView.isInitialized) {
            setupWebView()
            return
        }
        webView.post { loadRouteFromIntent(intent) }
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
        if (AuthSession.hasToken()) refreshHeaderBadges()
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
            episodiosJson: String? = null,
            recomendadosJson: String? = null,
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
                        putExtra(PlayerActivity.EXTRA_SERIES_EPISODES, episodiosJson.orEmpty())
                        putExtra(PlayerActivity.EXTRA_RECOMMENDATIONS, recomendadosJson.orEmpty())
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
        fun getProfileName(): String = activity.currentProfileName()

        @JavascriptInterface
        fun openFavorites() {
            activity.runOnUiThread {
                if (!AuthSession.hasToken()) {
                    Toast.makeText(activity, "Adicione um perfil nas configurações para usar favoritos.", Toast.LENGTH_LONG).show()
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
        fun refreshContinueWatching() {
            val profileId = AuthSession.activeProfileId()
            // A Home consulta diretamente o endpoint de progresso usando a sessão
            // já preservada no dispositivo; ela não abre nem depende da tela de login.
            if (!AuthSession.hasToken()) {
                activity.runOnUiThread { notifyHistoryReady() }
                return
            }
            if (activity.historyRefreshInFlight) return
            activity.historyRefreshInFlight = true
            activity.remoteHistoryProfileId = profileId
            activity.remoteHistoryCache = emptyList()
            Thread {
                try {
                    val result = AuthSession.continueWatching()
                    if (!result.ok) {
                        Log.w("TedflixMain", "Histórico remoto indisponível: ${result.message}")
                        return@Thread
                    }
                    val enriched = result.value.orEmpty().map { item ->
                        if (item.categoria.isNotBlank() && item.slug.isNotBlank()) return@map item
                        val resolved = AuthSession.resolveCatalogItem(item.titulo, item.filmeId).value
                            ?: return@map item
                        item.copy(
                            titulo = item.titulo.ifBlank { resolved.titulo },
                            thumb = item.thumb.ifBlank { resolved.thumb },
                            categoria = item.categoria.ifBlank { resolved.categoria },
                            slug = item.slug.ifBlank { resolved.slug },
                            tipo = item.tipo.ifBlank { resolved.tipo },
                        )
                    }
                    // O contrato antigo do histórico pode trazer apenas filmeId,
                    // titulo, tempo e thumb. Mantemos esses itens para a WebView
                    // tentar resolvê-los pelo catálogo antes de descartá-los.
                    activity.remoteHistoryCache = enriched
                    Log.d("TedflixMain", "Histórico remoto carregado: ${enriched.size} item(ns)")
                } catch (error: Throwable) {
                    Log.w("TedflixMain", "Falha ao atualizar histórico remoto", error)
                } finally {
                    activity.historyRefreshInFlight = false
                    activity.runOnUiThread { notifyHistoryReady() }
                }
            }.apply { name = "TedflixHistoryRefresh"; start() }
        }

        private fun notifyHistoryReady() {
            if (!activity.isFinishing && !activity.isDestroyed) {
                activity.webView.evaluateJavascript(
                    "window.__tedflixHistoryReady && window.__tedflixHistoryReady();",
                    null,
                )
            }
        }

        @JavascriptInterface
        fun getContinueWatching(): String {
            return try {
                val local = org.json.JSONArray(ContinueWatchingStore.toJson(activity))
                if (!AuthSession.hasToken()) return local.toString()
                val activeProfileId = AuthSession.activeProfileId()
                val remote = if (AuthSession.hasToken() &&
                    (activeProfileId.isBlank() || activeProfileId == activity.remoteHistoryProfileId)
                ) {
                    activity.remoteHistoryCache
                } else {
                    emptyList()
                }
                for (i in 0 until local.length()) {
                    val item = local.optJSONObject(i) ?: continue
                    val filmeId = item.optString("filmeId").ifBlank { item.optString("slug") }
                    val slug = item.optString("slug")
                    val categoria = item.optString("categoria")
                    val match = remote.firstOrNull { remoteItem ->
                        remoteItem.filmeId == filmeId ||
                            (slug.isNotBlank() && remoteItem.slug == slug) ||
                            (categoria.isNotBlank() && remoteItem.categoria == categoria && remoteItem.slug == slug)
                    }
                    if (match != null) {
                        if (match.thumb.isNotBlank()) item.put("thumb", match.thumb)
                        if (match.tempo.isNotBlank()) item.put("tempo", match.tempo)
                        if (item.optString("categoria").isBlank() && match.categoria.isNotBlank()) item.put("categoria", match.categoria)
                        if (item.optString("slug").isBlank() && match.slug.isNotBlank()) item.put("slug", match.slug)
                        if (item.optString("tipo").isBlank() && match.tipo.isNotBlank()) item.put("tipo", match.tipo)
                        if (item.optString("serieCategoria").isBlank() && match.serieCategoria.isNotBlank()) item.put("serieCategoria", match.serieCategoria)
                        if (item.optString("serieSlug").isBlank() && match.serieSlug.isNotBlank()) item.put("serieSlug", match.serieSlug)
                        item.put("remote", true)
                    }
                }
                remote.forEach { remoteItem ->
                    val categoria = remoteItem.categoria.trim()
                    val slug = remoteItem.slug.trim()
                    val alreadyPresent = (0 until local.length()).any { index ->
                        val item = local.optJSONObject(index) ?: return@any false
                        val sameEpisode = categoria.isNotBlank() && slug.isNotBlank() &&
                            item.optString("categoria") == categoria && item.optString("slug") == slug
                        val sameId = remoteItem.filmeId.isNotBlank() && item.optString("filmeId") == remoteItem.filmeId
                        val sameTitle = remoteItem.filmeId.isBlank() && remoteItem.titulo.isNotBlank() &&
                            item.optString("titulo").equals(remoteItem.titulo, ignoreCase = true)
                        sameEpisode || sameId || sameTitle
                    }
                    if (alreadyPresent) return@forEach
                    local.put(org.json.JSONObject().apply {
                        put("categoria", categoria)
                        put("slug", slug)
                        put("filmeId", remoteItem.filmeId.ifBlank { slug })
                        put("titulo", remoteItem.titulo)
                        put("thumb", remoteItem.thumb)
                        put("tempo", remoteItem.tempo)
                        put("tipo", remoteItem.tipo)
                        put("serieCategoria", remoteItem.serieCategoria)
                        put("serieSlug", remoteItem.serieSlug)
                        put("remote", true)
                    })
                }
                local.toString()
            } catch (error: Throwable) {
                Log.w("TedflixMain", "Falha ao combinar histórico remoto", error)
                try { ContinueWatchingStore.toJson(activity) } catch (_: Throwable) { "[]" }
            }
        }

        @JavascriptInterface
        fun getFavorites(): String {
            return org.json.JSONObject().put("success", true).put("favoritos", org.json.JSONArray().apply {
                activity.favoriteCache.forEach { item ->
                    put(org.json.JSONObject().apply {
                        put("filmeId", item.filmeId)
                        put("titulo", item.titulo)
                        put("thumb", item.thumb)
                        put("adicionadoEm", item.adicionadoEm)
                    })
                }
            }).toString()
        }

        @JavascriptInterface
        fun toggleFavorite(filmeId: String?, titulo: String?, thumb: String?, contentType: String?): String {
            val id = filmeId.orEmpty()
            val wasFavorite = activity.favoriteCache.any { it.filmeId == id }
            Thread {
                val result = AuthSession.toggleFavorite(id, titulo.orEmpty(), thumb.orEmpty(), contentType.orEmpty().ifBlank { "movie" })
                if (!result.ok) Log.w("TedflixMain", "Falha ao alternar favorito", Exception(result.message))
                activity.refreshHeaderBadges()
            }.apply { name = "TedflixToggleFavorite"; start() }
            return org.json.JSONObject().put("success", true).put("favorito", !wasFavorite).toString()
        }

        @JavascriptInterface
        fun getProfiles(): String {
            return try {
                AuthSession.restoreActiveProfileIfNeeded()
                val user = AuthSession.cachedUser()
                AuthSession.ensureCurrentProfile(
                    user?.username.orEmpty().ifBlank { user?.email.orEmpty() },
                    "tedflix-avatar-01",
                )
                val profiles = AuthSession.profiles()
                org.json.JSONObject().apply {
                    put("success", true)
                    put("activeId", AuthSession.activeProfileId())
                    put("profiles", org.json.JSONArray().apply {
                        profiles.forEach { profile ->
                            put(org.json.JSONObject().apply {
                                put("id", profile.id)
                                put("name", profile.name)
                                put("avatarSeed", profile.avatarSeed)
                                put("avatarStyle", profile.avatarStyle)
                                put("email", profile.email)
                            })
                        }
                    })
                }.toString()
            } catch (error: Throwable) {
                Log.w("TedflixMain", "Falha ao listar perfis", error)
                "{\"success\":false,\"error\":\"Não foi possível carregar os perfis.\"}"
            }
        }

        @JavascriptInterface
        fun activateProfileFromSettings(profileId: String?): String {
            return try {
                val result = AuthSession.activateProfile(profileId.orEmpty().trim())
                if (result.ok) {
                    activity.remoteHistoryProfileId = ""
                    activity.remoteHistoryCache = emptyList()
                    activity.refreshHeaderBadges()
                }
                org.json.JSONObject().apply {
                    put("success", result.ok)
                    result.value?.id?.let { put("profileId", it) }
                    if (result.message.isNotBlank()) put("error", result.message)
                }.toString()
            } catch (error: Throwable) {
                Log.w("TedflixMain", "Falha ao ativar perfil", error)
                "{\"success\":false,\"error\":\"Não foi possível selecionar o perfil.\"}"
            }
        }

        @JavascriptInterface
        fun updateStoredProfile(profileId: String?, name: String?, avatarSeed: String?): String {
            return try {
                AuthSession.restoreActiveProfileIfNeeded()
                val requestedId = profileId.orEmpty().trim()
                val user = AuthSession.cachedUser()
                val profiles = AuthSession.profiles()
                val requested = profiles.firstOrNull { it.id == requestedId }
                val active = profiles.firstOrNull { it.id == AuthSession.activeProfileId() }
                val ensured = if (requested == null && active == null) {
                    AuthSession.ensureCurrentProfile(
                        user?.username.orEmpty().ifBlank { user?.email.orEmpty() },
                        "tedflix-avatar-01",
                    )
                } else null
                val id = requested?.id ?: active?.id ?: ensured?.id.orEmpty()
                if (id.isBlank()) {
                    return org.json.JSONObject()
                        .put("success", false)
                        .put("error", "Nenhum perfil autenticado foi encontrado.")
                        .toString()
                }
                val updated = AuthSession.updateProfile(id, name.orEmpty(), avatarSeed.orEmpty())
                if (updated && id == AuthSession.activeProfileId()) activity.refreshHeaderBadges()
                org.json.JSONObject().put("success", updated).apply {
                    if (!updated) put("error", "Perfil não encontrado.")
                }.toString()
            } catch (error: Throwable) {
                Log.w("TedflixMain", "Falha ao atualizar perfil", error)
                "{\"success\":false,\"error\":\"Não foi possível salvar o perfil.\"}"
            }
        }

        @JavascriptInterface
        fun createProfileFromSettings(code: String?, email: String?, password: String?, name: String?, avatarSeed: String?): String {
            return try {
                val result = AuthSession.createRemoteProfile(
                    name.orEmpty(), false, "fun-emoji", avatarSeed.orEmpty(), "",
                )
                org.json.JSONObject().apply {
                    put("success", result.ok)
                    result.value?.id?.let { put("profileId", it) }
                    if (result.message.isNotBlank()) put("error", result.message)
                }.toString()
            } catch (error: Throwable) {
                Log.w("TedflixMain", "Falha ao criar perfil", error)
                "{\"success\":false,\"error\":\"Não foi possível adicionar o perfil.\"}"
            }
        }

        @JavascriptInterface
        fun deleteProfileFromSettings(profileId: String?): String {
            return try {
                val deleted = AuthSession.deleteProfile(profileId.orEmpty().trim())
                if (deleted) activity.refreshHeaderBadges()
                org.json.JSONObject().put("success", deleted).apply {
                    if (!deleted) put("error", "O perfil principal não pode ser excluído ou não foi encontrado.")
                }.toString()
            } catch (error: Throwable) {
                Log.w("TedflixMain", "Falha ao excluir perfil", error)
                "{\"success\":false,\"error\":\"Não foi possível excluir o perfil.\"}"
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
                val user = AuthSession.cachedUser()
                org.json.JSONObject().put("success", true).put("status", org.json.JSONObject().apply {
                    put("status", user?.accountStatus.orEmpty())
                    put("expiresAt", user?.expiresAt.orEmpty())
                    user?.daysRemaining?.let { put("daysRemaining", it) }
                }).toString()
            } catch (error: Throwable) {
                "{\"success\":false,\"error\":\"Não foi possível carregar o status da conta.\"}"
            }
        }

        @JavascriptInterface
        fun getAccountProfile(): String {
            return try {
                val user = AuthSession.cachedUser()
                org.json.JSONObject().apply {
                    put("success", user != null)
                    put("user", org.json.JSONObject().apply {
                        put("id", user?.id.orEmpty())
                        put("email", user?.email.orEmpty())
                        put("username", user?.username.orEmpty())
                        put("accountExpiresAt", user?.expiresAt.orEmpty())
                        put("accountStatus", user?.accountStatus.orEmpty())
                        user?.daysRemaining?.let { put("daysRemaining", it) }
                        put("createdAt", user?.createdAt.orEmpty())
                        put("lastUsedAt", user?.lastUsedAt.orEmpty())
                    })
                }.toString()
            } catch (error: Throwable) {
                Log.w("TedflixMain", "Falha ao carregar os dados da conta", error)
                "{\"success\":false,\"error\":\"Não foi possível carregar os dados da conta.\"}"
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
