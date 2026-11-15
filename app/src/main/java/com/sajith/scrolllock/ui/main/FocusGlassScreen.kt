package com.sajith.scrolllock.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.DataSaverOn
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sajith.scrolllock.data.model.AppItem
import com.sajith.scrolllock.data.model.FocusState
import com.sajith.scrolllock.service.NonEssentialAppClassifier
import com.sajith.scrolllock.ui.theme.AppJetBrainsMonoFamily
import com.sajith.scrolllock.ui.theme.GlassCard
import com.sajith.scrolllock.ui.theme.GlassColors
import com.sajith.scrolllock.util.TimeFormatter
import kotlin.math.roundToInt

@Composable
fun FocusGlassScreen(
    state: MainUiState,
    deepFocusAllowedPackages: Set<String>,
    onSelectDurationMinutes: (Int) -> Unit,
    onPrimaryActionClick: () -> Unit,
    onResetClick: () -> Unit,
    onToggleDeepFocus: (Boolean) -> Unit,
    onToggleHidePreviews: (Boolean) -> Unit,
    onToggleBlockInternet: (Boolean) -> Unit,
    onGrantDndAccess: () -> Unit,
    onOpenConnectivityPanel: () -> Unit,
    onOpenDataSaver: () -> Unit,
    onToggleDeepFocusException: (String) -> Unit,
    onOpenStudyCamera: () -> Unit,
    onOpenSandbox: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showCustomDialog by remember { mutableStateOf(false) }
    var showExceptionsDialog by remember { mutableStateOf(false) }

    val progressFraction = if (state.totalMillis > 0L) {
        (state.remainingMillis.toFloat() / state.totalMillis.toFloat()).coerceIn(0f, 1f)
    } else 1f
    val selectedMinutes = ((state.totalMillis + 59_999L) / 60_000L).toInt().coerceAtLeast(1)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Focus & Deep Focus",
            style = MaterialTheme.typography.titleLarge,
            color = GlassColors.TextPrimary
        )

        // Focus Countdown Hero Ring Card
        GlassCard(highlighted = state.focusState == FocusState.RUNNING, cornerRadius = 24.dp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(196.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val stroke = 11.dp.toPx()
                        drawArc(
                            color = GlassColors.GlassBorder,
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = if (state.deepFocusEnabled) GlassColors.VioletBright else GlassColors.CyanAccent,
                            startAngle = -90f,
                            sweepAngle = 360f * progressFraction,
                            useCenter = false,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = when (state.focusState) {
                                FocusState.RUNNING -> if (state.deepFocusEnabled) "DEEP FOCUS" else "FOCUS LOCK"
                                FocusState.PAUSED -> "PAUSED"
                                FocusState.COMPLETED -> "COMPLETED"
                                FocusState.IDLE -> "READY"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = AppJetBrainsMonoFamily,
                                letterSpacing = 1.sp
                            ),
                            color = GlassColors.VioletBright
                        )
                        Text(
                            text = TimeFormatter.formatCountdown(state.remainingMillis),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontFamily = AppJetBrainsMonoFamily,
                                fontSize = 38.sp
                            ),
                            color = GlassColors.TextPrimary,
                            modifier = Modifier.testTag("focus_timer_text")
                        )
                        Text(
                            text = "${state.sessionInterruptions} nudges • ${state.selectedAppsCount} guarded",
                            style = MaterialTheme.typography.labelSmall,
                            color = GlassColors.TextSecondary
                        )
                    }
                }

                // Duration Presets (15, 25, 45, 60, 90, Custom 5..180m)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val locked = state.focusState == FocusState.RUNNING || state.focusState == FocusState.PAUSED
                    listOf(15, 25, 45, 60, 90).forEach { mins ->
                        val active = selectedMinutes == mins
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (active) GlassColors.VioletSoft else GlassColors.GlassSurface,
                            border = BorderStroke(1.dp, if (active) GlassColors.ActiveGlowBorder else GlassColors.GlassBorder),
                            modifier = Modifier
                                .height(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = !locked) { onSelectDurationMinutes(mins) }
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
                                Text(
                                    text = "${mins}m",
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = AppJetBrainsMonoFamily),
                                    color = if (active) GlassColors.VioletBright else GlassColors.TextPrimary
                                )
                            }
                        }
                    }

                    val isCustomSelected = selectedMinutes !in listOf(15, 25, 45, 60, 90)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isCustomSelected) GlassColors.VioletSoft else GlassColors.GlassSurface,
                        border = BorderStroke(1.dp, if (isCustomSelected) GlassColors.ActiveGlowBorder else GlassColors.GlassBorder),
                        modifier = Modifier
                            .height(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = !locked) { showCustomDialog = true }
                            .testTag("focus_custom_duration_button")
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
                            Text(
                                text = if (isCustomSelected) "Custom (${selectedMinutes}m)" else "Custom",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isCustomSelected) GlassColors.VioletBright else GlassColors.TextPrimary
                            )
                        }
                    }
                }

                // Start / Pause / Reset Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onPrimaryActionClick,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GlassColors.VioletPrimary,
                            contentColor = GlassColors.TextPrimary
                        ),
                        modifier = Modifier
                            .weight(1.4f)
                            .height(52.dp)
                            .testTag("focus_primary_button")
                    ) {
                        Icon(
                            imageVector = if (state.focusState == FocusState.RUNNING) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = when (state.focusState) {
                                FocusState.RUNNING -> "Pause"
                                FocusState.PAUSED -> "Resume"
                                FocusState.COMPLETED, FocusState.IDLE -> "Start Focus"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = onResetClick,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, GlassColors.GlassBorder),
                        modifier = Modifier
                            .weight(0.9f)
                            .height(52.dp)
                            .testTag("focus_reset_button")
                    ) {
                        Icon(Icons.Rounded.Refresh, null, tint = GlassColors.TextPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Reset", color = GlassColors.TextPrimary)
                    }
                }
            }
        }

        // Deep Focus Mode Card (Notification Preview Suppression + Connectivity Shield + Exceptions)
        GlassCard(highlighted = state.deepFocusEnabled, cornerRadius = 22.dp) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Deep Focus Mode",
                            style = MaterialTheme.typography.titleMedium,
                            color = GlassColors.TextPrimary
                        )
                        Text(
                            text = "Hides notification previews via official DND Policy & shields non-essential internet apps.",
                            style = MaterialTheme.typography.labelSmall,
                            color = GlassColors.TextSecondary
                        )
                    }
                    Switch(
                        checked = state.deepFocusEnabled,
                        onCheckedChange = onToggleDeepFocus,
                        colors = SwitchDefaults.colors(checkedTrackColor = GlassColors.VioletPrimary),
                        modifier = Modifier.testTag("deep_focus_master_switch")
                    )
                }

                DeepFocusToggleRow(
                    title = "Hide Notification Previews",
                    icon = Icons.Rounded.DoNotDisturbOn,
                    checked = state.deepFocusHidePreviews,
                    onCheckedChange = onToggleHidePreviews
                )
                DeepFocusToggleRow(
                    title = "Offline & Non-Essential Net Shield",
                    icon = Icons.Rounded.Public,
                    checked = state.deepFocusBlockInternet,
                    onCheckedChange = onToggleBlockInternet
                )

                Text(
                    text = "Shielding ${state.deepFocusShieldedPreviewCount} non-essential apps • Calls, SMS & Alarms stay active",
                    style = MaterialTheme.typography.labelSmall,
                    color = GlassColors.CyanAccent
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onGrantDndAccess,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.dp,
                            if (state.hasDndAccess) GlassColors.MintVerified.copy(alpha = 0.5f) else GlassColors.AmberAlarm
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier.weight(1f).height(46.dp)
                    ) {
                        Text(
                            text = if (state.hasDndAccess) "DND Ready ✓" else "Grant DND Access",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (state.hasDndAccess) GlassColors.MintVerified else GlassColors.AmberAlarm,
                            maxLines = 1
                        )
                    }

                    OutlinedButton(
                        onClick = { showExceptionsDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, GlassColors.GlassBorder),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier.weight(1f).height(46.dp)
                    ) {
                        Text(
                            text = "Exceptions (${state.deepFocusAllowedCount})",
                            style = MaterialTheme.typography.labelMedium,
                            color = GlassColors.TextPrimary,
                            maxLines = 1
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenConnectivityPanel,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, GlassColors.GlassBorder),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier.weight(1f).height(46.dp)
                    ) {
                        Icon(Icons.Rounded.Wifi, null, tint = GlassColors.VioletBright, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Connectivity", style = MaterialTheme.typography.labelMedium, color = GlassColors.TextPrimary)
                    }

                    OutlinedButton(
                        onClick = onOpenDataSaver,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, GlassColors.GlassBorder),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier.weight(1f).height(46.dp)
                    ) {
                        Icon(Icons.Rounded.DataSaverOn, null, tint = GlassColors.CyanAccent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Data Saver", style = MaterialTheme.typography.labelMedium, color = GlassColors.TextPrimary)
                    }
                }
            }
        }

        // Quick Study & Test Bench Links
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GlassCard(
                cornerRadius = 18.dp,
                onClick = onOpenStudyCamera,
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.MenuBook, null, tint = GlassColors.CyanAccent)
                    Column {
                        Text("Study Counter", style = MaterialTheme.typography.labelLarge, color = GlassColors.TextPrimary)
                        Text("${state.defaultStudyPages}p goal", style = MaterialTheme.typography.labelSmall, color = GlassColors.TextSecondary)
                    }
                }
            }

            GlassCard(
                cornerRadius = 18.dp,
                onClick = onOpenSandbox,
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Rounded.Shield, null, tint = GlassColors.VioletBright)
                    Column {
                        Text("Scroll Barrier", style = MaterialTheme.typography.labelLarge, color = GlassColors.TextPrimary)
                        Text("Touch test bench", style = MaterialTheme.typography.labelSmall, color = GlassColors.TextSecondary)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
    }

    if (showCustomDialog) {
        CustomDurationDialog(
            initialMinutes = state.customDurationMinutes,
            onConfirm = { mins ->
                showCustomDialog = false
                onSelectDurationMinutes(mins)
            },
            onDismiss = { showCustomDialog = false }
        )
    }

    if (showExceptionsDialog) {
        DeepFocusExceptionsDialog(
            apps = state.installedApps,
            allowedPackages = deepFocusAllowedPackages,
            onToggleApp = onToggleDeepFocusException,
            onDismiss = { showExceptionsDialog = false }
        )
    }
}

