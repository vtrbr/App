package com.tedflix.app.auth

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.media3.common.util.UnstableApi
import com.tedflix.app.TedflixLoading

@UnstableApi
class NotificationActivity : Activity() {
    private lateinit var list: LinearLayout
    private lateinit var loadingPanel: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AuthSession.init(applicationContext)
        if (!AuthSession.hasToken()) { finish(); return }
        setContentView(buildScreen())
        loadNotifications()
    }

    private fun buildScreen(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(5, 6, 9)) }
        val scroll = ScrollView(this).apply { overScrollMode = View.OVER_SCROLL_NEVER }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(18), dp(20), dp(30)) }
        val toolbar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        toolbar.addView(button("‹", 30) { finish() }, LinearLayout.LayoutParams(dp(48), dp(52)))
        toolbar.addView(TextView(this).apply { text = "Notificações"; textSize = 23f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD }, LinearLayout.LayoutParams(0, -2, 1f))
        toolbar.addView(button("↻", 24) { loadNotifications() }, LinearLayout.LayoutParams(dp(48), dp(52)))
        content.addView(toolbar, LinearLayout.LayoutParams(-1, dp(58)).apply { bottomMargin = dp(14) })
        content.addView(TextView(this).apply { text = "Avisos da sua conta"; textSize = 13f; setTextColor(Color.LTGRAY) }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(18) })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(list, LinearLayout.LayoutParams(-1, -2))
        scroll.addView(content)
        root.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        loadingPanel = TedflixLoading.create(this).apply { visibility = View.GONE }
        root.addView(loadingPanel, FrameLayout.LayoutParams(-1, -1))
        return root
    }

    private fun loadNotifications() {
        if (!::list.isInitialized) return
        TedflixLoading.show(loadingPanel, true)
        list.removeAllViews()
        Thread {
            val result = AuthSession.notifications()
            runOnUiThread {
                TedflixLoading.show(loadingPanel, false)
                if (!result.ok) {
                    list.addView(message(result.message.ifBlank { "Não foi possível carregar as notificações." }))
                    return@runOnUiThread
                }
                val notifications = result.value.orEmpty()
                if (notifications.isEmpty()) {
                    list.addView(message("Você está em dia. Nenhuma notificação nova."))
                } else {
                    notifications.forEach { addNotification(it) }
                }
            }
        }.start()
    }

    private fun addNotification(notification: AuthSession.Notification) {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(15))
            background = rounded(if (!notification.read) Color.rgb(43, 22, 25) else Color.rgb(18, 18, 20), dp(14), if (!notification.read) Color.rgb(150, 30, 40) else Color.argb(55, 255, 255, 255), dp(1))
            setOnClickListener {
                if (!notification.read) {
                    Thread { AuthSession.markNotificationRead(notification.id) }.start()
                    background = rounded(Color.rgb(18, 18, 20), dp(14), Color.argb(55, 255, 255, 255), dp(1))
                }
            }
        }
        item.addView(TextView(this).apply { text = notification.title.ifBlank { notification.type.ifBlank { "Aviso" } }; textSize = 16f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD })
        item.addView(TextView(this).apply { text = notification.body.ifBlank { "Sem detalhes" }; textSize = 13f; setTextColor(Color.LTGRAY); setPadding(0, dp(6), 0, 0) })
        if (notification.sentAt.isNotBlank()) item.addView(TextView(this).apply { text = notification.sentAt; textSize = 11f; setTextColor(Color.GRAY); setPadding(0, dp(8), 0, 0) })
        list.addView(item, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
    }

    private fun message(text: String) = TextView(this).apply { this.text = text; textSize = 14f; setTextColor(Color.LTGRAY); gravity = Gravity.CENTER; setPadding(dp(18), dp(40), dp(18), dp(40)); background = rounded(Color.rgb(18, 18, 20), dp(14), Color.argb(55, 255, 255, 255), dp(1)) }
    private fun button(text: String, size: Int, action: () -> Unit) = Button(this).apply { this.text = text; textSize = size.toFloat(); isAllCaps = false; setTextColor(Color.WHITE); background = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT); setOnClickListener { action() } }
    private fun rounded(fill: Int, radius: Int, stroke: Int, width: Int) = android.graphics.drawable.GradientDrawable().apply { setColor(fill); cornerRadius = radius.toFloat(); setStroke(width, stroke) }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
