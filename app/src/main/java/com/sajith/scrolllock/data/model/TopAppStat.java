package com.sajith.scrolllock.data.model;

/**
 * Aggregated statistics for a guarded app tracked locally in SQLite.
 */
public class TopAppStat {
    private final String packageName;
    private final String appName;
    private final int timesSelected;
    private final int timesInterrupted;

    public TopAppStat(String packageName, String appName, int timesSelected, int timesInterrupted) {
        this.packageName = packageName;
        this.appName = appName;
        this.timesSelected = timesSelected;
        this.timesInterrupted = timesInterrupted;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getAppName() {
        return appName;
    }

    public int getTimesSelected() {
        return timesSelected;
    }

    public int getTimesInterrupted() {
        return timesInterrupted;
    }
}