@Composable
private fun DeepFocusToggleRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, null, tint = GlassColors.VioletBright, modifier = Modifier.size(18.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = GlassColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = GlassColors.VioletPrimary)
        )
    }
}

@Composable
private fun CustomDurationDialog(
    initialMinutes: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var slider by remember { mutableFloatStateOf(initialMinutes.coerceIn(5, 180).toFloat()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GlassColors.DeepIndigoBg,
        titleContentColor = GlassColors.TextPrimary,
        title = { Text("Custom Focus Duration") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "${slider.roundToInt()} minutes",
                    style = MaterialTheme.typography.headlineMedium.copy(fontFamily = AppJetBrainsMonoFamily),
                    color = GlassColors.VioletBright,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Slider(
                    value = slider,
                    onValueChange = { slider = it },
                    valueRange = 5f..180f,
                    colors = SliderDefaults.colors(
                        thumbColor = GlassColors.VioletBright,
                        activeTrackColor = GlassColors.VioletPrimary
                    )
                )
                Text(
                    text = "Set any length between 5 and 180 minutes for Deep Focus.",
                    style = MaterialTheme.typography.labelSmall,
                    color = GlassColors.TextSecondary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(slider.roundToInt().coerceIn(5, 180)) },
                colors = ButtonDefaults.buttonColors(containerColor = GlassColors.VioletPrimary)
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = GlassColors.TextSecondary)
            }
        }
    )
}

