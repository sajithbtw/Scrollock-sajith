package com.sajith.scrolllock.ui.settings;

import android.Manifest;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.BuildConfig;
import com.example.R;
import com.example.databinding.FragmentSettingsBinding;
import com.sajith.scrolllock.ScrollLockApp;
import com.sajith.scrolllock.data.local.PreferencesManager;
import com.sajith.scrolllock.data.model.FocusState;
import com.sajith.scrolllock.service.FocusSessionManager;
import com.sajith.scrolllock.ui.OnboardingActivity;
import com.sajith.scrolllock.ui.ScrollSandboxActivity;
import com.sajith.scrolllock.util.PermissionHelper;
import com.sajith.scrolllock.workout.ExerciseType;

/**
 * Settings screen managing Theme persistence, default Focus duration,
 * SweatLock Workout-to-Unlock parameters (exercise, reps, unlock duration, Voice Coach, Ghost Trainer),
 * transparent Android permission status & onboarding guide, platform boundaries, and local privacy assurances.
 */
public class SettingsFragment extends Fragment {

    private FragmentSettingsBinding binding;
    private PreferencesManager preferencesManager;
    private FocusSessionManager sessionManager;
    private boolean initializingControls = false;

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted ->
                    refreshPermissionStatuses()
            );

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        preferencesManager = new PreferencesManager(requireContext());
        sessionManager = FocusSessionManager.getInstance(requireContext());

        binding.tvAppVersion.setText(getString(R.string.settings_version_format, BuildConfig.VERSION_NAME));

        syncPreferenceChips();
        setupListeners();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshPermissionStatuses();
    }

    private void syncPreferenceChips() {
        initializingControls = true;

        int themeMode = preferencesManager.getThemeMode();
        if (themeMode == PreferencesManager.THEME_LIGHT) {
            binding.chipGroupTheme.check(R.id.chip_theme_light);
        } else if (themeMode == PreferencesManager.THEME_DARK) {
            binding.chipGroupTheme.check(R.id.chip_theme_dark);
        } else {
            binding.chipGroupTheme.check(R.id.chip_theme_system);
        }

        int defaultDuration = preferencesManager.getDefaultDurationMinutes();
        if (defaultDuration == 15) {
            binding.chipGroupDefaultDuration.check(R.id.chip_default_15);
        } else if (defaultDuration == 45) {
            binding.chipGroupDefaultDuration.check(R.id.chip_default_45);
        } else if (defaultDuration == 60) {
            binding.chipGroupDefaultDuration.check(R.id.chip_default_60);
        } else {
            binding.chipGroupDefaultDuration.check(R.id.chip_default_25);
        }

        // SweatLock & Reels Blocker Switches
        binding.switchSettingsReelsBlocker.setChecked(preferencesManager.isShortFormBlockerEnabled());
        binding.switchSettingsAlwaysGuard.setChecked(preferencesManager.isAlwaysGuardAppsEnabled());
        binding.switchSettingsWorkoutUnlock.setChecked(preferencesManager.isWorkoutUnlockEnabled());
        binding.switchSettingsVoiceCoach.setChecked(preferencesManager.isVoiceCoachEnabled());
        binding.switchSettingsGhostTrainer.setChecked(preferencesManager.isGhostTrainerEnabled());

        // Exercise ChipGroup
        String ex = preferencesManager.getPreferredExerciseName();
        if (ExerciseType.AUTO.name().equals(ex)) {
            binding.chipGroupExercise.check(R.id.chip_ex_auto);
        } else if (ExerciseType.PUSHUPS.name().equals(ex)) {
            binding.chipGroupExercise.check(R.id.chip_ex_pushups);
        } else if (ExerciseType.LUNGES.name().equals(ex)) {
            binding.chipGroupExercise.check(R.id.chip_ex_lunges);
        } else if (ExerciseType.JUMPING_JACKS.name().equals(ex)) {
            binding.chipGroupExercise.check(R.id.chip_ex_jacks);
        } else if (ExerciseType.PLANKS.name().equals(ex)) {
            binding.chipGroupExercise.check(R.id.chip_ex_planks);
        } else {
            binding.chipGroupExercise.check(R.id.chip_ex_squats);
        }

        // Required Reps ChipGroup
        int reps = preferencesManager.getRequiredWorkoutReps();
        if (reps == 5) {
            binding.chipGroupRequiredReps.check(R.id.chip_reps_5);
        } else if (reps == 15) {
            binding.chipGroupRequiredReps.check(R.id.chip_reps_15);
        } else if (reps == 20) {
            binding.chipGroupRequiredReps.check(R.id.chip_reps_20);
        } else {
            binding.chipGroupRequiredReps.check(R.id.chip_reps_10);
        }

        // Unlock Duration ChipGroup
        int unlockMin = preferencesManager.getUnlockDurationMinutes();
        if (unlockMin == 5) {
            binding.chipGroupUnlockDuration.check(R.id.chip_unlock_5);
        } else if (unlockMin == 30) {
            binding.chipGroupUnlockDuration.check(R.id.chip_unlock_30);
        } else {
            binding.chipGroupUnlockDuration.check(R.id.chip_unlock_15);
        }

        initializingControls = false;
    }

    private void setupListeners() {
        binding.chipGroupTheme.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (initializingControls || checkedIds.isEmpty()) {
                return;
            }
            int id = checkedIds.get(0);
            int selectedMode;
            if (id == R.id.chip_theme_light) {
                selectedMode = PreferencesManager.THEME_LIGHT;
            } else if (id == R.id.chip_theme_dark) {
                selectedMode = PreferencesManager.THEME_DARK;
            } else {
                selectedMode = PreferencesManager.THEME_SYSTEM;
            }
            if (selectedMode != preferencesManager.getThemeMode()) {
                preferencesManager.setThemeMode(selectedMode);
                ScrollLockApp.applyThemeMode(selectedMode);
            }
        });

        binding.chipGroupDefaultDuration.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (initializingControls || checkedIds.isEmpty()) {
                return;
            }
            int id = checkedIds.get(0);
            int minutes = 25;
            if (id == R.id.chip_default_15) {
                minutes = 15;
            } else if (id == R.id.chip_default_45) {
                minutes = 45;
            } else if (id == R.id.chip_default_60) {
                minutes = 60;
            }
            preferencesManager.setDefaultDurationMinutes(minutes);
            if (sessionManager.getCurrentStateValue() == FocusState.IDLE
                    || sessionManager.getCurrentStateValue() == FocusState.COMPLETED) {
                sessionManager.setPresetDurationMinutes(minutes);
            }
        });

        binding.switchSettingsReelsBlocker.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!initializingControls) {
                preferencesManager.setShortFormBlockerEnabled(isChecked);
            }
        });

        binding.switchSettingsAlwaysGuard.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!initializingControls) {
                preferencesManager.setAlwaysGuardAppsEnabled(isChecked);
            }
        });

        binding.switchSettingsWorkoutUnlock.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!initializingControls) {
                preferencesManager.setWorkoutUnlockEnabled(isChecked);
            }
        });

        binding.switchSettingsVoiceCoach.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!initializingControls) {
                preferencesManager.setVoiceCoachEnabled(isChecked);
            }
        });

        binding.switchSettingsGhostTrainer.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!initializingControls) {
                preferencesManager.setGhostTrainerEnabled(isChecked);
            }
        });

        binding.chipGroupExercise.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (initializingControls || checkedIds.isEmpty()) {
                return;
            }
            int id = checkedIds.get(0);
            ExerciseType type = ExerciseType.SQUATS;
            if (id == R.id.chip_ex_auto) {
                type = ExerciseType.AUTO;
            } else if (id == R.id.chip_ex_pushups) {
                type = ExerciseType.PUSHUPS;
            } else if (id == R.id.chip_ex_lunges) {
                type = ExerciseType.LUNGES;
            } else if (id == R.id.chip_ex_jacks) {
                type = ExerciseType.JUMPING_JACKS;
            } else if (id == R.id.chip_ex_planks) {
                type = ExerciseType.PLANKS;
            }
            preferencesManager.setPreferredExerciseName(type.name());
        });

        binding.chipGroupRequiredReps.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (initializingControls || checkedIds.isEmpty()) {
                return;
            }
            int id = checkedIds.get(0);
            int reps = 10;
            if (id == R.id.chip_reps_5) {
                reps = 5;
            } else if (id == R.id.chip_reps_15) {
                reps = 15;
            } else if (id == R.id.chip_reps_20) {
                reps = 20;
            }
            preferencesManager.setRequiredWorkoutReps(reps);
        });

        binding.chipGroupUnlockDuration.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (initializingControls || checkedIds.isEmpty()) {
                return;
            }
            int id = checkedIds.get(0);
            int duration = 15;
            if (id == R.id.chip_unlock_5) {
                duration = 5;
            } else if (id == R.id.chip_unlock_30) {
                duration = 30;
            }
            preferencesManager.setUnlockDurationMinutes(duration);
        });

        binding.btnSettingsOpenOnboarding.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), OnboardingActivity.class))
        );

        binding.btnSetupDnd.setOnClickListener(v ->
                PermissionHelper.openNotificationPolicySettings(requireContext())
        );

        binding.btnSetupUsage.setOnClickListener(v ->
                PermissionHelper.openUsageAccessSettings(requireContext())
        );

        binding.btnSetupNotifications.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && !PermissionHelper.hasNotificationPermission(requireContext())) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            } else {
                PermissionHelper.openAppNotificationSettings(requireContext());
            }
        });

        binding.btnSettingsOpenSandbox.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), ScrollSandboxActivity.class))
        );
    }

    private void refreshPermissionStatuses() {
        if (binding == null || !isAdded()) {
            return;
        }
        updateBadge(binding.tvDndStatus, PermissionHelper.hasNotificationPolicyAccess(requireContext()));
        updateBadge(binding.tvUsageStatus, PermissionHelper.hasUsageStatsPermission(requireContext()));

        boolean notifGranted = PermissionHelper.hasNotificationPermission(requireContext());
        updateBadge(binding.tvNotificationsStatus, notifGranted);
        binding.btnSetupNotifications.setText(notifGranted ? R.string.btn_open_settings : R.string.btn_grant_permission);
    }

    private void updateBadge(TextView badgeView, boolean enabled) {
        if (enabled) {
            badgeView.setText(R.string.status_enabled);
            badgeView.setBackgroundResource(R.drawable.bg_pill_accent_soft);
            badgeView.setTextColor(ContextCompat.getColor(requireContext(), R.color.sl_accent_strong));
        } else {
            badgeView.setText(R.string.status_not_enabled);
            badgeView.setBackgroundResource(R.drawable.bg_pill_neutral);
            badgeView.setTextColor(ContextCompat.getColor(requireContext(), R.color.sl_text_secondary));
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
