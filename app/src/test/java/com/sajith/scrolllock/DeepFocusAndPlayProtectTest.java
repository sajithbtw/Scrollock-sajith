package com.sajith.scrolllock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import androidx.test.core.app.ApplicationProvider;

import com.sajith.scrolllock.data.local.PreferencesManager;
import com.sajith.scrolllock.data.model.FocusState;
import com.sajith.scrolllock.service.DeepFocusController;
import com.sajith.scrolllock.service.NonEssentialAppClassifier;
import com.sajith.scrolllock.service.UsageStatsGuardMonitor;
import com.sajith.scrolllock.service.UsageStatsGuardMonitor.GuardAlertType;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Verifies Deep Focus mode logic, PreferencesManager persistence/clamping,
 * NonEssentialAppClassifier safety, UsageStatsGuardMonitor evaluation, and
 * Play Protect manifest compliance (no AccessibilityService, VpnService, or QUERY_ALL_PACKAGES).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {34})
public class DeepFocusAndPlayProtectTest {

    private static final String OWN_PKG = "com.sajith.scrolllock";

    @Test
    public void nonEssentialClassifier_preservesCoreOsAndAllowsWhitelists() {
        Set<String> allowed = Collections.emptySet();

        // Core OS / telephony / messaging / clock / IME / launcher must NEVER be blocked
        assertFalse(NonEssentialAppClassifier.shouldBlockInternet("android", OWN_PKG, false, true, allowed));
        assertFalse(NonEssentialAppClassifier.shouldBlockInternet("com.google.android.dialer", OWN_PKG, true, true, allowed));
        assertFalse(NonEssentialAppClassifier.shouldBlockInternet("com.google.android.apps.messaging", OWN_PKG, true, true, allowed));
        assertFalse(NonEssentialAppClassifier.shouldBlockInternet("com.android.emergency", OWN_PKG, true, true, allowed));
        assertFalse(NonEssentialAppClassifier.shouldBlockInternet("com.google.android.deskclock", OWN_PKG, true, true, allowed));
        assertFalse(NonEssentialAppClassifier.shouldBlockInternet("com.google.android.inputmethod.latin", OWN_PKG, false, true, allowed));
        assertFalse(NonEssentialAppClassifier.shouldBlockInternet("com.google.android.gms", OWN_PKG, false, true, allowed));
        assertFalse(NonEssentialAppClassifier.shouldBlockInternet(OWN_PKG, OWN_PKG, true, false, allowed));

        // Non-essential system & user apps ARE flagged unless whitelisted
        assertTrue(NonEssentialAppClassifier.shouldBlockInternet("com.android.chrome", OWN_PKG, true, true, allowed));
        assertTrue(NonEssentialAppClassifier.shouldBlockInternet("com.google.android.youtube", OWN_PKG, true, true, allowed));
        assertTrue(NonEssentialAppClassifier.shouldBlockInternet("com.android.vending", OWN_PKG, true, true, allowed));
        assertTrue(NonEssentialAppClassifier.shouldBlockInternet("com.instagram.android", OWN_PKG, true, false, allowed));

        // Explicit user whitelist exempts even non-essential apps
        Set<String> withChromeAllowed = new HashSet<>(Collections.singletonList("com.android.chrome"));
        assertFalse(NonEssentialAppClassifier.shouldBlockInternet("com.android.chrome", OWN_PKG, true, true, withChromeAllowed));
    }

    @Test
    public void preferencesManager_deepFocusAndCustomDurationWorkCorrectly() {
        Context context = ApplicationProvider.getApplicationContext();
        PreferencesManager prefs = new PreferencesManager(context);

        assertFalse(prefs.isDeepFocusEnabled());
        assertTrue(prefs.isDeepFocusHidePreviewsEnabled());
        assertTrue(prefs.isDeepFocusBlockInternetEnabled());

        prefs.setDeepFocusEnabled(true);
        prefs.setDeepFocusHidePreviewsEnabled(true);
        prefs.setDeepFocusBlockInternetEnabled(false);
        assertTrue(prefs.isDeepFocusEnabled());
        assertFalse(prefs.isDeepFocusBlockInternetEnabled());

        // Toggle allowed exception packages
        assertTrue(prefs.toggleDeepFocusAllowedPackage("com.slack.android"));
        assertTrue(prefs.getDeepFocusAllowedPackages().contains("com.slack.android"));
        assertFalse(prefs.toggleDeepFocusAllowedPackage("com.slack.android"));
        assertFalse(prefs.getDeepFocusAllowedPackages().contains("com.slack.android"));

        // Custom duration clamping [5..180]
        prefs.setCustomFocusDurationMinutes(2);
        assertEquals(5, prefs.getCustomFocusDurationMinutes());
        prefs.setCustomFocusDurationMinutes(240);
        assertEquals(180, prefs.getCustomFocusDurationMinutes());
        prefs.setCustomFocusDurationMinutes(75);
        assertEquals(75, prefs.getCustomFocusDurationMinutes());

        // Prior DND snapshot save and clear
        prefs.savePriorDndSnapshot(
                NotificationManager.INTERRUPTION_FILTER_ALL,
                NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS,
                NotificationManager.Policy.PRIORITY_SENDERS_ANY,
                NotificationManager.Policy.PRIORITY_SENDERS_CONTACTS,
                0
        );
        assertTrue(prefs.hasSavedPriorDndSnapshot());
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, prefs.getPriorDndFilter());

