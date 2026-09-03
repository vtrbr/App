package com.tedflix.app

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val activePlayer = sharedPlayer ?: return
        mediaSession = MediaSession.Builder(this, activePlayer)
            .setSessionActivity(createSessionIntent())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        sharedPlayer?.stop()
        sharedPlayer?.release()
        sharedPlayer = null
        mediaSession?.release()
        mediaSession = null
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    private fun createSessionIntent(): PendingIntent {
        val intent = sessionIntent ?: Intent(this, PlayerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            this,
            SESSION_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        @Volatile var sharedPlayer: Player? = null
        @Volatile var sessionIntent: Intent? = null
        private const val SESSION_REQUEST_CODE = 4102
    }
}
