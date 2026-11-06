package com.jerry.sharpwave;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Pure response-mapping checks independent of device or audio permissions. */
public final class VisualizerLayoutRegressionTest {
    @Test
    public void evenBandCountMirrorsBassAtTheCenter() {
        int[] expected = {5, 3, 0, 0, 3, 5};
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], SharpVisualizerView.mirroredBandIndex(i, 6));
        }
    }

    @Test
    public void oddBandCountMirrorsExactly() {
        int[] expected = {4, 2, 0, 2, 4};
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], SharpVisualizerView.mirroredBandIndex(i, 5));
        }
        assertEquals(0, SharpVisualizerView.mirroredBandIndex(0, 1));
        assertEquals(0, SharpVisualizerView.mirroredBandIndex(1, 2));
    }

    @Test
    public void waveformEnergyIsZeroOnDigitalSilenceAndNonzeroOnAudio() {
        assertEquals(0f, SharpVisualizerView.waveformRms(new byte[]{
                (byte) 128, (byte) 128, (byte) 128}), 0.0001f);
        assertEquals(1f, SharpVisualizerView.waveformRms(
                new byte[]{0, 0, 0, 0}), 0.0001f);
        assertEquals(0f, SharpVisualizerView.waveformRms(new byte[0]), 0.0001f);
    }

    @Test
    public void lockScreenContinuationIsBoundedAndRequiresSelectedActiveSource() {
        long max = 120000L;
        assertTrue(OverlayService.shouldKeepSelectedOnLock(
                true, true, true, true, true, 40000L, max));
        assertFalse(OverlayService.shouldKeepSelectedOnLock(
                true, true, true, true, true, 120001L, max));
        assertFalse(OverlayService.shouldKeepSelectedOnLock(
                false, true, true, true, true, 300L, max));
        assertFalse(OverlayService.shouldKeepSelectedOnLock(
                true, true, true, false, true, 300L, max));
        assertFalse(OverlayService.shouldKeepSelectedOnLock(
                true, true, true, true, false, 300L, max));
        assertFalse(OverlayService.shouldKeepSelectedOnLock(
                true, false, true, true, true, 300L, max));
        assertFalse(OverlayService.shouldKeepSelectedOnLock(
                true, true, false, true, true, 300L, max));
        assertFalse(OverlayService.shouldKeepSelectedOnLock(
                true, true, true, true, true, -1L, max));
    }
}
