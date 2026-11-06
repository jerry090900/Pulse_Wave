package com.jerry.sharpwave;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class OverlayVisibilityTest {
    @Test
    public void editingSuppressesArtworkOnlyWhenScreenUnlocked() {
        assertTrue(OverlayService.shouldHideOverlayInApp(true, false, false));
        assertFalse(OverlayService.shouldHideOverlayInApp(false, false, false));
        assertFalse(OverlayService.shouldHideOverlayInApp(true, true, false));
    }

    @Test
    public void explicitPreviewRemainsVisibleInSettings() {
        assertFalse(OverlayService.shouldHideOverlayInApp(true, false, true));
    }
}
