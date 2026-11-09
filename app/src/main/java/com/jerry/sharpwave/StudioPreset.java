package com.jerry.sharpwave;

import java.util.UUID;

/** User-owned scene data; this class deliberately has no Android framework dependencies. */
final class StudioPreset {
    static final int BARS = 0;
    static final int SPIKES = 1;
    static final int WAVE = 2;
    static final int PHOTO = 3;
    static final int RING = 4;

    String id;
    String name;
    int type;
    int primary = 0xFFFFFFFF;
    int accent = 0xFF9F000E;
    int third = 0xFF19CFFA;
    // -1 preserves original one/two-color presets on upgrades.
    int colorMode = -1;
    int flowStrength = 80;
    // Three side-by-side audio lines: fixed A / B / C identities.
    int lineSpacing = 1;
    // Color-stack mode: A/B/C share one bar; split points shift per FFT band.
    int layerASplit = 33;
    int layerBSplit = 68;
    int layerMotion = 85;
    // Optional Wave-only appearance controls. Defaults reproduce the old Wave.
    int waveBaseline = 82;     // vertical baseline, percent of overlay height
    int waveAmplitude = 100;   // percent of the original 72%-height response
    int waveStroke = 0;        // 0 = original automatic stroke width
    int waveSmoothness = 0;    // 0 = original sharp path, 100 = curved
    int waveGlow = 0;          // 0 = none
    int waveOpacity = 100;
    int width = 5;
    int gap = 1;
    int height = 92;
    int sensitivity = 160;
    boolean twoTone = true; // Built-in and user-created Studio styles default to dual color.
    int ringSize = 108;
    int offsetY = 65; // percent of preview height
    int opacity = 100;
    String photoUri = "";

    static StudioPreset template(String id, String name, int type) {
        StudioPreset p = new StudioPreset();
        p.id = id;
        p.name = name;
        p.type = clamp(type, BARS, RING);
        return p;
    }

    StudioPreset copy() {
        StudioPreset p = new StudioPreset();
        p.id = id;
        p.name = name;
        p.type = type;
        p.primary = primary;
        p.accent = accent;
        p.third = third;
        p.colorMode = colorMode;
        p.flowStrength = flowStrength;
        p.lineSpacing = lineSpacing;
        p.layerASplit = layerASplit;
        p.layerBSplit = layerBSplit;
        p.layerMotion = layerMotion;
        p.waveBaseline = waveBaseline;
        p.waveAmplitude = waveAmplitude;
        p.waveStroke = waveStroke;
        p.waveSmoothness = waveSmoothness;
        p.waveGlow = waveGlow;
        p.waveOpacity = waveOpacity;
        p.width = width;
        p.gap = gap;
        p.height = height;
        p.sensitivity = sensitivity;
        p.twoTone = twoTone;
        p.ringSize = ringSize;
        p.offsetY = offsetY;
        p.opacity = opacity;
        p.photoUri = photoUri;
        return p;
    }

    StudioPreset duplicate() {
        StudioPreset p = copy();
        p.id = UUID.randomUUID().toString();
        p.name = safeName(name + " copy");
        return p;
    }

    static final int COLOR_SOLID = 0;
    static final int COLOR_TWO = 1;
    static final int COLOR_THREE = 2;
    static final int COLOR_MUSIC_FLOW = 3;
    static final int COLOR_STACKED = 4; // Color segments share EXACT same x slot

    int effectiveColorMode() {
        return colorMode < 0 ? (twoTone ? COLOR_TWO : COLOR_SOLID)
                : clamp(colorMode, COLOR_SOLID, COLOR_STACKED);
    }

    void setColorMode(int mode) {
        colorMode = clamp(mode, COLOR_SOLID, COLOR_STACKED);
        twoTone = colorMode != COLOR_SOLID; // Compatibility with previous presets.
    }

    boolean isAudio() {
        return type == BARS || type == SPIKES || type == WAVE;
    }

    String category() {
        switch (type) {
            case BARS: return "Bars";
            case SPIKES: return "Spikes";
            case WAVE: return "Wave";
            case PHOTO: return "Photo AOD";
            case RING: return "Fingerprint FX";
            default: return "Bars";
        }
    }

    void normalize() {
        if (id == null || id.isEmpty() || id.length() > 90) {
            id = UUID.randomUUID().toString();
        }
        name = safeName(name);
        type = clamp(type, BARS, RING);
        width = clamp(width, 2, 18);
        gap = clamp(gap, 0, 10);
        height = clamp(height, 40, 220);
        sensitivity = clamp(sensitivity, 60, 260);
        ringSize = clamp(ringSize, 50, 220);
        offsetY = clamp(offsetY, 15, 90);
        opacity = clamp(opacity, 15, 100);
        flowStrength = clamp(flowStrength, 0, 100);
        lineSpacing = clamp(lineSpacing, 0, 4);
        layerASplit = clamp(layerASplit, 5, 70);
        layerBSplit = clamp(layerBSplit, layerASplit + 5, 95);
        layerMotion = clamp(layerMotion, 0, 100);
        waveBaseline = clamp(waveBaseline, 10, 95);
        waveAmplitude = clamp(waveAmplitude, 10, 180);
        waveStroke = clamp(waveStroke, 0, 18);
        waveSmoothness = clamp(waveSmoothness, 0, 100);
        waveGlow = clamp(waveGlow, 0, 100);
        waveOpacity = clamp(waveOpacity, 10, 100);
        if (colorMode >= 0) setColorMode(colorMode);
        if (photoUri == null || photoUri.length() > 2048 || !photoUri.startsWith("content://")) {
            photoUri = "";
        }
        primary |= 0xFF000000;
        accent |= 0xFF000000;
        third |= 0xFF000000;
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    static String safeName(String name) {
        if (name == null) return "Untitled style";
        String cleaned = name.trim().replaceAll("[\\p{Cntrl}]", "");
        if (cleaned.isEmpty()) return "Untitled style";
        return cleaned.substring(0, Math.min(45, cleaned.length()));
    }
}
