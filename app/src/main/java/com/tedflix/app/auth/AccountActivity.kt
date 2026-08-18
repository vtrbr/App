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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.media3.common.util.UnstableApi
import com.tedflix.app.R
import java.util.Locale

@UnstableApi
class AccountActivity : Activity() {
    private lateinit var content: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var notificationBox: LinearLayout
    private lateinit var refreshButton: Button
    private lateinit var usernameInput: EditText
    private lateinit var currentPasswordInput: EditText
    private lateinit var newPasswordInput: EditText
    private lateinit var progress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AuthSession.init(applicationContext)
        if (!AuthSession.hasToken()) {
            openLogin()
            return
        }
        setContentView(buildScreen())
        refresh()
    }

    private fun buildScreen(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(5, 6, 9)) }
        val scroll = ScrollView(this).apply { overScrollMode = View.OVER_SCROLL_NEVER }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(28))
        }
        val toolbar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
        }
        toolbar.addView(Button(this).apply {
            text = "‹"
            textSize = 30f
            setTextColor(Color.WHITE)
            setAllCaps(false)
            background = transparent()
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(52), dp(52)))
        toolbar.addView(TextView(this).apply {
            text = "Minha conta"
            textSize = 23f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, -2, 1f))
        refreshButton = Button(this).apply {
            text = "Atualizar"
            textSize = 12f
            setAllCaps(false)
            setTextColor(Color.WHITE)
            background = rounded(Color.argb(40, 255, 255, 255), dp(8), Color.argb(80, 255, 255, 255), dp(1))
            setOnClickListener { refresh() }
        }
        toolbar.addView(refreshButton, LinearLayout.LayoutParams(dp(92), dp(42)))
        content.addView(toolbar, LinearLayout.LayoutParams(-1, dp(58)).apply { bottomMargin = dp(12) })

        content.addView(ImageView(this).apply {
            setImageResource(R.drawable.tedflix_auth_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(-1, dp(58)).apply { bottomMargin = dp(16) })

        statusText = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.LTGRAY)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = rounded(Color.argb(30, 255, 255, 255), dp(10), Color.argb(55, 255, 255, 255), dp(1))
        }
        content.addView(statusText, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })

        val profileBox = card()
        profileBox.addView(sectionTitle("Perfil"))
        usernameInput = input("Nome de usuário")
        profileBox.addView(usernameInput, inputParams())
        profileBox.addView(actionButton("Salvar nome") { saveUsername() }, buttonParams())
        content.addView(profileBox, cardParams())

        val passwordBox = card()
        passwordBox.addView(sectionTitle("Senha"))
        currentPasswordInput = input("Senha atual")
        currentPasswordInput.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        newPasswordInput = input("Nova senha")
        newPasswordInput.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        passwordBox.addView(currentPasswordInput, inputParams())
        passwordBox.addView(newPasswordInput, inputParams())
        passwordBox.addView(actionButton("Alterar senha") { changePassword() }, buttonParams())
        content.addView(passwordBox, cardParams())

        val notificationsCard = card()
        notificationsCard.addView(sectionTitle("Notificações"))
        notificationBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        notificationsCard.addView(notificationBox, LinearLayout.LayoutParams(-1, -2))
        content.addView(notificationsCard, cardParams())

        val logout = actionButton("Sair da conta") { logout() }.apply {
            background = rounded(Color.rgb(80, 15, 20), dp(10), Color.rgb(229, 9, 20), dp(1))
        }
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

    private fun refresh() {
        setBusy(true)
        Thread {
            val profile = AuthSession.profile()
            val status = AuthSession.status()
            val notifications = AuthSession.notifications()
            runOnUiThread {
                setBusy(false)
                if (!profile.ok && profile.statusCode == 401) {
                    openLogin()
                    return@runOnUiThread
                }
                val user = profile.value ?: AuthSession.cachedUser()
                usernameInput.setText(user?.username.orEmpty())
                val statusJson = status.value
                statusText.text = buildStatus(user, statusJson, profile.message)
                notificationBox.removeAllViews()
                if (notifications.ok && !notifications.value.isNullOrEmpty()) {
                    notifications.value.orEmpty().forEach { addNotification(it) }
                } else {
                    notificationBox.addView(TextView(this).apply {
                        text = notifications.message.ifBlank { "Nenhuma notificação." }
                        textSize = 14f
                        setTextColor(Color.LTGRAY)
                        setPadding(0, dp(8), 0, dp(8))
                    })
                }
            }
        }.start()
    }

    private fun buildStatus(user: AuthSession.User?, status: org.json.JSONObject?, error: String): String {
        val json = status ?: org.json.JSONObject()
        val accountStatus = json.optString("status").ifBlank { user?.accountStatus.orEmpty() }.ifBlank { "não informado" }
        val expires = json.optString("expiresAt").ifBlank { json.optString("accountExpiresAt") }.ifBlank { user?.expiresAt.orEmpty() }.ifBlank { "não informada" }
        val days = if (json.has("daysRemaining")) json.optInt("daysRemaining") else user?.daysRemaining
        return buildString {
            append("Status: ").append(accountStatus)
            append("\nValidade: ").append(expires)
            if (days != null) append("\nDias restantes: ").append(days)
            if (error.isNotBlank()) append("\n\n").append(error)
        }
    }

    private fun addNotification(notification: AuthSession.Notification) {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(8))
            if (!notification.read) setBackgroundColor(Color.argb(25, 229, 9, 20))
            setOnClickListener {
                if (!notification.read) {
                    Thread { AuthSession.markNotificationRead(notification.id) }.start()
                    alpha = .65f
                }
            }
        }
        item.addView(TextView(this).apply {
            text = notification.title.ifBlank { notification.type.ifBlank { "Aviso" } }
            textSize = 15f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        })
        item.addView(TextView(this).apply {
            text = notification.body.ifBlank { notification.sentAt }
            textSize = 13f
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(3), 0, 0)
        })
        notificationBox.addView(item, LinearLayout.LayoutParams(-1, -2))
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
                if (result.ok) refresh()
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
                if (result.ok) {
                    currentPasswordInput.text.clear()
                    newPasswordInput.text.clear()
                }
            }
        }.start()
    }

    private fun logout() {
        setBusy(true)
        Thread {
            AuthSession.logout()
            runOnUiThread {
                setBusy(false)
                openLogin()
            }
        }.start()
    }

    private fun openLogin() {
        startActivity(Intent(this, AuthActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        finish()
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = rounded(Color.rgb(18, 18, 20), dp(12), Color.argb(55, 255, 255, 255), dp(1))
    }

    private fun sectionTitle(text: String) = TextView(this).apply {
        this.text = text
        textSize = 18f
        setTextColor(Color.WHITE)
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, 0, 0, dp(12))
    }

    private fun input(hint: String) = EditText(this).apply {
        this.hint = hint
        textSize = 15f
        setSingleLine(true)
        setTextColor(Color.WHITE)
        setHintTextColor(Color.GRAY)
        setPadding(dp(12), 0, dp(12), 0)
        background = rounded(Color.rgb(9, 9, 10), dp(8), Color.argb(70, 255, 255, 255), dp(1))
    }

    private fun actionButton(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        textSize = 14f
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = rounded(Color.rgb(229, 9, 20), dp(9), Color.TRANSPARENT, 0)
        setOnClickListener { action() }
    }

    private fun inputParams() = LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(10) }
    private fun buttonParams() = LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(2) }
    private fun cardParams() = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) }

    private fun setBusy(busy: Boolean) {
        if (!::progress.isInitialized) return
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        refreshButton.isEnabled = !busy
    }

    private fun toast(message: String) = android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
    private fun transparent() = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
    private fun rounded(fill: Int, radius: Int, stroke: Int, strokeWidth: Int): android.graphics.drawable.Drawable =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(fill)
            cornerRadius = radius.toFloat()
            if (strokeWidth > 0) setStroke(strokeWidth, stroke)
        }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
