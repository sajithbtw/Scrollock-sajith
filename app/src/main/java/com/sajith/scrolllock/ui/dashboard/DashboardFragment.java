package com.sajith.scrolllock.ui.dashboard;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.R;
import com.example.databinding.FragmentDashboardBinding;
import com.sajith.scrolllock.data.local.AppRepository;
import com.sajith.scrolllock.data.local.PreferencesManager;
import com.sajith.scrolllock.data.local.ScrollLockDatabaseHelper;
import com.sajith.scrolllock.data.model.FocusState;
import com.sajith.scrolllock.service.FocusSessionManager;
import com.sajith.scrolllock.ui.MainActivity;
import com.sajith.scrolllock.ui.OnboardingActivity;
import com.sajith.scrolllock.ui.ScrollSandboxActivity;
import com.sajith.scrolllock.ui.workout.RepReplayActivity;
import com.sajith.scrolllock.ui.workout.WorkoutCameraActivity;
import com.sajith.scrolllock.util.PermissionHelper;
import com.sajith.scrolllock.util.TimeFormatter;
import com.sajith.scrolllock.workout.ExerciseType;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Home Dashboard displaying greeting, focus & Deep Focus status, countdown ring,
 * AI Workout & SweatLock planner/challenge status, guarded app summary,
 * today's real device usage, and quick navigation.
 */
public class DashboardFragment extends Fragment {

    private FragmentDashboardBinding binding;
    private PreferencesManager preferencesManager;
    private FocusSessionManager sessionManager;
    private AppRepository appRepository;
    private ScrollLockDatabaseHelper dbHelper;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private boolean syncingGuardSwitch = false;
    private boolean syncingReelsSwitch = false;
    private boolean syncingDeepFocusSwitch = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        preferencesManager = new PreferencesManager(requireContext());
        sessionManager = FocusSessionManager.getInstance(requireContext());
        appRepository = new AppRepository(requireContext());
        dbHelper = ScrollLockDatabaseHelper.getInstance(requireContext());

        binding.tvGreeting.setText(TimeFormatter.getGreeting(requireContext()));

