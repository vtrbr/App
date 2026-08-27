package com.tedflix.app

/** Regras sem dependência de UI para continuidade e avanço automático do player. */
object PlaybackRules {
    const val AUTO_PLAY_WINDOW_MS = 30_000L

    fun percentage(positionMs: Long, durationMs: Long): Int {
        if (durationMs <= 0L || positionMs <= 0L) return 0
        return ((positionMs * 100L) / durationMs).toInt().coerceIn(0, 99)
    }

    fun shouldAutoPlay(
        positionMs: Long,
        durationMs: Long,
        isPlaying: Boolean,
        alreadyTriggered: Boolean,
    ): Boolean {
        if (alreadyTriggered || !isPlaying || durationMs <= 0L || positionMs < 0L) return false
        val remaining = (durationMs - positionMs).coerceAtLeast(0L)
        return remaining in 0L..AUTO_PLAY_WINDOW_MS
    }

    fun remoteTimeToMillis(raw: String): Long? {
        val value = raw.trim()
        if (value.isBlank()) return null
        val parts = value.split(":")
        if (parts.size !in 2..3) return value.toLongOrNull()?.times(1_000L)
        return try {
            val numbers = parts.map { it.toLong() }
            val seconds = if (numbers.size == 3) {
                numbers[0] * 3_600L + numbers[1] * 60L + numbers[2]
            } else {
                numbers[0] * 60L + numbers[1]
            }
            seconds.coerceAtLeast(0L) * 1_000L
        } catch (_: Throwable) {
            null
        }
    }

    fun catalogDurationToMillis(value: String): Long {
        val normalized = value.trim().lowercase()
        if (normalized.isBlank()) return 0L
        val numeric = Regex("""(\d+(?:[.,]\d+)?)""").find(normalized)?.groupValues?.getOrNull(1)
            ?.replace(',', '.')?.toDoubleOrNull() ?: return 0L
        return when {
            normalized.contains(":") -> remoteTimeToMillis(normalized) ?: 0L
            normalized.contains("h") -> (numeric * 3_600_000L).toLong()
            else -> (numeric * 60_000L).toLong()
        }
    }
}
