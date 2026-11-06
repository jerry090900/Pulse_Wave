package com.jerry.sharpwave;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StudioPresetTest {
    @Test
    public void templateNamesAndTypesAreStable() {
        StudioPreset p = StudioPreset.template("base", "White + red", StudioPreset.BARS);
        assertTrue(p.isAudio());
        assertEquals("Bars", p.category());
        assertEquals(0xFFFFFFFF, p.primary);
        assertEquals(0xFF9F000E, p.accent);
    }

    @Test
    public void invalidFieldsAreClampedBeforePersisting() {
        StudioPreset p = StudioPreset.template("bad", "  Demo  ", 1000);
        p.width = 10000;
        p.gap = -10;
        p.height = -1;
        p.sensitivity = 5000;
        p.ringSize = -2;
        p.offsetY = 200;
        p.opacity = -1;
        p.photoUri = "file:///sdcard/secret";
        p.normalize();
        assertEquals(StudioPreset.RING, p.type);
        assertEquals(18, p.width);
        assertEquals(0, p.gap);
        assertEquals(40, p.height);
        assertEquals(260, p.sensitivity);
        assertEquals(50, p.ringSize);
        assertEquals(90, p.offsetY);
        assertEquals(15, p.opacity);
        assertEquals("", p.photoUri);
        assertEquals("Demo", p.name);
    }

    @Test
    public void copyAndDuplicateDoNotMutateOriginal() {
        StudioPreset original = StudioPreset.template("default", "Photo design",
                StudioPreset.PHOTO);
        original.photoUri = "content://provider/photo";
        StudioPreset copy = original.copy();
        copy.opacity = 30;
        assertEquals(100, original.opacity);
        assertEquals(original.photoUri, copy.photoUri);

        StudioPreset duplicate = original.duplicate();
        assertNotEquals(original.id, duplicate.id);
        assertTrue(duplicate.name.contains("copy"));
        assertFalse(duplicate.isAudio());
    }

    @Test
    public void longAndEmptyNamesGetSafeDefaults() {
        assertEquals("Untitled style", StudioPreset.safeName(null));
        assertEquals("Untitled style", StudioPreset.safeName("  "));
        assertEquals(45, StudioPreset.safeName(
                "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz").length());
    }

    @Test
    public void fingerprintPreviewIsNeverAnAudioOverlay() {
        assertFalse(StudioPreset.template("ring", "Ring", StudioPreset.RING).isAudio());
        assertFalse(StudioPreset.template("photo", "Photo", StudioPreset.PHOTO).isAudio());
        assertTrue(StudioPreset.template("wave", "Wave", StudioPreset.WAVE).isAudio());
        assertTrue(StudioPreset.template("spikes", "Spikes", StudioPreset.SPIKES).isAudio());
    }
}
