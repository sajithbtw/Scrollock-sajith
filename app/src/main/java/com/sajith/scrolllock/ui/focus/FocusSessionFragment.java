package com.sajith.scrolllock.ui.focus;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;
import com.example.databinding.FragmentFocusSessionBinding;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.sajith.scrolllock.data.local.AppRepository;
import com.sajith.scrolllock.data.local.PreferencesManager;
import com.sajith.scrolllock.data.model.AppItem;
import com.sajith.scrolllock.data.model.FocusState;
import com.sajith.scrolllock.service.DeepFocusController;
import com.sajith.scrolllock.service.FocusSessionManager;
import com.sajith.scrolllock.ui.MainActivity;
import com.sajith.scrolllock.util.PermissionHelper;
import com.sajith.scrolllock.util.TimeFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Focus Session screen featuring preset & custom durations (15m–180m),
 * Deep Focus Mode (DND Notification Preview Hiding + Play-Safe Non-Essential Net Shield),
 * custom Firewall/Exception picker, and Start / Pause / Resume / Reset controls.
 */
public class FocusSessionFragment extends Fragment {

    private FragmentFocusSessionBinding binding;
    private PreferencesManager preferencesManager;
    private FocusSessionManager sessionManager;
    private AppRepository appRepository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private int selectedMinutes = 25;
    private boolean syncingUi = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentFocusSessionBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        preferencesManager = new PreferencesManager(requireContext());
        sessionManager = FocusSessionManager.getInstance(requireContext());
        appRepository = new AppRepository(requireContext());

        selectedMinutes = preferencesManager.getDefaultDurationMinutes();
        syncDurationChipSelection();
        syncDeepFocusUi();

