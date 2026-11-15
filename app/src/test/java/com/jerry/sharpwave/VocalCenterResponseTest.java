package com.jerry.sharpwave;

import org.junit.Test;
import static org.junit.Assert.*;

public class VocalCenterResponseTest {
    @Test public void vocalCenterWeightIsSymmetricAndFadesAtEdges() {
        assertEquals(SharpVisualizerView.vocalCenterWeight(10, 21),
                SharpVisualizerView.vocalCenterWeight(10, 21), 0.0001f);
        assertEquals(SharpVisualizerView.vocalCenterWeight(6, 21),
                SharpVisualizerView.vocalCenterWeight(14, 21), 0.0001f);
        assertEquals(1f, SharpVisualizerView.vocalCenterWeight(10, 21), 0.0001f);
        assertEquals(0f, SharpVisualizerView.vocalCenterWeight(0, 21), 0.0001f);
        assertEquals(0f, SharpVisualizerView.vocalCenterWeight(20, 21), 0.0001f);
        assertEquals(1f, SharpVisualizerView.vocalCenterWeight(0, 1), 0.0001f);
    }

    @Test public void vocalRangeIncludesMidrangeAndExcludesBassAndTreble() {
        float[] bassAndTreble = new float[512];
        bassAndTreble[2] = 90f;    // ~86 Hz at 44.1 kHz
        bassAndTreble[120] = 90f;  // ~5.2 kHz
        assertEquals(0f, SharpVisualizerView.vocalRangeRms(bassAndTreble, 44100),
                0.0001f);

        float[] midrange = new float[512];
        midrange[25] = 90f;  // ~1.08 kHz
        assertTrue(SharpVisualizerView.vocalRangeRms(midrange, 44100) > 0f);
    }

    @Test public void missingSpectrumIsSafe() {
        assertEquals(0f, SharpVisualizerView.vocalRangeRms(null, 44100), 0f);
        assertEquals(0f, SharpVisualizerView.vocalRangeRms(new float[0], 44100), 0f);
        assertEquals(0f, SharpVisualizerView.vocalRangeRms(new float[10], 0), 0f);
    }
}
