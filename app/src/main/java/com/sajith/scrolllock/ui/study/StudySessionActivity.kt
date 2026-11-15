package com.sajith.scrolllock.ui.study

import android.Manifest
import android.os.Bundle
import android.os.SystemClock
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sajith.scrolllock.data.local.PreferencesManager
import com.sajith.scrolllock.data.local.ScrollLockDatabaseHelper
import com.sajith.scrolllock.study.PageTurnDetector
import com.sajith.scrolllock.study.PageTurnState
import com.sajith.scrolllock.ui.theme.AmbientNebulaBackground
import com.sajith.scrolllock.ui.theme.AppJetBrainsMonoFamily
import com.sajith.scrolllock.ui.theme.GlassCard
import com.sajith.scrolllock.ui.theme.GlassColors
import com.sajith.scrolllock.ui.theme.ScrollLockGlassTheme
import com.sajith.scrolllock.ui.workout.VoiceCoachManager
import com.sajith.scrolllock.ui.workout.WorkoutDashboardReducer
import com.sajith.scrolllock.util.PermissionHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt

data class StudyUiState(
    val hasCameraPermission: Boolean = true,
    val subjectTitle: String = "Focused Reading",
    val targetPages: Int = 10,
    val pagesTurned: Int = 0,
    val elapsedSeconds: Long = 0L,
    val turnState: PageTurnState = PageTurnState.CALIBRATING,
    val motionLevel: Float = 0f,
    val statusHint: String = "Point camera at your open book or notes…",
    val isLowLight: Boolean = false,
    val isPaused: Boolean = false,
    val useBackCamera: Boolean = true,
    val torchEnabled: Boolean = false,
    val voiceCoachEnabled: Boolean = true,
    val pagePulseEventId: Int = 0,
    val completionMessage: String? = null
)

/**
 * Live CameraX Study Page-Turn Tracker.
 * Detects physical page turns automatically, lets users choose any page goal (1–200),
 * records study sessions + XP into SQLite, and completes linked Study/Alarm tasks.
 */
class StudySessionActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_SUBJECT_TITLE = "extra_subject_title"
        const val EXTRA_TARGET_PAGES = "extra_target_pages"
        const val EXTRA_FROM_ALARM = "extra_from_alarm"
    }

    private lateinit var prefs: PreferencesManager
    private lateinit var dbHelper: ScrollLockDatabaseHelper
    private lateinit var voiceCoach: VoiceCoachManager

    private val detector = PageTurnDetector()
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var previewSurfaceProvider: Preview.SurfaceProvider? = null
    private var boundCamera: Camera? = null

    private var linkedTaskId: Long = -1L
    private var fromAlarm: Boolean = false
    private var sessionSaved: Boolean = false
    private var startElapsedMs: Long = 0L

    private val uiState = MutableStateFlow(StudyUiState())

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        uiState.update { it.copy(hasCameraPermission = granted) }
        if (granted) startCameraIfReady()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        prefs = PreferencesManager(this)
        dbHelper = ScrollLockDatabaseHelper.getInstance(this)
        voiceCoach = VoiceCoachManager(this).apply {
            isEnabled = prefs.isVoiceCoachEnabled()
        }

        linkedTaskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        fromAlarm = intent.getBooleanExtra(EXTRA_FROM_ALARM, false)
        val initialSubject = intent.getStringExtra(EXTRA_SUBJECT_TITLE)
            ?.takeIf { it.isNotBlank() }
            ?: prefs.getDefaultStudySubject()
        val initialTarget = intent.getIntExtra(
            EXTRA_TARGET_PAGES,
            prefs.getDefaultStudyTargetPages()
        ).coerceIn(1, 200)

        val hasCam = PermissionHelper.hasCameraPermission(this)
        startElapsedMs = SystemClock.elapsedRealtime()

        uiState.value = StudyUiState(
            hasCameraPermission = hasCam,
            subjectTitle = initialSubject,
            targetPages = initialTarget,
            voiceCoachEnabled = prefs.isVoiceCoachEnabled()
        )

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    delay(1000L)
                    val s = uiState.value
                    if (s.hasCameraPermission && !s.isPaused && !sessionSaved && s.completionMessage == null) {
                        uiState.update { it.copy(elapsedSeconds = it.elapsedSeconds + 1L) }
                    }
                }
            }
        }

        setContent {
            val state by uiState.collectAsStateWithLifecycle()
            ScrollLockGlassTheme {
                StudySessionScreen(
                    state = state,
                    onProvideSurfaceProvider = { provider ->
                        previewSurfaceProvider = provider
                        startCameraIfReady()
                    },
                    onBack = { finish() },
                    onUpdateGoal = { newTarget, newSubject ->
                        prefs.setDefaultStudyTargetPages(newTarget)
                        prefs.setDefaultStudySubject(newSubject)
                        uiState.update {
                            it.copy(
                                targetPages = newTarget.coerceIn(1, 200),
                                subjectTitle = newSubject.ifBlank { "Focused Reading" }
                            )
                        }
                    },
                    onToggleCamera = {
                        val nextBack = !uiState.value.useBackCamera
                        uiState.update { it.copy(useBackCamera = nextBack, torchEnabled = false) }
                        detector.recalibrateBaseline()
                        startCameraIfReady()
                    },
                    onToggleTorch = {
                        val nextTorch = !uiState.value.torchEnabled
                        boundCamera?.cameraControl?.enableTorch(nextTorch)
                        uiState.update { it.copy(torchEnabled = nextTorch) }
                    },
                    onToggleVoice = {
                        val next = !uiState.value.voiceCoachEnabled
                        prefs.setVoiceCoachEnabled(next)
                        voiceCoach.isEnabled = next
                        uiState.update { it.copy(voiceCoachEnabled = next) }
                    },
                    onTogglePause = {
                        val nextPaused = !uiState.value.isPaused
                        uiState.update { it.copy(isPaused = nextPaused) }
                    },
                    onUndoPage = {
                        val updated = detector.adjustCount(-1)
                        uiState.update { it.copy(pagesTurned = updated, statusHint = "Adjusted to $updated pages.") }
                    },
                    onRecalibrate = {
                        detector.recalibrateBaseline()
                        Toast.makeText(this, "Desk baseline recalibrated", Toast.LENGTH_SHORT).show()
                    },
                    onFinishStudy = { completeAndSaveStudySession() },
                    onGrantCamera = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                    onOpenSettings = { PermissionHelper.openAppDetailsSettings(this) },
                    onDoneDialog = {
                        if (fromAlarm) {
                            setResult(RESULT_OK)
                        }
                        finish()
                    }
                )
            }
        }

        if (!hasCam) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onResume() {
        super.onResume()
        val hasCam = PermissionHelper.hasCameraPermission(this)
        if (hasCam != uiState.value.hasCameraPermission) {
            uiState.update { it.copy(hasCameraPermission = hasCam) }
            if (hasCam) startCameraIfReady()
        }
    }

    private fun startCameraIfReady() {
        val surfaceProvider = previewSurfaceProvider ?: return
        if (!PermissionHelper.hasCameraPermission(this)) return

        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = try {
                future.get()
            } catch (_: Exception) {
                return@addListener
            }

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = surfaceProvider
            }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                analyzePageFrame(imageProxy)
            }

            val selector = if (uiState.value.useBackCamera) {
                CameraSelector.DEFAULT_BACK_CAMERA
            } else {
                CameraSelector.DEFAULT_FRONT_CAMERA
            }

            try {
                provider.unbindAll()
                boundCamera = provider.bindToLifecycle(this, selector, preview, analysis)
            } catch (_: Exception) {
                try {
                    provider.unbindAll()
                    boundCamera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
                } catch (_: Exception) {
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyzePageFrame(imageProxy: ImageProxy) {
        try {
            val s = uiState.value
            if (s.isPaused || sessionSaved || s.completionMessage != null) {
                return
            }
            val plane = imageProxy.planes.firstOrNull() ?: return
            val grid = PageTurnDetector.downsampleLumaPlane(
                buffer = plane.buffer,
                width = imageProxy.width,
                height = imageProxy.height,
                rowStride = plane.rowStride,
                pixelStride = plane.pixelStride
            )
            val eval = detector.processLumaGrid(grid, System.currentTimeMillis())

            uiState.update { current ->
                current.copy(
                    pagesTurned = eval.pagesTurned,
                    turnState = eval.state,
                    motionLevel = eval.motionLevel,
                    isLowLight = eval.isLowLight,
                    statusHint = eval.statusHint,
                    pagePulseEventId = if (eval.justTurnedPage) current.pagePulseEventId + 1 else current.pagePulseEventId
                )
            }

            if (eval.justTurnedPage) {
                val target = uiState.value.targetPages
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    voiceCoach.speakMessage("Page ${eval.pagesTurned} of $target")
                    if (!sessionSaved && eval.pagesTurned >= target) {
                        completeAndSaveStudySession()
                    }
                }
            }
        } finally {
            imageProxy.close()
        }
    }

    private fun completeAndSaveStudySession() {
        if (sessionSaved) {
            finish()
            return
        }
        val state = uiState.value
        val pages = state.pagesTurned
        if (pages == 0 && !fromAlarm) {
            finish()
            return
        }
        sessionSaved = true
        val durationSec = ((SystemClock.elapsedRealtime() - startElapsedMs) / 1000L).toInt().coerceAtLeast(1)
        val xp = dbHelper.insertStudySession(
            linkedTaskId,
            state.subjectTitle,
            state.targetPages,
            pages,
            durationSec
        )
        prefs.addUserXp(xp)
        prefs.recordWorkoutDayCompleted()

        val avgPace = if (pages > 0) String.format(Locale.US, "%.1fs/page", durationSec.toFloat() / pages) else "—"
        voiceCoach.speakMessage("Study goal saved. $pages pages completed.")
        uiState.update {
            it.copy(
                completionMessage = "Subject: ${state.subjectTitle}\n" +
                    "Pages Studied: $pages / ${state.targetPages}\n" +
                    "Duration: ${WorkoutDashboardReducer.formatTimer(durationSec.toLong())} • Pace: $avgPace\n" +
                    "Earned: +$xp XP"
            )
        }
    }

    override fun onDestroy() {
        voiceCoach.shutdown()
        cameraExecutor.shutdown()
        super.onDestroy()
    }
}

