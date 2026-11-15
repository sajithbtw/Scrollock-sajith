package com.sajith.scrolllock.ui.main

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayDisabled
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sajith.scrolllock.data.model.AppItem
import com.sajith.scrolllock.ui.theme.AppJetBrainsMonoFamily
import com.sajith.scrolllock.ui.theme.GlassCard
import com.sajith.scrolllock.ui.theme.GlassColors
import com.sajith.scrolllock.util.TimeFormatter

enum class AppFilterMode(val label: String) {
    ALL("All"),
    REELS("No Reels"),
    GUARDED("Guarded"),
    SOCIAL_GAMES("Social & Games")
}

@Composable
fun AppsGuardGlassScreen(
    state: MainUiState,
    filterMode: AppFilterMode,
    isSocialOrGame: (String) -> Boolean,
    onFilterChange: (AppFilterMode) -> Unit,
    onToggleAppGuard: (AppItem, Boolean) -> Unit,
    onToggleAppReels: (AppItem) -> Unit,
    onPresetBlockReels: () -> Unit,
    onPresetGuardSocial: () -> Unit,
    modifier: Modifier = Modifier
) {
    val filteredApps = remember(state.installedApps, filterMode, state.searchQuery) {
        val q = state.searchQuery.trim()
        state.installedApps.filter { app ->
            val modeMatch = when (filterMode) {
                AppFilterMode.ALL -> true
                AppFilterMode.REELS -> app.isShortFormBlocked
                AppFilterMode.GUARDED -> app.isSelected
                AppFilterMode.SOCIAL_GAMES -> isSocialOrGame(app.packageName)
            }
            val queryMatch = q.isEmpty() ||
                app.appName.contains(q, ignoreCase = true) ||
                app.packageName.contains(q, ignoreCase = true)
            modeMatch && queryMatch
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "App & Reels Controls",
                    style = MaterialTheme.typography.titleLarge,
                    color = GlassColors.TextPrimary
                )
                Text(
                    text = "Toggle \"No Reels\" to guard short-form feeds, or flip the switch for Focus Lock & Workout-to-Unlock.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GlassColors.TextSecondary
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onPresetBlockReels,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, GlassColors.ActiveGlowBorder),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.weight(1f).height(48.dp).testTag("apps_preset_reels_button")
                ) {
                    Icon(Icons.Rounded.PlayDisabled, null, tint = GlassColors.VioletBright, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Guard Reels", color = GlassColors.VioletBright, maxLines = 1)
                }

                OutlinedButton(
                    onClick = onPresetGuardSocial,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, GlassColors.CyanAccent.copy(alpha = 0.6f)),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.weight(1f).height(48.dp).testTag("apps_preset_social_button")
                ) {
                    Icon(Icons.Rounded.Shield, null, tint = GlassColors.CyanAccent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Social & Games", color = GlassColors.CyanAccent, maxLines = 1)
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppFilterMode.entries.forEach { mode ->
                    val active = mode == filterMode
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (active) GlassColors.VioletSoft else GlassColors.GlassCard,
                        border = BorderStroke(1.dp, if (active) GlassColors.ActiveGlowBorder else GlassColors.GlassBorder),
                        modifier = Modifier
                            .height(38.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onFilterChange(mode) }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp)) {
                            Text(
                                text = mode.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (active) GlassColors.VioletBright else GlassColors.TextSecondary
                            )
                        }
                    }
                }
            }
        }

        if (state.appsLoading && state.installedApps.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = GlassColors.VioletBright)
                }
            }
        } else if (filteredApps.isEmpty()) {
            item {
                GlassCard(cornerRadius = 20.dp) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No matching installed apps found.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = GlassColors.TextSecondary
                        )
                    }
                }
            }
        } else {
            items(filteredApps, key = { it.packageName }) { app ->
                AppGuardCardRow(
                    app = app,
                    hasUsageAccess = state.hasUsageAccess,
                    onToggleGuard = { checked -> onToggleAppGuard(app, checked) },
                    onToggleReels = { onToggleAppReels(app) }
                )
            }
        }

        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun AppGuardCardRow(
    app: AppItem,
    hasUsageAccess: Boolean,
    onToggleGuard: (Boolean) -> Unit,
    onToggleReels: () -> Unit
) {
    val iconBitmap = remember(app.packageName) { drawableToBitmap(app.icon) }

    GlassCard(
        highlighted = app.isSelected || app.isShortFormBlocked,
        cornerRadius = 18.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap,
                    contentDescription = app.appName,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = GlassColors.GlassSurface,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Android, null, tint = GlassColors.VioletBright)
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.titleMedium,
                    color = GlassColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val usageText = if (!hasUsageAccess) {
                    "Usage permission off"
                } else {
                    "Today: ${TimeFormatter.formatDurationShort(app.todayUsageMillis)}"
                }
                Text(
                    text = usageText,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = AppJetBrainsMonoFamily),
                    color = GlassColors.TextSecondary
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (app.isShortFormBlocked) GlassColors.VioletSoft else GlassColors.GlassSurface,
                border = BorderStroke(
                    1.dp,
                    if (app.isShortFormBlocked) GlassColors.ActiveGlowBorder else GlassColors.GlassBorder
                ),
                modifier = Modifier
                    .height(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onToggleReels)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Rounded.Lock,
                        null,
                        tint = if (app.isShortFormBlocked) GlassColors.VioletBright else GlassColors.TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (app.isShortFormBlocked) "No Reels" else "Reels",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (app.isShortFormBlocked) GlassColors.VioletBright else GlassColors.TextSecondary
                    )
                }
            }

            Switch(
                checked = app.isSelected,
                onCheckedChange = onToggleGuard,
                colors = SwitchDefaults.colors(checkedTrackColor = GlassColors.VioletPrimary)
            )
        }
    }
}

private fun drawableToBitmap(drawable: Drawable?): ImageBitmap? {
    if (drawable == null) return null
    return try {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            drawable.bitmap.asImageBitmap()
        } else {
            val w = drawable.intrinsicWidth.coerceIn(48, 128)
            val h = drawable.intrinsicHeight.coerceIn(48, 128)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bmp.asImageBitmap()
        }
    } catch (_: Exception) {
        null
    }
}
