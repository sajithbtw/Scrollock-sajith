package com.sajith.scrolllock.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sajith.scrolllock.ui.theme.AppJetBrainsMonoFamily
import com.sajith.scrolllock.ui.theme.GlassCard
import com.sajith.scrolllock.ui.theme.GlassColors
import com.sajith.scrolllock.workout.ExerciseType
import kotlin.math.roundToInt

@Composable
fun SettingsGlassScreen(
    state: MainUiState,
    onSelectDefaultExercise: (ExerciseType) -> Unit,
    onSetRequiredReps: (Int) -> Unit,
    onSetDefaultStudyPages: (Int) -> Unit,
    onSetDefaultFocusMinutes: (Int) -> Unit,
    onToggleVoiceCoach: (Boolean) -> Unit,
    onToggleGhostTrainer: (Boolean) -> Unit,
    onToggleMirrorMode: (Boolean) -> Unit,
    onToggleShortFormBlocker: (Boolean) -> Unit,
    onToggleAlwaysGuard: (Boolean) -> Unit,
    onToggleWorkoutUnlock: (Boolean) -> Unit,
    onOpenUsageSettings: () -> Unit,
    onOpenDndSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenOnboarding: () -> Unit,
    onOpenRepReplay: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Settings & Permissions", style = MaterialTheme.typography.titleLarge, color = GlassColors.TextPrimary)

        // Permissions Health Card
        GlassCard(cornerRadius = 22.dp) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Permissions & System Access", style = MaterialTheme.typography.titleMedium, color = GlassColors.TextPrimary)
                PermissionRow("Usage Access (App & Reels Guard)", state.hasUsageAccess, onOpenUsageSettings)
                PermissionRow("Do Not Disturb (Hide Previews)", state.hasDndAccess, onOpenDndSettings)
                PermissionRow("Notifications (Alarms & Nudges)", state.hasNotificationPerm, onOpenNotificationSettings)
                PermissionRow("Camera (Study Pages & Pose AI)", state.hasCameraPerm, onOpenAppSettings)
                PermissionRow("Unrestricted Battery", state.hasBatteryExemption, onOpenBatterySettings)

                Button(
                    onClick = onOpenOnboarding,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GlassColors.VioletPrimary),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("settings_open_onboarding_btn")
                ) {
                    Text("Open Guided Setup Wizard")
                }
            }
        }

        // Study Page Goal Defaults
        GlassCard(cornerRadius = 22.dp) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Default Study Page Goal", style = MaterialTheme.typography.titleMedium, color = GlassColors.TextPrimary)
                    Text(
                        "${state.defaultStudyPages} pages",
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = AppJetBrainsMonoFamily),
                        color = GlassColors.CyanAccent
                    )
                }
                Slider(
                    value = state.defaultStudyPages.toFloat(),
                    onValueChange = { onSetDefaultStudyPages(it.roundToInt().coerceIn(1, 200)) },
                    valueRange = 1f..200f,
                    colors = SliderDefaults.colors(
                        thumbColor = GlassColors.CyanAccent,
                        activeTrackColor = GlassColors.VioletPrimary
                    )
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(5, 10, 15, 25, 50).forEach { preset ->
                        val active = state.defaultStudyPages == preset
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (active) GlassColors.VioletSoft else GlassColors.GlassSurface,
                            border = BorderStroke(1.dp, if (active) GlassColors.ActiveGlowBorder else GlassColors.GlassBorder),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onSetDefaultFocusMinutesIfNeeded(preset, onSetDefaultStudyPages) }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("${preset}p", style = MaterialTheme.typography.labelSmall, color = GlassColors.TextPrimary)
                            }
                        }
                    }
                }
            }
        }

        // Workout & Guard Preferences
        GlassCard(cornerRadius = 22.dp) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("AI Exercise & Unlock Goal", style = MaterialTheme.typography.titleMedium, color = GlassColors.TextPrimary)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExerciseType.entries.forEach { ex ->
                        val active = ex == state.preferredExercise
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (active) GlassColors.VioletSoft else GlassColors.GlassSurface,
                            border = BorderStroke(1.dp, if (active) GlassColors.ActiveGlowBorder else GlassColors.GlassBorder),
                            modifier = Modifier
                                .height(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelectDefaultExercise(ex) }
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
                                Text(
                                    ex.displayName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (active) GlassColors.VioletBright else GlassColors.TextSecondary
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Required Workout Reps", style = MaterialTheme.typography.labelLarge, color = GlassColors.TextPrimary)
                    Text(
                        "${state.requiredReps} reps",
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = AppJetBrainsMonoFamily),
                        color = GlassColors.VioletBright
                    )
                }
                Slider(
                    value = state.requiredReps.toFloat(),
                    onValueChange = { onSetRequiredReps(it.roundToInt().coerceIn(3, 30)) },
                    valueRange = 3f..30f,
                    colors = SliderDefaults.colors(
                        thumbColor = GlassColors.VioletBright,
                        activeTrackColor = GlassColors.VioletPrimary
                    )
                )

                Text("Default Focus Duration", style = MaterialTheme.typography.labelLarge, color = GlassColors.TextPrimary)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(15, 25, 45, 60, 90).forEach { mins ->
                        val active = state.defaultDurationMinutes == mins
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (active) GlassColors.VioletSoft else GlassColors.GlassSurface,
                            border = BorderStroke(1.dp, if (active) GlassColors.ActiveGlowBorder else GlassColors.GlassBorder),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onSetDefaultFocusMinutes(mins) }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("${mins}m", style = MaterialTheme.typography.labelSmall, color = GlassColors.TextPrimary)
                            }
                        }
                    }
                }

                SettingSwitchRow("Voice Coach Audio Cues", state.voiceCoachEnabled, onToggleVoiceCoach)
                SettingSwitchRow("Ghost Trainer Posture Guide", state.ghostTrainerEnabled, onToggleGhostTrainer)
                SettingSwitchRow("Mirror Front Camera Pose", state.mirrorModeEnabled, onToggleMirrorMode)
                SettingSwitchRow("Reels & Short-Form Blocker", state.shortFormBlockerEnabled, onToggleShortFormBlocker)
                SettingSwitchRow("Always Guard Selected Apps", state.alwaysGuardEnabled, onToggleAlwaysGuard)
                SettingSwitchRow("Workout-to-Unlock Mode", state.workoutUnlockEnabled, onToggleWorkoutUnlock)

                OutlinedButton(
                    onClick = onOpenRepReplay,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, GlassColors.GlassBorder),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Rounded.Replay, null, tint = GlassColors.VioletBright, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Open Latest Rep Replay", color = GlassColors.TextPrimary)
                }
            }
        }

        // Play Protect Safety Transparency Card
        GlassCard(cornerRadius = 22.dp) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(Icons.Rounded.Shield, null, tint = GlassColors.MintVerified, modifier = Modifier.size(22.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Play Protect & Privacy Guarantee", style = MaterialTheme.typography.titleMedium, color = GlassColors.TextPrimary)
                    Text(
                        text = "All page detection, ML Kit pose tracking, alarms, and usage analytics stay 100% on-device. ScrollLock never uses Accessibility snooping, background VPN interception, or restricted exact-alarm permissions.",
                        style = MaterialTheme.typography.labelSmall,
                        color = GlassColors.TextSecondary
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}

private fun onSetDefaultFocusMinutesIfNeeded(preset: Int, onSet: (Int) -> Unit) {
    onSet(preset)
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = GlassColors.GlassSurface,
        border = BorderStroke(1.dp, GlassColors.GlassBorder.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (granted) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                contentDescription = if (granted) "Granted" else "Needs attention",
                tint = if (granted) GlassColors.MintVerified else GlassColors.AmberAlarm,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = GlassColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (granted) "Enabled" else "Enable",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (granted) GlassColors.MintVerified else GlassColors.VioletBright
            )
            Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, tint = GlassColors.TextMuted, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun SettingSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = GlassColors.TextPrimary, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = GlassColors.VioletPrimary)
        )
    }
}
