package com.sajith.scrolllock.data.model;

/**
 * Represents a persisted focus session saved in the local SQLite database.
 */
public class FocusSessionRecord {
    private final long id;
    private final long startTimeMillis;
    private final long endTimeMillis;
    private final int plannedMinutes;
    private final int actualSeconds;
    private final boolean completed;
    private final int interruptedCount;
    private final String dayKey;

    public FocusSessionRecord(
            long id,
            long startTimeMillis,
            long endTimeMillis,
            int plannedMinutes,
            int actualSeconds,
            boolean completed,
            int interruptedCount,
            String dayKey
    ) {
        this.id = id;
        this.startTimeMillis = startTimeMillis;
        this.endTimeMillis = endTimeMillis;
        this.plannedMinutes = plannedMinutes;
        this.actualSeconds = actualSeconds;
        this.completed = completed;
        this.interruptedCount = interruptedCount;
        this.dayKey = dayKey;
    }

    public long getId() {
        return id;
    }

    public long getStartTimeMillis() {
        return startTimeMillis;
    }

    public long getEndTimeMillis() {
        return endTimeMillis;
    }

    public int getPlannedMinutes() {
        return plannedMinutes;
    }

    public int getActualSeconds() {
        return actualSeconds;
    }

    public boolean isCompleted() {
        return completed;
    }

    public int getInterruptedCount() {
        return interruptedCount;
    }

    public String getDayKey() {
        return dayKey;
    }
}
