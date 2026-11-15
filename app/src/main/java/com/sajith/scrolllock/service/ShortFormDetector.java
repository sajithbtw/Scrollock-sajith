package com.sajith.scrolllock.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Pure, low-latency short-form video feed classifier determining whether a package
 * or UI snapshot represents Reels, Shorts, Spotlight, or vertical short-form video.
 *
 * Play Protect safe: operates strictly on package rules and in-memory snapshots with zero
 * third-party Accessibility window scraping.
 */
public final class ShortFormDetector {

    private static final int MAX_NODES_TO_SCAN = 180;
    private static final int MAX_DEPTH = 16;

    private ShortFormDetector() {
    }

    public static final class NodeSnapshot {
        private final String viewId;
        private final String className;
        private final String text;
        private final String contentDesc;
        private final boolean selected;
        private final boolean visibleToUser;
        private final List<NodeSnapshot> children;

        public NodeSnapshot(
                String viewId,
                String className,
                String text,
                String contentDesc,
                boolean selected,
                boolean visibleToUser,
                List<NodeSnapshot> children
        ) {
            this.viewId = normalize(viewId);
            this.className = normalize(className);
            this.text = normalize(text);
            this.contentDesc = normalize(contentDesc);
            this.selected = selected;
            this.visibleToUser = visibleToUser;
            this.children = children == null ? Collections.emptyList() : children;
        }

        public String getViewId() {
            return viewId;
        }

        public String getClassName() {
            return className;
        }

        public String getText() {
            return text;
        }

        public String getContentDesc() {
            return contentDesc;
        }

        public boolean isSelected() {
            return selected;
        }

        public boolean isVisibleToUser() {
            return visibleToUser;
        }

        public List<NodeSnapshot> getChildren() {
            return children;
        }
    }

    public static final class DetectionResult {
        private final boolean detected;
        private final boolean dedicatedShortFormApp;
        private final String surfaceLabel;
        private final String matchedSignal;

        private DetectionResult(
                boolean detected,
                boolean dedicatedShortFormApp,
                String surfaceLabel,
                String matchedSignal
        ) {
            this.detected = detected;
            this.dedicatedShortFormApp = dedicatedShortFormApp;
            this.surfaceLabel = surfaceLabel;
            this.matchedSignal = matchedSignal;
        }

        public static DetectionResult none() {
            return new DetectionResult(false, false, "", "");
        }

        public static DetectionResult match(boolean dedicatedApp, String surfaceLabel, String signal) {
            return new DetectionResult(true, dedicatedApp, surfaceLabel, signal);
        }

        public boolean isDetected() {
            return detected;
        }

        public boolean isDedicatedShortFormApp() {
            return dedicatedShortFormApp;
        }

        public String getSurfaceLabel() {
            return surfaceLabel;
        }

        public String getMatchedSignal() {
            return matchedSignal;
        }
    }

