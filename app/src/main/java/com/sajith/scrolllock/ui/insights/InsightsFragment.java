package com.sajith.scrolllock.ui.insights;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.example.R;
import com.example.databinding.FragmentInsightsBinding;
import com.example.databinding.ItemTopAppStatBinding;
import com.sajith.scrolllock.data.local.AppRepository;
import com.sajith.scrolllock.data.local.PreferencesManager;
import com.sajith.scrolllock.data.local.ScrollLockDatabaseHelper;
import com.sajith.scrolllock.data.model.TopAppStat;
import com.sajith.scrolllock.ui.workout.RepReplayActivity;
import com.sajith.scrolllock.util.TimeFormatter;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Displays real locally recorded focus metrics, 7-day activity chart,
 * top distracted apps, plus AI Workout Analytics, Quality Report & Performance Prediction,
 * Personal Bests, Achievement Badges, Local Rank, and Offline JSON export.
 */
public class InsightsFragment extends Fragment {

    private FragmentInsightsBinding binding;
    private ScrollLockDatabaseHelper dbHelper;
    private AppRepository appRepository;
    private PreferencesManager preferencesManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentInsightsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        dbHelper = ScrollLockDatabaseHelper.getInstance(requireContext());
        appRepository = new AppRepository(requireContext());
        preferencesManager = new PreferencesManager(requireContext());

