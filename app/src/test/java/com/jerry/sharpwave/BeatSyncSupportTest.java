package com.jerry.sharpwave;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class BeatSyncSupportTest {
    @Test public void disabledSupportReturnsOriginalAudioLevelsExactly() {
        BeatSyncSupport support = new BeatSyncSupport();
        float[] original = {.1f, .32f, .84f, .2f, 0f};
        assertSame(original, support.enhance(original, 1000L,
                false, true, BeatSyncSupport.PUNCHY, true));
        assertEquals(.84f, original[2], .000001f);
        assertSame(original, support.enhance(original, 2000L,
                false, false, BeatSyncSupport.SMOOTH, true));
    }

    @Test public void enabledSupportEmphasizesSuddenBassWithoutMutatingCapture() {
        BeatSyncSupport soft = new BeatSyncSupport();
        BeatSyncSupport punch = new BeatSyncSupport();
        float[] baseline = {.12f, .12f, .12f, .12f, .12f};
        float[] hit = {.12f, .12f, .45f, .12f, .12f};
        soft.enhance(baseline, 1000L, true, false, BeatSyncSupport.SMOOTH, true);
        punch.enhance(baseline, 1000L, true, false, BeatSyncSupport.PUNCHY, true);
        float softResult = soft.enhance(hit, 1016L, true, false,
                BeatSyncSupport.SMOOTH, true)[2];
        float punchResult = punch.enhance(hit, 1016L, true, false,
                BeatSyncSupport.PUNCHY, true)[2];
        assertTrue(punchResult > softResult);
        assertTrue(softResult >= hit[2]);
        assertEquals(.45f, hit[2], .000001f);
    }

    @Test public void adaptiveGainIsOptInAndCannotCreateMusicFromSilence() {
        BeatSyncSupport support = new BeatSyncSupport();
        float[] quiet = {.1f, .1f, .1f, .1f, .1f};
        float[] output = support.enhance(quiet, 1000L, true, true,
                BeatSyncSupport.BALANCED, true);
        assertTrue(output[2] > quiet[2]);
        assertTrue(output[2] <= 1f);

        float[] silence = {0f, 0f, 0f, 0f, 0f};
        BeatSyncSupport fresh = new BeatSyncSupport();
        float[] still = fresh.enhance(silence, 1000L, true, true,
                BeatSyncSupport.BALANCED, true);
        assertEquals(0f, still[2], .000001f);
    }

    @Test public void bassLocationRespectsCenterBassSetting() {
        float[] frequencies = {.9f, .1f, .1f, .1f, .1f, .1f, .1f, .1f, .1f};
        assertTrue(BeatSyncSupport.bassEnergy(frequencies, false)
                > BeatSyncSupport.bassEnergy(frequencies, true));
    }
}
