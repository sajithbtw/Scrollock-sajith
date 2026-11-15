package com.sajith.scrolllock.ui.workout

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import com.sajith.scrolllock.data.local.PreferencesManager
import com.sajith.scrolllock.data.local.ScrollLockDatabaseHelper
import com.sajith.scrolllock.util.PermissionHelper
import com.sajith.scrolllock.workout.ExerciseType
import com.sajith.scrolllock.workout.GhostTrainerGenerator
import com.sajith.scrolllock.workout.MlKitPoseMapper
import com.sajith.scrolllock.workout.RepCounterEngine
import com.sajith.scrolllock.workout.RepReplaySerializer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Live AI Workout & Rep Counter camera powered by Jetpack Compose Canvas skeleton rendering,
 * reactive WorkoutDashboard State objects, CameraX live feed, and on-device Google ML Kit Pose Detection.
 */
class WorkoutCameraActivity : ComponentActivity() {

    companion object {
        const val EXTRA_UNLOCK_PKG = "extra_unlock_pkg"
        const val EXTRA_UNLOCK_APP_NAME = "extra_unlock_app_name"
        const val EXTRA_UNLOCK_SHORT_FORM_ONLY = "extra_unlock_short_form_only"
        const val EXTRA_TARGET_REPS = "extra_target_reps"
        const val EXTRA_EXERCISE_TYPE = "extra_exercise_type"
        const val EXTRA_TASK_ID = "extra_task_id"
    }

    private lateinit var prefs: PreferencesManager
    private lateinit var dbHelper: ScrollLockDatabaseHelper
    private lateinit var voiceCoach: VoiceCoachManager
    private lateinit var repEngine: RepCounterEngine

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var poseDetector: PoseDetector? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var previewSurfaceProvider: Preview.SurfaceProvider? = null

    private var unlockPackageName: String? = null
    private var unlockAppName: String? = null
    private var unlockShortFormOnly: Boolean = false
    private var linkedTaskId: Long = -1L
    private var targetReps: Int = 10
    private var useFrontCamera: Boolean = true
    private var isPaused: Boolean = false
    private var sessionSaved: Boolean = false
    private var workoutStartElapsedMs: Long = 0L
    private var lastRepAnnounced: Int = 0
    private var lastExerciseMode: ExerciseType = ExerciseType.SQUATS

