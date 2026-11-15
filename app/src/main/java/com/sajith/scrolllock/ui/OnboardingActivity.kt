package com.sajith.scrolllock.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.sajith.scrolllock.data.local.AppRepository
import com.sajith.scrolllock.data.local.PreferencesManager
import com.sajith.scrolllock.data.local.ScrollLockDatabaseHelper
import com.sajith.scrolllock.ui.workout.InterFamily
import com.sajith.scrolllock.ui.workout.JetBrainsMonoFamily
import com.sajith.scrolllock.ui.workout.PlusJakartaFamily
import com.sajith.scrolllock.util.PermissionHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Pure state model representing permission readiness during Onboarding.
 */
data class OnboardingPermissionsState(
    val cameraGranted: Boolean = false,
    val usageAccessGranted: Boolean = false,
    val dndPolicyGranted: Boolean = false,
    val batteryOptimizationIgnored: Boolean = false,
    val guardedAppsCount: Int = 0,
    val reelsBlockedCount: Int = 0
) {
    val coreGrantedCount: Int
        get() = listOf(cameraGranted, usageAccessGranted, dndPolicyGranted).count { it }

    val allCoreGranted: Boolean
        get() = cameraGranted && usageAccessGranted && dndPolicyGranted

    val progressFraction: Float
        get() = coreGrantedCount / 3f
}

/**
 * Jetpack Compose OnboardingActivity that guides users through granting Camera,
 * Usage Access, and Deep Focus Do Not Disturb Policy permissions with direct deep-links to
 * Android system settings and live permission status updates upon return.
 */
class OnboardingActivity : ComponentActivity() {

    companion object {
        const val EXTRA_LAUNCH_MAIN_AFTER = "extra_launch_main_after"
    }

    private lateinit var prefs: PreferencesManager
    private lateinit var appRepo: AppRepository
    private lateinit var dbHelper: ScrollLockDatabaseHelper
    private var launchMainAfter: Boolean = false
    private var cameraRequestAttempted: Boolean = false

    private val permissionsState = MutableStateFlow(OnboardingPermissionsState())

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        prefs = PreferencesManager(this)
        appRepo = AppRepository(this)
        dbHelper = ScrollLockDatabaseHelper.getInstance(this)
        launchMainAfter = intent.getBooleanExtra(EXTRA_LAUNCH_MAIN_AFTER, false)

        refreshPermissionsState()

        setContent {
            val themeMode = prefs.getThemeMode()
            val darkTheme = when (themeMode) {
                PreferencesManager.THEME_LIGHT -> false
                PreferencesManager.THEME_DARK -> true
                else -> isSystemInDarkTheme()
            }

            ScrollLockOnboardingTheme(darkTheme = darkTheme) {
                val state by permissionsState.collectAsStateWithLifecycle()

                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                    refreshPermissionsState()
                }

                val cameraPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { granted ->
                    refreshPermissionsState()
                    if (!granted && !ActivityCompat.shouldShowRequestPermissionRationale(
                            this,
                            Manifest.permission.CAMERA
                        )
                    ) {
                        Toast.makeText(
                            this,
                            R.string.onboarding_camera_settings_hint,
                            Toast.LENGTH_LONG
                        ).show()
                        PermissionHelper.openAppDetailsSettings(this)
                    }
                }

                OnboardingScreen(
                    state = state,
                    onRequestCamera = {
                        if (PermissionHelper.hasCameraPermission(this)) {
                            PermissionHelper.openAppDetailsSettings(this)
                        } else if (cameraRequestAttempted &&
                            !ActivityCompat.shouldShowRequestPermissionRationale(
                                this,
                                Manifest.permission.CAMERA
                            )
                        ) {
                            PermissionHelper.openAppDetailsSettings(this)
                        } else {
                            cameraRequestAttempted = true
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    onOpenUsageSettings = {
                        PermissionHelper.openUsageAccessSettings(this)
                    },
                    onOpenDndSettings = {
                        PermissionHelper.openNotificationPolicySettings(this)
                    },
                    onOpenBatterySettings = {
                        PermissionHelper.openBatteryOptimizationSettings(this)
                    },
                    onAutoGuardSocialAndReels = {
                        applyQuickGuardPreset()
                    },
                    onContinue = {
                        completeOnboarding()
                    }
                )
            }
        }
    }

