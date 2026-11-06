package com.jerry.sharpwave;

/**
 * Color motion follows captured audio energy. No microphone, network, metadata or
 * additional permission is needed; the existing FFT/levels already drive the bars.
 */
final class MusicColorFlow {
    private float phase;
    private float previousEnergy;
    private long lastFrameMs;

    static float wrap(float value) {
        float result = value - (float) Math.floor(value);
        return result >= 1f ? 0f : result;
    }

    static int blend(int from, int to, float fraction) {
        float t = Math.max(0f, Math.min(1f, fraction));
        int r = Math.round(((from >> 16) & 255) * (1f - t) + ((to >> 16) & 255) * t);
        int g = Math.round(((from >> 8) & 255) * (1f - t) + ((to >> 8) & 255) * t);
        int b = Math.round((from & 255) * (1f - t) + (to & 255) * t);
        int a = Math.round(((from >>> 24) & 255) * (1f - t) + ((to >>> 24) & 255) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Stationary three-color layout: first -> middle -> third across the lines. */
    static int threeColorAt(float position, int first, int middle, int third) {
        float p = Math.max(0f, Math.min(1f, position));
        return p <= .5f
                ? blend(first, middle, p * 2f)
                : blend(middle, third, (p - .5f) * 2f);
    }

    /** Rotating, continuous three-color wheel: third smoothly transitions back to first. */
    static int flowingColorAt(float position, float phase,
            int first, int middle, int third) {
        float p = wrap(position + phase);
        if (p < 1f / 3f) return blend(first, middle, p * 3f);
        if (p < 2f / 3f) return blend(middle, third, (p - 1f / 3f) * 3f);
        return blend(third, first, (p - 2f / 3f) * 3f);
    }

    /**
     * Called once per rendered frame, using ALREADY smoothed FFT visualizer levels.
     * Phase stops moving in silence. A positive onset advances the palette on beats.
     * Does not change the FFT levels, geometry, latency or visualizer position.
     */
    float update(float[] levels, long nowMs, int strengthPercent) {
        if (levels == null || levels.length == 0) return phase;
        float sum = 0f;
        for (float level : levels) {
            float bounded = Math.max(0f, Math.min(1f, level));
            sum += bounded;
        }
        float energy = sum / levels.length;
        if (lastFrameMs == 0L) {
            previousEnergy = energy;
            lastFrameMs = nowMs;
            return phase;
        }
        float seconds = Math.max(0f, Math.min(.08f, (nowMs - lastFrameMs) / 1000f));
        lastFrameMs = nowMs;
        float onset = Math.max(0f, energy - previousEnergy);
        previousEnergy += (energy - previousEnergy) * Math.min(1f, seconds * 6f);
        float strength = Math.max(0f, Math.min(1f, strengthPercent / 100f));
        // Slow brightness-linked drift plus a pulse on rising transients.
        float motion = seconds * .42f * energy + onset * .24f;
        phase = wrap(phase + motion * strength);
        return phase;
    }

    void reset() {
        phase = 0f;
        previousEnergy = 0f;
        lastFrameMs = 0L;
    }
}
