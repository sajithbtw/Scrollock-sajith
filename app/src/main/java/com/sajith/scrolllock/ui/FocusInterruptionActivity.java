package com.sajith.scrolllock.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.R;
import com.example.databinding.ActivityFocusInterruptionBinding;
import com.sajith.scrolllock.data.local.PreferencesManager;
import com.sajith.scrolllock.data.model.FocusState;
import com.sajith.scrolllock.service.FocusSessionManager;
import com.sajith.scrolllock.ui.workout.WorkoutCameraActivity;
import com.sajith.scrolllock.util.TimeFormatter;
import com.sajith.scrolllock.workout.ExerciseType;

/**
 * Interruption screen displayed when UsageStatsGuardMonitor's Focus Guard alert or the user
 * triggers a workout-to-unlock gate for a distracting app or Reels/Shorts host app.
 * Allows users to complete an AI-verified workout to earn temporary unlock time.
 */
public class FocusInterruptionActivity extends AppCompatActivity {

    public static final String EXTRA_BLOCKED_PKG = "extra_blocked_pkg";
    public static final String EXTRA_BLOCKED_APP_NAME = "extra_blocked_app_name";
    public static final String EXTRA_BLOCK_MODE = "extra_block_mode";
    public static final String EXTRA_BLOCK_SURFACE = "extra_block_surface";

    public static final String MODE_APP_LOCK = "app_lock";
    public static final String MODE_SHORT_FORM = "short_form";

    private ActivityFocusInterruptionBinding binding;
    private FocusSessionManager sessionManager;
    private PreferencesManager preferencesManager;

    private String blockedPackageName = "";
    private String blockedAppName = "App";
    private String blockMode = MODE_APP_LOCK;
    private String blockedSurface = "";
    private boolean startedWhileFocusRunning = false;

