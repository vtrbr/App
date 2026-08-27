package com.tedflix.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRulesTest {
    @Test fun `calcula barra de progresso sem completar indevidamente`() {
        assertEquals(50, PlaybackRules.percentage(60_000L, 120_000L))
        assertEquals(99, PlaybackRules.percentage(120_000L, 120_000L))
        assertEquals(0, PlaybackRules.percentage(10_000L, 0L))
    }

    @Test fun `dispara autoplay somente nos ultimos trinta segundos em reproducao`() {
        assertTrue(PlaybackRules.shouldAutoPlay(90_000L, 120_000L, true, false))
        assertTrue(PlaybackRules.shouldAutoPlay(119_999L, 120_000L, true, false))
        assertFalse(PlaybackRules.shouldAutoPlay(89_999L, 120_000L, true, false))
        assertFalse(PlaybackRules.shouldAutoPlay(90_000L, 120_000L, false, false))
        assertFalse(PlaybackRules.shouldAutoPlay(90_000L, 120_000L, true, true))
    }

    @Test fun `converte tempos remotos e duracoes de catalogo`() {
        assertEquals(3_723_000L, PlaybackRules.remoteTimeToMillis("01:02:03"))
        assertEquals(125_000L, PlaybackRules.remoteTimeToMillis("02:05"))
        assertEquals(5_400_000L, PlaybackRules.catalogDurationToMillis("90 min"))
    }
}
