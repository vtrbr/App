package com.tedflix.app.auth

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.tedflix.app.MainActivity
import com.tedflix.app.R
import com.tedflix.app.TedflixLoading
import androidx.media3.common.util.UnstableApi
import java.util.Locale

@UnstableApi
class AuthActivity : Activity() {
    private lateinit var root: FrameLayout
    private lateinit var content: LinearLayout
    private lateinit var messageView: TextView
    private var loading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        AuthSession.init(applicationContext)
        showProfiles(null)
    }

    private fun showProfiles(message: String?) {
        root = buildBackdrop()
        val scroll = ScrollView(this).apply { isFillViewport = true; overScrollMode = View.OVER_SCROLL_NEVER }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(28), dp(24), dp(30))
        }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.tedflix_auth_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
        }
        content.addView(logo, LinearLayout.LayoutParams(-1, dp(76)).apply { bottomMargin = dp(18) })
        content.addView(TextView(this).apply {
            text = "Quem está assistindo?"
            textSize = 27f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) })
        content.addView(TextView(this).apply {
            text = "Escolha um perfil para continuar"
            textSize = 15f
            setTextColor(Color.argb(205, 255, 255, 255))
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(22) })

        val profiles = AuthSession.profiles()
        if (profiles.isEmpty()) {
            addEmptyState()
        } else {
            profiles.forEach { profile -> addProfileCard(profile) }
        }
        val add = Button(this).apply {
            text = if (profiles.isEmpty()) "Adicionar perfil" else "+ Adicionar perfil"
            textSize = 16f
            setTextColor(Color.WHITE)
            isAllCaps = false
            background = rounded(Color.rgb(35, 35, 35), dp(11), Color.argb(100, 255, 255, 255), dp(1))
            setOnClickListener { showAddProfile() }
        }
        content.addView(add, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(8) })
        messageView = TextView(this).apply {
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(12), dp(12), dp(12))
            visibility = View.GONE
        }
        content.addView(messageView, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        scroll.addView(content)
        root.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        if (!message.isNullOrBlank()) showMessage(message, false)
    }

    private fun addEmptyState() {
        content.addView(TextView(this).apply {
            text = "Você ainda não adicionou um perfil.\nUse seu token de acesso para começar."
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
            setPadding(dp(20), dp(22), dp(20), dp(22))
            background = rounded(Color.argb(215, 15, 15, 15), dp(14), Color.argb(45, 255, 255, 255), dp(1))
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
    }

    private fun addProfileCard(profile: AuthSession.Profile) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(13), dp(12), dp(13))
            background = rounded(Color.argb(232, 15, 15, 15), dp(14), Color.argb(55, 255, 255, 255), dp(1))
            setOnClickListener { activate(profile.id) }
        }
        val avatar = TextView(this).apply {
            text = "▶"
            gravity = Gravity.CENTER
            textSize = 19f
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(229, 9, 20), dp(30), Color.TRANSPARENT, 0)
        }
        card.addView(avatar, LinearLayout.LayoutParams(dp(52), dp(52)))
        card.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, dp(8), 0)
            addView(TextView(this@AuthActivity).apply {
                text = profile.name
                textSize = 17f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
            })
            addView(TextView(this@AuthActivity).apply {
                text = "Toque para entrar"
                textSize = 13f
                setTextColor(Color.LTGRAY)
            })
        }, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(TextView(this).apply { text = "›"; textSize = 30f; setTextColor(Color.LTGRAY) }, LinearLayout.LayoutParams(dp(30), -2))
        content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
    }

    private fun showAddProfile() {
        root = buildBackdrop()
        val scroll = ScrollView(this).apply { isFillViewport = true; overScrollMode = View.OVER_SCROLL_NEVER }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(34), dp(24), dp(30))
        }
        box.addView(TextView(this).apply {
            text = "Adicionar perfil"
            textSize = 28f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        box.addView(TextView(this).apply {
            text = "Informe o nome e o token de acesso."
            textSize = 15f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(22) })
        val name = field("Nome do perfil", "Ex.: João", InputType.TYPE_CLASS_TEXT)
        val token = field("Token de acesso", "A1B2C-3D4E5-F6G7H-8I9J0-K1L2M", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS).apply {
            filters = arrayOf(InputFilter.LengthFilter(29))
        }
        box.addView(name, fieldParams())
        box.addView(token, fieldParams())
        val enter = Button(this).apply {
            text = "Validar e entrar"
            textSize = 17f
            setTextColor(Color.WHITE)
            isAllCaps = false
            background = rounded(Color.rgb(229, 9, 20), dp(11), Color.TRANSPARENT, 0)
            setOnClickListener { submitToken(name.text.toString(), token.text.toString()) }
        }
        box.addView(enter, LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(4) })
        val back = Button(this).apply {
            text = "Voltar para perfis"
            textSize = 15f
            setTextColor(Color.LTGRAY)
            isAllCaps = false
            background = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
            setOnClickListener { showProfiles(null) }
        }
        box.addView(back, LinearLayout.LayoutParams(-1, dp(52)))
        scroll.addView(box)
        root.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
    }

    private fun submitToken(name: String, rawToken: String) {
        if (loading) return
        val cleanName = name.trim()
        val cleanToken = rawToken.trim().uppercase(Locale.ROOT)
        if (cleanName.isBlank()) return showMessage("Informe um nome para o perfil.", false)
        if (!Regex("^[A-Z0-9]{5}(-[A-Z0-9]{5}){4}$").matches(cleanToken)) {
            return showMessage("Digite um token no formato A1B2C-3D4E5-F6G7H-8I9J0-K1L2M.", false)
        }
        loading = true
        Thread {
            val result = AuthSession.createTokenProfile(cleanToken, cleanName, "tedflix-avatar-${System.currentTimeMillis()}")
            runOnUiThread {
                loading = false
                if (result.ok) openMain() else showMessage(result.message.ifBlank { "Não foi possível validar o token." }, false)
            }
        }.start()
    }

    private fun activate(profileId: String) {
        if (loading) return
        loading = true
        Thread {
            val result = AuthSession.activateProfile(profileId)
            runOnUiThread {
                loading = false
                if (result.ok) openMain() else showProfiles(result.message.ifBlank { "Não foi possível abrir o perfil." })
            }
        }.start()
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK) })
        finish()
    }

    private fun showMessage(message: String, success: Boolean) {
        if (!::messageView.isInitialized) return
        messageView.text = message
        messageView.setTextColor(if (success) Color.rgb(0, 255, 136) else Color.rgb(255, 105, 105))
        messageView.background = rounded(if (success) Color.argb(35, 0, 255, 136) else Color.argb(45, 229, 9, 20), dp(8), if (success) Color.argb(90, 0, 255, 136) else Color.argb(90, 229, 9, 20), dp(1))
        messageView.visibility = View.VISIBLE
    }

    private fun field(label: String, hint: String, inputType: Int): EditText = EditText(this).apply {
        this.hint = hint
        this.inputType = inputType
        textSize = 16f
        setTextColor(Color.WHITE)
        setHintTextColor(Color.argb(100, 255, 255, 255))
        setSingleLine(true)
        setPadding(dp(14), 0, dp(14), 0)
        background = rounded(Color.rgb(10, 10, 10), dp(9), Color.argb(80, 255, 255, 255), dp(2))
        contentDescription = label
    }

    private fun fieldParams() = LinearLayout.LayoutParams(-1, dp(54)).apply { bottomMargin = dp(14) }

    private fun buildBackdrop(): FrameLayout = FrameLayout(this).apply {
        setBackgroundColor(Color.BLACK)
        addView(ImageView(this@AuthActivity).apply { setImageResource(R.drawable.tedflix_auth_banner); scaleType = ImageView.ScaleType.CENTER_CROP; alpha = .55f }, FrameLayout.LayoutParams(-1, -1))
        addView(View(this@AuthActivity).apply { setBackgroundColor(Color.argb(170, 0, 0, 0)) }, FrameLayout.LayoutParams(-1, -1))
    }

    private fun rounded(fill: Int, radius: Int, stroke: Int, strokeWidth: Int): android.graphics.drawable.Drawable = android.graphics.drawable.GradientDrawable().apply {
        setColor(fill)
        cornerRadius = radius.toFloat()
        if (strokeWidth > 0) setStroke(strokeWidth, stroke)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
