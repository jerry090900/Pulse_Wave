package com.jerry.sharpwave;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

final class Prefs {
    static final String NAME = "sharpwave";
    static final String KEY_APPS = "selected_apps";
    static final String KEY_SPIKE_WIDTH = "spike_width";
    static final String KEY_GAP = "gap";
    static final String KEY_HEIGHT = "height";
    static final String KEY_SENSITIVITY = "sensitivity";
    static final String KEY_PALETTE = "palette";
    static final int PALETTE_DHH_SIGNATURE = 0;
    // One built-in color theme. User-created Studio presets remain untouched.
    static final int PALETTE_COUNT = 1;
    static final String[] PALETTE_NAMES = {"DHH Signature"};
    static final String KEY_LOCK_SCREEN = "lock_screen_experimental";
    // New defaults affect presentation only. Legacy prefs remain unchanged for existing installs.
    static final String KEY_OVERLAY_POSITION = "overlay_position";
    static final int POSITION_TOP = 0;
    static final int POSITION_CENTER = 1;
    static final int POSITION_BOTTOM = 2;
    static final String KEY_CENTER_BASS = "center_bass";
    static final String KEY_SINGLE_COLOR = "single_color_bars";
    static final String KEY_BASS_GAIN = "bass_gain";
    static final String KEY_AS_PLAYED = "capture_as_played";
    // Optional, user-enabled media-session detection: no notification contents collected.
    static final String KEY_MEDIA_SESSION_DETECTION = "media_session_detection";

    private Prefs() {}

    static SharedPreferences get(Context context) {
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    static Set<String> selectedApps(Context context) {
        Set<String> value = get(context).getStringSet(KEY_APPS, Collections.emptySet());
        return new HashSet<>(value == null ? Collections.emptySet() : value);
    }

    static void saveSelectedApps(Context context, Set<String> apps) {
        get(context).edit().putStringSet(KEY_APPS, new HashSet<>(apps)).apply();
    }

    static int spikeWidth(Context c) { return get(c).getInt(KEY_SPIKE_WIDTH, 5); }
    static int gap(Context c) { return get(c).getInt(KEY_GAP, 1); }
    static int height(Context c) { return get(c).getInt(KEY_HEIGHT, 92); }
    static int sensitivity(Context c) { return get(c).getInt(KEY_SENSITIVITY, 160); }
    static int palette(Context c) {
        int value = get(c).getInt(KEY_PALETTE, PALETTE_DHH_SIGNATURE);
        return value >= 0 && value < PALETTE_COUNT ? value : PALETTE_DHH_SIGNATURE;
    }
    static boolean lockScreenEnabled(Context c) { return get(c).getBoolean(KEY_LOCK_SCREEN, false); }

    static int overlayPosition(Context c) {
        int p = get(c).getInt(KEY_OVERLAY_POSITION, POSITION_CENTER);
        return p >= POSITION_TOP && p <= POSITION_BOTTOM ? p : POSITION_CENTER;
    }
    static boolean centeredBass(Context c) { return get(c).getBoolean(KEY_CENTER_BASS, true); }
    static boolean singleColorBars(Context c) { return get(c).getBoolean(KEY_SINGLE_COLOR, false); }
    static int bassGain(Context c) {
        return Math.max(100, Math.min(240, get(c).getInt(KEY_BASS_GAIN, 160)));
    }
    static boolean asPlayed(Context c) { return get(c).getBoolean(KEY_AS_PLAYED, true); }
    static boolean mediaSessionDetectionEnabled(Context c) {
        return get(c).getBoolean(KEY_MEDIA_SESSION_DETECTION, false);
    }
}
