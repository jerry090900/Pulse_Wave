package com.jerry.sharpwave;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Collections;

public class MusicSessionPolicyTest {
    @Test public void onlySelectedPlayingAppIsAccepted() {
        assertTrue(MusicSessionListener.acceptsPlayingSource("music", 3, Collections.singleton("music")));
        assertFalse(MusicSessionListener.acceptsPlayingSource("music", 2, Collections.singleton("music")));
        assertFalse(MusicSessionListener.acceptsPlayingSource("other", 3, Collections.singleton("music")));
        assertFalse(MusicSessionListener.acceptsPlayingSource(null, 3, Collections.singleton("music")));
    }
}