        binding.btnInsightsRepReplay.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), RepReplayActivity.class))
        );

        binding.btnInsightsExportJson.setOnClickListener(v -> showOfflineBackupDialog());
    }

    @Override
    public void onResume() {
        super.onResume();
        loadInsights();
    }

    private void loadInsights() {
        if (binding == null || !isAdded()) {
            return;
        }
        int totalFocusSeconds = dbHelper.getTotalFocusSeconds();
        int completedSessions = dbHelper.getCompletedSessionsCount();
        int totalInterruptions = dbHelper.getTotalInterruptionsCount();

        binding.tvMetricTotalFocus.setText(TimeFormatter.formatFocusMinutesSummary(totalFocusSeconds));
        binding.tvMetricCompletedSessions.setText(String.valueOf(completedSessions));
        binding.tvMetricInterruptions.setText(String.valueOf(totalInterruptions));

        Map<String, Integer> weeklyData = dbHelper.getLast7DaysFocusMinutes();
        binding.weeklyBarChart.setData(weeklyData);

        boolean hasAnyWeeklyActivity = false;
        for (Integer minutes : weeklyData.values()) {
            if (minutes != null && minutes > 0) {
                hasAnyWeeklyActivity = true;
                break;
            }
        }
        binding.tvChartEmptyHint.setVisibility(hasAnyWeeklyActivity ? View.GONE : View.VISIBLE);

        List<TopAppStat> topApps = dbHelper.getTopDistractingApps(5);
        binding.containerTopApps.removeAllViews();

        if (topApps.isEmpty()) {
            binding.tvTopAppsEmpty.setVisibility(View.VISIBLE);
        } else {
            binding.tvTopAppsEmpty.setVisibility(View.GONE);
            int maxScore = 1;
            for (TopAppStat stat : topApps) {
                int score = stat.getTimesInterrupted() * 2 + stat.getTimesSelected();
                if (score > maxScore) {
                    maxScore = score;
                }
            }

            LayoutInflater inflater = LayoutInflater.from(requireContext());
            for (TopAppStat stat : topApps) {
                ItemTopAppStatBinding rowBinding = ItemTopAppStatBinding.inflate(
                        inflater, binding.containerTopApps, false
                );
                rowBinding.tvStatAppName.setText(stat.getAppName());
                rowBinding.tvStatAppDetail.setText(getString(
                        R.string.top_apps_detail_format,
                        stat.getTimesSelected(),
                        stat.getTimesInterrupted()
                ));
                Drawable icon = appRepository.getAppIcon(stat.getPackageName());
                if (icon != null) {
                    rowBinding.ivStatAppIcon.setImageDrawable(icon);
                } else {
                    rowBinding.ivStatAppIcon.setImageResource(R.drawable.ic_nav_apps);
                }

                int score = stat.getTimesInterrupted() * 2 + stat.getTimesSelected();
                int progress = Math.max(8, (int) ((score * 100f) / maxScore));
                rowBinding.progressStatApp.setProgress(progress);
                binding.containerTopApps.addView(rowBinding.getRoot());
            }
        }

        loadWorkoutAnalytics();
    }

    private void loadWorkoutAnalytics() {
        int totalReps = dbHelper.getTotalWorkoutReps();
        int avgForm = dbHelper.getAverageWorkoutFormScore();
        int rejected = dbHelper.getTotalInvalidReps();
        int sessions = dbHelper.getWorkoutSessionsCount();
        int xp = preferencesManager.getUserXp();
        int level = preferencesManager.getUserLevel();
        int streak = preferencesManager.getWorkoutStreakDays();

        binding.tvWorkoutTotalReps.setText(String.valueOf(totalReps));
        binding.tvWorkoutAvgForm.setText(avgForm + "%");
        binding.tvWorkoutRejectedReps.setText(String.valueOf(rejected));

        // Quality Report & Performance Prediction
        if (sessions == 0) {
            binding.tvWorkoutPredictionReport.setText(
                    "Complete your first AI workout to unlock Performance Prediction, Fatigue Recovery, and Quality Reports."
            );
        } else {
            int nextGoal = Math.max(10, (totalReps / sessions) + 2);
            int restSec = avgForm >= 85 ? 30 : 60;
            binding.tvWorkoutPredictionReport.setText(String.format(
                    Locale.getDefault(),
                    "Quality Report (%d sessions): Avg form %d%% • Anti-cheat filtered %d partial/fast reps.\n"
                            + "Performance Prediction: Ready for %d reps next set • Adaptive Rest: %ds.",
                    sessions, avgForm, rejected, nextGoal, restSec
            ));
        }

        // Local Rank / Offline Progression Tier
        String rankTitle;
        if (xp >= 1500) {
            rankTitle = "Apex Guardian (Top Local Tier)";
        } else if (xp >= 750) {
            rankTitle = "Iron Challenger";
        } else if (xp >= 250) {
            rankTitle = "Disciplined Mover";
        } else {
            rankTitle = "Starter Contender";
        }
        binding.tvLocalLeaderboardTier.setText(String.format(
                Locale.getDefault(),
                "Local Tier: %s • Lv.%d (%d XP) • Streak: %dd",
                rankTitle, level, xp, streak
        ));

        // Personal Bests
        List<ScrollLockDatabaseHelper.PersonalBestItem> pbs = dbHelper.getPersonalBests();
        if (pbs.isEmpty()) {
            binding.tvPersonalBestsSummary.setText("Personal Bests: None yet.");
        } else {
            StringBuilder pbBuilder = new StringBuilder("Personal Bests:\n");
            for (ScrollLockDatabaseHelper.PersonalBestItem pb : pbs) {
                pbBuilder.append("• ").append(pb.exerciseType)
                        .append(": ").append(pb.bestReps).append(" reps (Best Form ")
                        .append(pb.bestFormScore).append("%)\n");
            }
            binding.tvPersonalBestsSummary.setText(pbBuilder.toString().trim());
        }

        // Achievements
        List<ScrollLockDatabaseHelper.AchievementBadge> badges = dbHelper.getUnlockedAchievements();
        if (badges.isEmpty()) {
            binding.tvAchievementsSummary.setText("Badges: Complete a workout to earn your first badge.");
        } else {
            StringBuilder badgeBuilder = new StringBuilder("Unlocked Badges (").append(badges.size()).append("):\n");
            for (ScrollLockDatabaseHelper.AchievementBadge b : badges) {
                badgeBuilder.append("★ ").append(b.title).append(" — ").append(b.description).append("\n");
            }
            binding.tvAchievementsSummary.setText(badgeBuilder.toString().trim());
        }

        // Recent History
        List<ScrollLockDatabaseHelper.WorkoutHistoryItem> history = dbHelper.getRecentWorkouts(5);
        if (history.isEmpty()) {
            binding.tvRecentWorkoutsList.setText("Recent Workout History: No sessions logged yet.");
        } else {
            StringBuilder histBuilder = new StringBuilder("Recent Workout History:\n");
            for (ScrollLockDatabaseHelper.WorkoutHistoryItem h : history) {
                histBuilder.append("• ").append(h.dayKey).append(" | ")
                        .append(h.exerciseType).append(": ").append(h.repsCompleted)
                        .append(" reps • Form ").append(h.formScore).append("% • Sym ")
                        .append(h.balancePercent).append("% (+").append(h.xpEarned).append(" XP)\n");
            }
            binding.tvRecentWorkoutsList.setText(histBuilder.toString().trim());
        }
    }

    private void showOfflineBackupDialog() {
        String json = dbHelper.exportOfflineSummaryJson();
        new AlertDialog.Builder(requireContext())
                .setTitle("Offline Workout Data JSON")
                .setMessage(json)
                .setPositiveButton("Close", null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