    private fun refreshPermissionsState() {
        val hasCamera = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        permissionsState.update {
            OnboardingPermissionsState(
                cameraGranted = hasCamera,
                usageAccessGranted = PermissionHelper.hasUsageStatsPermission(this),
                dndPolicyGranted = PermissionHelper.hasNotificationPolicyAccess(this),
                batteryOptimizationIgnored = PermissionHelper.isIgnoringBatteryOptimizations(this),
                guardedAppsCount = prefs.getSelectedPackages().size,
                reelsBlockedCount = prefs.getShortFormBlockedPackages().size
            )
        }
    }

    private fun applyQuickGuardPreset() {
        val apps = appRepo.getLaunchableApps()
        val selected = prefs.getSelectedPackages()
        val shortForm = prefs.getShortFormBlockedPackages()
        var addedGuard = 0
        var addedReels = 0
        for (item in apps) {
            if (appRepo.isSocialOrGamePackage(item.packageName)) {
                if (selected.add(item.packageName)) {
                    dbHelper.recordAppSelected(item.packageName, item.appName)
                    addedGuard++
                }
            }
            if (appRepo.isShortFormHostPackage(item.packageName)) {
                if (shortForm.add(item.packageName)) {
                    addedReels++
                }
            }
        }
        prefs.setSelectedPackages(selected)
        prefs.setShortFormBlockedPackages(shortForm)
        prefs.setShortFormBlockerEnabled(true)
        prefs.setAlwaysGuardAppsEnabled(true)
        prefs.setWorkoutUnlockEnabled(true)
        refreshPermissionsState()

        val msg = when {
            addedGuard > 0 || addedReels > 0 ->
                "Guarded $addedGuard apps & blocked Reels/Shorts in $addedReels apps."
            selected.isNotEmpty() || shortForm.isNotEmpty() ->
                "${selected.size} guarded • ${shortForm.size} Reels-blocked apps active."
            else ->
                "No social or short-video apps found yet — choose apps anytime in the Apps tab."
        }
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun completeOnboarding() {
        prefs.setOnboardingCompleted(true)
        if (launchMainAfter) {
            startActivity(Intent(this, MainActivity::class.java))
        }
        finish()
    }
}

private val ScrollLockLightPalette = darkColorScheme(
    primary = Color(0xFF7C5CFF),
    onPrimary = Color(0xFFF6F7FC),
    primaryContainer = Color(0x307C5CFF),
    onPrimaryContainer = Color(0xFFA78BFF),
    secondary = Color(0xFF54D6C2),
    onSecondary = Color(0xFF080914),
    background = Color(0xFF080914),
    onBackground = Color(0xFFF6F7FC),
    surface = Color(0xFF171933),
    onSurface = Color(0xFFF6F7FC),
    surfaceVariant = Color(0xFF121429),
    onSurfaceVariant = Color(0xFFA3A7CE),
    outline = Color(0xFF333663)
)

private val ScrollLockDarkPalette = ScrollLockLightPalette

@Composable
private fun ScrollLockOnboardingTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) ScrollLockDarkPalette else ScrollLockLightPalette,
        content = content
    )
}

