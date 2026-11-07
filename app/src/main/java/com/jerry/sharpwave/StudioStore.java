package com.jerry.sharpwave;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Versioned local-only preset store. It never reads notification or music content. */
final class StudioStore {
    private static final String KEY_SCENES = "studio_scenes_v1";
    private static final String KEY_ACTIVE_AUDIO = "studio_active_audio_v1";
    private static final String KEY_ACTIVE_PREVIEW = "studio_active_preview_v1";
    private static final int MAX_PRESETS = 40;

    private StudioStore() {}

    static List<StudioPreset> load(Context context) {
        String json = Prefs.get(context).getString(KEY_SCENES, "");
        if (json.isEmpty()) return defaults();
        List<StudioPreset> results = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length() && results.size() < MAX_PRESETS; i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                StudioPreset p = new StudioPreset();
                p.id = item.optString("id", "");
                p.name = item.optString("name", "Untitled style");
                p.type = item.optInt("type", StudioPreset.BARS);
                p.primary = item.optInt("primary", 0xFFFFFFFF);
                p.accent = item.optInt("accent", 0xFF9F000E);
                p.third = item.optInt("third", 0xFF19CFFA);
                p.flowStrength = item.optInt("flowStrength", 80);
                p.colorMode = item.has("colorMode") ? item.optInt("colorMode", -1) : -1;
                p.width = item.optInt("width", 5);
                p.gap = item.optInt("gap", 1);
                p.height = item.optInt("height", 92);
                p.sensitivity = item.optInt("sensitivity", 160);
                p.twoTone = item.optBoolean("twoTone", true);
                p.ringSize = item.optInt("ringSize", 108);
                p.offsetY = item.optInt("offsetY", 65);
                p.opacity = item.optInt("opacity", 100);
                p.photoUri = item.optString("photoUri", "");
                p.normalize();
                boolean duplicate = false;
                for (StudioPreset existing : results) {
                    if (existing.id.equals(p.id)) { duplicate = true; break; }
                }
                if (!duplicate) results.add(p);
            }
        } catch (Exception ignored) {
            return defaults(); // Damaged settings should never crash app startup.
        }
        if (results.isEmpty()) return defaults();
        addFlowStarter(results);
        addOverlaidStarter(results);
        return results;
    }

    static StudioPreset activeAudio(Context context) {
        String id = Prefs.get(context).getString(KEY_ACTIVE_AUDIO, "");
        if (id.isEmpty()) return null; // Existing users retain their legacy visualizer.
        for (StudioPreset p : load(context)) {
            if (p.id.equals(id) && p.isAudio()) return p;
        }
        return null;
    }

    static void clearAppliedAudio(Context context) {
        Prefs.get(context).edit().remove(KEY_ACTIVE_AUDIO).apply();
    }

    static boolean isApplied(Context context, StudioPreset p) {
        String key = p.isAudio() ? KEY_ACTIVE_AUDIO : KEY_ACTIVE_PREVIEW;
        return p.id.equals(Prefs.get(context).getString(key, ""));
    }

    static void save(Context context, StudioPreset source) {
        StudioPreset preset = source.copy();
        preset.normalize();
        List<StudioPreset> scenes = load(context);
        boolean found = false;
        for (int i = 0; i < scenes.size(); i++) {
            if (scenes.get(i).id.equals(preset.id)) {
                scenes.set(i, preset);
                found = true;
                break;
            }
        }
        if (!found) {
            if (scenes.size() >= MAX_PRESETS) throw new IllegalStateException("Preset limit reached");
            scenes.add(preset);
        }
        JSONArray array = new JSONArray();
        for (StudioPreset p : scenes) {
            try {
                JSONObject item = new JSONObject();
                item.put("id", p.id);
                item.put("name", p.name);
                item.put("type", p.type);
                item.put("primary", p.primary);
                item.put("accent", p.accent);
                item.put("third", p.third);
                item.put("colorMode", p.effectiveColorMode());
                item.put("flowStrength", p.flowStrength);
                item.put("width", p.width);
                item.put("gap", p.gap);
                item.put("height", p.height);
                item.put("sensitivity", p.sensitivity);
                item.put("twoTone", p.twoTone);
                item.put("ringSize", p.ringSize);
                item.put("offsetY", p.offsetY);
                item.put("opacity", p.opacity);
                item.put("photoUri", p.photoUri);
                array.put(item);
            } catch (Exception e) {
                throw new IllegalStateException("Preset serialization failed", e);
            }
        }
        // One atomic preference transaction for the complete preset collection.
        Prefs.get(context).edit().putString(KEY_SCENES, array.toString()).apply();
    }

    static void apply(Context context, StudioPreset p) {
        save(context, p);
        SharedPreferences.Editor edit = Prefs.get(context).edit();
        if (p.isAudio()) {
            edit.putString(KEY_ACTIVE_AUDIO, p.id)
                .putInt(Prefs.KEY_SPIKE_WIDTH, p.width)
                .putInt(Prefs.KEY_GAP, p.gap)
                .putInt(Prefs.KEY_HEIGHT, p.height)
                .putInt(Prefs.KEY_SENSITIVITY, p.sensitivity)
                .putInt(Prefs.KEY_PALETTE, Prefs.PALETTE_DHH_SIGNATURE);
        } else {
            // This selects the in-app design preview, not the OEM's secure lock screen/AOD.
            edit.putString(KEY_ACTIVE_PREVIEW, p.id);
        }
        edit.apply();
    }

    private static StudioPreset flowStarter() {
        StudioPreset flow = StudioPreset.template("pw_music_flow",
                "Prismatic Music Flow", StudioPreset.WAVE);
        flow.primary = 0xFFF5F6FE;
        flow.accent = 0xFF9F000E;
        flow.third = 0xFF19CFFA;
        flow.setColorMode(StudioPreset.COLOR_MUSIC_FLOW);
        flow.flowStrength = 80;
        flow.width = 5;
        return flow;
    }

    /** Optional Muviz-style Bars starter: no existing preset is overwritten. */
    private static StudioPreset overlaidStarter() {
        StudioPreset p = StudioPreset.template("pw_overlaid_three_lines",
                "Overlaid 3 Lines", StudioPreset.BARS);
        p.primary = 0xFF19CFFA; // cyan
        p.accent = 0xFFF6F4FF;  // soft white
        p.third = 0xFFFF368B;  // pink
        p.setColorMode(StudioPreset.COLOR_MUSIC_FLOW);
        p.flowStrength = 80;
        p.width = 5;
        p.gap = 1;
        return p;
    }

    private static void addOverlaidStarter(List<StudioPreset> list) {
        if (list.size() >= MAX_PRESETS) return;
        for (StudioPreset item : list) {
            if ("pw_overlaid_three_lines".equals(item.id)) return;
        }
        list.add(overlaidStarter());
    }

    private static void addFlowStarter(List<StudioPreset> list) {
        if (list.size() >= MAX_PRESETS) return;
        for (StudioPreset item : list) {
            if ("pw_music_flow".equals(item.id)) return;
        }
        list.add(flowStarter());
    }

    private static List<StudioPreset> defaults() {
        List<StudioPreset> presets = new ArrayList<>();
        presets.add(StudioPreset.template("pw_signature", "PulseWave Signature", StudioPreset.BARS));
        StudioPreset thin = StudioPreset.template("pw_thin", "Fine Bars", StudioPreset.BARS);
        thin.width = 3;
        thin.gap = 1;
        thin.primary = 0xFFEDEDED;
        thin.accent = 0xFF6A1023;
        presets.add(thin);
        StudioPreset spikes = StudioPreset.template("pw_spikes", "Sharp Spikes", StudioPreset.SPIKES);
        spikes.width = 8;
        spikes.gap = 2;
        presets.add(spikes);
        StudioPreset wave = StudioPreset.template("pw_wave", "Soft Wave", StudioPreset.WAVE);
        wave.primary = 0xFFB7EFFD;
        wave.accent = 0xFF3169C7;
        presets.add(wave);
        presets.add(StudioPreset.template("pw_photo", "My Photo Clock", StudioPreset.PHOTO));
        presets.add(StudioPreset.template("pw_ring", "Fingerprint Halo", StudioPreset.RING));
        presets.add(flowStarter());
        presets.add(overlaidStarter());
        return presets;
    }
}