    /**
     * Checks whether the given foreground package & active window snapshot represent an active
     * Reels / Shorts / Spotlight / vertical short-form video feed.
     */
    public static DetectionResult detect(String packageName, NodeSnapshot root) {
        String pkg = normalize(packageName);
        if (pkg.isEmpty()) {
            return DetectionResult.none();
        }

        // 1. Dedicated standalone short-form video apps -> always short-form
        if (isDedicatedShortFormPackage(pkg)) {
            return DetectionResult.match(true, resolveDedicatedAppSurface(pkg), "dedicated_short_form_pkg:" + pkg);
        }

        if (root == null) {
            return DetectionResult.none();
        }

        List<NodeSnapshot> visibleNodes = new ArrayList<>();
        collectVisibleNodes(root, visibleNodes);

        // 2. Instagram Reels vs regular Feed / DMs / Search / Profile
        if (pkg.contains("instagram") && !pkg.contains("barcelona")) {
            for (NodeSnapshot node : visibleNodes) {
                String id = node.getViewId();
                if (containsAny(id,
                        "clips_viewer_view_pager",
                        "clips_swipe_refresh_container",
                        "clips_video_container",
                        "clips_viewer_root",
                        "clips_author_info_container",
                        "clips_audio_attribution",
                        "clips_video_player",
                        "clips_ufi_container",
                        "reel_viewer_root")) {
                    return DetectionResult.match(false, "Instagram Reels", id);
                }
                if (node.isSelected() && (id.contains("clips_tab")
                        || matchesTabLabel(node, "reels"))) {
                    return DetectionResult.match(false, "Instagram Reels", id.isEmpty() ? "selected:reels" : id);
                }
            }
            return DetectionResult.none();
        }

        // 3. YouTube Shorts vs standard horizontal watch_player / Home / Search
        if (pkg.contains("youtube")) {
            for (NodeSnapshot node : visibleNodes) {
                String id = node.getViewId();
                if (containsAny(id,
                        "reel_watch_player",
                        "reel_watch_fragment_root",
                        "reel_player_page_container",
                        "shorts_player_view",
                        "reel_channel_bar",
                        "reel_right_action_bar",
                        "reel_player_underlay")) {
                    return DetectionResult.match(false, "YouTube Shorts", id);
                }
                if (node.isSelected() && (id.contains("shorts_tab")
                        || id.contains("shorts_pivot_button")
                        || matchesTabLabel(node, "shorts"))) {
                    return DetectionResult.match(false, "YouTube Shorts", id.isEmpty() ? "selected:shorts" : id);
                }
            }
            return DetectionResult.none();
        }

        // 4. Facebook Reels vs Feed / Messenger / Groups
        if (pkg.contains("facebook.katana") || pkg.contains("facebook.lite")) {
            for (NodeSnapshot node : visibleNodes) {
                String id = node.getViewId();
                if (containsAny(id,
                        "reels_viewer",
                        "fb_reels_viewer",
                        "reels_player",
                        "reels_overlay",
                        "reels_video_container")) {
                    return DetectionResult.match(false, "Facebook Reels", id);
                }
                if (node.isSelected() && matchesTabLabel(node, "reels")) {
                    return DetectionResult.match(false, "Facebook Reels", id.isEmpty() ? "selected:reels" : id);
                }
            }
            return DetectionResult.none();
        }

        // 5. Snapchat Spotlight vs Chat / Camera / Stories
        if (pkg.contains("snapchat")) {
            for (NodeSnapshot node : visibleNodes) {
                String id = node.getViewId();
                if (containsAny(id,
                        "spotlight_view_pager",
                        "spotlight_container",
                        "spotlight_chrome",
                        "spotlight_player")) {
                    return DetectionResult.match(false, "Snapchat Spotlight", id);
                }
                if (node.isSelected() && (id.contains("spotlight_tab") || matchesTabLabel(node, "spotlight"))) {
                    return DetectionResult.match(false, "Snapchat Spotlight", id.isEmpty() ? "selected:spotlight" : id);
                }
            }
            return DetectionResult.none();
        }

        // 6. Reddit / X / Other user-selected apps -> Generic vertical short-form player cues
        for (NodeSnapshot node : visibleNodes) {
            String id = node.getViewId();
            if (containsAny(id,
                    "immersive_video_pager",
                    "vertical_video_pager",
                    "immersive_media_viewer",
                    "explore_video_pager",
                    "short_form",
                    "reels_viewer",
                    "clips_viewer",
                    "reel_watch_player",
                    "shorts_player",
                    "spotlight_view_pager")) {
                return DetectionResult.match(false, resolveGenericSurface(pkg), id);
            }
            if (node.isSelected() && (matchesTabLabel(node, "reels")
                    || matchesTabLabel(node, "shorts")
                    || matchesTabLabel(node, "spotlight"))) {
                return DetectionResult.match(false, resolveGenericSurface(pkg), "selected:" + node.getText());
            }
        }

        return DetectionResult.none();
    }

    public static boolean isDedicatedShortFormPackage(String pkg) {
        return pkg.equals("com.zhiliaoapp.musically")
                || pkg.equals("com.ss.android.ugc.trill")
                || pkg.equals("com.ss.android.ugc.aweme")
                || pkg.equals("com.musically.live")
                || pkg.equals("com.instagram.reels")
                || pkg.equals("com.google.android.apps.youtube.shorts")
                || pkg.equals("com.kwai.video")
                || pkg.equals("in.mohalla.video")
                || pkg.equals("com.eterno.shortvideos")
                || pkg.equals("video.like");
    }

    private static String resolveDedicatedAppSurface(String pkg) {
        if (pkg.contains("musically") || pkg.contains("ugc.") || pkg.contains("tiktok")) {
            return "TikTok Feed";
        }
        if (pkg.contains("youtube.shorts")) {
            return "YouTube Shorts";
        }
        if (pkg.contains("instagram.reels")) {
            return "Instagram Reels";
        }
        return "Short-Form Video Feed";
    }

    private static String resolveGenericSurface(String pkg) {
        if (pkg.contains("reddit")) {
            return "Reddit Video Feed";
        }
        if (pkg.contains("twitter")) {
            return "X Vertical Video";
        }
        return "Short-Form Reels/Shorts";
    }

    private static void collectVisibleNodes(NodeSnapshot node, List<NodeSnapshot> out) {
        if (node == null || !node.isVisibleToUser() || out.size() >= MAX_NODES_TO_SCAN) {
            return;
        }
        out.add(node);
        for (NodeSnapshot child : node.getChildren()) {
            collectVisibleNodes(child, out);
            if (out.size() >= MAX_NODES_TO_SCAN) {
                break;
            }
        }
    }

    private static boolean containsAny(String value, String... targets) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (String t : targets) {
            if (value.contains(t)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesTabLabel(NodeSnapshot node, String targetLabel) {
        String text = node.getText();
        String desc = node.getContentDesc();
        return text.equals(targetLabel)
                || desc.equals(targetLabel)
                || desc.equals(targetLabel + " tab");
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.US);
    }
}
