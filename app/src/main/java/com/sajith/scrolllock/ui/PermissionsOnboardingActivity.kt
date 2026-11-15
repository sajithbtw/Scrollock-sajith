package com.sajith.scrolllock.ui

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.R
import com.example.databinding.ActivityPermissionsOnboardingBinding
import com.sajith.scrolllock.data.local.AppRepository
import com.sajith.scrolllock.data.local.PreferencesManager
import com.sajith.scrolllock.data.local.ScrollLockDatabaseHelper
import com.sajith.scrolllock.util.PermissionHelper
import com.sajith.scrolllock.workout.ExerciseType

/**
 * Complete one-stop Express Setup screen explaining and linking Camera, Do Not Disturb Access,
 * Usage Access, Battery Optimization, one-tap Social Media & Games app guarding, and workout goal presets.
 */
class PermissionsOnboardingActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_LAUNCH_MAIN_AFTER = "extra_launch_main_after"
    }

    private lateinit var binding: ActivityPermissionsOnboardingBinding
    private lateinit var prefs: PreferencesManager
    private lateinit var appRepo: AppRepository
    private lateinit var dbHelper: ScrollLockDatabaseHelper
    private var launchMainAfter = false
    private var syncingChips = false

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshStatuses() }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityPermissionsOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferencesManager(this)
        appRepo = AppRepository(this)
        dbHelper = ScrollLockDatabaseHelper.getInstance(this)
        launchMainAfter = intent.getBooleanExtra(EXTRA_LAUNCH_MAIN_AFTER, false)

        ViewCompat.setOnApplyWindowInsetsListener(binding.onboardingRoot) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val pad = (16 * resources.displayMetrics.density).toInt()
            v.setPadding(pad + bars.left, pad + bars.top, pad + bars.right, pad + bars.bottom)
            insets
        }

        setupListeners()
        syncQuickPresetChips()
        refreshGuardedSummary()
    }

    private fun setupListeners() {
        binding.btnOnbCamera.setOnClickListener {
            if (!PermissionHelper.hasCameraPermission(this)) {
                cameraLauncher.launch(Manifest.permission.CAMERA)
            } else {
                PermissionHelper.openAppDetailsSettings(this)
            }
        }
        binding.btnOnbDnd.setOnClickListener {
            PermissionHelper.openNotificationPolicySettings(this)
        }
        binding.btnOnbUsage.setOnClickListener {
            PermissionHelper.openUsageAccessSettings(this)
        }
        binding.btnOnbBattery.setOnClickListener {
            PermissionHelper.openBatteryOptimizationSettings(this)
        }

        binding.btnOnbAutoGuardSocial.setOnClickListener {
            val apps = appRepo.getLaunchableApps()
            val selected = prefs.getSelectedPackages()
            val shortForm = prefs.getShortFormBlockedPackages()
            var addedGuard = 0
            var addedReels = 0
            for (item in apps) {
                if (appRepo.isSocialOrGamePackage(item.packageName)) {
                    if (!selected.contains(item.packageName)) {
                        selected.add(item.packageName)
                        dbHelper.recordAppSelected(item.packageName, item.appName)
                        addedGuard++
                    }
                }
                if (appRepo.isShortFormHostPackage(item.packageName)) {
                    if (!shortForm.contains(item.packageName)) {
                        shortForm.add(item.packageName)
                        addedReels++
                    }
                }
            }
            prefs.setSelectedPackages(selected)
            prefs.setShortFormBlockedPackages(shortForm)
            prefs.setShortFormBlockerEnabled(true)
            prefs.setAlwaysGuardAppsEnabled(true)
            prefs.setWorkoutUnlockEnabled(true)
            refreshGuardedSummary()
            val msg = if (addedGuard > 0 || addedReels > 0) {
                "Guarded $addedGuard apps & blocked Reels/Shorts in $addedReels video apps."
            } else if (selected.isNotEmpty() || shortForm.isNotEmpty()) {
                "${selected.size} guarded • ${shortForm.size} Reels-blocked apps ready."
            } else {
                "No third-party social/game apps detected — you can pick any apps in the Apps tab."
            }
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        binding.chipGroupOnbExercise.setOnCheckedStateChangeListener { _, checkedIds ->
            if (syncingChips || checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val ex = when (checkedIds[0]) {
                R.id.chip_onb_auto -> ExerciseType.AUTO
                R.id.chip_onb_pushups -> ExerciseType.PUSHUPS
                R.id.chip_onb_lunges -> ExerciseType.LUNGES
                R.id.chip_onb_jacks -> ExerciseType.JUMPING_JACKS
                else -> ExerciseType.SQUATS
            }
            prefs.setPreferredExerciseName(ex.name)
        }

        binding.chipGroupOnbReps.setOnCheckedStateChangeListener { _, checkedIds ->
            if (syncingChips || checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val reps = when (checkedIds[0]) {
                R.id.chip_onb_reps_5 -> 5
                R.id.chip_onb_reps_15 -> 15
                else -> 10
            }
            prefs.setRequiredWorkoutReps(reps)
        }

        binding.btnOnbContinue.setOnClickListener {
            prefs.setOnboardingCompleted(true)
            if (launchMainAfter) {
                startActivity(Intent(this, MainActivity::class.java))
            }
            finish()
        }
    }

    private fun syncQuickPresetChips() {
        syncingChips = true
        when (prefs.getPreferredExerciseName()) {
            ExerciseType.AUTO.name -> binding.chipGroupOnbExercise.check(R.id.chip_onb_auto)
            ExerciseType.PUSHUPS.name -> binding.chipGroupOnbExercise.check(R.id.chip_onb_pushups)
            ExerciseType.LUNGES.name -> binding.chipGroupOnbExercise.check(R.id.chip_onb_lunges)
            ExerciseType.JUMPING_JACKS.name -> binding.chipGroupOnbExercise.check(R.id.chip_onb_jacks)
            else -> binding.chipGroupOnbExercise.check(R.id.chip_onb_squats)
        }
        when (prefs.getRequiredWorkoutReps()) {
            5 -> binding.chipGroupOnbReps.check(R.id.chip_onb_reps_5)
            15 -> binding.chipGroupOnbReps.check(R.id.chip_onb_reps_15)
            else -> binding.chipGroupOnbReps.check(R.id.chip_onb_reps_10)
        }
        syncingChips = false
    }

    private fun refreshGuardedSummary() {
        val count = prefs.getSelectedPackages().size
        val reelsCount = prefs.getShortFormBlockedPackages().size
        binding.tvOnbGuardedSummary.text = if (count == 0 && reelsCount == 0) {
            "Guarded Apps: None selected yet"
        } else {
            "Guarded: $count apps • Reels Blocked: $reelsCount apps"
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatuses()
        refreshGuardedSummary()
    }

    private fun refreshStatuses() {
        updateBadge(binding.tvOnbCameraStatus, PermissionHelper.hasCameraPermission(this))
        updateBadge(binding.tvOnbDndStatus, PermissionHelper.hasNotificationPolicyAccess(this))
        updateBadge(binding.tvOnbUsageStatus, PermissionHelper.hasUsageStatsPermission(this))
        updateBadge(binding.tvOnbBatteryStatus, PermissionHelper.isIgnoringBatteryOptimizations(this))
    }

    private fun updateBadge(view: TextView, granted: Boolean) {
        if (granted) {
            view.setText(R.string.status_enabled)
            view.setBackgroundResource(R.drawable.bg_pill_accent_soft)
            view.setTextColor(ContextCompat.getColor(this, R.color.sl_accent_strong))
        } else {
            view.setText(R.string.status_not_enabled)
            view.setBackgroundResource(R.drawable.bg_pill_neutral)
            view.setTextColor(ContextCompat.getColor(this, R.color.sl_text_secondary))
        }
    }
}
