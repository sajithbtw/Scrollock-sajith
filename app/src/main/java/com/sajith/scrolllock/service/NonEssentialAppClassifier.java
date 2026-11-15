package com.sajith.scrolllock.service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Pure, unit-testable package classifier determining which apps are essential system/emergency
 * services (always keep internet) versus non-essential system & user apps (internet blocked
 * during Deep Focus unless explicitly allowed by the user).
 */
public final class NonEssentialAppClassifier {

    private static final Set<String> CORE_ESSENTIAL_PACKAGES = new HashSet<>(Arrays.asList(
            "android",
            "com.android.systemui",
            "com.android.settings",
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.incallui",
            "com.google.android.dialer",
            "com.samsung.android.dialer",
            "com.samsung.android.incallui",
            "com.android.cellbroadcastreceiver",
            "com.google.android.cellbroadcastreceiver",
            "com.android.emergency",
            "com.android.mms",
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.android.providers.telephony",
            "com.android.providers.contacts",
            "com.android.contacts",
            "com.google.android.contacts",
            "com.android.deskclock",
            "com.google.android.deskclock",
            "com.sec.android.app.clockpackage",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.android.networkstack",
            "com.google.android.networkstack",
            "com.android.captiveportallogin",
            "com.android.bluetooth",
            "com.android.nfc",
            "com.android.providers.settings"
    ));

    private static final String[] NON_ESSENTIAL_SYSTEM_PREFIXES = new String[]{
            "com.android.chrome",
            "com.chrome.",
            "org.mozilla.",
            "com.opera.",
            "com.brave.",
            "com.microsoft.emmx",
            "com.sec.android.app.sbrowser",
            "com.android.browser",
            "com.google.android.youtube",
            "com.android.vending",
            "com.google.android.apps.magazines",
            "com.google.android.apps.podcasts",
            "com.google.android.videos",
            "com.google.android.music",
            "com.google.android.apps.youtube.music",
            "com.google.android.gm",
            "com.google.android.apps.googleassistant",
            "com.samsung.android.app.spage",
            "com.facebook.",
            "com.instagram.",
            "com.zhiliaoapp.",
            "com.ss.android.ugc",
            "com.netflix.",
            "com.spotify.",
            "com.amazon.",
            "com.reddit.",
            "com.twitter.",
            "com.snapchat."
    };

    private NonEssentialAppClassifier() {
    }

    /**
     * Returns true if the package provides vital OS connectivity, emergency alerts,
     * telephony, SMS, alarms, keyboards, or launchers that must never be blocked.
     */
    public static boolean isCoreEssentialPackage(String packageName, String ownPackageName) {
        if (packageName == null || packageName.trim().isEmpty()) {
            return true;
        }
        String pkg = packageName.trim().toLowerCase(Locale.US);
        if (ownPackageName != null && pkg.equals(ownPackageName.trim().toLowerCase(Locale.US))) {
            return true;
        }
        if (CORE_ESSENTIAL_PACKAGES.contains(pkg)) {
            return true;
        }
        return pkg.contains("inputmethod")
                || pkg.contains("gboard")
                || pkg.contains("swiftkey")
                || pkg.contains("launcher")
                || pkg.contains("trebuchet")
                || pkg.contains("carrierconfig")
                || pkg.contains(".telecom")
                || pkg.contains(".dialer")
                || pkg.contains(".incallui")
                || pkg.contains("cellbroadcast")
                || pkg.contains("deskclock")
                || pkg.contains("clockpackage")
                || pkg.contains("networkstack");
    }

    /**
     * Returns true if a preinstalled/system package is a well-known non-essential browser,
     * store, media player, news feed, or social app whose internet should be blocked in Deep Focus.
     */
    public static boolean isKnownNonEssentialSystemApp(String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) {
            return false;
        }
        String pkg = packageName.trim().toLowerCase(Locale.US);
        for (String prefix : NON_ESSENTIAL_SYSTEM_PREFIXES) {
            if (pkg.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determines whether an installed app's internet traffic should be blocked by the Deep Focus
     * local firewall during an active Deep Focus session.
     */
    public static boolean shouldBlockInternet(
            String packageName,
            String ownPackageName,
            boolean isLaunchableApp,
            boolean isSystemApp,
            Set<String> userAllowedPackages
    ) {
        if (packageName == null || packageName.trim().isEmpty()) {
            return false;
        }
        if (isCoreEssentialPackage(packageName, ownPackageName)) {
            return false;
        }
        if (userAllowedPackages != null && userAllowedPackages.contains(packageName)) {
            return false;
        }
        if (isKnownNonEssentialSystemApp(packageName)) {
            return true;
        }
        if (!isSystemApp) {
            return true;
        }
        // Any user-facing preinstalled system app (browser, gallery, mail, calendar, etc.)
        // with a launcher icon is treated as non-essential unless whitelisted by the user.
        return isLaunchableApp;
    }
}