@Composable
private fun StudySessionScreen(
    state: StudyUiState,
    onProvideSurfaceProvider: (Preview.SurfaceProvider) -> Unit,
    onBack: () -> Unit,
    onUpdateGoal: (Int, String) -> Unit,
    onToggleCamera: () -> Unit,
    onToggleTorch: () -> Unit,
    onToggleVoice: () -> Unit,
    onTogglePause: () -> Unit,
    onUndoPage: () -> Unit,
    onRecalibrate: () -> Unit,
    onFinishStudy: () -> Unit,
    onGrantCamera: () -> Unit,
    onOpenSettings: () -> Unit,
    onDoneDialog: () -> Unit
) {
    var showGoalEditor by remember { mutableStateOf(false) }

    val pulseScale = remember { Animatable(1f) }
    LaunchedEffect(state.pagePulseEventId) {
        if (state.pagePulseEventId > 0) {
            pulseScale.snapTo(1f)
            pulseScale.animateTo(1.18f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
            pulseScale.animateTo(1f, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow))
        }
    }

    val progressFraction by animateFloatAsState(
        targetValue = (state.pagesTurned.toFloat() / state.targetPages.coerceAtLeast(1)).coerceIn(0f, 1f),
        animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow),
        label = "studyProgressRing"
    )

    Box(modifier = Modifier.fillMaxSize().background(GlassColors.VoidBg)) {
        if (state.hasCameraPermission) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        onProvideSurfaceProvider(surfaceProvider)
                    }
                }
            )

            // Scrims for legible glass HUD
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(listOf(GlassColors.VoidBg.copy(alpha = 0.8f), Color.Transparent)))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, GlassColors.VoidBg.copy(alpha = 0.9f))))
            )

            // Top HUD
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = GlassColors.GlassSurface,
                        border = BorderStroke(1.dp, GlassColors.GlassBorder),
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onBack)
                            .testTag("study_back_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = GlassColors.TextPrimary)
                        }
                    }

                    GlassCard(
                        highlighted = true,
                        cornerRadius = 18.dp,
                        onClick = { showGoalEditor = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("study_goal_picker_button")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.MenuBook,
                                null,
                                tint = GlassColors.VioletBright,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "${state.subjectTitle} • Goal ${state.targetPages}p",
                                style = MaterialTheme.typography.titleMedium,
                                color = GlassColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Rounded.Tune, "Set Pages", tint = GlassColors.VioletBright, modifier = Modifier.size(16.dp))
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = GlassColors.GlassSurface,
                        border = BorderStroke(1.dp, GlassColors.GlassBorder),
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onToggleCamera)
                            .testTag("study_camera_flip_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Cameraswitch, "Flip Camera", tint = GlassColors.TextPrimary)
                        }
                    }
                }

                // Quick controls strip (Torch, Voice, Page presets)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (state.useBackCamera) {
                        StudyChip(
                            label = if (state.torchEnabled) "Torch: ON" else "Torch: OFF",
                            icon = if (state.torchEnabled) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                            active = state.torchEnabled,
                            onClick = onToggleTorch
                        )
                    }
                    StudyChip(
                        label = if (state.voiceCoachEnabled) "Audio: ON" else "Audio: OFF",
                        icon = if (state.voiceCoachEnabled) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff,
                        active = state.voiceCoachEnabled,
                        onClick = onToggleVoice
                    )
                    listOf(5, 10, 15, 25, 50).forEach { preset ->
                        StudyChip(
                            label = "${preset}p",
                            icon = null,
                            active = state.targetPages == preset,
                            onClick = { onUpdateGoal(preset, state.subjectTitle) }
                        )
                    }
                }
            }

            // Bottom Study Dashboard HUD
            GlassCard(
                highlighted = state.turnState == PageTurnState.COOLDOWN,
                cornerRadius = 26.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val bannerBg by animateColorAsState(
                        targetValue = when {
                            state.isLowLight -> GlassColors.AmberSoft
                            state.turnState == PageTurnState.COOLDOWN -> GlassColors.MintVerified.copy(alpha = 0.22f)
                            else -> GlassColors.VioletSoft
                        },
                        label = "studyBannerBg"
                    )
                    val bannerTextColor = when {
                        state.isLowLight -> GlassColors.AmberAlarm
                        state.turnState == PageTurnState.COOLDOWN -> GlassColors.MintVerified
                        else -> GlassColors.VioletBright
                    }

                    Surface(shape = RoundedCornerShape(14.dp), color = bannerBg, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = if (state.isPaused) "Study tracking paused" else state.statusHint,
                            style = MaterialTheme.typography.labelLarge,
                            color = bannerTextColor,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(88.dp)
                                .graphicsLayer {
                                    scaleX = pulseScale.value
                                    scaleY = pulseScale.value
                                }
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val sw = 7.dp.toPx()
                                drawArc(
                                    color = GlassColors.GlassBorder,
                                    startAngle = -90f,
                                    sweepAngle = 360f,
                                    useCenter = false,
                                    style = Stroke(width = sw, cap = StrokeCap.Round)
                                )
                                drawArc(
                                    color = GlassColors.VioletBright,
                                    startAngle = -90f,
                                    sweepAngle = 360f * progressFraction,
                                    useCenter = false,
                                    style = Stroke(width = sw, cap = StrokeCap.Round)
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${state.pagesTurned}",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontFamily = AppJetBrainsMonoFamily,
                                        fontSize = 24.sp
                                    ),
                                    color = GlassColors.TextPrimary,
                                    modifier = Modifier.testTag("study_pages_count_text")
                                )
                                Text(
                                    text = "/ ${state.targetPages}p",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = AppJetBrainsMonoFamily),
                                    color = GlassColors.TextSecondary
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Elapsed ${WorkoutDashboardReducer.formatTimer(state.elapsedSeconds)}",
                                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = AppJetBrainsMonoFamily),
                                    color = GlassColors.TextPrimary
                                )
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = GlassColors.VioletSoft
                                ) {
                                    Text(
                                        text = if (state.isPaused) "PAUSED" else state.turnState.label,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = AppJetBrainsMonoFamily,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = GlassColors.VioletBright,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Text(
                                text = "Page-Turn Motion Sensor",
                                style = MaterialTheme.typography.labelSmall,
                                color = GlassColors.TextSecondary
                            )
                            LinearProgressIndicator(
                                progress = { state.motionLevel.coerceIn(0f, 1f) },
                                color = if (state.turnState == PageTurnState.TURNING) GlassColors.CyanAccent else GlassColors.VioletBright,
                                trackColor = GlassColors.GlassBorder,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )

                            val paceText = if (state.pagesTurned > 0) {
                                val secPerPage = state.elapsedSeconds.toFloat() / state.pagesTurned.toFloat()
                                String.format(Locale.US, "Pace %.1fs/page • Tap goal bar to change pages", secPerPage)
                            } else {
                                "Place book clearly in frame & flip pages normally"
                            }
                            Text(
                                text = paceText,
                                style = MaterialTheme.typography.labelSmall,
                                color = GlassColors.TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = onTogglePause,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, GlassColors.GlassBorder),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Icon(
                                if (state.isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                null,
                                tint = GlassColors.TextPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(if (state.isPaused) "Resume" else "Pause", color = GlassColors.TextPrimary, maxLines = 1)
                        }

                        OutlinedButton(
                            onClick = onUndoPage,
                            enabled = state.pagesTurned > 0,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, GlassColors.GlassBorder),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier.weight(0.75f).height(48.dp)
                        ) {
                            Text("-1", color = GlassColors.TextPrimary, fontFamily = AppJetBrainsMonoFamily)
                        }

                        OutlinedButton(
                            onClick = onRecalibrate,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, GlassColors.GlassBorder),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier.weight(0.75f).height(48.dp)
                        ) {
                            Icon(Icons.Rounded.Refresh, "Recalibrate", tint = GlassColors.TextPrimary, modifier = Modifier.size(17.dp))
                        }

                        Button(
                            onClick = onFinishStudy,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GlassColors.VioletPrimary,
                                contentColor = GlassColors.TextPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            modifier = Modifier
                                .weight(1.05f)
                                .height(48.dp)
                                .testTag("study_finish_button")
                        ) {
                            Icon(Icons.Rounded.CheckCircle, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Finish", fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
            }
        } else {
            AmbientNebulaBackground {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.AutoMirrored.Rounded.MenuBook, null, tint = GlassColors.VioletBright, modifier = Modifier.size(52.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Camera Needed for Page Counter",
                        style = MaterialTheme.typography.titleLarge,
                        color = GlassColors.TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "ScrollLock analyzes camera brightness shifts on-device to count when you turn physical pages—zero images leave your phone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GlassColors.TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = onGrantCamera,
                        colors = ButtonDefaults.buttonColors(containerColor = GlassColors.VioletPrimary),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Text("Allow Camera Access")
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onOpenSettings,
                        border = BorderStroke(1.dp, GlassColors.GlassBorder),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("Open App Settings", color = GlassColors.TextPrimary)
                    }
                    TextButton(onClick = onBack, modifier = Modifier.height(48.dp)) {
                        Text("Back", color = GlassColors.TextSecondary)
                    }
                }
            }
        }
    }

    if (showGoalEditor) {
        StudyGoalDialog(
            currentPages = state.targetPages,
            currentSubject = state.subjectTitle,
            onConfirm = { newPages, newSubject ->
                showGoalEditor = false
                onUpdateGoal(newPages, newSubject)
            },
            onDismiss = { showGoalEditor = false }
        )
    }

    state.completionMessage?.let { message ->
        AlertDialog(
            onDismissRequest = {},
            containerColor = GlassColors.DeepIndigoBg,
            titleContentColor = GlassColors.TextPrimary,
            textContentColor = GlassColors.TextSecondary,
            title = { Text("Study Session Complete!") },
            text = { Text(message) },
            confirmButton = {
                Button(
                    onClick = onDoneDialog,
                    colors = ButtonDefaults.buttonColors(containerColor = GlassColors.VioletPrimary)
                ) {
                    Text("Done")
                }
            }
        )
    }
}

