package com.jerry.sharpwave;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The app-opening experience is now user-controlled and ON by default. */
public final class OverlayVisibilityTest {
    @Test public void enablingInAppVisualizerPreventsUnwantedHiding() {
        assertFalse(OverlayService.shouldHideOverlayInApp(
                true, false, false, true));
        assertFalse(OverlayService.shouldHideOverlayInApp(
                false, false, false, true));
    }

    @Test public void userCanStillHideBarsWhileEditingIfDesired() {
        assertTrue(OverlayService.shouldHideOverlayInApp(
                true, false, false, false));
        assertFalse(OverlayService.shouldHideOverlayInApp(
                true, true, false, false));
    }

    @Test public void explicitPreviewAlwaysShowsRegardlessOfPreference() {
        assertFalse(OverlayService.shouldHideOverlayInApp(
                true, false, true, false));
        assertFalse(OverlayService.shouldHideOverlayInApp(
                true, false, true, true));
    }

    @Test public void notificationOffUsesExistingServiceStopContract() {
        assertTrue(OverlayService.ACTION_STOP.endsWith(".STOP"));
    }
}