@Composable
private fun DeepFocusExceptionsDialog(
    apps: List<AppItem>,
    allowedPackages: Set<String>,
    onToggleApp: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val ownPkg = LocalContext.current.packageName
    var filterQuery by remember { mutableStateOf("") }
    val filtered = remember(apps, filterQuery, ownPkg) {
        val q = filterQuery.trim()
        apps.filter {
            !NonEssentialAppClassifier.isCoreEssentialPackage(it.packageName, ownPkg) &&
                (q.isEmpty() || it.appName.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GlassColors.DeepIndigoBg,
        titleContentColor = GlassColors.TextPrimary,
        title = { Text("Deep Focus Exceptions") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Calls, SMS, Alarms, Keyboard, and Play Services stay connected automatically.",
                    style = MaterialTheme.typography.labelSmall,
                    color = GlassColors.TextSecondary
                )
                OutlinedTextField(
                    value = filterQuery,
                    onValueChange = { filterQuery = it },
                    placeholder = { Text("Filter apps…") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GlassColors.VioletBright,
                        unfocusedBorderColor = GlassColors.GlassBorder,
                        focusedTextColor = GlassColors.TextPrimary,
                        unfocusedTextColor = GlassColors.TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    filtered.forEach { app ->
                        val allowed = allowedPackages.contains(app.packageName)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onToggleApp(app.packageName) }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = app.appName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = GlassColors.TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Checkbox(
                                checked = allowed,
                                onCheckedChange = { onToggleApp(app.packageName) },
                                colors = CheckboxDefaults.colors(checkedColor = GlassColors.VioletPrimary)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = GlassColors.VioletPrimary)
            ) {
                Text("Done")
            }
        }
    )
}
