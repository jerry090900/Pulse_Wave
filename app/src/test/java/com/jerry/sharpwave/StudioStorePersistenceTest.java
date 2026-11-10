package com.jerry.sharpwave;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class StudioStorePersistenceTest {
    // Reopening the Context shares only persisted preferences, not activity or preset objects.
    private static Context reopen(Map<String, Object> disk) {
        SharedPreferences prefs = (SharedPreferences) Proxy.newProxyInstance(
                SharedPreferences.class.getClassLoader(), new Class<?>[]{SharedPreferences.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("edit")) return editor(disk);
                    if (name.equals("contains")) return disk.containsKey(args[0]);
                    if (name.startsWith("get") && args != null && args.length == 2)
                        return disk.getOrDefault(args[0], args[1]);
                    throw new UnsupportedOperationException(name);
                });
        return new ContextWrapper(null) {
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return prefs;
            }
            @Override public String getPackageName() { return "com.jerry.sharpwave.preview"; }
        };
    }

    private static SharedPreferences.Editor editor(Map<String, Object> disk) {
        Map<String, Object> pending = new HashMap<>();
        return (SharedPreferences.Editor) Proxy.newProxyInstance(
                SharedPreferences.Editor.class.getClassLoader(),
                new Class<?>[]{SharedPreferences.Editor.class}, (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.startsWith("put")) { pending.put((String) args[0], args[1]); return proxy; }
                    if (name.equals("remove")) { pending.put((String) args[0], null); return proxy; }
                    if (name.equals("apply") || name.equals("commit")) {
                        pending.forEach((key, value) -> {
                            if (value == null) disk.remove(key); else disk.put(key, value);
                        });
                        return name.equals("commit") ? true : null;
                    }
                    throw new UnsupportedOperationException(name);
                });
    }

    @Test public void saveApplyBackEditHeightAndReopenKeepsStyleAndOtherDesigns() {
        Map<String, Object> disk = new HashMap<>();
        Context c = reopen(disk);
        StudioPreset p = StudioPreset.template("personal-wave", "My saved Wave", StudioPreset.WAVE);
        p.primary = 0xFF23CABA;
        p.accent = 0xFFE4BE63;
        p.third = 0xFFCB45A2;
        p.waveGlow = 36;
        p.setColorMode(StudioPreset.COLOR_THREE);
        StudioPreset other = StudioPreset.template("other", "Other design", StudioPreset.SPIKES);
        StudioStore.save(c, other);
        StudioStore.save(c, p);
        StudioStore.apply(c, p);
        StudioStore.updateAudioSetting(reopen(disk), Prefs.KEY_HEIGHT, 175);
        StudioStore.updateAudioSetting(reopen(disk), Prefs.KEY_SPIKE_WIDTH, 9);
        StudioStore.updateAudioSetting(reopen(disk), Prefs.KEY_GAP, 3);
        StudioStore.updateAudioSetting(reopen(disk), Prefs.KEY_SENSITIVITY, 210);
        Context restarted = reopen(disk);
        StudioStore.restoreClassicBarsOnce(restarted);
        StudioPreset restored = StudioStore.activeAudio(restarted);
        assertNotNull(restored);
        assertTrue(StudioStore.isApplied(restarted, p));
        assertEquals(p.id, restored.id);
        assertEquals(p.name, restored.name);
        assertEquals(StudioPreset.WAVE, restored.type);
        assertEquals(175, restored.height);
        assertEquals(175, Prefs.height(restarted));
        assertEquals(9, restored.width);
        assertEquals(3, restored.gap);
        assertEquals(210, restored.sensitivity);
        assertEquals(p.primary, restored.primary);
        assertEquals(p.accent, restored.accent);
        assertEquals(p.third, restored.third);
        assertEquals(p.effectiveColorMode(), restored.effectiveColorMode());
        assertEquals(36, restored.waveGlow);
        assertEquals(other.height, StudioStore.load(restarted).stream()
                .filter(scene -> scene.id.equals(other.id)).findFirst().get().height);
    }

    @Test public void oldSchemaAndEnabledExtraFlagsKeepPresetAndLegacyResponseSettings() {
        Map<String, Object> disk = new HashMap<>();
        disk.put("studio_scenes_v1", "[{\"id\":\"old\",\"name\":\"Old style\",\"type\":0,\"primary\":-1,\"accent\":-6356978,\"height\":95}]");
        disk.put("studio_active_audio_v1", "old");
        disk.put("beat_sync_support", true);
        disk.put("adaptive_visual_response", true);
        disk.put("visual_response_profile", 2);
        Context c = reopen(disk);
        StudioStore.updateAudioSetting(c, Prefs.KEY_HEIGHT, 140);
        assertEquals("old", StudioStore.activeAudio(reopen(disk)).id);
        assertEquals(140, StudioStore.activeAudio(c).height);
        StudioStore.updateAudioSetting(c, Prefs.KEY_BASS_GAIN, 180);
        assertEquals(180, Prefs.bassGain(c));
        assertEquals(140, StudioStore.activeAudio(c).height);
        assertEquals(-6356978, StudioStore.activeAudio(c).accent);
    }

    @Test public void paletteEditKeepsSelectionAndExplicitClassicActionStillClearsIt() {
        Map<String, Object> disk = new HashMap<>();
        Context c = reopen(disk);
        StudioPreset p = StudioPreset.template("bars", "Personal", StudioPreset.BARS);
        StudioStore.apply(c, p);
        StudioStore.updateAppliedPalette(c, Prefs.PALETTE_DHH_SIGNATURE);
        assertTrue(StudioStore.isApplied(reopen(disk), p));
        assertEquals(0xFF9F000E, StudioStore.activeAudio(c).accent);
        StudioStore.clearAppliedAudio(c);
        StudioStore.updateAudioSetting(c, Prefs.KEY_HEIGHT, 150);
        assertNull(StudioStore.activeAudio(reopen(disk)));
        assertEquals(150, Prefs.height(c));
        assertTrue(StudioStore.load(c).stream().anyMatch(scene -> scene.id.equals(p.id)));
    }
}