@Composable
fun OnboardingScreen(
    state: OnboardingPermissionsState,
    onRequestCamera: () -> Unit,
    onOpenUsageSettings: () -> Unit,
    onOpenDndSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onAutoGuardSocialAndReels: () -> Unit,
    onContinue: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val animatedProgress by animateFloatAsState(
        targetValue = state.progressFraction,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 320f),
        label = "permission_progress"
    )
    val accentGlowColor = Color(0xFF7C5CFF)

    Scaffold(
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            OnboardingBottomBar(
                state = state,
                onContinue = onContinue
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                accentGlowColor.copy(alpha = 0.14f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.82f, size.height * 0.08f),
                            radius = size.width * 0.65f
                        ),
                        center = Offset(size.width * 0.82f, size.height * 0.08f),
                        radius = size.width * 0.65f
                    )
                }
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OnboardingHeroCard(
                    state = state,
                    animatedProgress = animatedProgress
                )

                Text(
                    text = stringResource(R.string.onboarding_section_core),
                    fontFamily = PlusJakartaFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = colors.onBackground,
                    modifier = Modifier.padding(top = 4.dp)
                )

                // Step 1: Camera Permission
                PermissionGuideCard(
                    stepNumber = "01",
                    title = stringResource(R.string.onboarding_camera_title),
                    subtitle = stringResource(R.string.onboarding_camera_desc),
                    stepsHint = stringResource(R.string.onboarding_camera_steps),
                    icon = Icons.Outlined.CameraAlt,
                    granted = state.cameraGranted,
                    actionLabel = if (state.cameraGranted) {
                        stringResource(R.string.btn_open_app_settings)
                    } else {
                        stringResource(R.string.btn_grant_camera)
                    },
                    isSystemDeepLink = state.cameraGranted,
                    buttonTestTag = "btn_grant_camera",
                    cardTestTag = "card_perm_camera",
                    onActionClick = onRequestCamera
                )

                // Step 2: Usage Access Permission
                PermissionGuideCard(
                    stepNumber = "02",
                    title = stringResource(R.string.perm_usage_title),
                    subtitle = stringResource(R.string.perm_usage_desc),
                    stepsHint = stringResource(R.string.perm_usage_steps),
                    icon = Icons.Outlined.QueryStats,
                    granted = state.usageAccessGranted,
                    actionLabel = if (state.usageAccessGranted) {
                        stringResource(R.string.btn_review_usage_settings)
                    } else {
                        stringResource(R.string.btn_open_usage_settings)
                    },
                    isSystemDeepLink = true,
                    buttonTestTag = "btn_open_usage_settings",
                    cardTestTag = "card_perm_usage",
                    onActionClick = onOpenUsageSettings
                )

                // Step 3: Deep Focus Do Not Disturb Policy Access
                PermissionGuideCard(
                    stepNumber = "03",
                    title = stringResource(R.string.perm_dnd_title),
                    subtitle = stringResource(R.string.perm_dnd_desc),
                    stepsHint = stringResource(R.string.perm_dnd_steps),
                    icon = Icons.Outlined.DoNotDisturbOn,
                    granted = state.dndPolicyGranted,
                    actionLabel = if (state.dndPolicyGranted) {
                        stringResource(R.string.btn_review_dnd_settings)
                    } else {
                        stringResource(R.string.btn_open_dnd_settings)
                    },
                    isSystemDeepLink = true,
                    buttonTestTag = "btn_open_dnd_settings",
                    cardTestTag = "card_perm_dnd",
                    onActionClick = onOpenDndSettings
                )

                // Quick Guard & Background Reliability Card
                QuickSetupExtrasCard(
                    state = state,
                    onOpenBatterySettings = onOpenBatterySettings,
                    onAutoGuardSocialAndReels = onAutoGuardSocialAndReels
                )

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun OnboardingHeroCard(
    state: OnboardingPermissionsState,
    animatedProgress: Float
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outline),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF080914),
                    border = BorderStroke(1.5.dp, Color(0xFF8C77FF)),
                    modifier = Modifier.size(56.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_logo_art),
                        contentDescription = stringResource(R.string.app_name),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.onboarding_title),
                        fontFamily = PlusJakartaFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = colors.onSurface
                    )
                    Text(
                        text = stringResource(R.string.onboarding_subtitle),
                        fontFamily = InterFamily,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.surfaceVariant.copy(alpha = 0.65f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (state.allCoreGranted) {
                                stringResource(R.string.onboarding_all_ready)
                            } else {
                                stringResource(R.string.onboarding_readiness_label)
                            },
                            fontFamily = InterFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = colors.onSurface
                        )
                        Text(
                            text = "${state.coreGrantedCount} / 3",
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = colors.primary
                        )
                    }

                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        color = Color(0xFFA78BFF),
                        trackColor = colors.outline,
                        strokeCap = StrokeCap.Round,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionGuideCard(
    stepNumber: String,
    title: String,
    subtitle: String,
    stepsHint: String,
    icon: ImageVector,
    granted: Boolean,
    actionLabel: String,
    isSystemDeepLink: Boolean,
    buttonTestTag: String,
    cardTestTag: String,
    onActionClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var showGuide by remember { mutableStateOf(!granted) }

    val borderColor by animateColorAsState(
        targetValue = if (granted) Color(0xFF8C77FF).copy(alpha = 0.65f) else colors.outline,
        label = "card_border"
    )
    val badgeBg by animateColorAsState(
        targetValue = if (granted) colors.primaryContainer else colors.surfaceVariant,
        label = "badge_bg"
    )
    val badgeTextColor by animateColorAsState(
        targetValue = if (granted) colors.primary else colors.onSurfaceVariant,
        label = "badge_text"
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(cardTestTag)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (granted) colors.primaryContainer else colors.surfaceVariant,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = if (granted) colors.primary else colors.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "STEP $stepNumber",
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 0.8.sp,
                        color = colors.primary
                    )
                    Text(
                        text = title,
                        fontFamily = PlusJakartaFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = colors.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = badgeBg
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (granted) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = stringResource(R.string.status_enabled),
                                tint = badgeTextColor,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Text(
                            text = stringResource(
                                if (granted) R.string.status_enabled else R.string.status_not_enabled
                            ),
                            fontFamily = InterFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = badgeTextColor
                        )
                    }
                }
            }

            Text(
                text = subtitle,
                fontFamily = InterFamily,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = colors.onSurfaceVariant
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showGuide = !showGuide }
                    .minimumInteractiveComponentSize()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.onboarding_how_to_enable),
                    fontFamily = InterFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = colors.primary
                )
                Icon(
                    imageVector = if (showGuide) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = stringResource(R.string.onboarding_how_to_enable),
                    tint = colors.primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(visible = showGuide) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.surfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stepsHint,
                        fontFamily = InterFamily,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = colors.onSurface,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            if (granted) {
                OutlinedButton(
                    onClick = onActionClick,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, colors.outline),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag(buttonTestTag)
                ) {
                    Text(
                        text = actionLabel,
                        fontFamily = PlusJakartaFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = colors.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                Button(
                    onClick = onActionClick,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF7C5CFF),
                        contentColor = Color(0xFFF6F7FC)
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag(buttonTestTag)
                ) {
                    Text(
                        text = actionLabel,
                        fontFamily = PlusJakartaFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = if (isSystemDeepLink) {
                            Icons.AutoMirrored.Rounded.OpenInNew
                        } else {
                            Icons.AutoMirrored.Rounded.ArrowForward
                        },
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickSetupExtrasCard(
    state: OnboardingPermissionsState,
    onOpenBatterySettings: () -> Unit,
    onAutoGuardSocialAndReels: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shield,
                    contentDescription = stringResource(R.string.onboarding_quick_guard_title),
                    tint = colors.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.onboarding_quick_guard_title),
                    fontFamily = PlusJakartaFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = colors.onSurface
                )
            }

            Text(
                text = if (state.guardedAppsCount == 0 && state.reelsBlockedCount == 0) {
                    stringResource(R.string.onboarding_quick_guard_empty)
                } else {
                    stringResource(
                        R.string.onboarding_quick_guard_status_format,
                        state.guardedAppsCount,
                        state.reelsBlockedCount
                    )
                },
                fontFamily = InterFamily,
                fontSize = 12.sp,
                color = colors.primary,
                fontWeight = FontWeight.SemiBold
            )

            OutlinedButton(
                onClick = onAutoGuardSocialAndReels,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFF8C77FF)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = colors.primaryContainer,
                    contentColor = colors.primary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("btn_auto_guard_social_reels")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.btn_guard_social_games),
                    fontFamily = PlusJakartaFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceVariant.copy(alpha = 0.5f))
                    .clickable(onClick = onOpenBatterySettings)
                    .minimumInteractiveComponentSize()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .testTag("btn_battery_optimization_settings"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.BatteryChargingFull,
                    contentDescription = stringResource(R.string.onboarding_battery_title),
                    tint = if (state.batteryOptimizationIgnored) colors.primary else colors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.onboarding_battery_title),
                        fontFamily = InterFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = colors.onSurface
                    )
                    Text(
                        text = if (state.batteryOptimizationIgnored) {
                            stringResource(R.string.onboarding_battery_granted)
                        } else {
                            stringResource(R.string.onboarding_battery_desc)
                        },
                        fontFamily = InterFamily,
                        fontSize = 11.sp,
                        color = colors.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun OnboardingBottomBar(
    state: OnboardingPermissionsState,
    onContinue: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = colors.surface.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, colors.outline),
        tonalElevation = 3.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.widthIn(max = 600.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = onContinue,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.allCoreGranted) {
                            Color(0xFF7C5CFF)
                        } else {
                            colors.surfaceVariant
                        },
                        contentColor = Color(0xFFF6F7FC)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("btn_onboarding_continue")
                ) {
                    Text(
                        text = if (state.allCoreGranted) {
                            stringResource(R.string.btn_onboarding_done)
                        } else {
                            stringResource(R.string.btn_onboarding_continue_later)
                        },
                        fontFamily = PlusJakartaFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                if (!state.allCoreGranted) {
                    TextButton(
                        onClick = onContinue,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Text(
                            text = stringResource(R.string.onboarding_privacy_footer),
                            fontFamily = InterFamily,
                            fontSize = 11.sp,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
