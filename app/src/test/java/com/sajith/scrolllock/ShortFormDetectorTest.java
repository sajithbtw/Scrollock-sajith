package com.sajith.scrolllock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.sajith.scrolllock.service.ShortFormDetector;
import com.sajith.scrolllock.service.ShortFormDetector.DetectionResult;
import com.sajith.scrolllock.service.ShortFormDetector.NodeSnapshot;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/**
 * Unit tests verifying accurate Reels / Shorts / Spotlight / vertical short-form detection
 * and zero false positives across regular feeds, DMs, and standard horizontal video players.
 */
public class ShortFormDetectorTest {

    private static NodeSnapshot node(
            String viewId,
            String text,
            String desc,
            boolean selected,
            NodeSnapshot... children
    ) {
        return new NodeSnapshot(
                viewId,
                "android.widget.FrameLayout",
                text,
                desc,
                selected,
                true,
                Arrays.asList(children)
        );
    }

    @Test
    public void testInstagramReelsBlockedAndDmsAllowed() {
        NodeSnapshot reelsRoot = node(
                "com.instagram.android:id/root_layout", "", "", false,
                node("com.instagram.android:id/clips_viewer_view_pager", "", "", false)
        );
        DetectionResult reelsHit = ShortFormDetector.detect("com.instagram.android", reelsRoot);
        assertTrue(reelsHit.isDetected());
        assertEquals("Instagram Reels", reelsHit.getSurfaceLabel());

        NodeSnapshot selectedClipsTab = node(
                "com.instagram.android:id/tab_bar", "", "", false,
                node("com.instagram.android:id/clips_tab", "", "Reels", true)
        );
        assertTrue(ShortFormDetector.detect("com.instagram.android", selectedClipsTab).isDetected());

        NodeSnapshot dmRoot = node(
                "com.instagram.android:id/root_layout", "", "", false,
                node("com.instagram.android:id/direct_inbox_recycler_view", "", "", false),
                node("com.instagram.android:id/row_thread_composer_edittext", "Message…", "", false),
                node("com.instagram.android:id/clips_tab", "", "Reels", false)
        );
        assertFalse(ShortFormDetector.detect("com.instagram.android", dmRoot).isDetected());
    }

    @Test
    public void testYouTubeShortsBlockedAndRegularVideoAllowed() {
        NodeSnapshot shortsRoot = node(
                "com.google.android.youtube:id/coordinator", "", "", false,
                node("com.google.android.youtube:id/reel_watch_player", "", "", false)
        );
        DetectionResult shortsHit = ShortFormDetector.detect("com.google.android.youtube", shortsRoot);
        assertTrue(shortsHit.isDetected());
        assertEquals("YouTube Shorts", shortsHit.getSurfaceLabel());

        NodeSnapshot selectedShortsTab = node(
                "com.google.android.youtube:id/pivot_bar", "", "", false,
                node("com.google.android.youtube:id/tab", "Shorts", "Shorts", true)
        );
        assertTrue(ShortFormDetector.detect("com.google.android.youtube", selectedShortsTab).isDetected());

        NodeSnapshot regularVideoRoot = node(
                "com.google.android.youtube:id/watch_while_layout", "", "", false,
                node("com.google.android.youtube:id/watch_player", "", "Video player", false),
                node("com.google.android.youtube:id/tab", "Home", "Home", true),
                node("com.google.android.youtube:id/tab", "Shorts", "Shorts", false)
        );
        assertFalse(ShortFormDetector.detect("com.google.android.youtube", regularVideoRoot).isDetected());
    }

    @Test
    public void testDedicatedShortFormAppAndHybridNullSafe() {
        DetectionResult tiktok = ShortFormDetector.detect("com.zhiliaoapp.musically", null);
        assertTrue(tiktok.isDetected());
        assertTrue(tiktok.isDedicatedShortFormApp());

        // Hybrid app with null root must not false-positive
        assertFalse(ShortFormDetector.detect("com.instagram.android", null).isDetected());
    }

    @Test
    public void testFacebookReelsAndSnapchatSpotlight() {
        NodeSnapshot fbReels = node(
                "com.facebook.katana:id/main", "", "", false,
                node("com.facebook.katana:id/fb_reels_viewer_container", "", "", false)
        );
        assertTrue(ShortFormDetector.detect("com.facebook.katana", fbReels).isDetected());

        NodeSnapshot fbFeed = node(
                "com.facebook.katana:id/main", "", "", false,
                node("com.facebook.katana:id/news_feed_recycler_view", "", "", false)
        );
        assertFalse(ShortFormDetector.detect("com.facebook.katana", fbFeed).isDetected());

        NodeSnapshot snapSpotlight = node(
                "com.snapchat.android:id/root", "", "", false,
                node("com.snapchat.android:id/spotlight_view_pager", "", "", false)
        );
        DetectionResult snapHit = ShortFormDetector.detect("com.snapchat.android", snapSpotlight);
        assertTrue(snapHit.isDetected());
        assertEquals("Snapchat Spotlight", snapHit.getSurfaceLabel());

        NodeSnapshot snapChat = new NodeSnapshot(
                "com.snapchat.android:id/chat_input_bar",
                "android.widget.EditText",
                "Send a chat",
                "",
                false,
                true,
                Collections.emptyList()
        );
        assertFalse(ShortFormDetector.detect("com.snapchat.android", snapChat).isDetected());
    }
}
