package org.lunaris.settings;

import android.app.ActionBar;
import android.content.ActivityNotFoundException;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.recyclerview.widget.RecyclerView;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.internal.util.lunaris.VibrationUtils;
import com.android.settings.R;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import org.lunaris.settings.preferences.SingularityDashboardPreference;

@SearchIndexable
public class LunarisSettings extends DashboardFragment {

    private static final String TAG = "LunarisSettings";

    private View mAppBar;
    private int mAppBarVisibility;
    private boolean mRestoreActionBar;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        // Keep real preferences for Settings navigation, metrics and search indexing.
        // Only their presentation is replaced by the dashboard.
        for (int i = 0; i < getPreferenceScreen().getPreferenceCount(); i++) {
            Preference preference = getPreferenceScreen().getPreference(i);
            preference.setVisible("singularity_dashboard".equals(preference.getKey()));
        }
        SingularityDashboardPreference dashboard = findPreference("singularity_dashboard");
        dashboard.setNavigationListener(this::openPreference);
        dashboard.setBackListener(() ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.setBackgroundColor(getContext().getColor(R.color.singularity_background));
        RecyclerView list = getListView();
        list.setPadding(0, list.getPaddingTop(), 0, list.getPaddingBottom());
        setDivider(null);
        // The dashboard supplies its own compact header. Restore the host chrome
        // when the view goes away so other Settings pages retain their toolbar.
        mAppBar = requireActivity().findViewById(R.id.app_bar);
        if (mAppBar != null) {
            mAppBarVisibility = mAppBar.getVisibility();
            mAppBar.setVisibility(View.GONE);
        } else {
            ActionBar actionBar = requireActivity().getActionBar();
            mRestoreActionBar = actionBar != null && actionBar.isShowing();
            if (mRestoreActionBar) {
                actionBar.hide();
            }
        }
    }

    private void openPreference(String key) {
        Preference preference = findPreference(key);
        if (preference == null) {
            return;
        }
        try {
            preference.performClick();
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(getContext(), R.string.singularity_option_unavailable,
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        if (mAppBar != null) {
            mAppBar.setVisibility(mAppBarVisibility);
            mAppBar = null;
        }
        if (mRestoreActionBar) {
            ActionBar actionBar = requireActivity().getActionBar();
            if (actionBar != null) {
                actionBar.show();
            }
            mRestoreActionBar = false;
        }
        super.onDestroyView();
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.lunaris_settings;
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.LUNARIS;
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference != null && preference.getKey() != null) {
            VibrationUtils.triggerVibration(getContext(), 3);
        }
        return super.onPreferenceTreeClick(preference);
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.lunaris_settings);
}
