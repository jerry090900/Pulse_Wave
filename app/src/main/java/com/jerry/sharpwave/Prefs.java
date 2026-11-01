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
    static final int PALETTE_COUNT = 10;
    static final String[] PALETTE_NAMES = {
            "DHH Signature", "Talha Night", "Seedhe Smoke", "Red Room", "808 Ice",
            "Heartbreak Kid", "Jeweler Cut", "Nothing Minimal", "AMOLED Dusk", "Monsoon Mood"
    };
    static final String KEY_LOCK_SCREEN = "lock_screen_experimental";

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
}
