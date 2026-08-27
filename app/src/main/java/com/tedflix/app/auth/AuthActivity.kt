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
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import com.tedflix.app.MainActivity
import com.tedflix.app.TedflixLoading
import androidx.media3.common.util.UnstableApi
import com.tedflix.app.R
import java.util.Locale

@UnstableApi
class AuthActivity : Activity() {
    private lateinit var codeInput: EditText
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var enterButton: Button
    private lateinit var messageView: TextView
    private lateinit var loadingPanel: View
    private var checkingExistingSession = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        AuthSession.init(applicationContext)
        if (AuthSession.hasToken()) {
            checkingExistingSession = true
            showCheckingSession()
            Thread {
                val result = AuthSession.verify()
                runOnUiThread {
                    checkingExistingSession = false
                    if (result.ok) openMain() else {
                        AuthSession.clear()
                        showLogin(result.message.takeIf { it.isNotBlank() })
                    }
                }
            }.start()
        } else {
            showLogin(null)
        }
    }

    private fun showLogin(initialMessage: String?) {
        val root = buildBackdrop()
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(28), dp(24), dp(30))
        }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.tedflix_auth_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
        }
        content.addView(logo, LinearLayout.LayoutParams(-1, dp(76)).apply {
            bottomMargin = dp(18)
        })
        content.addView(TextView(this).apply {
            text = "Entrar"
            textSize = 30f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        content.addView(TextView(this).apply {
            text = "Informe seu código de acesso, e-mail e senha."
            textSize = 15f
            setTextColor(Color.argb(205, 255, 255, 255))
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(22) })

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(22))
            background = rounded(Color.argb(232, 15, 15, 15), dp(16), Color.argb(45, 255, 255, 255), dp(1))
        }
        card.addView(TextView(this).apply {
            text = "ACESSO TEDFLIX"
            textSize = 13f
            letterSpacing = .18f
            setTextColor(Color.argb(180, 255, 255, 255))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(18) })
        codeInput = field("CÓDIGO DE ACESSO", "ABC123XYZ", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS).apply {
            filters = arrayOf(InputFilter.LengthFilter(9))
        }
        emailInput = field("E-MAIL", "voce@email.com", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        passwordInput = field("SENHA", "Sua senha", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        card.addView(codeInput, fieldParams())
        card.addView(emailInput, fieldParams())
        card.addView(passwordInput, fieldParams())
        content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(18) })

        enterButton = Button(this).apply {
            text = "ENTRAR"
            textSize = 17f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            background = rounded(Color.rgb(229, 9, 20), dp(11), Color.TRANSPARENT, 0)
            setOnClickListener { submit() }
        }
        content.addView(enterButton, LinearLayout.LayoutParams(-1, dp(58)).apply { bottomMargin = dp(12) })
        loadingPanel = TedflixLoading.create(this).apply { visibility = View.GONE }
        content.addView(loadingPanel, LinearLayout.LayoutParams(-1, dp(176)))
        messageView = TextView(this).apply {
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(10), dp(12), dp(10))
            visibility = View.GONE
        }
        content.addView(messageView, LinearLayout.LayoutParams(-1, -2))
        scroll.addView(content)
        root.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        if (!initialMessage.isNullOrBlank()) showMessage(initialMessage, false)
    }

    private fun showCheckingSession() {
        val root = buildBackdrop()
        val box = TedflixLoading.create(this, "Verificando sua sessão...")
        root.addView(box, FrameLayout.LayoutParams(-1, -1).apply { gravity = Gravity.CENTER })
        setContentView(root)
    }

    private fun submit() {
        if (checkingExistingSession) return
        val code = codeInput.text.toString().trim().uppercase(Locale.ROOT)
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString()
        when {
            code.length != 9 -> return showMessage("Digite o código de acesso com 9 caracteres.", false)
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> return showMessage("Digite um e-mail válido.", false)
            password.length < 6 -> return showMessage("A senha deve ter pelo menos 6 caracteres.", false)
        }
        setLoading(true)
        Thread {
            val result = AuthSession.login(code, email, password)
            runOnUiThread {
                setLoading(false)
                if (result.ok) openMain() else showMessage(result.message.ifBlank { "Não foi possível entrar." }, false)
            }
        }.start()
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        finish()
    }

    private fun setLoading(loading: Boolean) {
        enterButton.isEnabled = !loading
        enterButton.text = if (loading) "ENTRANDO..." else "ENTRAR"
        TedflixLoading.show(loadingPanel, loading)
    }

    private fun showMessage(message: String, success: Boolean) {
        messageView.text = message
        messageView.setTextColor(if (success) Color.rgb(0, 255, 136) else Color.rgb(255, 105, 105))
        messageView.background = rounded(
            if (success) Color.argb(35, 0, 255, 136) else Color.argb(45, 229, 9, 20),
            dp(8),
            if (success) Color.argb(90, 0, 255, 136) else Color.argb(90, 229, 9, 20),
            dp(1),
        )
        messageView.visibility = View.VISIBLE
    }

    private fun field(label: String, hint: String, inputType: Int): EditText {
        val input = EditText(this).apply {
            this.hint = hint
            this.inputType = inputType
            textSize = 16f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.argb(75, 255, 255, 255))
            setSingleLine(true)
            setPadding(dp(14), 0, dp(14), 0)
            background = rounded(Color.rgb(10, 10, 10), dp(9), Color.argb(80, 255, 255, 255), dp(2))
        }
        input.contentDescription = label
        return input
    }

    private fun fieldParams() = LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(14) }

    private fun buildBackdrop(): FrameLayout {
        return FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(ImageView(this@AuthActivity).apply {
                setImageResource(R.drawable.tedflix_auth_banner)
                scaleType = ImageView.ScaleType.CENTER_CROP
                alpha = .55f
            }, FrameLayout.LayoutParams(-1, -1))
            addView(View(this@AuthActivity).apply {
                setBackgroundColor(Color.argb(170, 0, 0, 0))
            }, FrameLayout.LayoutParams(-1, -1))
        }
    }

    private fun rounded(fill: Int, radius: Int, stroke: Int, strokeWidth: Int): android.graphics.drawable.Drawable =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(fill)
            cornerRadius = radius.toFloat()
            if (strokeWidth > 0) setStroke(strokeWidth, stroke)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
