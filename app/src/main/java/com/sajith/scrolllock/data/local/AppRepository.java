package com.sajith.scrolllock.data.local;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import com.sajith.scrolllock.data.model.AppItem;
import com.sajith.scrolllock.util.PermissionHelper;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads installed launchable apps and merges real local usage statistics from UsageStatsManager.
 */
public class AppRepository {

    private final Context context;
    private final PackageManager packageManager;
    private final PreferencesManager preferencesManager;

    public AppRepository(Context context) {
        this.context = context.getApplicationContext();
        this.packageManager = this.context.getPackageManager();
        this.preferencesManager = new PreferencesManager(this.context);
    }

    /**
     * Loads all launchable apps (excluding ScrollLock itself), sorted with selected apps first,
     * then alphabetically by app name.
     */
    public List<AppItem> getLaunchableApps() {
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> resolvedList = packageManager.queryIntentActivities(mainIntent, 0);
        Set<String> selectedPackages = preferencesManager.getSelectedPackages();
        Set<String> shortFormPackages = preferencesManager.getShortFormBlockedPackages();
        Map<String, Long> todayUsageMap = getTodayUsageByPackage();

        Set<String> seenPackages = new HashSet<>();
        List<AppItem> items = new ArrayList<>();
        String ownPackage = context.getPackageName();

        for (ResolveInfo info : resolvedList) {
            if (info.activityInfo == null || info.activityInfo.packageName == null) {
                continue;
            }
            String pkg = info.activityInfo.packageName;
            if (pkg.equals(ownPackage) || seenPackages.contains(pkg)) {
                continue;
            }
            seenPackages.add(pkg);

            CharSequence labelSeq = info.loadLabel(packageManager);
            String label = labelSeq != null ? labelSeq.toString().trim() : pkg;
            Drawable icon = null;
            try {
                icon = info.loadIcon(packageManager);
            } catch (Exception ignored) {
                // Gracefully fallback if an app icon cannot be loaded
            }
            long usageMillis = todayUsageMap.containsKey(pkg) ? todayUsageMap.get(pkg) : 0L;
            boolean isSelected = selectedPackages.contains(pkg);
            boolean isShortFormBlocked = shortFormPackages.contains(pkg);

            items.add(new AppItem(pkg, label, icon, usageMillis, isSelected, isShortFormBlocked));
        }

        Collections.sort(items, (a, b) -> {
            boolean aActive = a.isSelected() || a.isShortFormBlocked();
            boolean bActive = b.isSelected() || b.isShortFormBlocked();
            if (aActive != bActive) {
                return aActive ? -1 : 1;
            }
            if (a.isSelected() != b.isSelected()) {
                return a.isSelected() ? -1 : 1;
            }
            return a.getAppName().compareToIgnoreCase(b.getAppName());
        });

        return items;
    }

    /**
     * Queries today's real foreground usage time per package using UsageStatsManager.
     * Returns an empty map if Usage Access is not granted (never returns fake data).
     */
    public Map<String, Long> getTodayUsageByPackage() {
        Map<String, Long> usageMap = new HashMap<>();
        if (!PermissionHelper.hasUsageStatsPermission(context)) {
            return usageMap;
        }

        UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) {
            return usageMap;
        }

        Calendar cal = Calendar.getInstance();
        long endTime = cal.getTimeInMillis();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long startTime = cal.getTimeInMillis();

        List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime);
        if (stats == null) {
            return usageMap;
        }

        for (UsageStats stat : stats) {
            if (stat == null || stat.getPackageName() == null) {
                continue;
            }
            long timeInForeground = stat.getTotalTimeInForeground();
            if (timeInForeground > 0L) {
                Long existing = usageMap.get(stat.getPackageName());
                usageMap.put(stat.getPackageName(), (existing == null ? 0L : existing) + timeInForeground);
            }
        }
        return usageMap;
    }

    /**
     * Resolves a human-readable app label from a package name safely.
     */
    public String getAppLabel(String packageName) {
        try {
            ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
            CharSequence label = packageManager.getApplicationLabel(appInfo);
            return label != null ? label.toString() : packageName;
        } catch (PackageManager.NameNotFoundException e) {
            return packageName;
        }
    }

    /**
     * Resolves an app icon safely, or null if the app is no longer installed.
     */
    public Drawable getAppIcon(String packageName) {
        try {
            return packageManager.getApplicationIcon(packageName);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    /**
     * Identifies whether an installed app belongs to Social, Games, or Video distraction categories.
     */
    public boolean isSocialOrGamePackage(String packageName) {
        try {
            ApplicationInfo info = packageManager.getApplicationInfo(packageName, 0);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                int cat = info.category;
                if (cat == ApplicationInfo.CATEGORY_SOCIAL
                        || cat == ApplicationInfo.CATEGORY_GAME
                        || cat == ApplicationInfo.CATEGORY_VIDEO) {
                    return true;
                }
            }
            if ((info.flags & ApplicationInfo.FLAG_IS_GAME) != 0) {
                return true;
            }
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
        String lower = packageName.toLowerCase(java.util.Locale.US);
        return lower.contains("instagram") || lower.contains("tiktok") || lower.contains("musically")
                || lower.contains("youtube") || lower.contains("facebook") || lower.contains("snapchat")
                || lower.contains("twitter") || lower.contains("reddit") || lower.contains("threads")
                || lower.contains("game");
    }

    /**
     * Identifies installed apps that host Reels, Shorts, Spotlight, or vertical short-form video feeds.
     */
    public boolean isShortFormHostPackage(String packageName) {
        if (packageName == null) {
            return false;
        }
        String lower = packageName.toLowerCase(java.util.Locale.US);
        return lower.contains("instagram")
                || lower.contains("youtube")
                || lower.contains("musically")
                || lower.contains("tiktok")
                || lower.contains("ugc.trill")
                || lower.contains("ugc.aweme")
                || lower.contains("facebook.katana")
                || lower.contains("facebook.lite")
                || lower.contains("snapchat")
                || lower.contains("reddit")
                || lower.contains("twitter")
                || lower.contains("barcelona")
                || lower.contains("mohalla")
                || lower.contains("eterno.shortvideos")
                || lower.contains("video.like")
                || lower.contains("kwai");
    }
}
