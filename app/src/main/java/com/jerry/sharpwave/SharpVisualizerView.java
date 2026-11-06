package com.jerry.sharpwave;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
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
    private static final float TWO_TONE_MIN_RED_FRACTION = 0.35f;
    private static final float TWO_TONE_MAX_RED_FRACTION = 0.70f;
    private static final float TWO_TONE_MIN_SEGMENT_PX = 1f;

    private static final int[][] PALETTES = {
            {0xFFFFFFFF, 0xFF9F000E, 0xFFB8B8B8}
    };
    private static final int NOTHING_MINIMAL_BODY = 0xFFD7D7D7;

    private final Paint paint = new Paint();
    private final Object spectrumLock = new Object();
    private byte[] waveform = new byte[0];
    private byte[] waveformPending = new byte[0];
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
    private int studioMode = StudioPreset.BARS;
    private boolean studioColorsActive;
    private int studioPrimary = 0xFFFFFFFF;
    private int studioAccent = 0xFF9F000E;
    private boolean centerBass = true;
    private boolean solidBars = true;
    private int bassGain = 160;
    private volatile float waveformEnergy = 1f;
    private volatile long lastEnergyAt;
    private final Path studioPath = new Path();
    private volatile long lastSpectrumAt;
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

    /** Applies only original PulseWave-owned audio presets; null keeps legacy behavior. */
    public void setStudioScene(StudioPreset scene) {
        if (scene == null || !scene.isAudio()) {
            studioMode = StudioPreset.BARS;
            studioColorsActive = false;
        } else {
            studioMode = scene.type;
            studioColorsActive = true;
            studioPrimary = scene.primary;
            studioAccent = scene.accent;
        }
        invalidate();
    }

    /** Presentation controls are independent of capture and app selection. */
    public void setPresentation(boolean bassInCenter, boolean oneColor, int bassGainPercent) {
        centerBass = bassInCenter;
        solidBars = oneColor;
        bassGain = StudioPreset.clamp(bassGainPercent, 100, 240);
        invalidate();
    }

    /** Identical left/right FFT ordering with bass in the middle. */
    static int mirroredBandIndex(int i, int count) {
        if (count <= 2) return 0;
        int safe = Math.max(0, Math.min(count - 1, i));
        float midpoint = (count - 1) * .5f;
        float minimumDistance = count % 2 == 0 ? .5f : 0f;
        float span = midpoint - minimumDistance;
        if (span <= 0f) return 0;
        float distance = Math.abs(safe - midpoint) - minimumDistance;
        return Math.min(count - 1, Math.max(0,
                Math.round(distance / span * (count - 1))));
    }

    static float waveformRms(byte[] data) {
        if (data == null || data.length == 0) return 0f;
        double energy = 0.0;
        for (int i = 0; i < data.length; i++) {
            int sample = (data[i] & 0xFF) - 128;
            energy += (double) sample * sample;
        }
        return (float) (Math.sqrt(energy / data.length) / 128.0);
    }

    public void setWaveform(byte[] data) {
        updateWaveform(data, true);
    }

    void submitWaveform(byte[] data) {
        updateWaveform(data, false);
    }

    private void updateWaveform(byte[] data, boolean preferWaveform) {
        if (data == null || data.length == 0) return;
        waveformEnergy = waveformRms(data);
        lastEnergyAt = android.os.SystemClock.elapsedRealtime();
        synchronized (spectrumLock) {
            if (waveformPending.length != data.length) waveformPending = new byte[data.length];
            System.arraycopy(data, 0, waveformPending, 0, data.length);
            byte[] swap = waveform;
            waveform = waveformPending;
            waveformPending = swap;
            if (preferWaveform) spectrumMode = false;
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
            pendingSampleRate = samplingRateHz(sampleRate);
            spectrumDirty = true;
            spectrumMode = true;
            lastSpectrumAt = android.os.SystemClock.elapsedRealtime();
        }
        postInvalidateOnAnimation();
    }

    public void clearSpectrum() {
        synchronized (spectrumLock) {
            spectrumPending = new float[0];
            spectrumActive = new float[0];
            pendingBins = 0;
            pendingSampleRate = 0;
            activeBins = 0;
            activeSampleRate = 0;
            waveform = new byte[0];
            waveformPending = new byte[0];
            spectrumDirty = false;
            spectrumMode = false;
            lastSpectrumAt = 0L;
        }
        waveformEnergy = 0f;
        lastEnergyAt = 0L;
        resetResponseState();
        postInvalidateOnAnimation();
    }

    private void resetResponseState() {
        resetResponseLayers(smoothed, rawTargets, blurredTargets);
        lastFrameAt = 0L;
    }

    static void resetResponseLayers(float[]... layers) {
        if (layers == null) return;
        for (float[] layer : layers) {
            if (layer != null) java.util.Arrays.fill(layer, 0f);
        }
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
        boolean recentWaveform = lastEnergyAt > 0L && now >= lastEnergyAt
                && now - lastEnergyAt < 500L;
        float actualLoudness = recentWaveform
                ? Math.min(1f, waveformEnergy * 3.2f)
                : 1f;
        for (int i = 0; i < count; i++) {
            int band = centerBass ? mirroredBandIndex(i, count) : i;
            float frequency = fftReady ? bandCenterFrequency(band, bins, sampleRate)
                    : barFrequency(band, count);
            float bassMultiplier = frequency <= 180f ? bassGain / 100f
                    : frequency < 450f ? 1f + (bassGain / 100f - 1f)
                        * (450f - frequency) / 270f
                    : 1f;
            float signal = fftReady ? fftTargetForBar(band, spectrum, bins)
                    : waveformTargetForBar(band, count, wave);
            rawTargets[i] = Math.min(1f, signal * bassMultiplier
                    * (fftReady ? actualLoudness : 1f));
        }

        for (int i = 0; i < count; i++) {
            int left = i == 0 ? 0 : i - 1;
            int right = i == count - 1 ? count - 1 : i + 1;
            blurredTargets[i] = rawTargets[i] * NEIGHBOR_CENTER
                    + (rawTargets[left] + rawTargets[right]) * NEIGHBOR_SIDE;
        }

        studioPath.reset();
        for (int i = 0; i < count; i++) {
            float target = blurredTargets[i];
            float tau = target > smoothed[i] ? ATTACK_TAU_SECONDS : RELEASE_TAU_SECONDS;
            smoothed[i] = smoothLevel(smoothed[i], target, elapsed, tau * 1000f);

            float x = i * step;
            float h = Math.min(height, Math.max(0f, smoothed[i] * height));
            float top = height - h;
            float bottom = height;
            float right = i == count - 1 ? width : x + barWidth;
            if (right <= x) continue;

            if (studioColorsActive && studioMode == StudioPreset.WAVE) {
                float y = Math.max(1f, height * (0.82f - smoothed[i] * .72f));
                float center = (x + right) * .5f;
                if (i == 0) studioPath.moveTo(center, y);
                else studioPath.lineTo(center, y);
                continue;
            }

            if (studioColorsActive && studioMode == StudioPreset.SPIKES) {
                drawStudioSpike(canvas, x, right, top, bottom, smoothed[i]);
                continue;
            }

            if (solidBars) {
                paint.setColor(studioColorsActive
                        ? studioPrimary : PALETTES[paletteIndex(palette)][0]);
                canvas.drawRect(x, top, right, bottom, paint);
            } else if (isTwoTonePalette()) {
                drawTwoToneBar(canvas, x, right, top, bottom, smoothed[i]);
            } else {
                int band = centerBass ? mirroredBandIndex(i, count) : i;
                float colorFrequency = fftReady
                        ? bandCenterFrequency(band, bins, sampleRate)
                        : barFrequency(band, count);
                paint.setColor(colorFor(colorFrequency));
                canvas.drawRect(x, top, right, bottom, paint);
            }
        }
        if (studioColorsActive && studioMode == StudioPreset.WAVE) {
            boolean antiAlias = paint.isAntiAlias();
            paint.setAntiAlias(true);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(dp(2f), dp(spikeWidthDp) * .5f));
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(studioAccent);
            canvas.drawPath(studioPath, paint);
            paint.setStrokeWidth(dp(1.5f));
            paint.setColor(studioPrimary);
            canvas.drawPath(studioPath, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setAntiAlias(antiAlias);
        }
    }

    private boolean isTwoTonePalette() {
        return studioColorsActive || paletteIndex(palette) == Prefs.PALETTE_DHH_SIGNATURE;
    }

    private void drawStudioSpike(Canvas canvas, float left, float right,
            float top, float bottom, float inputLevel) {
        if (bottom - top <= 0f) return;
        studioPath.reset();
        studioPath.moveTo(left, bottom);
        studioPath.lineTo((left + right) * .5f, top);
        studioPath.lineTo(right, bottom);
        studioPath.close();
        paint.setColor(solidBars ? studioPrimary : studioAccent);
        canvas.drawPath(studioPath, paint);
        if (!solidBars) {
            float seam = top + (bottom - top) * (.40f + .20f * (1f - inputLevel));
            canvas.save();
            canvas.clipRect(left, top, right, seam);
            paint.setColor(studioPrimary);
            canvas.drawPath(studioPath, paint);
            canvas.restore();
        }
    }

    private void drawTwoToneBar(Canvas canvas, float left, float right,
            float top, float bottom, float inputLevel) {
        float height = bottom - top;
        if (height <= 0f) return;
        float radius = Math.min((right - left) * 0.5f, height * 0.5f);
        float redFraction = TWO_TONE_MIN_RED_FRACTION
                + (TWO_TONE_MAX_RED_FRACTION - TWO_TONE_MIN_RED_FRACTION)
                * Math.max(0f, Math.min(1f, inputLevel));
        float minSegment = Math.min(TWO_TONE_MIN_SEGMENT_PX, height * 0.5f);
        float redHeight = Math.max(minSegment, Math.min(height - minSegment,
                height * redFraction));
        float seam = height - redHeight;
        boolean antiAlias = paint.isAntiAlias();
        paint.setAntiAlias(true);

        paint.setColor(studioColorsActive
                ? studioAccent : PALETTES[Prefs.PALETTE_DHH_SIGNATURE][1]);
        canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint);

        int saveCount = canvas.save();
        canvas.clipRect(left, top, right, top + seam);
        paint.setColor(studioColorsActive
                ? studioPrimary : PALETTES[Prefs.PALETTE_DHH_SIGNATURE][0]);
        canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint);
        canvas.restoreToCount(saveCount);
        paint.setAntiAlias(antiAlias);
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
        if (samples == 0) return 0f;
        float rms = (float) Math.sqrt(sumSq / samples);
        float db = 20f * (float) Math.log10(Math.max(rms, 0.0001f) / FFT_SCALE);
        float normalized = (db - FFT_FLOOR_DB) / (FFT_CEIL_DB - FFT_FLOOR_DB);
        if (normalized < 0f) normalized = 0f;
        if (normalized > 1f) normalized = 1f;
        // Preserve quieter transients instead of flattening every band at high sensitivity.
        float scaled = (float) Math.pow(normalized, 1.65f)
                * (sensitivity / 160f);
        return Math.min(1f, Math.max(0f, scaled));
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
        if (wave.length == 0) return 0f;
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
        return Math.max(0f, Math.min(1f, scaled));
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

    static float fftMagnitudeAtBin(byte[] fft, int bin) {
        // Visualizer reserves byte 0 for DC and byte 1 for Nyquist; bins 1+ are pairs.
        if (fft == null || fft.length < 2 || bin < 0 || bin >= fft.length / 2) return 0f;
        if (bin == 0) return Math.abs(fft[0]);
        int offset = bin * 2;
        return (float) Math.hypot(fft[offset], fft[offset + 1]);
    }

    static float fftNyquistMagnitude(byte[] fft) {
        return fft != null && fft.length >= 2 ? Math.abs(fft[1]) : 0f;
    }

    static float fftPeakMagnitude(byte[] fft) {
        if (fft == null || fft.length < 2) return 0f;
        float peak = fftNyquistMagnitude(fft);
        for (int bin = 1; bin < fft.length / 2; bin++) {
            peak = Math.max(peak, fftMagnitudeAtBin(fft, bin));
        }
        return peak;
    }

    static int samplingRateHz(int samplingRate) {
        if (samplingRate <= 0) return 44100;
        return samplingRate >= 1_000_000 ? Math.max(1, samplingRate / 1000) : samplingRate;
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
