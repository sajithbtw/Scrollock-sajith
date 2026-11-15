package com.sajith.scrolllock.workout

/**
 * Pure, unit-testable repetition counter & movement analyzer.
 * Accepts normalized pose landmarks + timestamps and returns rep count, phase (START/DESCENDING/BOTTOM/RETURNING),
 * UP/DOWN state, posture corrections, anti-cheat verdicts, and recorded rep replay frames.
 */
class RepCounterEngine(
    initialExercise: ExerciseType = ExerciseType.SQUATS,
    minRepDurationMs: Long = 1000L
) {
    var selectedExercise: ExerciseType = initialExercise
        set(value) {
            if (field != value) {
                field = value
                resetRepCycleOnly()
            }
        }

    var requireFullBodyInFrame: Boolean = true

    private val smoother = LandmarkSmoother(alpha = 0.50f)
    private val calibrator = DynamicCalibrator(requiredSamples = 8)
    private val antiCheat = AntiCheatValidator(minRepDurationMs = minRepDurationMs)
    private val fatigueAnalyzer = FatigueAnalyzer()
    private val balanceAnalyzer = BodyBalanceAnalyzer()

    private var repCount = 0
    private var invalidRepCount = 0
    private var phase = RepPhase.START
    private var reachedBottomInCycle = false
    private var repStartMs = 0L
    private var minAngleInCycle = 180f
    private var maxAngleInCycle = 0f

    private var plankAccumulatedMs = 0L
    private var lastFrameMs = 0L

    private val currentCycleFrames = mutableListOf<PoseFrame>()
    private val currentCycleCues = mutableSetOf<String>()
    private val recordedReps = mutableListOf<RepAnalysisRecord>()
    private val formScores = mutableListOf<Int>()

    fun reset() {
        smoother.reset()
        calibrator.reset()
        antiCheat.reset()
        fatigueAnalyzer.reset()
        balanceAnalyzer.reset()
        repCount = 0
        invalidRepCount = 0
        plankAccumulatedMs = 0L
        lastFrameMs = 0L
        recordedReps.clear()
        formScores.clear()
        resetRepCycleOnly()
    }

    private fun resetRepCycleOnly() {
        phase = RepPhase.START
        reachedBottomInCycle = false
        repStartMs = 0L
        minAngleInCycle = 180f
        maxAngleInCycle = 0f
        currentCycleFrames.clear()
        currentCycleCues.clear()
    }

    fun getRecordedReps(): List<RepAnalysisRecord> = recordedReps.toList()

    fun getRepCount(): Int = if (selectedExercise == ExerciseType.PLANKS) getPlankHoldSeconds() else repCount

    fun getInvalidRepCount(): Int = invalidRepCount

    fun getPlankHoldSeconds(): Int = (plankAccumulatedMs / 1000L).toInt()

    fun getBalanceReport(): BalanceReport = balanceAnalyzer.getReport()

    fun getFatigueStatus(): FatigueStatus = fatigueAnalyzer.evaluate()

    fun getAverageFormScore(): Int {
        return if (formScores.isEmpty()) 100 else formScores.average().toInt().coerceIn(0, 100)
    }

    /**
     * Evaluates a single frame of normalized pose landmarks.
     */
    fun processFrame(
        rawLandmarks: Map<Int, LandmarkPoint>,
        timestampMs: Long = System.currentTimeMillis()
    ): WorkoutEvaluation {
        val landmarks = smoother.smooth(rawLandmarks)
        val activeExercise = if (selectedExercise == ExerciseType.AUTO) {
            ExerciseDetectors.classifyExercise(landmarks, calibrator)
        } else {
            selectedExercise
        }

        // Anti-cheat frame & visibility check
        val frameCheck = antiCheat.validateFrame(activeExercise, landmarks, requireFullBodyInFrame)
        if (!frameCheck.isClean) {
            lastFrameMs = timestampMs
            return WorkoutEvaluation(
                detectedExercise = activeExercise,
                repCount = repCount,
                invalidRepCount = invalidRepCount,
                phase = phase,
                stateLabel = if (phase == RepPhase.BOTTOM || phase == RepPhase.DESCENDING) "DOWN" else "UP",
                primaryAngle = 0f,
                secondaryAngle = 0f,
                formQualityScore = 0,
                errorJoints = emptySet(),
                postureCorrection = frameCheck.reason,
                isCheatingDetected = true,
                cheatReason = frameCheck.reason,
                plankHoldSeconds = (plankAccumulatedMs / 1000L).toInt(),
                isCalibrated = calibrator.profile.isCalibrated,
                calibrationProgress = (calibrator.profile.sampleCount / 8f).coerceIn(0f, 1f),
                fatigueStatus = fatigueAnalyzer.evaluate(),
                balanceReport = balanceAnalyzer.getReport()
            )
        }

        // Dynamic joint angle calibration while user is in neutral starting posture
        if (!calibrator.profile.isCalibrated && phase == RepPhase.START) {
            calibrator.update(landmarks)
        }

        val signal = ExerciseDetectors.evaluate(activeExercise, landmarks, calibrator)
        balanceAnalyzer.addSample(signal.leftMeasureAngle, signal.rightMeasureAngle)
        formScores.add(signal.formQualityScore)

        // Plank Hold uses time accumulation instead of up/down rep cycles
        if (activeExercise == ExerciseType.PLANKS) {
            if (lastFrameMs > 0L && signal.isPlankHolding) {
                val delta = (timestampMs - lastFrameMs).coerceIn(0L, 500L)
                plankAccumulatedMs += delta
            }
            lastFrameMs = timestampMs
            val holdSeconds = (plankAccumulatedMs / 1000L).toInt()
            return WorkoutEvaluation(
                detectedExercise = activeExercise,
                repCount = holdSeconds,
                invalidRepCount = invalidRepCount,
                phase = if (signal.isPlankHolding) RepPhase.BOTTOM else RepPhase.START,
                stateLabel = if (signal.isPlankHolding) "HOLD" else "ADJUST",
                primaryAngle = signal.primaryAngle,
                secondaryAngle = signal.secondaryAngle,
                formQualityScore = signal.formQualityScore,
                errorJoints = signal.errorJoints,
                postureCorrection = signal.postureCue,
                isCheatingDetected = false,
                cheatReason = null,
                plankHoldSeconds = holdSeconds,
                isCalibrated = calibrator.profile.isCalibrated,
                calibrationProgress = (calibrator.profile.sampleCount / 8f).coerceIn(0f, 1f),
                fatigueStatus = fatigueAnalyzer.evaluate(),
                balanceReport = balanceAnalyzer.getReport()
            )
        }

        lastFrameMs = timestampMs

        // Track min/max angles & replay frames during active movement
        if (phase != RepPhase.START || signal.isDescending) {
            minAngleInCycle = minOf(minAngleInCycle, signal.primaryAngle)
            maxAngleInCycle = maxOf(maxAngleInCycle, signal.primaryAngle)
            if (currentCycleFrames.size < 90) {
                currentCycleFrames.add(
                    PoseFrame(
                        timestampMs = timestampMs,
                        landmarks = landmarks,
                        jointAngles = signal.jointAnglesMap,
                        errorJoints = signal.errorJoints,
                        feedback = signal.postureCue
                    )
                )
            }
            signal.postureCue?.let { currentCycleCues.add(it) }
        }

        var justCompletedRep: RepAnalysisRecord? = null
        var cheatAlert: String? = null

        when (phase) {
            RepPhase.START -> {
                maxAngleInCycle = maxOf(maxAngleInCycle, signal.primaryAngle)
                if (signal.isDescending) {
                    phase = RepPhase.DESCENDING
                    repStartMs = timestampMs
                    minAngleInCycle = signal.primaryAngle
                }
            }
            RepPhase.DESCENDING -> {
                if (signal.isAtBottom) {
                    phase = RepPhase.BOTTOM
                    reachedBottomInCycle = true
                } else if (signal.isAtTop && !reachedBottomInCycle) {
                    // User aborted movement before reaching bottom -> reject half rep
                    val elapsed = timestampMs - repStartMs
                    if (elapsed > 300L) {
                        invalidRepCount++
                        cheatAlert = "Half rep rejected — go all the way down."
                        justCompletedRep = buildRepRecord(
                            repNumber = invalidRepCount,
                            exercise = activeExercise,
                            isValid = false,
                            rejectionReason = cheatAlert,
                            durationMs = elapsed,
                            formScore = signal.formQualityScore
                        )
                        recordReplaySafely(justCompletedRep)
                    }
                    resetRepCycleOnly()
                }
            }
            RepPhase.BOTTOM -> {
                if (!signal.isAtBottom) {
                    phase = RepPhase.RETURNING
                }
            }
            RepPhase.RETURNING -> {
                if (signal.isAtBottom) {
                    phase = RepPhase.BOTTOM
                } else if (signal.isAtTop) {
                    val check = antiCheat.validateCompletedRep(
                        timestampMs = timestampMs,
                        repStartMs = repStartMs,
                        reachedBottom = reachedBottomInCycle,
                        exercise = activeExercise,
                        minAngleReached = minAngleInCycle,
                        maxAngleReached = maxAngleInCycle
                    )
                    val duration = timestampMs - repStartMs
                    if (check.isClean) {
                        repCount++
                        fatigueAnalyzer.recordRep(duration, minAngleInCycle, maxAngleInCycle)
                        justCompletedRep = buildRepRecord(
                            repNumber = repCount,
                            exercise = activeExercise,
                            isValid = true,
                            rejectionReason = null,
                            durationMs = duration,
                            formScore = signal.formQualityScore
                        )
                    } else {
                        invalidRepCount++
                        cheatAlert = check.reason
                        justCompletedRep = buildRepRecord(
                            repNumber = invalidRepCount,
                            exercise = activeExercise,
                            isValid = false,
                            rejectionReason = check.reason,
                            durationMs = duration,
                            formScore = signal.formQualityScore
                        )
                    }
                    recordReplaySafely(justCompletedRep)
                    resetRepCycleOnly()
                }
            }
        }

        val stateLabel = when (phase) {
            RepPhase.BOTTOM, RepPhase.DESCENDING -> "DOWN"
            RepPhase.RETURNING, RepPhase.START -> "UP"
        }

        return WorkoutEvaluation(
            detectedExercise = activeExercise,
            repCount = repCount,
            invalidRepCount = invalidRepCount,
            phase = phase,
            stateLabel = stateLabel,
            primaryAngle = signal.primaryAngle,
            secondaryAngle = signal.secondaryAngle,
            formQualityScore = signal.formQualityScore,
            errorJoints = signal.errorJoints,
            postureCorrection = cheatAlert ?: signal.postureCue,
            isCheatingDetected = cheatAlert != null,
            cheatReason = cheatAlert,
            plankHoldSeconds = 0,
            isCalibrated = calibrator.profile.isCalibrated,
            calibrationProgress = (calibrator.profile.sampleCount / 8f).coerceIn(0f, 1f),
            fatigueStatus = fatigueAnalyzer.evaluate(),
            balanceReport = balanceAnalyzer.getReport(),
            justCompletedRep = justCompletedRep
        )
    }

    private fun buildRepRecord(
        repNumber: Int,
        exercise: ExerciseType,
        isValid: Boolean,
        rejectionReason: String?,
        durationMs: Long,
        formScore: Int
    ): RepAnalysisRecord {
        val balance = balanceAnalyzer.getReport()
        val lrDiff = kotlin.math.abs(balance.leftAvgAngle - balance.rightAvgAngle)
        return RepAnalysisRecord(
            repNumber = repNumber,
            exercise = exercise,
            isValid = isValid,
            rejectionReason = rejectionReason,
            durationMs = durationMs,
            minPrimaryAngle = minAngleInCycle,
            maxPrimaryAngle = maxAngleInCycle,
            leftRightAsymmetryDeg = lrDiff,
            formQualityScore = formScore,
            postureCues = currentCycleCues.toList(),
            frames = currentCycleFrames.toList()
        )
    }

    private fun recordReplaySafely(record: RepAnalysisRecord) {
        if (recordedReps.size >= 40) {
            recordedReps.removeAt(0)
        }
        recordedReps.add(record)
    }
}