    private final ActivityResultLauncher<Intent> workoutUnlockLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    // User earned unlock duration; exit interruption overlay cleanly
                    finish();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        binding = ActivityFocusInterruptionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.interruptionRoot, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int pad = (int) (24 * getResources().getDisplayMetrics().density);
            v.setPadding(pad + bars.left, pad + bars.top, pad + bars.right, pad + bars.bottom);
            return insets;
        });

        sessionManager = FocusSessionManager.getInstance(this);
        preferencesManager = new PreferencesManager(this);
        startedWhileFocusRunning = sessionManager.getCurrentStateValue() == FocusState.RUNNING;

        updateBlockedAppLabel(getIntent());
        configureWorkoutPrompt();

        sessionManager.getRemainingMillis().observe(this, remaining -> {
            boolean focusRunning = sessionManager.getCurrentStateValue() == FocusState.RUNNING;
            if (focusRunning) {
                binding.tvInterruptionTimerLabel.setVisibility(View.VISIBLE);
                binding.tvInterruptionTimer.setVisibility(View.VISIBLE);
                binding.tvInterruptionTimer.setText(
                        TimeFormatter.formatCountdown(remaining != null ? remaining : 0L)
                );
            } else {
                binding.tvInterruptionTimerLabel.setText("WORKOUT GUARD ACTIVE");
                binding.tvInterruptionTimer.setVisibility(View.GONE);
            }
        });

        sessionManager.getFocusState().observe(this, state -> {
            if (startedWhileFocusRunning && state == FocusState.COMPLETED) {
                returnToScrollLock(R.id.nav_focus);
            }
        });

        binding.btnStartWorkoutUnlock.setOnClickListener(v -> launchWorkoutToUnlock());
        binding.btnReturnToFocus.setOnClickListener(v -> returnToScrollLock(R.id.nav_focus));
        binding.btnOpenDashboard.setOnClickListener(v -> returnToScrollLock(R.id.nav_home));

        // Pressing Back returns safely to ScrollLock instead of dropping back into the distracted app
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                returnToScrollLock(R.id.nav_home);
            }
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        startedWhileFocusRunning = sessionManager.getCurrentStateValue() == FocusState.RUNNING;
        updateBlockedAppLabel(intent);
        configureWorkoutPrompt();
    }

    private void updateBlockedAppLabel(Intent intent) {
        blockedPackageName = intent != null ? intent.getStringExtra(EXTRA_BLOCKED_PKG) : "";
        if (blockedPackageName == null) {
            blockedPackageName = "";
        }
        String appName = intent != null ? intent.getStringExtra(EXTRA_BLOCKED_APP_NAME) : null;
        if (appName == null || appName.trim().isEmpty()) {
            appName = getString(R.string.label_selected_apps);
        }
        blockedAppName = appName;

        String mode = intent != null ? intent.getStringExtra(EXTRA_BLOCK_MODE) : null;
        blockMode = MODE_SHORT_FORM.equals(mode) ? MODE_SHORT_FORM : MODE_APP_LOCK;

        String surface = intent != null ? intent.getStringExtra(EXTRA_BLOCK_SURFACE) : null;
        blockedSurface = (surface != null && !surface.trim().isEmpty()) ? surface.trim() : (appName + " Reels/Shorts");

        if (MODE_SHORT_FORM.equals(blockMode)) {
            binding.tvInterruptionBadgeText.setText(R.string.interruption_short_form_badge);
            binding.tvInterruptionTitle.setText(R.string.interruption_short_form_title);
            binding.tvInterruptionApp.setText(
                    getString(R.string.interruption_short_form_paused_format, blockedSurface)
            );
            binding.tvInterruptionReminder.setText(R.string.interruption_short_form_reminder);
            binding.tvInterruptionNote.setText(R.string.interruption_short_form_note);
        } else {
            binding.tvInterruptionBadgeText.setText(R.string.interruption_badge);
            binding.tvInterruptionTitle.setText(R.string.interruption_title);
            binding.tvInterruptionApp.setText(getString(R.string.interruption_app_paused_format, appName));
            binding.tvInterruptionReminder.setText(R.string.interruption_reminder);
            binding.tvInterruptionNote.setText(R.string.interruption_limitation_note);
        }
    }

    private void configureWorkoutPrompt() {
        boolean workoutUnlockAllowed = preferencesManager.isWorkoutUnlockEnabled();
        binding.btnStartWorkoutUnlock.setVisibility(workoutUnlockAllowed ? View.VISIBLE : View.GONE);
        binding.tvInterruptionWorkoutGoal.setVisibility(workoutUnlockAllowed ? View.VISIBLE : View.GONE);

        if (workoutUnlockAllowed) {
            int reps = preferencesManager.getRequiredWorkoutReps();
            int duration = preferencesManager.getUnlockDurationMinutes();
            String exRaw = preferencesManager.getPreferredExerciseName();
            String exDisplay;
            try {
                exDisplay = ExerciseType.valueOf(exRaw).getDisplayName();
            } catch (Exception e) {
                exDisplay = ExerciseType.SQUATS.getDisplayName();
            }
            String targetLabel = MODE_SHORT_FORM.equals(blockMode) ? blockedSurface : blockedAppName;
            binding.tvInterruptionWorkoutGoal.setText(
                    getString(R.string.interruption_workout_prompt_format, reps, exDisplay, targetLabel, duration)
            );
        }
    }

    private void launchWorkoutToUnlock() {
        Intent intent = new Intent(this, WorkoutCameraActivity.class);
        intent.putExtra(WorkoutCameraActivity.EXTRA_UNLOCK_PKG, blockedPackageName);
        intent.putExtra(
                WorkoutCameraActivity.EXTRA_UNLOCK_APP_NAME,
                MODE_SHORT_FORM.equals(blockMode) ? blockedSurface : blockedAppName
        );
        intent.putExtra(
                WorkoutCameraActivity.EXTRA_UNLOCK_SHORT_FORM_ONLY,
                MODE_SHORT_FORM.equals(blockMode)
        );
        intent.putExtra(WorkoutCameraActivity.EXTRA_TARGET_REPS, preferencesManager.getRequiredWorkoutReps());
        intent.putExtra(WorkoutCameraActivity.EXTRA_EXERCISE_TYPE, preferencesManager.getPreferredExerciseName());
        workoutUnlockLauncher.launch(intent);
    }

    private void returnToScrollLock(int targetTab) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_TARGET_TAB, targetTab);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}