    private val uiState = MutableStateFlow(WorkoutUiState())
    private val dashboardFlow = MutableStateFlow(WorkoutDashboardState())

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        uiState.update { it.copy(hasCameraPermission = granted) }
        if (granted) {
            startCameraIfReady()
        }
    }

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startVoiceCommandListening()
        } else {
            Toast.makeText(this, "Microphone permission is needed for voice commands.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        prefs = PreferencesManager(this)
        dbHelper = ScrollLockDatabaseHelper.getInstance(this)
        voiceCoach = VoiceCoachManager(this).apply {
            isEnabled = prefs.isVoiceCoachEnabled()
        }

        unlockPackageName = intent.getStringExtra(EXTRA_UNLOCK_PKG)
        unlockAppName = intent.getStringExtra(EXTRA_UNLOCK_APP_NAME)
        unlockShortFormOnly = intent.getBooleanExtra(EXTRA_UNLOCK_SHORT_FORM_ONLY, false)
        linkedTaskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        targetReps = intent.getIntExtra(EXTRA_TARGET_REPS, prefs.getRequiredWorkoutReps()).coerceAtLeast(1)

        val exerciseRaw = intent.getStringExtra(EXTRA_EXERCISE_TYPE) ?: prefs.getPreferredExerciseName()
        lastExerciseMode = try {
            ExerciseType.valueOf(exerciseRaw)
        } catch (_: Exception) {
            ExerciseType.SQUATS
        }
        if (lastExerciseMode == ExerciseType.PLANKS && !intent.hasExtra(EXTRA_TARGET_REPS)) {
            targetReps = 30
        }

        repEngine = RepCounterEngine(initialExercise = lastExerciseMode)
        workoutStartElapsedMs = SystemClock.elapsedRealtime()

        val options = PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build()
        poseDetector = PoseDetection.getClient(options)

        val hasCam = PermissionHelper.hasCameraPermission(this)
        uiState.value = WorkoutUiState(
            hasCameraPermission = hasCam,
            useFrontCamera = useFrontCamera,
            showGhostTrainer = prefs.isGhostTrainerEnabled(),
            voiceCoachEnabled = prefs.isVoiceCoachEnabled(),
            selectedExercise = lastExerciseMode,
            unlockAppName = unlockAppName,
            cameraSubStatus = lastExerciseMode.cameraGuide
        )
        dashboardFlow.value = WorkoutDashboardState(
            targetReps = targetReps,
            selectedExercise = lastExerciseMode,
            detectedExercise = lastExerciseMode,
            plankRemainingSeconds = if (lastExerciseMode == ExerciseType.PLANKS) targetReps else 0
        )

        startWorkoutTimerLoop()

        setContent {
            val state by uiState.collectAsStateWithLifecycle()
            val dashState = dashboardFlow.collectAsStateWithLifecycle()
            ScrollLockWorkoutTheme {
                WorkoutScreen(
                    uiState = state,
                    dashboardState = dashState,
                    onProvideSurfaceProvider = { provider ->
                        previewSurfaceProvider = provider
                        startCameraIfReady()
                    },
                    onCloseClick = { finish() },
                    onSelectExercise = { exercise -> selectExercise(exercise) },
                    onToggleCamera = {
                        useFrontCamera = !useFrontCamera
                        uiState.update { it.copy(useFrontCamera = useFrontCamera) }
                        startCameraIfReady()
                    },
                    onToggleGhost = {
                        val next = !prefs.isGhostTrainerEnabled()
                        prefs.setGhostTrainerEnabled(next)
                        uiState.update { it.copy(showGhostTrainer = next) }
                    },
                    onToggleVoiceCoach = {
                        val next = !prefs.isVoiceCoachEnabled()
                        prefs.setVoiceCoachEnabled(next)
                        voiceCoach.isEnabled = next
                        uiState.update { it.copy(voiceCoachEnabled = next) }
                    },
                    onVoiceCommandClick = {
                        if (PermissionHelper.hasAudioPermission(this)) {
                            startVoiceCommandListening()
                        } else {
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onTogglePause = { togglePause() },
                    onToggleRest = { toggleRestTimer() },
                    onOpenReplay = {
                        saveReplayCache()
                        startActivity(Intent(this, RepReplayActivity::class.java))
                    },
                    onFinishWorkout = {
                        completeAndSaveWorkout(unlockedApp = false)
                    },
                    onGrantCameraPermission = {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    onOpenAppSettings = {
                        PermissionHelper.openAppDetailsSettings(this)
                    },
                    onCompletionContinue = {
                        val summary = uiState.value.completionSummary
                        if (summary?.unlockedApp == true || summary?.challengeMet == true) {
                            setResult(RESULT_OK)
                        }
                        finish()
                    },
                    onCompletionReplay = {
                        val summary = uiState.value.completionSummary
                        startActivity(Intent(this, RepReplayActivity::class.java))
                        if (summary?.unlockedApp == true || summary?.challengeMet == true) {
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

    private fun startWorkoutTimerLoop() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    delay(1000L)
                    if (!uiState.value.hasCameraPermission || sessionSaved || uiState.value.completionSummary != null) {
                        continue
                    }
                    val wasResting = dashboardFlow.value.isResting
                    dashboardFlow.update { current ->
                        WorkoutDashboardReducer.tickOneSecond(current, sessionEnded = sessionSaved)
                    }
                    if (wasResting && !dashboardFlow.value.isResting) {
                        voiceCoach.speakMessage("Rest complete, resume your set")
                    }
                }
            }
        }
    }

    private fun togglePause() {
        isPaused = !isPaused
        dashboardFlow.update { it.copy(isPaused = isPaused) }
        voiceCoach.speakMessage(if (isPaused) "Workout paused" else "Resuming workout")
    }

    private fun toggleRestTimer() {
        val current = dashboardFlow.value
        if (current.isResting) {
            dashboardFlow.update {
                it.copy(
                    isResting = false,
                    restRemainingSeconds = 0,
                    phaseLabel = "UP",
                    postureFeedback = "Rest skipped — resume your set",
                    isAlertFeedback = false
                )
            }
            voiceCoach.speakMessage("Resuming set")
        } else {
            val restSecs = current.recommendedRestSeconds.coerceAtLeast(15)
            dashboardFlow.update {
                it.copy(
                    isResting = true,
                    restRemainingSeconds = restSecs,
                    isPaused = false,
                    phaseLabel = "REST"
                )
            }
            isPaused = false
            voiceCoach.speakMessage("Starting $restSecs second recovery rest")
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

    private fun selectExercise(exercise: ExerciseType) {
        lastExerciseMode = exercise
        repEngine.selectedExercise = exercise
        prefs.setPreferredExerciseName(exercise.name)
        if (exercise == ExerciseType.PLANKS && unlockPackageName == null) {
            targetReps = 30
        }
        uiState.update {
            it.copy(
                selectedExercise = exercise,
                cameraSubStatus = exercise.cameraGuide
            )
        }
        dashboardFlow.update {
            it.copy(
                selectedExercise = exercise,
                detectedExercise = exercise,
                targetReps = targetReps,
                plankRemainingSeconds = if (exercise == ExerciseType.PLANKS) {
                    (targetReps - repEngine.getPlankHoldSeconds()).coerceAtLeast(0)
                } else {
                    0
                }
            )
        }
    }

    private fun startCameraIfReady() {
        val surfaceProvider = previewSurfaceProvider ?: return
        if (!PermissionHelper.hasCameraPermission(this)) return

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = try {
                cameraProviderFuture.get()
            } catch (_: Exception) {
                return@addListener
            }

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = surfaceProvider
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                analyzeImageProxy(imageProxy)
            }

            val selector = if (useFrontCamera) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, selector, preview, imageAnalysis)
            } catch (_: Exception) {
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
                } catch (_: Exception) {
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(ExperimentalGetImage::class)
    private fun analyzeImageProxy(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        val detector = poseDetector
        if (mediaImage == null || detector == null || isPaused ||
            dashboardFlow.value.isResting || uiState.value.completionSummary != null
        ) {
            imageProxy.close()
            return
        }

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val rotatedW = if (rotationDegrees == 90 || rotationDegrees == 270) imageProxy.height else imageProxy.width
        val rotatedH = if (rotationDegrees == 90 || rotationDegrees == 270) imageProxy.width else imageProxy.height
        val avgLuma = estimateAverageLuma(imageProxy)

        val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)
        detector.process(inputImage)
            .addOnSuccessListener { pose ->
                val rawMap = MlKitPoseMapper.toNormalizedMap(
                    pose = pose,
                    rotatedImageWidth = rotatedW,
                    rotatedImageHeight = rotatedH,
                    isFrontCamera = useFrontCamera
                )
                val camStatus = MlKitPoseMapper.checkCameraPosition(lastExerciseMode, rawMap, avgLuma)
                val eval = repEngine.processFrame(rawMap, System.currentTimeMillis())
                val showGhost = prefs.isGhostTrainerEnabled()
                val ghostPose = if (showGhost) {
                    GhostTrainerGenerator.generateIdealPose(eval.detectedExercise, eval.phase)
                } else {
                    emptyMap()
                }
                val recordedCount = repEngine.getRecordedReps().size
                val subStatusText = when {
                    camStatus.lightingWarning != null -> camStatus.lightingWarning
                    !eval.isCalibrated -> "Calibrating posture (${(eval.calibrationProgress * 100).toInt()}%)…"
                    else -> camStatus.recommendedAngle
                }

                uiState.update {
                    it.copy(
                        showGhostTrainer = showGhost,
                        cameraGuidance = camStatus.guidanceMessage,
                        cameraSubStatus = subStatusText,
                        isCalibrated = eval.isCalibrated,
                        calibrationProgress = eval.calibrationProgress,
                        isBodyAligned = camStatus.isReady && !eval.isCheatingDetected && eval.errorJoints.isEmpty(),
                        userLandmarks = rawMap,
                        ghostLandmarks = ghostPose,
                        errorJoints = eval.errorJoints,
                        analysisWidth = rotatedW,
                        analysisHeight = rotatedH
                    )
                }

                dashboardFlow.update { current ->
                    WorkoutDashboardReducer.applyEvaluation(
                        current = current,
                        eval = eval,
                        recordedReplayCount = recordedCount
                    )
                }

                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (eval.repCount > lastRepAnnounced && eval.detectedExercise != ExerciseType.PLANKS) {
                        lastRepAnnounced = eval.repCount
                        voiceCoach.speakRepCount(eval.repCount, targetReps)
                    } else if (eval.postureCorrection != null && camStatus.isReady) {
                        voiceCoach.speakPostureCue(eval.postureCorrection)
                    }

                    if (!sessionSaved && eval.repCount >= targetReps) {
                        onTargetRepsReached()
                    }
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun estimateAverageLuma(imageProxy: ImageProxy): Float {
        return try {
            val plane = imageProxy.planes.firstOrNull() ?: return 120f
            val buffer = plane.buffer
            buffer.rewind()
            var sum = 0L
            var count = 0
            val step = 64
            while (buffer.remaining() > 0) {
                sum += (buffer.get().toInt() and 0xFF)
                count++
                val nextPos = buffer.position() + step
                if (nextPos < buffer.limit()) buffer.position(nextPos) else break
            }
            if (count == 0) 120f else sum.toFloat() / count.toFloat()
        } catch (_: Exception) {
            120f
        }
    }

    private fun onTargetRepsReached() {
        val unlocked = !unlockPackageName.isNullOrBlank()
        completeAndSaveWorkout(unlockedApp = unlocked)
    }

    private fun completeAndSaveWorkout(unlockedApp: Boolean) {
        if (sessionSaved) {
            finish()
            return
        }
        val repsDone = lastRepAnnounced.coerceAtLeast(repEngine.getRepCount())
        val invalidReps = repEngine.getInvalidRepCount()
        if (repsDone == 0 && !unlockedApp) {
            finish()
            return
        }
        sessionSaved = true
        saveReplayCache()

        val durationSec = ((SystemClock.elapsedRealtime() - workoutStartElapsedMs) / 1000L).toInt().coerceAtLeast(1)
        val avgForm = repEngine.getAverageFormScore()
        val balance = repEngine.getBalanceReport().symmetryPercent
        val fatigue = repEngine.getFatigueStatus().level.label

        val xp = dbHelper.insertWorkoutSession(
            lastExerciseMode.name,
            repsDone,
            invalidReps,
            avgForm,
            durationSec,
            balance,
            fatigue,
            unlockedApp
        )
        prefs.addUserXp(xp)
        prefs.recordWorkoutDayCompleted()

        val goalMet = repsDone >= targetReps
        if (linkedTaskId > 0L && repsDone > 0) {
            dbHelper.updateTaskProgress(linkedTaskId, repsDone, goalMet)
        }
        if (goalMet) {
            setResult(RESULT_OK)
        }

        if (unlockedApp && !unlockPackageName.isNullOrBlank()) {
            val unlockMins = prefs.getUnlockDurationMinutes()
            if (unlockShortFormOnly) {
                prefs.grantShortFormUnlock(unlockPackageName, unlockMins)
            } else {
                prefs.grantAppUnlock(unlockPackageName, unlockMins)
            }
            voiceCoach.speakMessage("Goal completed! ${unlockAppName ?: "App"} unlocked for $unlockMins minutes.")
            uiState.update {
                it.copy(
                    completionSummary = WorkoutCompletionSummary(
                        title = "Workout Complete!",
                        message = "${unlockAppName ?: "App"} is unlocked for $unlockMins minutes.\n" +
                            "Reps: $repsDone • Form: $avgForm% • Balance: $balance% • +$xp XP",
                        unlockedApp = true,
                        challengeMet = goalMet
                    )
                )
            }
        } else {
            voiceCoach.speakMessage("Workout saved. $repsDone reps completed.")
            uiState.update {
                it.copy(
                    completionSummary = WorkoutCompletionSummary(
                        title = if (goalMet && linkedTaskId > 0L) "Task Challenge Complete!" else "Workout Quality Report",
                        message = "Exercise: ${lastExerciseMode.displayName}\n" +
                            "Valid Reps: $repsDone (Rejected: $invalidReps)\n" +
                            "Form Score: $avgForm%\n" +
                            "L/R Symmetry: $balance%\n" +
                            "Fatigue: $fatigue • +$xp XP earned",
                        unlockedApp = false,
                        challengeMet = goalMet
                    )
                )
            }
        }
    }

    private fun saveReplayCache() {
        val records = repEngine.getRecordedReps()
        if (records.isNotEmpty()) {
            prefs.saveLatestReplayJson(RepReplaySerializer.toJson(records))
        }
    }

    private fun startVoiceCommandListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition not available on this device.", Toast.LENGTH_SHORT).show()
            return
        }
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Toast.makeText(this@WorkoutCameraActivity, "Listening: say pause, resume, squats, pushups…", Toast.LENGTH_SHORT).show()
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
                override fun onResults(results: Bundle?) {
                    val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.lowercase(Locale.US) ?: return
                    when {
                        spoken.contains("pause") -> {
                            isPaused = true
                            dashboardFlow.update { it.copy(isPaused = true) }
                            voiceCoach.speakMessage("Paused")
                        }
                        spoken.contains("resume") || spoken.contains("start") -> {
                            isPaused = false
                            dashboardFlow.update { it.copy(isPaused = false, isResting = false) }
                            voiceCoach.speakMessage("Resumed")
                        }
                        spoken.contains("rest") -> toggleRestTimer()
                        spoken.contains("squat") -> {
                            selectExercise(ExerciseType.SQUATS)
                            voiceCoach.speakMessage("Switched to squats")
                        }
                        spoken.contains("push") -> {
                            selectExercise(ExerciseType.PUSHUPS)
                            voiceCoach.speakMessage("Switched to pushups")
                        }
                        spoken.contains("high knee") || spoken.contains("knee") -> {
                            selectExercise(ExerciseType.HIGH_KNEES)
                            voiceCoach.speakMessage("Switched to high knees")
                        }
                        spoken.contains("press") || spoken.contains("shoulder") -> {
                            selectExercise(ExerciseType.SHOULDER_PRESS)
                            voiceCoach.speakMessage("Switched to overhead press")
                        }
                        spoken.contains("curl") || spoken.contains("bicep") -> {
                            selectExercise(ExerciseType.BICEP_CURLS)
                            voiceCoach.speakMessage("Switched to arm curls")
                        }
                        spoken.contains("sit") || spoken.contains("crunch") -> {
                            selectExercise(ExerciseType.SIT_UPS)
                            voiceCoach.speakMessage("Switched to sit ups")
                        }
                        spoken.contains("lunge") -> {
                            selectExercise(ExerciseType.LUNGES)
                            voiceCoach.speakMessage("Switched to lunges")
                        }
                        spoken.contains("jack") -> {
                            selectExercise(ExerciseType.JUMPING_JACKS)
                            voiceCoach.speakMessage("Switched to jumping jacks")
                        }
                        spoken.contains("plank") -> {
                            selectExercise(ExerciseType.PLANKS)
                            voiceCoach.speakMessage("Switched to plank hold")
                        }
                        spoken.contains("finish") || spoken.contains("stop") -> {
                            completeAndSaveWorkout(unlockedApp = false)
                        }
                    }
                }
            })
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        speechRecognizer?.startListening(intent)
    }

    override fun onDestroy() {
        voiceCoach.shutdown()
        speechRecognizer?.destroy()
        poseDetector?.close()
        cameraExecutor.shutdown()
        super.onDestroy()
    }
}