@Composable
private fun StudyChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    active: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (active) GlassColors.VioletSoft else GlassColors.GlassSurface,
        border = BorderStroke(1.dp, if (active) GlassColors.ActiveGlowBorder else GlassColors.GlassBorder),
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    null,
                    tint = if (active) GlassColors.VioletBright else GlassColors.TextPrimary,
                    modifier = Modifier.size(15.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (active) GlassColors.VioletBright else GlassColors.TextPrimary
            )
        }
    }
}

@Composable
private fun StudyGoalDialog(
    currentPages: Int,
    currentSubject: String,
    onConfirm: (Int, String) -> Unit,
    onDismiss: () -> Unit
) {
    var pagesSlider by remember { mutableFloatStateOf(currentPages.toFloat()) }
    var subject by remember { mutableStateOf(currentSubject) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GlassColors.DeepIndigoBg,
        titleContentColor = GlassColors.TextPrimary,
        title = { Text("Choose Study Goal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Book / Subject") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GlassColors.VioletBright,
                        unfocusedBorderColor = GlassColors.GlassBorder,
                        focusedTextColor = GlassColors.TextPrimary,
                        unfocusedTextColor = GlassColors.TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                val selectedPages = pagesSlider.roundToInt().coerceIn(1, 200)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        shape = CircleShape,
                        color = GlassColors.GlassCard,
                        border = BorderStroke(1.dp, GlassColors.GlassBorder),
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable { pagesSlider = (selectedPages - 1).coerceAtLeast(1).toFloat() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Remove, "Decrease pages", tint = GlassColors.TextPrimary)
                        }
                    }

                    Text(
                        text = "$selectedPages pages",
                        style = MaterialTheme.typography.titleLarge.copy(fontFamily = AppJetBrainsMonoFamily),
                        color = GlassColors.VioletBright
                    )

                    Surface(
                        shape = CircleShape,
                        color = GlassColors.GlassCard,
                        border = BorderStroke(1.dp, GlassColors.GlassBorder),
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable { pagesSlider = (selectedPages + 1).coerceAtMost(200).toFloat() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Add, "Increase pages", tint = GlassColors.TextPrimary)
                        }
                    }
                }

                Slider(
                    value = pagesSlider,
                    onValueChange = { pagesSlider = it },
                    valueRange = 1f..200f,
                    colors = SliderDefaults.colors(
                        thumbColor = GlassColors.VioletBright,
                        activeTrackColor = GlassColors.VioletPrimary,
                        inactiveTrackColor = GlassColors.GlassBorder
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(pagesSlider.roundToInt().coerceIn(1, 200), subject.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = GlassColors.VioletPrimary)
            ) {
                Text("Apply Goal")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = GlassColors.TextSecondary)
            }
        }
    )
}
