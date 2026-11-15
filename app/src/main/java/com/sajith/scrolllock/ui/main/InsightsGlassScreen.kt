package com.sajith.scrolllock.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sajith.scrolllock.ui.theme.ActivitySparklineChart
import com.sajith.scrolllock.ui.theme.AppJetBrainsMonoFamily
import com.sajith.scrolllock.ui.theme.GlassCard
import com.sajith.scrolllock.ui.theme.GlassColors
import com.sajith.scrolllock.util.TimeFormatter
import com.sajith.scrolllock.workout.ExerciseType

@Composable
fun InsightsGlassScreen(
    state: MainUiState,
    onExportJsonClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Focus & Study Insights", style = MaterialTheme.typography.titleLarge, color = GlassColors.TextPrimary)
                Text(
                    "Stored privately on-device in SQLite & SharedPreferences",
                    style = MaterialTheme.typography.labelMedium,
                    color = GlassColors.TextSecondary
                )
            }
            Button(
                onClick = onExportJsonClick,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GlassColors.VioletSoft),
                modifier = Modifier.height(44.dp).testTag("insights_export_button")
            ) {
                Icon(Icons.Rounded.Share, null, tint = GlassColors.VioletBright, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Export", color = GlassColors.VioletBright)
            }
        }

        // Top Summary Bento Trio
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            InsightMetricCard(
                label = "Focus Saved",
                value = TimeFormatter.formatFocusMinutesSummary(state.totalFocusSeconds),
                sub = "${state.completedSessionsCount} sessions",
                accent = GlassColors.VioletBright,
                modifier = Modifier.weight(1f)
            )
            InsightMetricCard(
                label = "Study Pages",
                value = "${state.totalStudyPages}",
                sub = "${state.todayStudyPages}p today",
                accent = GlassColors.CyanAccent,
                modifier = Modifier.weight(1f)
            )
            InsightMetricCard(
                label = "Workout Reps",
                value = "${state.totalWorkoutReps}",
                sub = "Form ${state.averageFormScore}%",
                accent = GlassColors.MintVerified,
                modifier = Modifier.weight(1f)
            )
        }

        // 7-Day Focus Trend Card (Sparkline + Daily Bars)
        GlassCard(highlighted = true, cornerRadius = 22.dp) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Last 7 Days Focus Activity (min)", style = MaterialTheme.typography.titleMedium, color = GlassColors.TextPrimary)
                    Text(
                        text = "${state.totalInterruptionsCount} nudges",
                        style = MaterialTheme.typography.labelSmall,
                        color = GlassColors.AmberAlarm
                    )
                }

                val entries = state.weeklyFocusMinutes.entries.toList()
                val sparkVals = entries.map { it.value.toFloat() }
                ActivitySparklineChart(
                    values = if (sparkVals.any { it > 0f }) sparkVals else listOf(0f, 5f, 3f, 8f, 6f, 10f, 12f),
                    modifier = Modifier.fillMaxWidth().height(72.dp)
                )

                if (entries.isNotEmpty()) {
                    val maxMin = (entries.maxOfOrNull { it.value } ?: 1).coerceAtLeast(1)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        entries.forEach { (dayLabel, mins) ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = if (mins > 0) "${mins}m" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GlassColors.TextSecondary,
                                    fontSize = 9.sp
                                )
                                Spacer(Modifier.height(4.dp))
                                val frac = (mins.toFloat() / maxMin.toFloat()).coerceIn(0.08f, 1f)
                                Box(
                                    modifier = Modifier
                                        .width(14.dp)
                                        .height((48f * frac).dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(GlassColors.VioletBright, GlassColors.VioletDeep)
                                            )
                                        )
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(text = dayLabel, style = MaterialTheme.typography.labelSmall, color = GlassColors.TextMuted)
                            }
                        }
                    }
                }
            }
        }

        // Recent Study Page Sessions
        if (state.recentStudySessions.isNotEmpty()) {
            GlassCard(cornerRadius = 22.dp) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.MenuBook, null, tint = GlassColors.CyanAccent, modifier = Modifier.size(18.dp))
                        Text("Recent Study Sessions", style = MaterialTheme.typography.titleMedium, color = GlassColors.TextPrimary)
                    }
                    state.recentStudySessions.take(5).forEach { s ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(s.subjectTitle, style = MaterialTheme.typography.labelLarge, color = GlassColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${s.dayKey} • ${s.pagesTurned}/${s.targetPages} pages",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GlassColors.TextSecondary
                                )
                            }
                            Text(
                                "+${s.xpEarned} XP",
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = AppJetBrainsMonoFamily),
                                color = GlassColors.MintVerified
                            )
                        }
                    }
                }
            }
        }

        // Personal Bests & Badges
        if (state.personalBests.isNotEmpty() || state.achievements.isNotEmpty()) {
            GlassCard(cornerRadius = 22.dp) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.EmojiEvents, null, tint = GlassColors.AmberAlarm, modifier = Modifier.size(18.dp))
                        Text("Personal Bests & Badges", style = MaterialTheme.typography.titleMedium, color = GlassColors.TextPrimary)
                    }
                    state.personalBests.forEach { pb ->
                        val exLabel = runCatching { ExerciseType.valueOf(pb.exerciseType).displayName }.getOrDefault(pb.exerciseType)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(exLabel, style = MaterialTheme.typography.bodyMedium, color = GlassColors.TextPrimary)
                            Text(
                                "${pb.bestReps} reps • ${pb.bestFormScore}% form",
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = AppJetBrainsMonoFamily),
                                color = GlassColors.VioletBright
                            )
                        }
                    }
                    state.achievements.forEach { badge ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = GlassColors.GlassSurface,
                            border = BorderStroke(1.dp, GlassColors.GlassBorder.copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(badge.title, style = MaterialTheme.typography.labelLarge, color = GlassColors.MintVerified)
                                Text(badge.description, style = MaterialTheme.typography.labelSmall, color = GlassColors.TextSecondary)
                            }
                        }
                    }
                }
            }
        }

        // Top Guarded Apps
        if (state.topApps.isNotEmpty()) {
            GlassCard(cornerRadius = 22.dp) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Most Guarded Apps", style = MaterialTheme.typography.titleMedium, color = GlassColors.TextPrimary)
                    state.topApps.forEach { app ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(app.appName, style = MaterialTheme.typography.bodyMedium, color = GlassColors.TextPrimary, modifier = Modifier.weight(1f))
                            Text(
                                "${app.timesInterrupted} alerts",
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = AppJetBrainsMonoFamily),
                                color = GlassColors.TextSecondary
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun InsightMetricCard(
    label: String,
    value: String,
    sub: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    GlassCard(cornerRadius = 18.dp, modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = GlassColors.TextSecondary, maxLines = 1)
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = AppJetBrainsMonoFamily),
                color = accent,
                maxLines = 1
            )
            Text(sub, style = MaterialTheme.typography.labelSmall, color = GlassColors.TextMuted, maxLines = 1)
        }
    }
}
