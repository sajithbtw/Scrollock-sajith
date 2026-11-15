package com.sajith.scrolllock.ui.apps;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.R;
import com.example.databinding.FragmentAppSelectionBinding;
import com.sajith.scrolllock.data.local.AppRepository;
import com.sajith.scrolllock.data.local.PreferencesManager;
import com.sajith.scrolllock.data.local.ScrollLockDatabaseHelper;
import com.sajith.scrolllock.data.model.AppItem;
import com.sajith.scrolllock.util.PermissionHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Displays installed launchable applications with search, All/Reels Blocked/Locked/Social & Games
 * filter chips, one-tap Reels & Shorts blocker preset, full-app guard preset, and per-app toggles.
 */
public class AppSelectionFragment extends Fragment {

    private static final int FILTER_ALL = 0;
    private static final int FILTER_SELECTED = 1;
    private static final int FILTER_SOCIAL_GAMES = 2;
    private static final int FILTER_REELS_BLOCKED = 3;

    private FragmentAppSelectionBinding binding;
    private AppRepository appRepository;
    private PreferencesManager preferencesManager;
    private ScrollLockDatabaseHelper dbHelper;
    private AppSelectionAdapter adapter;

    private final List<AppItem> allApps = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private String currentQuery = "";
    private int currentFilterMode = FILTER_ALL;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAppSelectionBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        appRepository = new AppRepository(requireContext());
        preferencesManager = new PreferencesManager(requireContext());
        dbHelper = ScrollLockDatabaseHelper.getInstance(requireContext());

        adapter = new AppSelectionAdapter(new AppSelectionAdapter.OnAppToggleListener() {
            @Override
            public void onAppToggled(AppItem item, boolean isSelected) {
                preferencesManager.togglePackageSelection(item.getPackageName());
                if (isSelected) {
                    dbHelper.recordAppSelected(item.getPackageName(), item.getAppName());
                }
                updateSelectedSummary();
                if (currentFilterMode == FILTER_SELECTED) {
                    applyFilter();
                }
            }

            @Override
            public void onShortFormToggled(AppItem item, boolean isShortFormBlocked) {
                preferencesManager.toggleShortFormPackage(item.getPackageName());
                if (isShortFormBlocked) {
                    preferencesManager.setShortFormBlockerEnabled(true);
                    dbHelper.recordAppSelected(item.getPackageName(), item.getAppName());
                    if (!PermissionHelper.hasUsageStatsPermission(requireContext())) {
                        Toast.makeText(
                                requireContext(),
                                R.string.toast_enable_usage_for_reels,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }
                updateSelectedSummary();
                if (currentFilterMode == FILTER_REELS_BLOCKED) {
                    applyFilter();
                }
            }
        });

        binding.rvApps.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvApps.setAdapter(adapter);

        binding.etSearchApps.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentQuery = s != null ? s.toString().trim().toLowerCase(Locale.getDefault()) : "";
                applyFilter();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        binding.chipGroupAppFilter.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.contains(R.id.chip_reels_blocked)) {
                currentFilterMode = FILTER_REELS_BLOCKED;
            } else if (checkedIds.contains(R.id.chip_selected_apps)) {
                currentFilterMode = FILTER_SELECTED;
            } else if (checkedIds.contains(R.id.chip_social_games)) {
                currentFilterMode = FILTER_SOCIAL_GAMES;
            } else {
                currentFilterMode = FILTER_ALL;
            }
            applyFilter();
        });

        binding.btnBlockReelsApps.setOnClickListener(v -> blockShortFormHosts());
        binding.btnGuardSocialGames.setOnClickListener(v -> guardDetectedSocialAndGames());
    }

    @Override
    public void onResume() {
        super.onResume();
        loadAppsAsync();
    }

    private void blockShortFormHosts() {
        Set<String> shortForm = preferencesManager.getShortFormBlockedPackages();
        int newlyAdded = 0;
        for (AppItem item : allApps) {
            if (appRepository.isShortFormHostPackage(item.getPackageName())) {
                if (!shortForm.contains(item.getPackageName())) {
                    shortForm.add(item.getPackageName());
                    dbHelper.recordAppSelected(item.getPackageName(), item.getAppName());
                    newlyAdded++;
                }
                item.setShortFormBlocked(true);
            }
        }
        preferencesManager.setShortFormBlockedPackages(shortForm);
        preferencesManager.setShortFormBlockerEnabled(true);
        updateSelectedSummary();
        applyFilter();

        String msg;
        if (!PermissionHelper.hasUsageStatsPermission(requireContext())) {
            msg = getString(R.string.toast_enable_usage_for_reels);
        } else if (newlyAdded > 0) {
            msg = "Reels & Shorts guard enabled on " + newlyAdded + " video/social apps.";
        } else if (!shortForm.isEmpty()) {
            msg = "Reels & Shorts guard active on " + shortForm.size() + " apps.";
        } else {
            msg = "Tap 'Block Reels' on any installed app to guard its short-form feed.";
        }
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
    }

    private void guardDetectedSocialAndGames() {
        Set<String> selected = preferencesManager.getSelectedPackages();
        int newlyAdded = 0;
        for (AppItem item : allApps) {
            if (appRepository.isSocialOrGamePackage(item.getPackageName())) {
                if (!selected.contains(item.getPackageName())) {
                    selected.add(item.getPackageName());
                    dbHelper.recordAppSelected(item.getPackageName(), item.getAppName());
                    newlyAdded++;
                }
                item.setSelected(true);
            }
        }
        preferencesManager.setSelectedPackages(selected);
        updateSelectedSummary();
        applyFilter();
        String msg = newlyAdded > 0
                ? "Added " + newlyAdded + " Social/Game apps to full-app guard."
                : "All detected Social & Game apps are already guarded.";
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
    }

    private void loadAppsAsync() {
        executor.execute(() -> {
            List<AppItem> loaded = appRepository.getLaunchableApps();
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    if (binding == null) {
                        return;
                    }
                    allApps.clear();
                    allApps.addAll(loaded);
                    updateSelectedSummary();
                    applyFilter();
                });
            }
        });
    }

    private void applyFilter() {
        if (binding == null) {
            return;
        }
        List<AppItem> filtered = new ArrayList<>();
        for (AppItem item : allApps) {
            if (currentFilterMode == FILTER_REELS_BLOCKED && !item.isShortFormBlocked()) {
                continue;
            }
            if (currentFilterMode == FILTER_SELECTED && !item.isSelected()) {
                continue;
            }
            if (currentFilterMode == FILTER_SOCIAL_GAMES
                    && !appRepository.isSocialOrGamePackage(item.getPackageName())) {
                continue;
            }
            if (!currentQuery.isEmpty()
                    && !item.getAppName().toLowerCase(Locale.getDefault()).contains(currentQuery)) {
                continue;
            }
            filtered.add(item);
        }

        boolean hasUsage = PermissionHelper.hasUsageStatsPermission(requireContext());
        adapter.submitList(filtered, hasUsage);
        binding.tvAppsEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void updateSelectedSummary() {
        if (binding == null) {
            return;
        }
        int lockedCount = preferencesManager.getSelectedPackages().size();
        int reelsCount = preferencesManager.getShortFormBlockedPackages().size();
        if (lockedCount == 0 && reelsCount == 0) {
            binding.tvAppsSelectedSummary.setText(R.string.label_no_apps_selected);
        } else {
            binding.tvAppsSelectedSummary.setText(String.format(
                    Locale.getDefault(), "%d Locked • %d No Reels", lockedCount, reelsCount
            ));
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
