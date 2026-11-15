package com.sajith.scrolllock.service;

import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.sajith.scrolllock.data.local.PreferencesManager;
import com.sajith.scrolllock.data.local.ScrollLockDatabaseHelper;
import com.sajith.scrolllock.data.model.FocusState;

/**
 * Coordinates the active Focus Session state across UI screens, Foreground Service,
 * DeepFocusController, and UsageStatsGuardMonitor using persisted SharedPreferences and LiveData.
 */
public class FocusSessionManager {

    private static FocusSessionManager instance;

    private final Context appContext;
    private final PreferencesManager prefs;
    private final ScrollLockDatabaseHelper dbHelper;

    private final MutableLiveData<FocusState> focusStateLiveData = new MutableLiveData<>(FocusState.IDLE);
    private final MutableLiveData<Long> remainingMillisLiveData = new MutableLiveData<>(25 * 60_000L);
    private final MutableLiveData<Long> totalMillisLiveData = new MutableLiveData<>(25 * 60_000L);
    private final MutableLiveData<Integer> sessionInterruptionsLiveData = new MutableLiveData<>(0);

    private long startEpochMillis = 0L;
    private long endEpochMillis = 0L;

    public static synchronized FocusSessionManager getInstance(Context context) {
        if (instance == null) {
            instance = new FocusSessionManager(context.getApplicationContext());
        }
        return instance;
    }

    private FocusSessionManager(Context context) {
        this.appContext = context;
        this.prefs = new PreferencesManager(context);
        this.dbHelper = ScrollLockDatabaseHelper.getInstance(context);
        restorePersistedState();
    }

    /**
     * Restores timer progress if the app was closed or rotated while a session was active or paused.
     */
    public synchronized void restorePersistedState() {
        FocusState savedState = prefs.getFocusState();
        long savedTotal = prefs.getSessionTotalMillis();
        long savedRemaining = prefs.getSessionRemainingMillis();
        long savedStart = prefs.getSessionStartEpochMillis();
        long savedEnd = prefs.getSessionEndEpochMillis();
        int savedInterruptions = prefs.getSessionInterruptions();

        if (savedState == FocusState.RUNNING) {
            long now = System.currentTimeMillis();
            long recalculatedRemaining = savedEnd - now;
            if (recalculatedRemaining <= 0L) {
                completeSessionInternal(savedTotal, savedStart, savedEnd, savedInterruptions);
                return;
            }
            this.startEpochMillis = savedStart;
            this.endEpochMillis = savedEnd;
            focusStateLiveData.postValue(FocusState.RUNNING);
            totalMillisLiveData.postValue(savedTotal);
            remainingMillisLiveData.postValue(recalculatedRemaining);
            sessionInterruptionsLiveData.postValue(savedInterruptions);
            DeepFocusController.engage(appContext);
            startForegroundTimerService();
        } else if (savedState == FocusState.PAUSED) {
            this.startEpochMillis = savedStart;
            this.endEpochMillis = 0L;
            focusStateLiveData.postValue(FocusState.PAUSED);
            totalMillisLiveData.postValue(savedTotal);
            remainingMillisLiveData.postValue(savedRemaining);
            sessionInterruptionsLiveData.postValue(savedInterruptions);
            DeepFocusController.reconcileIfIdle(appContext);
        } else {
            long defaultMillis = prefs.getDefaultDurationMinutes() * 60_000L;
            focusStateLiveData.postValue(FocusState.IDLE);
            totalMillisLiveData.postValue(defaultMillis);
            remainingMillisLiveData.postValue(defaultMillis);
            sessionInterruptionsLiveData.postValue(0);
            DeepFocusController.reconcileIfIdle(appContext);
        }
    }

    public LiveData<FocusState> getFocusState() {
        return focusStateLiveData;
    }

    public LiveData<Long> getRemainingMillis() {
        return remainingMillisLiveData;
    }

    public LiveData<Long> getTotalMillis() {
        return totalMillisLiveData;
    }

    public LiveData<Integer> getSessionInterruptions() {
        return sessionInterruptionsLiveData;
    }

    public FocusState getCurrentStateValue() {
        FocusState value = focusStateLiveData.getValue();
        return value != null ? value : FocusState.IDLE;
    }

    public long getCurrentRemainingMillisValue() {
        Long value = remainingMillisLiveData.getValue();
        return value != null ? value : prefs.getDefaultDurationMinutes() * 60_000L;
    }

    public long getCurrentTotalMillisValue() {
        Long value = totalMillisLiveData.getValue();
        return value != null ? value : prefs.getDefaultDurationMinutes() * 60_000L;
    }

    public int getCurrentInterruptionsValue() {
        Integer value = sessionInterruptionsLiveData.getValue();
        return value != null ? value : 0;
    }

    public synchronized void setPresetDurationMinutes(int minutes) {
        if (getCurrentStateValue() == FocusState.RUNNING || getCurrentStateValue() == FocusState.PAUSED) {
            return;
        }
        long millis = minutes * 60_000L;
        focusStateLiveData.setValue(FocusState.IDLE);
        totalMillisLiveData.setValue(millis);
        remainingMillisLiveData.setValue(millis);
        sessionInterruptionsLiveData.setValue(0);
        prefs.saveTimerState(FocusState.IDLE, millis, millis, 0L, 0L, 0);
    }

