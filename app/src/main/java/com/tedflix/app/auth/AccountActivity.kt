package com.tedflix.app.auth

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.media3.common.util.UnstableApi
import com.tedflix.app.R

@UnstableApi
class AccountActivity : Activity() {
    private lateinit var statusText: TextView
    private lateinit var usernameInput: EditText
    private lateinit var currentPasswordInput: EditText
    private lateinit var newPasswordInput: EditText
    private lateinit var progress: ProgressBar
    private lateinit var refreshButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AuthSession.init(applicationContext)
        if (!AuthSession.hasToken()) { openLogin(); return }
        setContentView(buildScreen())
        refreshProfile()
    }

    private fun buildScreen(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(5, 6, 9)) }
        val scroll = ScrollView(this).apply { overScrollMode = View.OVER_SCROLL_NEVER }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(30))
        }
        val toolbar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        toolbar.addView(button("‹", 30) { finish() }, LinearLayout.LayoutParams(dp(48), dp(52)))
        toolbar.addView(TextView(this).apply {
            text = "Configurações da conta"
            textSize = 22f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, -2, 1f))
        refreshButton = button("Atualizar", 13) { refreshProfile() }
        refreshButton.background = rounded(Color.argb(45, 255, 255, 255), dp(9), Color.argb(100, 255, 255, 255), dp(1))
        toolbar.addView(refreshButton, LinearLayout.LayoutParams(dp(94), dp(42)))
        content.addView(toolbar, LinearLayout.LayoutParams(-1, dp(58)).apply { bottomMargin = dp(18) })

        content.addView(TextView(this).apply {
            text = "Minha conta"
            textSize = 28f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4) })
        content.addView(TextView(this).apply {
            text = "Gerencie seus dados e preferências em partes separadas."
            textSize = 13f
            setTextColor(Color.LTGRAY)
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(18) })

        statusText = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.LTGRAY)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = rounded(Color.rgb(31, 32, 36), dp(14), Color.argb(75, 255, 255, 255), dp(1))
        }
        content.addView(statusText, cardParams())

        val profile = card()
        profile.addView(sectionTitle("Perfil"))
        profile.addView(TextView(this).apply {
            text = "Nome exibido na tela inicial do Tedflix"
            textSize = 12f
            setTextColor(Color.LTGRAY)
        })
        usernameInput = input("Nome do perfil")
        profile.addView(usernameInput, inputParams())
        profile.addView(actionButton("Salvar nome") { saveUsername() }, buttonParams())
        content.addView(profile, cardParams())

        val security = card()
        security.addView(sectionTitle("Segurança"))
        security.addView(TextView(this).apply {
            text = "Altere sua senha de acesso"
            textSize = 12f
            setTextColor(Color.LTGRAY)
        })
        currentPasswordInput = input("Senha atual").apply { inputType = 0x81 }
        newPasswordInput = input("Nova senha").apply { inputType = 0x81 }
        security.addView(currentPasswordInput, inputParams())
        security.addView(newPasswordInput, inputParams())
        security.addView(actionButton("Alterar senha") { changePassword() }, buttonParams())
        content.addView(security, cardParams())

        val notifications = optionCard("Notificações", "Veja avisos e atualizações da sua conta", "Abrir") {
            startActivity(Intent(this, NotificationActivity::class.java))
        }
        content.addView(notifications, cardParams())

        val logout = actionButton("Sair da conta") { logout() }
        logout.background = rounded(Color.rgb(78, 14, 20), dp(11), Color.rgb(229, 9, 20), dp(1))
        content.addView(logout, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(4) })
        scroll.addView(content)
        root.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        progress = ProgressBar(this).apply {
            visibility = View.GONE
            indeterminateTintList = android.content.res.ColorStateList.valueOf(Color.rgb(229, 9, 20))
        }
        root.addView(progress, FrameLayout.LayoutParams(dp(42), dp(42), Gravity.CENTER))
        return root
    }

    private fun refreshProfile() {
        setBusy(true)
        Thread {
            val profile = AuthSession.profile()
            val status = AuthSession.status()
            runOnUiThread {
                setBusy(false)
                if (!profile.ok && profile.statusCode == 401) { openLogin(); return@runOnUiThread }
                val user = profile.value ?: AuthSession.cachedUser()
                usernameInput.setText(user?.username.orEmpty())
                statusText.text = buildStatus(user, status.value, profile.message)
            }
        }.start()
    }

    private fun saveUsername() {
        val value = usernameInput.text.toString().trim()
        if (value.length < 2) return toast("Digite um nome válido.")
        setBusy(true)
        Thread {
            val result = AuthSession.updateUsername(value)
            runOnUiThread {
                setBusy(false)
                toast(if (result.ok) "Nome atualizado." else result.message.ifBlank { "Não foi possível atualizar." })
                if (result.ok) refreshProfile()
            }
        }.start()
    }

    private fun changePassword() {
        val current = currentPasswordInput.text.toString()
        val next = newPasswordInput.text.toString()
        if (current.isBlank() || next.length < 6) return toast("Informe a senha atual e uma nova senha com pelo menos 6 caracteres.")
        setBusy(true)
        Thread {
            val result = AuthSession.changePassword(current, next)
            runOnUiThread {
                setBusy(false)
                toast(if (result.ok) "Senha alterada." else result.message.ifBlank { "Não foi possível alterar a senha." })
                if (result.ok) { currentPasswordInput.text.clear(); newPasswordInput.text.clear() }
            }
        }.start()
    }

    private fun logout() {
        setBusy(true)
        Thread {
            AuthSession.logout()
            runOnUiThread { openLogin() }
        }.start()
    }

    private fun buildStatus(user: AuthSession.User?, status: org.json.JSONObject?, error: String): String {
        val json = status ?: org.json.JSONObject()
        val state = json.optString("status").ifBlank { user?.accountStatus.orEmpty() }.ifBlank { "não informado" }
        val expires = json.optString("expiresAt").ifBlank { json.optString("accountExpiresAt") }.ifBlank { user?.expiresAt.orEmpty() }.ifBlank { "não informada" }
        val days = if (json.has("daysRemaining")) json.optInt("daysRemaining") else user?.daysRemaining
        return buildString {
            append("Status: ").append(state).append("\nValidade: ").append(expires)
            if (days != null) append("\nDias restantes: ").append(days)
            if (error.isNotBlank()) append("\n\n").append(error)
        }
    }

    private fun optionCard(title: String, description: String, action: String, onClick: () -> Unit) = card().apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(LinearLayout(this@AccountActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(sectionTitle(title))
            addView(TextView(this@AccountActivity).apply { text = description; textSize = 12f; setTextColor(Color.LTGRAY) })
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(actionButton(action, onClick), LinearLayout.LayoutParams(dp(88), dp(44)))
    }

    private fun sectionTitle(text: String) = TextView(this).apply {
        this.text = text; textSize = 18f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
    }
    private fun card() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16)); background = rounded(Color.rgb(18, 18, 20), dp(14), Color.argb(55, 255, 255, 255), dp(1)) }
    private fun input(hint: String) = EditText(this).apply { this.hint = hint; textSize = 15f; setSingleLine(true); setTextColor(Color.WHITE); setHintTextColor(Color.GRAY); setPadding(dp(12), 0, dp(12), 0); background = rounded(Color.rgb(9, 9, 10), dp(9), Color.argb(70, 255, 255, 255), dp(1)) }
    private fun actionButton(text: String, action: () -> Unit) = Button(this).apply { this.text = text; textSize = 13f; isAllCaps = false; setTextColor(Color.WHITE); background = rounded(Color.rgb(229, 9, 20), dp(9), Color.TRANSPARENT, 0); setOnClickListener { action() } }
    private fun button(text: String, size: Int, action: () -> Unit) = Button(this).apply { this.text = text; textSize = size.toFloat(); isAllCaps = false; setTextColor(Color.WHITE); background = transparent(); setOnClickListener { action() } }
    private fun inputParams() = LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(10) }
    private fun buttonParams() = LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) }
    private fun cardParams() = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) }
    private fun setBusy(busy: Boolean) { if (::progress.isInitialized) progress.visibility = if (busy) View.VISIBLE else View.GONE; if (::refreshButton.isInitialized) refreshButton.isEnabled = !busy }
    private fun openLogin() { startActivity(Intent(this, AuthActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK) }); finish() }
    private fun toast(message: String) = android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
    private fun transparent() = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
    private fun rounded(fill: Int, radius: Int, stroke: Int, strokeWidth: Int): android.graphics.drawable.Drawable = android.graphics.drawable.GradientDrawable().apply { setColor(fill); cornerRadius = radius.toFloat(); if (strokeWidth > 0) setStroke(strokeWidth, stroke) }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
