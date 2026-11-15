package com.sajith.scrolllock.ui.workout

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.SeekBar
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.databinding.ActivityRepReplayBinding
import com.google.android.material.chip.Chip
import com.sajith.scrolllock.data.local.PreferencesManager
import com.sajith.scrolllock.workout.GhostTrainerGenerator
import com.sajith.scrolllock.workout.RepAnalysisRecord
import com.sajith.scrolllock.workout.RepPhase
import com.sajith.scrolllock.workout.RepReplaySerializer
import java.util.Locale

/**
 * Allows users to replay recorded exercise reps frame-by-frame, compare against the
 * Ghost Trainer reference skeleton, and inspect joint angles and posture errors.
 */
class RepReplayActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRepReplayBinding
    private val handler = Handler(Looper.getMainLooper())
    private var records: List<RepAnalysisRecord> = emptyList()
    private var currentRepIndex = 0
    private var currentFrameIndex = 0
    private var isPlaying = true
    private var showGhost = true

    private val playbackRunnable = object : Runnable {
        override fun run() {
            if (!isPlaying || records.isEmpty()) return
            val rep = records[currentRepIndex]
            if (rep.frames.isNotEmpty()) {
                currentFrameIndex = (currentFrameIndex + 1) % rep.frames.size
                renderCurrentFrame()
                handler.postDelayed(this, 85L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityRepReplayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.replayRoot) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val pad = (16 * resources.displayMetrics.density).toInt()
            v.setPadding(pad + bars.left, pad + bars.top, pad + bars.right, pad + bars.bottom)
            insets
        }

        binding.btnReplayBack.setOnClickListener { finish() }

        val json = PreferencesManager(this).getLatestReplayJson()
        records = RepReplaySerializer.fromJson(json)

        if (records.isEmpty()) {
            binding.tvReplayEmpty.visibility = View.VISIBLE
            binding.cardReplayControls.visibility = View.GONE
            return
        }

        setupRepChips()
        setupControls()
        selectRep(0)
        handler.postDelayed(playbackRunnable, 120L)
    }

    private fun setupRepChips() {
        binding.chipGroupReps.removeAllViews()
        records.forEachIndexed { index, rep ->
            val chip = Chip(this).apply {
                id = View.generateViewId()
                text = if (rep.isValid) "Rep ${rep.repNumber}" else "Invalid #${rep.repNumber}"
                isCheckable = true
                isChecked = index == 0
                setOnClickListener { selectRep(index) }
            }
            binding.chipGroupReps.addView(chip)
        }
    }

    private fun setupControls() {
        binding.btnReplayPlayPause.setOnClickListener {
            isPlaying = !isPlaying
            handler.removeCallbacks(playbackRunnable)
            if (isPlaying) {
                handler.postDelayed(playbackRunnable, 85L)
            }
        }

        binding.btnReplayGhostToggle.setOnClickListener {
            showGhost = !showGhost
            binding.btnReplayGhostToggle.text = if (showGhost) "Ghost: ON" else "Ghost: OFF"
            renderCurrentFrame()
        }

        binding.seekReplay.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && records.isNotEmpty()) {
                    currentFrameIndex = progress.coerceIn(0, (records[currentRepIndex].frames.size - 1).coerceAtLeast(0))
                    renderCurrentFrame()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isPlaying = false
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun selectRep(index: Int) {
        currentRepIndex = index.coerceIn(0, records.lastIndex)
        currentFrameIndex = 0
        val rep = records[currentRepIndex]
        binding.seekReplay.max = (rep.frames.size - 1).coerceAtLeast(0)

        val statusTag = if (rep.isValid) "Valid Rep" else "Rejected: ${rep.rejectionReason}"
        binding.tvReplayRepSummary.text = String.format(
            Locale.getDefault(),
            "%s • %s • %dms • Form %d%%",
            rep.exercise.displayName,
            statusTag,
            rep.durationMs,
            rep.formQualityScore
        )
        renderCurrentFrame()
    }

    private fun renderCurrentFrame() {
        if (records.isEmpty()) return
        val rep = records[currentRepIndex]
        if (rep.frames.isEmpty()) return
        val safeIdx = currentFrameIndex.coerceIn(0, rep.frames.lastIndex)
        val frame = rep.frames[safeIdx]
        binding.seekReplay.progress = safeIdx

        val phase = if (safeIdx in (rep.frames.size / 3)..((rep.frames.size * 2) / 3)) {
            RepPhase.BOTTOM
        } else {
            RepPhase.START
        }
        val ghostPose = if (showGhost) GhostTrainerGenerator.generateIdealPose(rep.exercise, phase) else emptyMap()

        binding.replaySkeletonView.updatePose(
            landmarks = frame.landmarks,
            errors = frame.errorJoints,
            ghost = ghostPose,
            showGhostTrainer = showGhost,
            mirrorMode = false
        )

        val anglesText = frame.jointAngles.entries.joinToString(" • ") {
            "${it.key}: ${it.value.toInt()}°"
        }
        val fb = frame.feedback?.let { " | $it" } ?: ""
        binding.tvReplayFrameAngles.text = if (anglesText.isNotEmpty()) "$anglesText$fb" else "Frame ${safeIdx + 1}/${rep.frames.size}"
    }

    override fun onDestroy() {
        handler.removeCallbacks(playbackRunnable)
        super.onDestroy()
    }
}
