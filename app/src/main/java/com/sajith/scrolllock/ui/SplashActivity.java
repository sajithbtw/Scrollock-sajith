package com.sajith.scrolllock.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.DecelerateInterpolator;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.databinding.ActivitySplashBinding;
import com.sajith.scrolllock.data.local.PreferencesManager;

/**
 * Minimalist dark charcoal splash screen displaying the ScrollLock emblem and tagline
 * with a restrained launch animation before launching first-time full setup or MainActivity.
 */
public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DURATION_MS = 950L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean navigated = false;

    private final Runnable launchRunnable = this::navigateNext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        ActivitySplashBinding binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.splashBrandContainer.setAlpha(0f);
        binding.splashBrandContainer.setScaleX(0.92f);
        binding.splashBrandContainer.setScaleY(0.92f);

        binding.splashBrandContainer.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(480L)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        handler.postDelayed(launchRunnable, SPLASH_DURATION_MS);
    }

    private void navigateNext() {
        if (navigated || isFinishing()) {
            return;
        }
        navigated = true;
        PreferencesManager prefs = new PreferencesManager(this);
        Intent intent;
        if (!prefs.isOnboardingCompleted()) {
            intent = new Intent(this, OnboardingActivity.class);
            intent.putExtra(OnboardingActivity.EXTRA_LAUNCH_MAIN_AFTER, true);
        } else {
            intent = new Intent(this, MainActivity.class);
        }
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(launchRunnable);
        super.onDestroy();
    }
}
