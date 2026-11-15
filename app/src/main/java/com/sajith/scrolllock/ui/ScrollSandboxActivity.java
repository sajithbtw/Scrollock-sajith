package com.sajith.scrolllock.ui;

import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;
import com.example.databinding.ActivityScrollSandboxBinding;
import com.sajith.scrolllock.data.model.DemoFeedItem;
import com.sajith.scrolllock.ui.sandbox.SandboxFeedAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * Interactive demonstration feed showing true gesture-level vertical scroll locking inside ScrollLock.
 * Explains transparently why Android restricts gesture interception inside external third-party apps.
 */
public class ScrollSandboxActivity extends AppCompatActivity {

    private static final int AUTO_LOCK_CARD_THRESHOLD = 3;

    private ActivityScrollSandboxBinding binding;
    private LinearLayoutManager layoutManager;
    private boolean isBarrierLocked = false;
    private int maxScrolledCardPosition = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        binding = ActivityScrollSandboxBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.sandboxRoot, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        binding.btnSandboxBack.setOnClickListener(v -> finish());

        setupFeed();
        setupControls();
        updateCounterLabel(0);
    }

    private void setupFeed() {
        List<DemoFeedItem> items = new ArrayList<>();
        items.add(new DemoFeedItem("CARD 1 • SCROLL SANDBOX", "Notice How Feeds Pull Attention",
                "Infinite vertical feeds are designed to eliminate natural stopping cues. Scroll down to test ScrollLock's in-app barrier."));
        items.add(new DemoFeedItem("CARD 2 • PLATFORM REALITY", "Why Other Apps Can't Be Swipe-Blocked",
                "Android isolates every app in its own security sandbox. No third-party app can block vertical scroll gestures inside Instagram, TikTok, or YouTube."));
        items.add(new DemoFeedItem("CARD 3 • COMPLIANT GUARD", "How ScrollLock Protects Focus",
                "Instead of invasive accessibility snooping, ScrollLock uses user-granted Usage Access, official Do Not Disturb preview hiding, and Focus Guard alerts during your Focus Session."));
        items.add(new DemoFeedItem("CARD 4 • BARRIER TRIGGER", "Natural Stopping Point Reached",
                "When Auto-Lock is enabled, scrolling past 3 cards triggers the ScrollLock vertical barrier automatically."));
        items.add(new DemoFeedItem("CARD 5 • MINDFUL RESET", "Break the Scroll. Take Control.",
                "Adding brief friction gives your prefrontal cortex a moment to decide whether to continue or return to what matters."));
        items.add(new DemoFeedItem("CARD 6 • LOCAL PRIVACY", "Zero Tracking, Zero Accounts",
                "All your focus minutes, app selections, and daily insights remain locally on your device."));

        layoutManager = new LinearLayoutManager(this) {
            @Override
            public boolean canScrollVertically() {
                return !isBarrierLocked && super.canScrollVertically();
            }
        };

        binding.rvSandboxFeed.setLayoutManager(layoutManager);
        binding.rvSandboxFeed.setAdapter(new SandboxFeedAdapter(items));

        binding.rvSandboxFeed.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (layoutManager == null) {
                    return;
                }
                int lastVisible = layoutManager.findLastCompletelyVisibleItemPosition();
                if (lastVisible > maxScrolledCardPosition) {
                    maxScrolledCardPosition = lastVisible;
                }
                int cardsViewed = Math.min(AUTO_LOCK_CARD_THRESHOLD, maxScrolledCardPosition + 1);
                updateCounterLabel(cardsViewed);

                if (binding.cbAutoLock.isChecked()
                        && !isBarrierLocked
                        && maxScrolledCardPosition >= AUTO_LOCK_CARD_THRESHOLD) {
                    setBarrierLocked(true);
                }
            }
        });
    }

    private void setupControls() {
        binding.switchScrollLock.setOnCheckedChangeListener((buttonView, isChecked) -> {
            setBarrierLocked(isChecked);
        });

        binding.btnUnlockSandbox.setOnClickListener(v -> {
            maxScrolledCardPosition = 0;
            updateCounterLabel(1);
            binding.rvSandboxFeed.scrollToPosition(0);
            setBarrierLocked(false);
        });
    }

    private void setBarrierLocked(boolean locked) {
        this.isBarrierLocked = locked;
        binding.switchScrollLock.setOnCheckedChangeListener(null);
        binding.switchScrollLock.setChecked(locked);
        binding.switchScrollLock.setOnCheckedChangeListener((buttonView, isChecked) -> setBarrierLocked(isChecked));

        if (locked) {
            binding.layoutBarrierOverlay.setAlpha(0f);
            binding.layoutBarrierOverlay.setVisibility(View.VISIBLE);
            binding.layoutBarrierOverlay.animate().alpha(1f).setDuration(220L).start();
        } else {
            binding.layoutBarrierOverlay.setVisibility(View.GONE);
        }
    }

    private void updateCounterLabel(int count) {
        binding.tvSandboxScrollCounter.setText(getString(R.string.sandbox_scroll_count_format, count));
    }
}
