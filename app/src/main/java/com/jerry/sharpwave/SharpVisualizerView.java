package com.jerry.sharpwave;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

public class SharpVisualizerView extends View {
    private static final float MIN_LEVEL = 0.025f;
    private static final float ATTACK_TAU_SECONDS = 0.035f;
    private static final float RELEASE_TAU_SECONDS = 0.11f;
    private static final long SPECTRUM_STALE_MS = 700L;
    private static final float NEIGHBOR_CENTER = 0.72f;
    private static final float NEIGHBOR_SIDE = 0.14f;
    private static final float FFT_MIN_HZ = 35f;
    private static final float FFT_MAX_HZ = 16000f;
    private static final float FFT_FLOOR_DB = -58f;
    private static final float FFT_CEIL_DB = -6f;
    private static final float FFT_SCALE = 128f;
    private static final float BASS_CROSSOVER_HZ = 700f;
    private static final float NOTHING_BODY_HZ = 1000f;
    private static final float PRESENCE_CROSSOVER_HZ = 4000f;

    private static final int[][] PALETTES = {
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
    private static final int NOTHING_MINIMAL_BODY = 0xFFD7D7D7;

    private final Paint paint = new Paint();
    private final Object spectrumLock = new Object();
    private byte[] waveform = new byte[0];
    private float[] spectrumPending = new float[0];
    private float[] spectrumActive = new float[0];
    private int pendingBins;
    private int pendingSampleRate;
    private int activeBins;
    private int activeSampleRate;
    private boolean spectrumDirty;
    private boolean spectrumMode;
    private float[] smoothed = new float[0];
    private float[] rawTargets = new float[0];
    private float[] blurredTargets = new float[0];
    private int[] bandStart = new int[0];
    private int[] bandEnd = new int[0];
    private int mappedCount = -1;
    private int mappedBins = -1;
    private int mappedSampleRate = -1;
    private int spikeWidthDp = 5;
    private int gapDp = 1;
    private int sensitivity = 160;
    private int palette = Prefs.PALETTE_DHH_SIGNATURE;
    private long lastSpectrumAt;
    private long lastFrameAt;

    public SharpVisualizerView(Context context) {
        super(context);
        setBackgroundColor(Color.TRANSPARENT);
        setClickable(false);
        setFocusable(false);
    }

    public void setStyle(int spikeWidthDp, int gapDp, int sensitivity, int palette) {
        this.spikeWidthDp = Math.max(2, spikeWidthDp);
        this.gapDp = Math.max(0, gapDp);
        this.sensitivity = Math.max(50, sensitivity);
        this.palette = palette;
        smoothed = new float[0];
        rawTargets = new float[0];
        blurredTargets = new float[0];
        mappedCount = -1;
        invalidate();
    }

    public void setWaveform(byte[] data) {
        if (data == null || data.length == 0) return;
        synchronized (spectrumLock) {
            if (waveform.length != data.length) waveform = new byte[data.length];
            System.arraycopy(data, 0, waveform, 0, data.length);
            spectrumMode = false;
        }
        postInvalidateOnAnimation();
    }

    public void submitSpectrum(float[] spectrum, int sampleRate) {
        if (spectrum == null || spectrum.length < 2) return;
        int bins = spectrum.length;
        synchronized (spectrumLock) {
            if (spectrumPending.length < bins) spectrumPending = new float[bins];
            System.arraycopy(spectrum, 0, spectrumPending, 0, bins);
            pendingBins = bins;
            pendingSampleRate = sampleRate > 0 ? sampleRate : 44100;
            spectrumDirty = true;
            spectrumMode = true;
            lastSpectrumAt = android.os.SystemClock.elapsedRealtime();
        }
        postInvalidateOnAnimation();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;

        float barWidth = Math.max(1f, dp(spikeWidthDp));
        float gap = Math.max(0f, dp(gapDp));
        int count = Math.max(1, (int) Math.ceil((width + gap) / (barWidth + gap)));
        if (gap > 0f) {
            int maxCount = Math.max(1, (int) Math.floor((width + gap) / gap));
            count = Math.min(count, maxCount);
        }
        float step = (width + gap) / count;
        barWidth = Math.max(1f, step - gap);
        ensureRenderArrays(count);

        float[] spectrum;
        byte[] wave;
        int bins;
        int sampleRate;
        boolean useSpectrum;
        synchronized (spectrumLock) {
            if (spectrumDirty) {
                float[] swap = spectrumActive;
                spectrumActive = spectrumPending;
                spectrumPending = swap;
                activeBins = pendingBins;
                activeSampleRate = pendingSampleRate;
                spectrumDirty = false;
            }
            spectrum = spectrumActive;
            wave = waveform;
            bins = activeBins;
            sampleRate = activeSampleRate;
            useSpectrum = spectrumMode;
        }

        long now = android.os.SystemClock.elapsedRealtime();
        long elapsed = lastFrameAt > 0L ? now - lastFrameAt : 16L;
        lastFrameAt = now;
        boolean fftReady = isSpectrumFresh(useSpectrum && bins > 1,
                now, lastSpectrumAt, SPECTRUM_STALE_MS);
        if (fftReady) ensureBandMap(count, bins, sampleRate);
        for (int i = 0; i < count; i++) {
            rawTargets[i] = fftReady
                    ? fftTargetForBar(i, spectrum, bins)
                    : waveformTargetForBar(i, count, wave);
        }

        for (int i = 0; i < count; i++) {
            int left = i == 0 ? 0 : i - 1;
            int right = i == count - 1 ? count - 1 : i + 1;
            blurredTargets[i] = rawTargets[i] * NEIGHBOR_CENTER
                    + (rawTargets[left] + rawTargets[right]) * NEIGHBOR_SIDE;
        }

        for (int i = 0; i < count; i++) {
            float target = blurredTargets[i];
            float tau = target > smoothed[i] ? ATTACK_TAU_SECONDS : RELEASE_TAU_SECONDS;
            smoothed[i] = smoothLevel(smoothed[i], target, elapsed, tau * 1000f);

            float x = i * step;
            float h = Math.max(dp(2), smoothed[i] * height);
            float right = i == count - 1 ? width : x + barWidth;
            if (right <= x) continue;

            float colorFrequency = fftReady
                    ? bandCenterFrequency(i, bins, sampleRate)
                    : barFrequency(i, count);
            paint.setColor(colorFor(colorFrequency));
            canvas.drawRect(x, 0f, right, h, paint);
        }
    }

    private void ensureRenderArrays(int count) {
        if (smoothed.length != count) {
            smoothed = new float[count];
            rawTargets = new float[count];
            blurredTargets = new float[count];
        }
    }

    private void ensureBandMap(int count, int bins, int sampleRate) {
        if (mappedCount == count && mappedBins == bins && mappedSampleRate == sampleRate) return;
        if (bandStart.length < count) {
            bandStart = new int[count];
            bandEnd = new int[count];
        }
        float nyquist = sampleRate * 0.5f;
        float maxHz = Math.min(FFT_MAX_HZ, nyquist * 0.94f);
        float minHz = FFT_MIN_HZ;
        if (maxHz <= minHz * 2f) {
            minHz = Math.max(20f, nyquist * 0.02f);
            maxHz = Math.max(minHz * 2f, nyquist * 0.8f);
        }
        double logMin = Math.log(minHz);
        double logSpan = Math.log(maxHz / minHz);
        float hzPerBin = sampleRate / (bins * 2f);
        for (int i = 0; i < count; i++) {
            double f0 = Math.exp(logMin + logSpan * i / count);
            double f1 = Math.exp(logMin + logSpan * (i + 1) / count);
            int k0 = (int) Math.floor(f0 / hzPerBin);
            int k1 = (int) Math.ceil(f1 / hzPerBin);
            k0 = Math.max(1, Math.min(bins - 1, k0));
            k1 = Math.max(k0 + 1, Math.min(bins, k1));
            bandStart[i] = k0;
            bandEnd[i] = k1;
        }
        mappedCount = count;
        mappedBins = bins;
        mappedSampleRate = sampleRate;
    }

    private float fftTargetForBar(int bar, float[] spectrum, int bins) {
        int start = bandStart[bar];
        int end = Math.min(bins, bandEnd[bar]);
        float sumSq = 0f;
        int samples = 0;
        for (int i = start; i < end; i++) {
            float magnitude = spectrum[i];
            sumSq += magnitude * magnitude;
            samples++;
        }
        if (samples == 0) return MIN_LEVEL;
        float rms = (float) Math.sqrt(sumSq / samples);
        float db = 20f * (float) Math.log10(Math.max(rms, 0.0001f) / FFT_SCALE);
        float normalized = (db - FFT_FLOOR_DB) / (FFT_CEIL_DB - FFT_FLOOR_DB);
        if (normalized < 0f) normalized = 0f;
        if (normalized > 1f) normalized = 1f;
        float scaled = normalized * (sensitivity / 100f);
        if (scaled > 1f) scaled = 1f;
        return MIN_LEVEL + (1f - MIN_LEVEL) * scaled;
    }

    private float bandCenterFrequency(int bar, int bins, int sampleRate) {
        float hzPerBin = sampleRate / (bins * 2f);
        float centerBin = (bandStart[bar] + bandEnd[bar] - 1) * 0.5f;
        return centerBin * hzPerBin;
    }

    private float barFrequency(int bar, int count) {
        float nyquist = 22050f;
        float maxHz = Math.min(FFT_MAX_HZ, nyquist * 0.94f);
        float minHz = FFT_MIN_HZ;
        float position = (bar + 0.5f) / Math.max(1, count);
        return (float) (minHz * Math.pow(maxHz / minHz, position));
    }

    private float waveformTargetForBar(int bar, int count, byte[] wave) {
        if (wave.length == 0) return MIN_LEVEL;
        int start = bar * wave.length / count;
        int end = Math.max(start + 1, (bar + 1) * wave.length / count);
        end = Math.min(wave.length, end);
        float sum = 0f;
        for (int i = start; i < end; i++) {
            int unsigned = wave[i] & 0xFF;
            sum += Math.abs(unsigned - 128) / 128f;
        }
        float avg = sum / Math.max(1, end - start);
        float scaled = avg * (sensitivity / 100f) * 2.15f;
        return Math.max(MIN_LEVEL, Math.min(1f, scaled));
    }

    private int colorFor(float frequency) {
        int preset = paletteIndex(palette);
        if (preset == 7) {
            int[] anchors = PALETTES[preset];
            if (frequency <= BASS_CROSSOVER_HZ) {
                float position = logPosition(frequency, FFT_MIN_HZ, BASS_CROSSOVER_HZ);
                return blend(anchors[0], anchors[1], smoothstep(position));
            }
            if (frequency <= 2200f) {
                float position = logPosition(frequency, BASS_CROSSOVER_HZ, 2200f);
                return blend(anchors[1], NOTHING_MINIMAL_BODY, smoothstep(position));
            }
            if (frequency <= PRESENCE_CROSSOVER_HZ) {
                float position = logPosition(frequency, 2200f, PRESENCE_CROSSOVER_HZ);
                return blend(NOTHING_MINIMAL_BODY, anchors[2], smoothstep(position));
            }
            return anchors[2];
        }
        if (frequency <= BASS_CROSSOVER_HZ) {
            float position = logPosition(frequency, FFT_MIN_HZ, BASS_CROSSOVER_HZ);
            return blend(PALETTES[preset][0], PALETTES[preset][1], smoothstep(position));
        }
        if (frequency <= 9000f) {
            float position = logPosition(frequency, BASS_CROSSOVER_HZ, 9000f);
            return blend(PALETTES[preset][1], PALETTES[preset][2],
                    smoothstep(position));
        }
        return PALETTES[preset][2];
    }

    static float smoothLevel(float current, float target, long elapsedMillis,
            float timeConstantMillis) {
        float alpha = 1f - (float) Math.exp(-Math.max(1L, elapsedMillis)
                / timeConstantMillis);
        return current + (target - current) * alpha;
    }

    static boolean isSpectrumFresh(boolean spectrumAvailable, long now, long lastSpectrumAt,
            long staleAfterMillis) {
        return spectrumAvailable && lastSpectrumAt > 0L
                && now - lastSpectrumAt <= staleAfterMillis;
    }

    static int paletteIndex(int value) {
        return value >= 0 && value < PALETTES.length ? value : 0;
    }

    static int paletteAnchor(int paletteIndex, int anchorIndex) {
        return PALETTES[paletteIndex(paletteIndex)][anchorIndex];
    }

    static int nothingMinimalBody() {
        return NOTHING_MINIMAL_BODY;
    }

    private float logPosition(float frequency, float minHz, float maxHz) {
        return (float) (Math.log(frequency / minHz) / Math.log(maxHz / minHz));
    }

    private float smoothstep(float value) {
        float t = Math.max(0f, Math.min(1f, value));
        return t * t * (3f - 2f * t);
    }

    private int blend(int from, int to, float amount) {
        float t = Math.max(0f, Math.min(1f, amount));
        return Color.rgb(
                Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * t),
                Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * t),
                Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t));
    }
}
