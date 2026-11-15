package com.sajith.scrolllock

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.sajith.scrolllock.ui.OnboardingActivity
import com.sajith.scrolllock.ui.OnboardingPermissionsState
import com.sajith.scrolllock.util.PermissionHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Unit & Robolectric tests verifying OnboardingPermissionsState readiness math,
 * Compose OnboardingActivity creation, and system settings deep-link Intents.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OnboardingActivityTest {

    @Test
    fun onboardingPermissionsStateProgressAndReadiness() {
        val none = OnboardingPermissionsState()
        assertEquals(0, none.coreGrantedCount)
        assertFalse(none.allCoreGranted)
        assertEquals(0f, none.progressFraction, 0.001f)

        val partial = OnboardingPermissionsState(
            cameraGranted = true,
            usageAccessGranted = true,
            dndPolicyGranted = false
        )
        assertEquals(2, partial.coreGrantedCount)
        assertFalse(partial.allCoreGranted)
        assertEquals(2f / 3f, partial.progressFraction, 0.001f)

        val all = OnboardingPermissionsState(
            cameraGranted = true,
            usageAccessGranted = true,
            dndPolicyGranted = true
        )
        assertEquals(3, all.coreGrantedCount)
        assertTrue(all.allCoreGranted)
        assertEquals(1f, all.progressFraction, 0.001f)
    }

    @Test
    fun settingsDeepLinksTargetCorrectAndroidSettingsScreens() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val controller = Robolectric.buildActivity(
            OnboardingActivity::class.java,
            Intent(context, OnboardingActivity::class.java).apply {
                putExtra(OnboardingActivity.EXTRA_LAUNCH_MAIN_AFTER, true)
            }
        ).setup()
        val activity = controller.get()
        val shadowActivity = Shadows.shadowOf(activity)

        PermissionHelper.openUsageAccessSettings(activity)
        val usageIntent = shadowActivity.nextStartedActivity
        assertEquals(Settings.ACTION_USAGE_ACCESS_SETTINGS, usageIntent.action)

        PermissionHelper.openNotificationPolicySettings(activity)
        val dndIntent = shadowActivity.nextStartedActivity
        assertEquals(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS, dndIntent.action)

        PermissionHelper.openInternetConnectivityPanel(activity)
        val internetIntent = shadowActivity.nextStartedActivity
        assertEquals(Settings.Panel.ACTION_INTERNET_CONNECTIVITY, internetIntent.action)

        PermissionHelper.openDataSaverSettings(activity)
        val dataSaverIntent = shadowActivity.nextStartedActivity
        assertEquals(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS, dataSaverIntent.action)

        PermissionHelper.openAppDetailsSettings(activity)
        val appDetailsIntent = shadowActivity.nextStartedActivity
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, appDetailsIntent.action)

        PermissionHelper.openBatteryOptimizationSettings(activity)
        val batteryIntent = shadowActivity.nextStartedActivity
        assertEquals(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, batteryIntent.action)
    }
}
