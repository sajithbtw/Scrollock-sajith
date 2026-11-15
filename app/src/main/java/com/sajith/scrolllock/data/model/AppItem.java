package com.sajith.scrolllock.data.model;

import android.graphics.drawable.Drawable;

/**
 * Represents an installed launchable application displayed on the App Selection screen.
 */
public class AppItem {
    private final String packageName;
    private final String appName;
    private final Drawable icon;
    private final long todayUsageMillis;
    private boolean selected;
    private boolean shortFormBlocked;

    public AppItem(String packageName, String appName, Drawable icon, long todayUsageMillis, boolean selected) {
        this(packageName, appName, icon, todayUsageMillis, selected, false);
    }

    public AppItem(
            String packageName,
            String appName,
            Drawable icon,
            long todayUsageMillis,
            boolean selected,
            boolean shortFormBlocked
    ) {
        this.packageName = packageName;
        this.appName = appName;
        this.icon = icon;
        this.todayUsageMillis = todayUsageMillis;
        this.selected = selected;
        this.shortFormBlocked = shortFormBlocked;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getAppName() {
        return appName;
    }

    public Drawable getIcon() {
        return icon;
    }

    public long getTodayUsageMillis() {
        return todayUsageMillis;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public boolean isShortFormBlocked() {
        return shortFormBlocked;
    }

    public void setShortFormBlocked(boolean shortFormBlocked) {
        this.shortFormBlocked = shortFormBlocked;
    }
}