        setupObservers();
        setupClickListeners();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshDashboardSummaries();
    }

    private void setupObservers() {
        sessionManager.getFocusState().observe(getViewLifecycleOwner(), state -> renderTimerCard());
        sessionManager.getRemainingMillis().observe(getViewLifecycleOwner(), millis -> renderTimerCard());
    }

    private void renderTimerCard() {
        if (binding == null) {
            return;
        }
        FocusState state = sessionManager.getCurrentStateValue();
        long remaining = sessionManager.getCurrentRemainingMillisValue();
        long total = Math.max(1L, sessionManager.getCurrentTotalMillisValue());

        binding.tvDashboardTimer.setText(TimeFormatter.formatCountdown(remaining));
        float progress = state == FocusState.COMPLETED ? 0f : (float) remaining / (float) total;
        binding.focusRingDashboard.setProgress(progress);

        if (state == FocusState.RUNNING) {
            binding.layoutStatusPill.setBackgroundResource(R.drawable.bg_pill_accent_soft);
            binding.viewStatusDot.setVisibility(View.VISIBLE);
            binding.tvFocusStatus.setText(
                    preferencesManager.isDeepFocusEnabled()
                            ? R.string.status_deep_focus_active
                            : R.string.status_active
            );
            binding.btnDashboardStartFocus.setText(R.string.btn_open_session);
            binding.btnDashboardStartFocus.setIconResource(R.drawable.ic_nav_focus);
        } else if (state == FocusState.PAUSED) {
            binding.layoutStatusPill.setBackgroundResource(R.drawable.bg_pill_neutral);
            binding.viewStatusDot.setVisibility(View.VISIBLE);
            binding.tvFocusStatus.setText(R.string.status_paused);
            binding.btnDashboardStartFocus.setText(R.string.btn_resume_focus);
            binding.btnDashboardStartFocus.setIconResource(R.drawable.ic_play);
        } else {
            binding.layoutStatusPill.setBackgroundResource(R.drawable.bg_pill_neutral);
            binding.viewStatusDot.setVisibility(View.GONE);
            binding.tvFocusStatus.setText(R.string.status_ready);
            binding.btnDashboardStartFocus.setText(R.string.btn_start_focus);
            binding.btnDashboardStartFocus.setIconResource(R.drawable.ic_play);
        }
    }

    private void setupClickListeners() {
        binding.btnDashboardStartFocus.setOnClickListener(v -> {
            FocusState state = sessionManager.getCurrentStateValue();
            if (state == FocusState.IDLE || state == FocusState.COMPLETED) {
                sessionManager.startNewSession(preferencesManager.getDefaultDurationMinutes());
            } else if (state == FocusState.PAUSED) {
                sessionManager.resumeSession();
            }
            navigateTo(R.id.nav_focus);
        });

        binding.layoutDashboardDeepFocus.setOnClickListener(v -> navigateTo(R.id.nav_focus));

        binding.switchDashboardDeepFocus.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!syncingDeepFocusSwitch) {
                preferencesManager.setDeepFocusEnabled(isChecked);
                sessionManager.syncDeepFocusWithState();
                renderTimerCard();
            }
            renderDeepFocusStrip(isChecked);
        });

        binding.btnOpenPermissionsOnboarding.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), OnboardingActivity.class))
        );

        binding.tileReelsGuard.setOnClickListener(v -> {
            Set<String> shortFormApps = preferencesManager.getShortFormBlockedPackages();
            if (shortFormApps.isEmpty()) {
                navigateTo(R.id.nav_apps);
            } else {
                boolean next = !binding.switchDashboardReelsGuard.isChecked();
                binding.switchDashboardReelsGuard.setChecked(next);
            }
        });

        binding.switchDashboardReelsGuard.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!syncingReelsSwitch) {
                preferencesManager.setShortFormBlockerEnabled(isChecked);
                if (isChecked && preferencesManager.getShortFormBlockedPackages().isEmpty()) {
                    navigateTo(R.id.nav_apps);
                }
            }
            renderReelsTileState(isChecked);
        });

        binding.tileAppGuard.setOnClickListener(v -> {
            boolean next = !binding.switchDashboardAppGuard.isChecked();
            binding.switchDashboardAppGuard.setChecked(next);
        });

        binding.switchDashboardAppGuard.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!syncingGuardSwitch) {
                preferencesManager.setAlwaysGuardAppsEnabled(isChecked);
            }
            renderGuardTileState(isChecked);
        });

        binding.btnStartAiWorkout.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), WorkoutCameraActivity.class);
            intent.putExtra(WorkoutCameraActivity.EXTRA_TARGET_REPS, preferencesManager.getRequiredWorkoutReps());
            intent.putExtra(WorkoutCameraActivity.EXTRA_EXERCISE_TYPE, preferencesManager.getPreferredExerciseName());
            startActivity(intent);
        });

        binding.btnDashboardReplay.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), RepReplayActivity.class))
        );

        binding.cardGuardedApps.setOnClickListener(v -> navigateTo(R.id.nav_apps));
        binding.cardQuickInsights.setOnClickListener(v -> navigateTo(R.id.nav_insights));
        binding.cardQuickSettings.setOnClickListener(v -> navigateTo(R.id.nav_settings));

        binding.btnDashboardEnableUsage.setOnClickListener(v ->
                PermissionHelper.openUsageAccessSettings(requireContext())
        );

        binding.btnOpenSandbox.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), ScrollSandboxActivity.class))
        );
    }

    private void renderDeepFocusStrip(boolean enabled) {
        if (binding == null || !isAdded()) {
            return;
        }
        binding.tvDashboardDeepFocusBadge.setText(
                enabled ? R.string.deep_focus_badge_on : R.string.deep_focus_badge_off
        );
        binding.tvDashboardDeepFocusBadge.setTextColor(ContextCompat.getColor(
                requireContext(),
                enabled ? R.color.sl_accent_strong : R.color.sl_text_secondary
        ));
    }

    private void renderReelsTileState(boolean enabled) {
        if (binding == null || !isAdded()) {
            return;
        }
        int shortFormCount = preferencesManager.getShortFormBlockedPackages().size();
        boolean usageGranted = PermissionHelper.hasUsageStatsPermission(requireContext());
        if (enabled && shortFormCount > 0) {
            binding.tileReelsGuard.setBackgroundResource(R.drawable.bg_bento_tile_active);
            if (usageGranted) {
                binding.tvReelsTileState.setText(getString(R.string.reels_blocker_tile_active, shortFormCount));
            } else {
                binding.tvReelsTileState.setText(getString(R.string.reels_blocker_tile_needs_usage, shortFormCount));
            }
            binding.tvReelsTileState.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.sl_accent_strong));
        } else if (enabled) {
            binding.tileReelsGuard.setBackgroundResource(R.drawable.bg_card_minimal);
            binding.tvReelsTileState.setText(R.string.reels_blocker_tile_empty);
            binding.tvReelsTileState.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.sl_text_secondary));
        } else {
            binding.tileReelsGuard.setBackgroundResource(R.drawable.bg_card_minimal);
            binding.tvReelsTileState.setText(R.string.reels_blocker_tile_paused);
            binding.tvReelsTileState.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.sl_text_secondary));
        }
    }

    private void renderGuardTileState(boolean enabled) {
        if (binding == null || !isAdded()) {
            return;
        }
        if (enabled) {
            binding.tileAppGuard.setBackgroundResource(R.drawable.bg_bento_tile_active);
            binding.tvGuardTileState.setText("Always-Guard Active");
            binding.tvGuardTileState.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.sl_accent_strong));
        } else {
            binding.tileAppGuard.setBackgroundResource(R.drawable.bg_card_minimal);
            binding.tvGuardTileState.setText("Focus-Only Mode");
            binding.tvGuardTileState.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.sl_text_secondary));
        }
    }

    private void refreshDashboardSummaries() {
        if (binding == null || !isAdded()) {
            return;
        }

        // Permissions Banner visibility (Camera, Usage Access, Notifications — zero Accessibility)
        boolean hasCam = PermissionHelper.hasCameraPermission(requireContext());
        boolean hasUsage = PermissionHelper.hasUsageStatsPermission(requireContext());
        boolean hasNotif = PermissionHelper.hasNotificationPermission(requireContext());
        binding.cardPermissionsBanner.setVisibility((hasCam && hasUsage && hasNotif) ? View.GONE : View.VISIBLE);

        // Deep Focus Toggle Strip
        boolean deepFocusEnabled = preferencesManager.isDeepFocusEnabled();
        syncingDeepFocusSwitch = true;
        binding.switchDashboardDeepFocus.setChecked(deepFocusEnabled);
        syncingDeepFocusSwitch = false;
        renderDeepFocusStrip(deepFocusEnabled);
        renderTimerCard();

        // Reels & Shorts Blocker Tile
        boolean reelsEnabled = preferencesManager.isShortFormBlockerEnabled();
        syncingReelsSwitch = true;
        binding.switchDashboardReelsGuard.setChecked(reelsEnabled);
        syncingReelsSwitch = false;
        renderReelsTileState(reelsEnabled);

        // Workout & SweatLock Hub
        boolean guardEnabled = preferencesManager.isAlwaysGuardAppsEnabled();
        syncingGuardSwitch = true;
        binding.switchDashboardAppGuard.setChecked(guardEnabled);
        syncingGuardSwitch = false;
        renderGuardTileState(guardEnabled);

        int level = preferencesManager.getUserLevel();
        int xp = preferencesManager.getUserXp();
        int streak = preferencesManager.getWorkoutStreakDays();
        binding.tvWorkoutLevelXp.setText(String.format(
                Locale.getDefault(), "Lv. %d • %d XP • %dd streak", level, xp, streak
        ));

        int todayReps = dbHelper.getTodayWorkoutReps();
        int totalReps = dbHelper.getTotalWorkoutReps();
        int avgForm = dbHelper.getAverageWorkoutFormScore();
        int dailyGoal = 20;
        binding.tvDailyChallengeLabel.setText(String.format(
                Locale.getDefault(), "Daily Challenge: %d / %d reps today", todayReps, dailyGoal
        ));
        binding.progressDailyChallenge.setMax(dailyGoal);
        binding.progressDailyChallenge.setProgress(Math.min(todayReps, dailyGoal));

        String fitnessTier = totalReps >= 150 ? "Advanced" : (totalReps >= 40 ? "Intermediate" : "Starter");
        int suggestedReps = totalReps >= 150 ? 20 : (totalReps >= 40 ? 15 : 10);
        String exName = preferencesManager.getPreferredExerciseName();
        String exDisplay;
        try {
            exDisplay = ExerciseType.valueOf(exName).getDisplayName();
        } catch (Exception e) {
            exDisplay = "Squats";
        }
        binding.tvPersonalizedPlan.setText(String.format(
                Locale.getDefault(),
                "Assessment: %s (Avg Form %d%%) • Plan: %d %s",
                fitnessTier,
                avgForm,
                suggestedReps,
                exDisplay
        ));

        Set<String> selected = preferencesManager.getSelectedPackages();
        if (selected.isEmpty()) {
            binding.tvSelectedAppsCount.setText(R.string.label_no_apps_selected);
        } else {
            binding.tvSelectedAppsCount.setText(
                    getString(R.string.label_selected_count_format, selected.size())
            );
        }

        if (!hasUsage) {
            binding.tvUsageSummaryPrimary.setText(R.string.label_usage_permission_needed);
            binding.tvUsageSummarySecondary.setVisibility(View.GONE);
            binding.btnDashboardEnableUsage.setVisibility(View.VISIBLE);
        } else {
            binding.btnDashboardEnableUsage.setVisibility(View.GONE);
            binding.tvUsageSummarySecondary.setVisibility(View.VISIBLE);
            executor.execute(() -> {
                Map<String, Long> usageMap = appRepository.getTodayUsageByPackage();
                long guardedMillis = 0L;
                long totalMillis = 0L;
                for (Map.Entry<String, Long> entry : usageMap.entrySet()) {
                    long val = entry.getValue() != null ? entry.getValue() : 0L;
                    totalMillis += val;
                    if (selected.contains(entry.getKey())) {
                        guardedMillis += val;
                    }
                }
                final long finalGuarded = guardedMillis;
                final long finalTotal = totalMillis;
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        if (binding == null) {
                            return;
                        }
                        binding.tvUsageSummaryPrimary.setText(getString(
                                R.string.label_usage_total_format,
                                TimeFormatter.formatDurationShort(finalGuarded)
                        ));
                        binding.tvUsageSummarySecondary.setText(getString(
                                R.string.label_usage_device_format,
                                TimeFormatter.formatDurationShort(finalTotal)
                        ));
                    });
                }
            });
        }
    }

    private void navigateTo(int navId) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).navigateToTab(navId);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
