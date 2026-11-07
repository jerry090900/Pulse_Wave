package com.jerry.sharpwave;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Only rendering coordinates and colors change; FFT capture, mapping, smoothing,
 * music timing, and the Wave's path geometry stay exactly as before.
 */
public final class LegacyBarDirectionAndColorTest {
    @Test public void topBarsStartAtTopAndExtendDownward() {
        assertEquals(0f, StudioAudioRenderer.barTopY(100f, .25f, true), .00001f);
        assertEquals(25f, StudioAudioRenderer.barBottomY(100f, .25f, true), .00001f);
        assertEquals(0f, StudioAudioRenderer.barTopY(100f, 1f, true), .00001f);
        assertEquals(100f, StudioAudioRenderer.barBottomY(100f, 1f, true), .00001f);
    }

    @Test public void middleAndBottomBarsPreserveUpwardGeometry() {
        assertEquals(75f, StudioAudioRenderer.barTopY(100f, .25f, false), .00001f);
        assertEquals(100f, StudioAudioRenderer.barBottomY(100f, .25f, false), .00001f);
        assertEquals(100f, StudioAudioRenderer.barTopY(100f, 0f, false), .00001f);
        assertEquals(0f, StudioAudioRenderer.barBottomY(100f, 0f, true), .00001f);
    }

    @Test public void waveFollowsChosenScreenPositionButKeepsOriginalShape() {
        StudioPreset wave = StudioPreset.template("wave", "Wave", StudioPreset.WAVE);
        StudioPreset bars = StudioPreset.template("bars", "Bars", StudioPreset.BARS);
        for (int requested = Prefs.POSITION_TOP; requested <= Prefs.POSITION_BOTTOM;
                requested++) {
            assertEquals(requested,
                    OverlayService.overlayPositionForScene(requested, wave));
            assertEquals(requested,
                    OverlayService.overlayPositionForScene(requested, bars));
            assertEquals(requested,
                    OverlayService.overlayPositionForScene(requested, null));
        }
        assertEquals(82f, StudioAudioRenderer.waveY(100f, 0f), .00001f);
        assertEquals(10f, StudioAudioRenderer.waveY(100f, 1f), .00001f);
    }

    @Test public void threeColorsAreAnchoredToFullBarAreaNotStretchedPerBar() {
        int cyan = 0xFF00DDFF;
        int white = 0xFFFFFFFF;
        int pink = 0xFFFF55A2;
        int[] stops = StudioAudioRenderer.heightStops(
                cyan, white, pink, StudioPreset.COLOR_THREE, .76f);
        assertEquals(3, stops.length);
        assertEquals(cyan, stops[0]);
        assertEquals(white, stops[1]);
        assertEquals(pink, stops[2]);
        // At one-fifth of FULL overlay height the global gradient still
        // emphasizes the first swatch. Only long bars reach the last swatch.
        int shortBarColor = MusicColorFlow.threeColorAt(.2f, cyan, white, pink);
        assertNotEquals(pink, shortBarColor);
        assertNotEquals(white, shortBarColor);
        assertEquals(white, MusicColorFlow.threeColorAt(.5f, cyan, white, pink));
        assertEquals(pink, MusicColorFlow.threeColorAt(1f, cyan, white, pink));
    }

    @Test public void musicFlowGentlyMovesGlobalColorsButNotBarHeights() {
        int cyan = 0xFF00DDFF;
        int white = 0xFFFFFFFF;
        int pink = 0xFFFF55A2;
        int[] still = StudioAudioRenderer.heightStops(
                cyan, white, pink, StudioPreset.COLOR_MUSIC_FLOW, 0f);
        int[] moving = StudioAudioRenderer.heightStops(
                cyan, white, pink, StudioPreset.COLOR_MUSIC_FLOW, .25f);
        int[] cycle = StudioAudioRenderer.heightStops(
                cyan, white, pink, StudioPreset.COLOR_MUSIC_FLOW, 1f);
        assertEquals(17, still.length);
        assertEquals(17, moving.length);
        assertEquals(cyan, still[0]);
        assertEquals(white, still[8]);
        assertEquals(pink, still[16]);
        assertNotEquals(still[8], moving[8]);
        assertEquals(still[8], cycle[8]); // No visible palette jump on wrap.
        assertEquals(25f, StudioAudioRenderer.barBottomY(100f, .25f, true), .00001f);
        assertEquals(75f, StudioAudioRenderer.barTopY(100f, .25f, false), .00001f);
        assertTrue(MusicColorFlow.wrap(1f) == 0f);
    }
}
