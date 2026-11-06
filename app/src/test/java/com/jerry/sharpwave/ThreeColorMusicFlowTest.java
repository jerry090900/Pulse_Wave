package com.jerry.sharpwave;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ThreeColorMusicFlowTest {
    @Test public void threeColorLinesHitAllThreeSwatches() {
        int red = 0xFFFF0000, green = 0xFF00FF00, blue = 0xFF0000FF;
        assertEquals(red, MusicColorFlow.threeColorAt(0f, red, green, blue));
        assertEquals(green, MusicColorFlow.threeColorAt(.5f, red, green, blue));
        assertEquals(blue, MusicColorFlow.threeColorAt(1f, red, green, blue));
        assertEquals(0xFF808000, MusicColorFlow.threeColorAt(.25f, red, green, blue));
        assertEquals(red, StudioAudioRenderer.colorForLine(0f, red, green, blue,
                StudioPreset.COLOR_THREE, .5f)); // static ignores animation phase
    }

    @Test public void flowingColorsJoinSeamlessly() {
        int a = 0xFFF2F2F2, b = 0xFF9F000E, c = 0xFF19CFFA;
        assertEquals(MusicColorFlow.flowingColorAt(0f, .2f, a, b, c),
                MusicColorFlow.flowingColorAt(1f, .2f, a, b, c));
        assertEquals(a, MusicColorFlow.flowingColorAt(0f, 0f, a, b, c));
        assertEquals(b, MusicColorFlow.flowingColorAt(1f / 3f, 0f, a, b, c));
        assertEquals(c, MusicColorFlow.flowingColorAt(2f / 3f, 0f, a, b, c));
    }

    @Test public void silenceDoesNotShiftPalette() {
        MusicColorFlow flow = new MusicColorFlow();
        float first = flow.update(new float[]{0f, 0f, 0f}, 1000L, 100);
        float second = flow.update(new float[]{0f, 0f, 0f}, 1500L, 100);
        assertEquals(first, second, 0.000001f);
        flow.update(new float[]{.75f, .65f, .80f}, 1550L, 100);
        float peak = flow.update(new float[]{.75f, .65f, .80f}, 1600L, 100);
        assertTrue(peak > second);
        flow.update(new float[]{0f, 0f, 0f}, 1650L, 100);
        float after = flow.update(new float[]{0f, 0f, 0f}, 1700L, 100);
        assertEquals(after, flow.update(new float[]{0f, 0f, 0f}, 1800L, 100), 0.000001f);
    }

    @Test public void strengthZeroKeepsColorsStillOnBeats() {
        MusicColorFlow flow = new MusicColorFlow();
        float initial = flow.update(new float[]{0f, 0f}, 1000L, 0);
        assertEquals(initial, flow.update(new float[]{1f, 1f}, 1200L, 0), .000001f);
    }

    @Test public void previousPresetsRetainOneOrTwoColorSelection() {
        StudioPreset old = StudioPreset.template("old", "Original", StudioPreset.WAVE);
        assertEquals(StudioPreset.COLOR_TWO, old.effectiveColorMode());
        old.twoTone = false;
        assertEquals(StudioPreset.COLOR_SOLID, old.effectiveColorMode());
        old.setColorMode(StudioPreset.COLOR_MUSIC_FLOW);
        old.third = 0xFFABC123;
        old.flowStrength = 88;
        StudioPreset copy = old.duplicate();
        assertEquals(StudioPreset.COLOR_MUSIC_FLOW, copy.effectiveColorMode());
        assertEquals(0xFFABC123, copy.third);
        assertEquals(88, copy.flowStrength);
        old.flowStrength = 999;
        old.normalize();
        assertEquals(100, old.flowStrength);
    }
}
