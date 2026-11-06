package com.jerry.sharpwave;

import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.service.notification.NotificationListenerService;

import java.util.List;
import java.util.Set;

/**
 * User-approved notification access is used solely to query playing media sessions.
 *
 * This listener never reads, stores, forwards, or logs notification contents.
 * Granting OS notification-listener access itself is broad; the app explains this to users.
 */
public final class MusicSessionListener extends NotificationListenerService {
    static boolean hasAccess(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        return manager != null && manager.isNotificationListenerAccessGranted(
                new ComponentName(context, MusicSessionListener.class));
    }

    static boolean acceptsPlayingSource(String packageName, int state, Set<String> selected) {
        return packageName != null && selected != null && selected.contains(packageName)
                && state == PlaybackState.STATE_PLAYING;
    }

    static String selectedPlayingApp(Context context, Set<String> selected) {
        if (!Prefs.mediaSessionDetectionEnabled(context) || selected == null
                || selected.isEmpty() || !hasAccess(context)) return null;

        MediaSessionManager manager = context.getSystemService(MediaSessionManager.class);
        if (manager == null) return null;
        try {
            List<MediaController> sessions = manager.getActiveSessions(
                    new ComponentName(context, MusicSessionListener.class));
            if (sessions == null) return null;
            for (MediaController controller : sessions) {
                if (controller == null) continue;
                PlaybackState playback = controller.getPlaybackState();
                if (acceptsPlayingSource(controller.getPackageName(),
                        playback == null ? -1 : playback.getState(), selected)) {
                    return controller.getPackageName();
                }
            }
        } catch (RuntimeException ignored) {
            // Android may revoke access or deny the session API on certain OEM builds.
            // Caller falls back to its existing selected-app and audio detection.
        }
        return null;
    }
}
