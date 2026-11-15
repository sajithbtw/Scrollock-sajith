package com.sajith.scrolllock.ui.apps;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;
import com.example.databinding.ItemAppRowBinding;
import com.sajith.scrolllock.data.local.PreferencesManager;
import com.sajith.scrolllock.data.model.AppItem;
import com.sajith.scrolllock.util.TimeFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * RecyclerView adapter displaying launchable installed apps with live search filtering,
 * temporary workout-unlock countdown badges, per-app Reels/Shorts lock pill toggles,
 * and persistent full-app lock switches.
 */
public class AppSelectionAdapter extends RecyclerView.Adapter<AppSelectionAdapter.AppViewHolder> {

    public interface OnAppToggleListener {
        void onAppToggled(AppItem item, boolean isSelected);

        void onShortFormToggled(AppItem item, boolean isShortFormBlocked);
    }

    private final List<AppItem> items = new ArrayList<>();
    private final OnAppToggleListener listener;
    private PreferencesManager preferencesManager;
    private boolean hasUsagePermission = false;

    public AppSelectionAdapter(OnAppToggleListener listener) {
        this.listener = listener;
    }

    public void submitList(List<AppItem> newItems, boolean hasUsagePermission) {
        this.items.clear();
        this.items.addAll(newItems);
        this.hasUsagePermission = hasUsagePermission;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (preferencesManager == null) {
            preferencesManager = new PreferencesManager(parent.getContext());
        }
        ItemAppRowBinding binding = ItemAppRowBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new AppViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class AppViewHolder extends RecyclerView.ViewHolder {
        private final ItemAppRowBinding binding;

        AppViewHolder(ItemAppRowBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(AppItem item) {
            Context context = binding.getRoot().getContext();
            binding.tvAppName.setText(item.getAppName());
            if (item.getIcon() != null) {
                binding.ivAppIcon.setImageDrawable(item.getIcon());
            } else {
                binding.ivAppIcon.setImageResource(R.drawable.ic_nav_apps);
            }

            if (hasUsagePermission) {
                String formatted = TimeFormatter.formatDurationShort(item.getTodayUsageMillis());
                binding.tvAppUsage.setText(
                        context.getString(R.string.apps_today_format, formatted)
                );
            } else {
                binding.tvAppUsage.setText(R.string.apps_usage_unavailable);
            }

            boolean fullUnlocked = preferencesManager != null && item.isSelected()
                    && preferencesManager.isAppTemporarilyUnlocked(item.getPackageName());
            boolean shortFormUnlocked = preferencesManager != null && item.isShortFormBlocked()
                    && preferencesManager.isShortFormTemporarilyUnlocked(item.getPackageName());

            if (fullUnlocked) {
                int mins = preferencesManager.getAppUnlockRemainingMinutes(item.getPackageName());
                binding.tvAppUnlockBadge.setVisibility(View.VISIBLE);
                binding.tvAppUnlockBadge.setText(String.format(
                        Locale.getDefault(), "App unlocked • %dm left (tap to lock)", mins
                ));
                binding.tvAppUnlockBadge.setOnClickListener(v -> {
                    preferencesManager.relockApp(item.getPackageName());
                    notifyItemChanged(getBindingAdapterPosition());
                });
            } else if (shortFormUnlocked) {
                int mins = preferencesManager.getShortFormUnlockRemainingMinutes(item.getPackageName());
                binding.tvAppUnlockBadge.setVisibility(View.VISIBLE);
                binding.tvAppUnlockBadge.setText(String.format(
                        Locale.getDefault(), "Reels unlocked • %dm left (tap to lock)", mins
                ));
                binding.tvAppUnlockBadge.setOnClickListener(v -> {
                    preferencesManager.relockShortForm(item.getPackageName());
                    notifyItemChanged(getBindingAdapterPosition());
                });
            } else {
                binding.tvAppUnlockBadge.setVisibility(View.GONE);
                binding.tvAppUnlockBadge.setOnClickListener(null);
            }

            renderShortFormPill(context, item.isShortFormBlocked());
            binding.btnToggleShortForm.setOnClickListener(v -> {
                boolean next = !item.isShortFormBlocked();
                item.setShortFormBlocked(next);
                renderShortFormPill(context, next);
                if (listener != null) {
                    listener.onShortFormToggled(item, next);
                }
            });

            // Detach previous listener before updating checked state during recycling
            binding.switchAppSelected.setOnCheckedChangeListener(null);
            binding.switchAppSelected.setChecked(item.isSelected());

            binding.switchAppSelected.setOnCheckedChangeListener((buttonView, isChecked) -> {
                item.setSelected(isChecked);
                if (listener != null) {
                    listener.onAppToggled(item, isChecked);
                }
            });

            binding.getRoot().setOnClickListener(v ->
                    binding.switchAppSelected.setChecked(!binding.switchAppSelected.isChecked())
            );
        }

        private void renderShortFormPill(Context context, boolean active) {
            if (active) {
                binding.btnToggleShortForm.setText(R.string.btn_short_form_on);
                binding.btnToggleShortForm.setBackgroundTintList(
                        ColorStateList.valueOf(ContextCompat.getColor(context, R.color.sl_accent_soft)));
                binding.btnToggleShortForm.setTextColor(
                        ContextCompat.getColor(context, R.color.sl_accent_strong));
                binding.btnToggleShortForm.setStrokeColor(
                        ColorStateList.valueOf(ContextCompat.getColor(context, R.color.sl_accent)));
            } else {
                binding.btnToggleShortForm.setText(R.string.btn_short_form_off);
                binding.btnToggleShortForm.setBackgroundTintList(
                        ColorStateList.valueOf(ContextCompat.getColor(context, android.R.color.transparent)));
                binding.btnToggleShortForm.setTextColor(
                        ContextCompat.getColor(context, R.color.sl_text_secondary));
                binding.btnToggleShortForm.setStrokeColor(
                        ColorStateList.valueOf(ContextCompat.getColor(context, R.color.sl_border)));
            }
        }
    }
}