    public synchronized void startNewSession(int durationMinutes) {
        long totalMillis = durationMinutes * 60_000L;
        long now = System.currentTimeMillis();
        this.startEpochMillis = now;
        this.endEpochMillis = now + totalMillis;

        focusStateLiveData.setValue(FocusState.RUNNING);
        totalMillisLiveData.setValue(totalMillis);
        remainingMillisLiveData.setValue(totalMillis);
        sessionInterruptionsLiveData.setValue(0);

        prefs.saveTimerState(FocusState.RUNNING, totalMillis, totalMillis, startEpochMillis, endEpochMillis, 0);
        DeepFocusController.engage(appContext);
        startForegroundTimerService();
    }

    public synchronized void pauseSession() {
        if (getCurrentStateValue() != FocusState.RUNNING) {
            return;
        }
        long now = System.currentTimeMillis();
        long remaining = Math.max(0L, endEpochMillis - now);
        long total = getCurrentTotalMillisValue();
        int interruptions = getCurrentInterruptionsValue();

        focusStateLiveData.setValue(FocusState.PAUSED);
        remainingMillisLiveData.setValue(remaining);
        prefs.saveTimerState(FocusState.PAUSED, total, remaining, startEpochMillis, 0L, interruptions);
        DeepFocusController.disengage(appContext);

        Intent intent = new Intent(appContext, FocusTimerService.class);
        intent.setAction(FocusTimerService.ACTION_PAUSE);
        appContext.startService(intent);
    }

    public synchronized void resumeSession() {
        if (getCurrentStateValue() != FocusState.PAUSED) {
            return;
        }
        long remaining = getCurrentRemainingMillisValue();
        if (remaining <= 0L) {
            resetSession();
            return;
        }
        long total = getCurrentTotalMillisValue();
        long now = System.currentTimeMillis();
        this.endEpochMillis = now + remaining;
        int interruptions = getCurrentInterruptionsValue();

        focusStateLiveData.setValue(FocusState.RUNNING);
        prefs.saveTimerState(FocusState.RUNNING, total, remaining, startEpochMillis, endEpochMillis, interruptions);
        DeepFocusController.engage(appContext);
        startForegroundTimerService();
    }

    public synchronized void resetSession() {
        long defaultMillis = prefs.getDefaultDurationMinutes() * 60_000L;
        this.startEpochMillis = 0L;
        this.endEpochMillis = 0L;

        focusStateLiveData.setValue(FocusState.IDLE);
        totalMillisLiveData.setValue(defaultMillis);
        remainingMillisLiveData.setValue(defaultMillis);
        sessionInterruptionsLiveData.setValue(0);
        prefs.clearTimerStateToIdle();
        DeepFocusController.disengage(appContext);

        Intent intent = new Intent(appContext, FocusTimerService.class);
        intent.setAction(FocusTimerService.ACTION_STOP);
        appContext.startService(intent);
    }

    /**
     * Called every tick by FocusTimerService to update remaining countdown.
     */
    public synchronized void onTimerTick() {
        if (getCurrentStateValue() != FocusState.RUNNING) {
            return;
        }
        long now = System.currentTimeMillis();
        long remaining = endEpochMillis - now;
        if (remaining <= 0L) {
            completeSessionInternal(
                    getCurrentTotalMillisValue(),
                    startEpochMillis,
                    now,
                    getCurrentInterruptionsValue()
            );
        } else {
            remainingMillisLiveData.postValue(remaining);
            prefs.saveTimerState(
                    FocusState.RUNNING,
                    getCurrentTotalMillisValue(),
                    remaining,
                    startEpochMillis,
                    endEpochMillis,
                    getCurrentInterruptionsValue()
            );
        }
    }

    private void completeSessionInternal(long totalMillis, long startEpoch, long endEpoch, int interruptions) {
        int plannedMinutes = (int) Math.max(1L, totalMillis / 60_000L);
        int actualSeconds = (int) Math.max(1L, totalMillis / 1000L);
        dbHelper.insertCompletedSession(startEpoch, endEpoch, plannedMinutes, actualSeconds, interruptions);

        focusStateLiveData.postValue(FocusState.COMPLETED);
        remainingMillisLiveData.postValue(0L);
        totalMillisLiveData.postValue(totalMillis);
        sessionInterruptionsLiveData.postValue(interruptions);

        long defaultMillis = prefs.getDefaultDurationMinutes() * 60_000L;
        prefs.saveTimerState(FocusState.IDLE, defaultMillis, defaultMillis, 0L, 0L, 0);
        DeepFocusController.disengage(appContext);

        Intent intent = new Intent(appContext, FocusTimerService.class);
        intent.setAction(FocusTimerService.ACTION_COMPLETE);
        appContext.startService(intent);
    }

    public synchronized void syncDeepFocusWithState() {
        DeepFocusController.syncWithSessionState(appContext, getCurrentStateValue());
    }

    public synchronized void incrementSessionInterruption(String packageName, String appName) {
        int updated = getCurrentInterruptionsValue() + 1;
        sessionInterruptionsLiveData.postValue(updated);
        dbHelper.recordAppInterrupted(packageName, appName);

        if (getCurrentStateValue() == FocusState.RUNNING) {
            prefs.saveTimerState(
                    FocusState.RUNNING,
                    getCurrentTotalMillisValue(),
                    getCurrentRemainingMillisValue(),
                    startEpochMillis,
                    endEpochMillis,
                    updated
            );
        }
    }

    private void startForegroundTimerService() {
        Intent intent = new Intent(appContext, FocusTimerService.class);
        intent.setAction(FocusTimerService.ACTION_START_OR_RESUME);
        ContextCompat.startForegroundService(appContext, intent);
    }
}