        // Reconcile when session is IDLE clears runtime state and DND snapshot cleanly
        prefs.setDeepFocusRuntimeActive(true);
        prefs.clearTimerStateToIdle();
        assertEquals(FocusState.IDLE, prefs.getFocusState());
        DeepFocusController.reconcileIfIdle(context);
        assertFalse(prefs.isDeepFocusRuntimeActive());
        assertFalse(prefs.hasSavedPriorDndSnapshot());
    }

    @Test
    public void usageStatsGuardMonitor_evaluatesForegroundAlertsAccurately() {
        Set<String> guarded = new HashSet<>(Arrays.asList("com.instagram.android", "com.twitter.android"));
        Set<String> reels = new HashSet<>(Collections.singletonList("com.google.android.youtube"));
        Set<String> allowedDeep = new HashSet<>(Collections.singletonList("org.wikipedia"));

        // Own app & essential system apps always return NONE
        assertEquals(GuardAlertType.NONE, UsageStatsGuardMonitor.evaluateForegroundPackage(
                OWN_PKG, OWN_PKG, true, true, true, true, guarded, reels, allowedDeep, false, false));
        assertEquals(GuardAlertType.NONE, UsageStatsGuardMonitor.evaluateForegroundPackage(
                "com.google.android.dialer", OWN_PKG, true, true, true, true, guarded, reels, allowedDeep, false, false));

        // Guarded app triggers APP_LOCK during session or when always-guard is active without temp unlock
        assertEquals(GuardAlertType.APP_LOCK, UsageStatsGuardMonitor.evaluateForegroundPackage(
                "com.instagram.android", OWN_PKG, true, false, true, true, guarded, reels, allowedDeep, false, false));
        assertEquals(GuardAlertType.NONE, UsageStatsGuardMonitor.evaluateForegroundPackage(
                "com.instagram.android", OWN_PKG, false, true, true, false, guarded, reels, allowedDeep, true, false));

        // Short-form host app triggers SHORT_FORM alert unless temporarily unlocked outside Focus
        assertEquals(GuardAlertType.SHORT_FORM, UsageStatsGuardMonitor.evaluateForegroundPackage(
                "com.google.android.youtube", OWN_PKG, true, false, true, true, guarded, reels, allowedDeep, false, false));

        // Non-essential app under Deep Focus triggers DEEP_FOCUS_NETWORK unless whitelisted
        assertEquals(GuardAlertType.DEEP_FOCUS_NETWORK, UsageStatsGuardMonitor.evaluateForegroundPackage(
                "com.android.chrome", OWN_PKG, true, false, true, true, guarded, reels, allowedDeep, false, false));
        assertEquals(GuardAlertType.NONE, UsageStatsGuardMonitor.evaluateForegroundPackage(
                "org.wikipedia", OWN_PKG, true, false, true, true, guarded, reels, allowedDeep, false, false));
    }

    @Test
    public void manifest_compliesWithGooglePlayProtectRestrictions() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        PackageManager pm = context.getPackageManager();
        PackageInfo pkgInfo = pm.getPackageInfo(
                context.getPackageName(),
                PackageManager.GET_PERMISSIONS | PackageManager.GET_SERVICES
        );

        if (pkgInfo.requestedPermissions != null) {
            Set<String> perms = new HashSet<>(Arrays.asList(pkgInfo.requestedPermissions));
            assertFalse(perms.contains("android.permission.BIND_ACCESSIBILITY_SERVICE"));
            assertFalse(perms.contains("android.permission.BIND_VPN_SERVICE"));
            assertFalse(perms.contains("android.permission.QUERY_ALL_PACKAGES"));
            assertFalse(perms.contains("android.permission.SYSTEM_ALERT_WINDOW"));
            assertFalse(perms.contains("android.permission.REQUEST_INSTALL_PACKAGES"));
        }

        if (pkgInfo.services != null) {
            for (android.content.pm.ServiceInfo si : pkgInfo.services) {
                assertFalse("Should not declare AccessibilityService permission",
                        "android.permission.BIND_ACCESSIBILITY_SERVICE".equals(si.permission));
                assertFalse("Should not declare VpnService permission",
                        "android.permission.BIND_VPN_SERVICE".equals(si.permission));
            }
        }
    }
}
