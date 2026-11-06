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
    int width = 5;
    int gap = 1;
    int height = 92;
    int sensitivity = 160;
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
        p.width = width;
        p.gap = gap;
        p.height = height;
        p.sensitivity = sensitivity;
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
        if (photoUri == null || photoUri.length() > 2048 || !photoUri.startsWith("content://")) {
            photoUri = "";
        }
        primary |= 0xFF000000;
        accent |= 0xFF000000;
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