        setupListeners();
        setupObservers();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshGuardAndAppsSummary();
        syncDeepFocusUi();
    }

    private void setupListeners() {
        binding.chipGroupDuration.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (syncingUi || checkedIds.isEmpty()) {
                return;
            }
            int id = checkedIds.get(0);
            if (id == R.id.chip_15_min) {
                applyPresetMinutes(15);
            } else if (id == R.id.chip_45_min) {
                applyPresetMinutes(45);
            } else if (id == R.id.chip_60_min) {
                applyPresetMinutes(60);
            } else if (id == R.id.chip_90_min) {
                applyPresetMinutes(90);
            } else if (id == R.id.chip_custom_min) {
                showCustomDurationDialog();
            } else {
                applyPresetMinutes(25);
            }
        });

        binding.chipCustomMin.setOnClickListener(v -> {
            if (binding.chipCustomMin.isChecked()
                    && (sessionManager.getCurrentStateValue() == FocusState.IDLE
                    || sessionManager.getCurrentStateValue() == FocusState.COMPLETED)) {
                showCustomDurationDialog();
            }
        });

        binding.btnFocusStartPause.setOnClickListener(v -> {
            FocusState state = sessionManager.getCurrentStateValue();
            if (state == FocusState.RUNNING) {
                sessionManager.pauseSession();
            } else if (state == FocusState.PAUSED) {
                sessionManager.resumeSession();
            } else {
                if (preferencesManager.isDeepFocusEnabled()
                        && preferencesManager.isDeepFocusHidePreviewsEnabled()
                        && !PermissionHelper.hasNotificationPolicyAccess(requireContext())) {
                    Toast.makeText(
                            requireContext(),
                            "Tip: Grant DND Access in Deep Focus card to hide notification previews.",
                            Toast.LENGTH_LONG
                    ).show();
                }
                sessionManager.startNewSession(selectedMinutes);
            }
        });

        binding.btnFocusReset.setOnClickListener(v -> {
            sessionManager.resetSession();
            selectedMinutes = preferencesManager.getDefaultDurationMinutes();
            syncDurationChipSelection();
            sessionManager.setPresetDurationMinutes(selectedMinutes);
        });

        binding.btnStartAnother.setOnClickListener(v -> {
            sessionManager.resetSession();
            sessionManager.startNewSession(selectedMinutes);
        });

        binding.btnCompleteViewInsights.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).navigateToTab(R.id.nav_insights);
            }
        });

        binding.tvSessionManageApps.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).navigateToTab(R.id.nav_apps);
            }
        });

        binding.cardGuardStatus.setOnClickListener(v -> {
            if (!PermissionHelper.hasUsageStatsPermission(requireContext())) {
                PermissionHelper.openUsageAccessSettings(requireContext());
            } else if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).navigateToTab(R.id.nav_settings);
            }
        });

        // Deep Focus listeners
        binding.switchDeepFocus.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (syncingUi) {
                return;
            }
            preferencesManager.setDeepFocusEnabled(isChecked);
            sessionManager.syncDeepFocusWithState();
            syncDeepFocusUi();
        });

        binding.switchDeepHidePreviews.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (syncingUi) {
                return;
            }
            preferencesManager.setDeepFocusHidePreviewsEnabled(isChecked);
            if (isChecked && !PermissionHelper.hasNotificationPolicyAccess(requireContext())) {
                PermissionHelper.openNotificationPolicySettings(requireContext());
            }
            sessionManager.syncDeepFocusWithState();
            syncDeepFocusUi();
        });

        binding.btnDndPermissionPill.setOnClickListener(v ->
                PermissionHelper.openNotificationPolicySettings(requireContext())
        );

        binding.switchDeepBlockInternet.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (syncingUi) {
                return;
            }
            preferencesManager.setDeepFocusBlockInternetEnabled(isChecked);
            sessionManager.syncDeepFocusWithState();
            syncDeepFocusUi();
        });

        binding.btnInternetPanelPill.setOnClickListener(v ->
                PermissionHelper.openInternetConnectivityPanel(requireContext())
        );

        binding.btnDeepFocusDataSaver.setOnClickListener(v ->
                PermissionHelper.openDataSaverSettings(requireContext())
        );

        binding.btnDeepFocusExceptions.setOnClickListener(v -> showExceptionsBottomSheet());
    }

    private void applyPresetMinutes(int minutes) {
        selectedMinutes = minutes;
        if (sessionManager.getCurrentStateValue() == FocusState.IDLE
                || sessionManager.getCurrentStateValue() == FocusState.COMPLETED) {
            sessionManager.setPresetDurationMinutes(selectedMinutes);
        }
    }

    private void showCustomDurationDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_custom_duration, null, false);
        TextView tvMinutes = dialogView.findViewById(R.id.tv_custom_minutes_value);
        Slider slider = dialogView.findViewById(R.id.slider_custom_minutes);
        MaterialButton btnMinus = dialogView.findViewById(R.id.btn_custom_minus_5);
        MaterialButton btnPlus = dialogView.findViewById(R.id.btn_custom_plus_5);

        int initial = preferencesManager.getCustomFocusDurationMinutes();
        int snapped = Math.max(5, Math.min(180, ((initial + 2) / 5) * 5));
        slider.setValue((float) snapped);
        tvMinutes.setText(String.format(Locale.getDefault(), "%d min", snapped));

        slider.addOnChangeListener((s, value, fromUser) -> {
            int mins = Math.round(value);
            tvMinutes.setText(String.format(Locale.getDefault(), "%d min", mins));
        });

        btnMinus.setOnClickListener(v -> {
            int next = Math.max(5, Math.round(slider.getValue()) - 5);
            slider.setValue((float) next);
        });
        btnPlus.setOnClickListener(v -> {
            int next = Math.min(180, Math.round(slider.getValue()) + 5);
            slider.setValue((float) next);
        });

        new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setPositiveButton(R.string.btn_done, (d, which) -> {
                    int chosen = Math.round(slider.getValue());
                    preferencesManager.setCustomFocusDurationMinutes(chosen);
                    applyPresetMinutes(chosen);
                    syncDurationChipSelection();
                })
                .setNegativeButton(android.R.string.cancel, (d, which) -> syncDurationChipSelection())
                .show();
    }

    private void showExceptionsBottomSheet() {
        BottomSheetDialog sheet = new BottomSheetDialog(requireContext());
        View sheetView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_deep_focus_exceptions, null, false);
        sheet.setContentView(sheetView);

        EditText etSearch = sheetView.findViewById(R.id.et_search_deep_focus_apps);
        RecyclerView rvApps = sheetView.findViewById(R.id.rv_deep_focus_apps);
        MaterialButton btnDone = sheetView.findViewById(R.id.btn_deep_focus_exceptions_done);

        rvApps.setLayoutManager(new LinearLayoutManager(requireContext()));
        List<AppItem> fullList = new ArrayList<>();
        List<AppItem> filteredList = new ArrayList<>();
        Set<String> allowedSet = preferencesManager.getDeepFocusAllowedPackages();

        RecyclerView.Adapter<ExceptionViewHolder> adapter = new RecyclerView.Adapter<ExceptionViewHolder>() {
            @NonNull
            @Override
            public ExceptionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View row = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_deep_focus_app_row, parent, false);
                return new ExceptionViewHolder(row);
            }

            @Override
            public void onBindViewHolder(@NonNull ExceptionViewHolder holder, int position) {
                AppItem item = filteredList.get(position);
                holder.tvName.setText(item.getAppName());
                if (item.getIcon() != null) {
                    holder.ivIcon.setImageDrawable(item.getIcon());
                } else {
                    holder.ivIcon.setImageResource(R.drawable.ic_nav_apps);
                }

                boolean allowed = allowedSet.contains(item.getPackageName());
                holder.tvStatus.setText(allowed
                        ? R.string.deep_focus_status_kept_online
                        : R.string.deep_focus_status_shielded);
                holder.tvStatus.setTextColor(ContextCompat.getColor(
                        requireContext(),
                        allowed ? R.color.sl_accent_strong : R.color.sl_text_secondary
                ));

                holder.switchAllow.setOnCheckedChangeListener(null);
                holder.switchAllow.setChecked(allowed);
                holder.switchAllow.setOnCheckedChangeListener((btn, isChecked) -> {
                    boolean nowAllowed = preferencesManager.toggleDeepFocusAllowedPackage(item.getPackageName());
                    if (nowAllowed) {
                        allowedSet.add(item.getPackageName());
                    } else {
                        allowedSet.remove(item.getPackageName());
                    }
                    holder.tvStatus.setText(nowAllowed
                            ? R.string.deep_focus_status_kept_online
                            : R.string.deep_focus_status_shielded);
                    holder.tvStatus.setTextColor(ContextCompat.getColor(
                            requireContext(),
                            nowAllowed ? R.color.sl_accent_strong : R.color.sl_text_secondary
                    ));
                    sessionManager.syncDeepFocusWithState();
                    syncDeepFocusUi();
                });
            }

            @Override
            public int getItemCount() {
                return filteredList.size();
            }
        };
        rvApps.setAdapter(adapter);

        executor.execute(() -> {
            List<AppItem> loaded = appRepository.getLaunchableApps();
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    fullList.clear();
                    fullList.addAll(loaded);
                    filteredList.clear();
                    filteredList.addAll(loaded);
                    adapter.notifyDataSetChanged();
                });
            }
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String q = s != null ? s.toString().trim().toLowerCase(Locale.getDefault()) : "";
                filteredList.clear();
                for (AppItem item : fullList) {
                    if (q.isEmpty() || item.getAppName().toLowerCase(Locale.getDefault()).contains(q)) {
                        filteredList.add(item);
                    }
                }
                adapter.notifyDataSetChanged();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        btnDone.setOnClickListener(v -> sheet.dismiss());
        sheet.show();
    }

    private static class ExceptionViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivIcon;
        final TextView tvName;
        final TextView tvStatus;
        final MaterialSwitch switchAllow;

        ExceptionViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.iv_df_app_icon);
            tvName = itemView.findViewById(R.id.tv_df_app_name);
            tvStatus = itemView.findViewById(R.id.tv_df_app_status);
            switchAllow = itemView.findViewById(R.id.switch_df_allow);
        }
    }

    private void setupObservers() {
        sessionManager.getFocusState().observe(getViewLifecycleOwner(), state -> renderSessionUi());
        sessionManager.getRemainingMillis().observe(getViewLifecycleOwner(), millis -> renderSessionUi());
        sessionManager.getTotalMillis().observe(getViewLifecycleOwner(), millis -> renderSessionUi());
    }

    private void renderSessionUi() {
        if (binding == null) {
            return;
        }
        FocusState state = sessionManager.getCurrentStateValue();
        long remaining = sessionManager.getCurrentRemainingMillisValue();
        long total = Math.max(1L, sessionManager.getCurrentTotalMillisValue());

        binding.tvSessionTimer.setText(TimeFormatter.formatCountdown(remaining));
        float progress = state == FocusState.COMPLETED ? 0f : (float) remaining / (float) total;
        binding.focusRingSession.setProgress(progress);

        boolean canChangeDuration = (state == FocusState.IDLE || state == FocusState.COMPLETED);
        for (int i = 0; i < binding.chipGroupDuration.getChildCount(); i++) {
            binding.chipGroupDuration.getChildAt(i).setEnabled(canChangeDuration);
        }

        if (state == FocusState.RUNNING) {
            binding.tvSessionState.setText(
                    preferencesManager.isDeepFocusEnabled()
                            ? R.string.status_deep_focus_active
                            : R.string.status_active
            );
            binding.btnFocusStartPause.setText(R.string.btn_pause);
            binding.btnFocusStartPause.setIconResource(R.drawable.ic_pause);
            binding.btnFocusReset.setEnabled(true);
            binding.cardSessionComplete.setVisibility(View.GONE);
        } else if (state == FocusState.PAUSED) {
            binding.tvSessionState.setText(R.string.status_paused);
            binding.btnFocusStartPause.setText(R.string.btn_resume);
            binding.btnFocusStartPause.setIconResource(R.drawable.ic_play);
            binding.btnFocusReset.setEnabled(true);
            binding.cardSessionComplete.setVisibility(View.GONE);
        } else if (state == FocusState.COMPLETED) {
            binding.tvSessionState.setText(R.string.status_completed);
            binding.btnFocusStartPause.setText(R.string.btn_start_focus);
            binding.btnFocusStartPause.setIconResource(R.drawable.ic_play);
            binding.btnFocusReset.setEnabled(false);

            int completedMinutes = (int) Math.max(1L, total / 60_000L);
            int interruptions = sessionManager.getCurrentInterruptionsValue();
            binding.tvCompletionSummary.setText(
                    getString(R.string.focus_completion_summary_format, completedMinutes, interruptions)
            );
            binding.cardSessionComplete.setVisibility(View.VISIBLE);
        } else {
            binding.tvSessionState.setText(R.string.status_ready);
            binding.btnFocusStartPause.setText(R.string.btn_start_focus);
            binding.btnFocusStartPause.setIconResource(R.drawable.ic_play);
            binding.btnFocusReset.setEnabled(false);
            binding.cardSessionComplete.setVisibility(View.GONE);
        }
    }

    private void syncDurationChipSelection() {
        if (binding == null) {
            return;
        }
        syncingUi = true;
        int customMins = preferencesManager.getCustomFocusDurationMinutes();
        int targetChipId;
        if (selectedMinutes == 15) {
            targetChipId = R.id.chip_15_min;
            binding.chipCustomMin.setText(R.string.focus_duration_custom);
        } else if (selectedMinutes == 25) {
            targetChipId = R.id.chip_25_min;
            binding.chipCustomMin.setText(R.string.focus_duration_custom);
        } else if (selectedMinutes == 45) {
            targetChipId = R.id.chip_45_min;
            binding.chipCustomMin.setText(R.string.focus_duration_custom);
        } else if (selectedMinutes == 60) {
            targetChipId = R.id.chip_60_min;
            binding.chipCustomMin.setText(R.string.focus_duration_custom);
        } else if (selectedMinutes == 90) {
            targetChipId = R.id.chip_90_min;
            binding.chipCustomMin.setText(R.string.focus_duration_custom);
        } else {
            targetChipId = R.id.chip_custom_min;
            binding.chipCustomMin.setText(getString(R.string.focus_duration_custom_format, customMins));
        }
        binding.chipGroupDuration.check(targetChipId);
        syncingUi = false;
    }

    private void syncDeepFocusUi() {
        if (binding == null || !isAdded()) {
            return;
        }
        syncingUi = true;
        boolean deepEnabled = preferencesManager.isDeepFocusEnabled();
        binding.switchDeepFocus.setChecked(deepEnabled);
        binding.layoutDeepFocusOptions.setVisibility(deepEnabled ? View.VISIBLE : View.GONE);
        binding.switchDeepHidePreviews.setChecked(preferencesManager.isDeepFocusHidePreviewsEnabled());
        binding.switchDeepBlockInternet.setChecked(preferencesManager.isDeepFocusBlockInternetEnabled());

        boolean dndGranted = PermissionHelper.hasNotificationPolicyAccess(requireContext());
        binding.btnDndPermissionPill.setText(
                dndGranted ? R.string.deep_focus_dnd_granted : R.string.deep_focus_dnd_grant
        );

        int allowedCount = preferencesManager.getDeepFocusAllowedPackages().size();
        binding.btnDeepFocusExceptions.setText(
                getString(R.string.deep_focus_exceptions_btn, allowedCount)
        );
        syncingUi = false;

        executor.execute(() -> {
            int blockedCount = DeepFocusController.resolveBlockedInternetPackages(requireContext()).size();
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    if (binding != null) {
                        binding.tvDeepFocusSummary.setText(
                                getString(R.string.deep_focus_summary_format, blockedCount)
                        );
                    }
                });
            }
        });
    }

    private void refreshGuardAndAppsSummary() {
        if (binding == null || !isAdded()) {
            return;
        }
        boolean usageGranted = PermissionHelper.hasUsageStatsPermission(requireContext());
        if (usageGranted) {
            binding.tvGuardStatusText.setText(R.string.focus_guard_active);
            binding.ivGuardIcon.setImageResource(R.drawable.ic_shield_check);
        } else {
            binding.tvGuardStatusText.setText(R.string.focus_guard_usage_fallback);
            binding.ivGuardIcon.setImageResource(R.drawable.ic_info);
        }

        Set<String> selectedPackages = preferencesManager.getSelectedPackages();
        if (selectedPackages.isEmpty()) {
            binding.tvSessionAppsList.setText(R.string.focus_no_apps_warning);
        } else {
            List<String> names = new ArrayList<>();
            for (String pkg : selectedPackages) {
                names.add(appRepository.getAppLabel(pkg));
            }
            binding.tvSessionAppsList.setText(android.text.TextUtils.join(" • ", names));
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
