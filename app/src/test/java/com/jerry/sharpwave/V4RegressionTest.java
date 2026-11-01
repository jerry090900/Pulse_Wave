package com.jerry.sharpwave;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class V4RegressionTest {
    @Test
    public void selectedSourceRequiresGlobalAudioOrExistingHold() {
        assertEquals("music.app", OverlayService.resolveSelectedSource(
                "music.app", false, "music.app", true, false, true));
        assertEquals("music.app", OverlayService.resolveSelectedSource(
                "music.app", false, "music.app", false, true, true));
        assertNull(OverlayService.resolveSelectedSource(
                "music.app", false, "music.app", false, false, false));
        assertEquals("music.app", OverlayService.resolveSelectedSource(
                "music.app", false, "music.app", true, true, false));
    }

    @Test
    public void eligibilityDoesNotInventPerAppAttribution() {
        assertEquals("selected.music", OverlayService.resolveSelectedSource(
                "selected.music", false, "selected.music", true, false, false));
        assertFalse(OverlayService.isAudioActivityPresent(0L, 3000L, 2500L, false));
        assertTrue(OverlayService.isAudioActivityPresent(1000L, 3000L, 2500L, false));
        assertTrue(OverlayService.isAudioActivityPresent(0L, 3000L, 2500L, true));
    }

    @Test
    public void stopAndInactivityClearRememberedSelection() {
        assertNull(OverlayService.nextActiveSource(null, null, false, false));
        assertNull(OverlayService.nextActiveSource(null, null, false, true));
        assertEquals("music.app", OverlayService.nextActiveSource(
                "music.app", "music.app", true, false));
    }

    @Test
    public void smoothingUsesElapsedTimeWithAttackAndReleaseRates() {
        float fastAttack = SharpVisualizerView.smoothLevel(0f, 1f, 35L, 35f);
        float slowAttack = SharpVisualizerView.smoothLevel(0f, 1f, 70L, 35f);
        float release = SharpVisualizerView.smoothLevel(1f, 0f, 110L, 110f);
        assertTrue(slowAttack > fastAttack);
        assertEquals((float) Math.exp(-1d), release, 0.0001f);
        assertEquals(1f, SharpVisualizerView.smoothLevel(0f, 1f, 1000L, 35f), 0.0001f);
    }

    @Test
    public void spectrumFallsBackWhenStale() {
        assertTrue(SharpVisualizerView.isSpectrumFresh(true, 1700L, 1000L, 700L));
        assertFalse(SharpVisualizerView.isSpectrumFresh(true, 1701L, 1000L, 700L));
        assertFalse(SharpVisualizerView.isSpectrumFresh(false, 1000L, 1000L, 700L));
        assertTrue(SharpVisualizerView.isSpectrumFresh(true, 900L, 1000L, 700L));
    }

    @Test
    public void allTenPaletteNamesAndNothingMinimalAnchorsRemainFixed() {
        int[][] anchors = {
                {0xFFFFFFFF, 0xFF9F000E, 0xFFB8B8B8},
                {0xFFF4F4F4, 0xFF6E0B14, 0xFF8E8E8E},
                {0xFFEDEDED, 0xFF8B000C, 0xFF666666},
                {0xFFF8F8F8, 0xFFB00012, 0xFF4A4A4A},
                {0xFFFFFFFF, 0xFFBFC5CC, 0xFF7F8B99},
                {0xFFE8E8E8, 0xFF7D0B16, 0xFF7B7B7B},
                {0xFFF7F7F7, 0xFFA3001B, 0xFFB5B8BD},
                {0xFFFFFFFF, 0xFF9F000E, 0xFF9A9A9A},
                {0xFFCFCFCF, 0xFF640912, 0xFF555555},
                {0xFFE5E7EA, 0xFF721521, 0xFF727A84}
        };
        assertEquals(10, Prefs.PALETTE_NAMES.length);
        for (int i = 0; i < Prefs.PALETTE_COUNT; i++) {
            assertEquals(i, SharpVisualizerView.paletteIndex(i));
            for (int anchor = 0; anchor < 3; anchor++) {
                assertEquals(anchors[i][anchor], SharpVisualizerView.paletteAnchor(i, anchor));
            }
        }
        assertEquals(0, SharpVisualizerView.paletteIndex(-1));
        assertEquals(0, SharpVisualizerView.paletteIndex(10));
        assertEquals(0xFFFFFFFF, SharpVisualizerView.paletteAnchor(7, 0));
        assertEquals(0xFF9F000E, SharpVisualizerView.paletteAnchor(7, 1));
        assertEquals(0xFFD7D7D7, SharpVisualizerView.nothingMinimalBody());
        assertEquals(0xFF9A9A9A, SharpVisualizerView.paletteAnchor(7, 2));
    }
}
