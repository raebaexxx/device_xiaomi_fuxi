/*
 * Copyright (C) 2025-2026 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.hypercharge;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragment;
import androidx.preference.PreferenceManager;

import com.android.settingslib.widget.MainSwitchPreference;

import org.lineageos.settings.Constants;
import org.lineageos.settings.R;

public class HyperChargeSettingsFragment extends PreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "ChargingControl";

    private MainSwitchPreference mMainSwitch;
    private ListPreference mLimitPref;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context != null) {
            sanitizePreferences(context);
        }
        addPreferencesFromResource(R.xml.hypercharge_settings);

        mMainSwitch = findPreference(Constants.KEY_CHARGE_LIMIT_ENABLED);
        mMainSwitch.setOnPreferenceChangeListener(this);

        mLimitPref = findPreference(Constants.KEY_CHARGE_LIMIT);
        mLimitPref.setOnPreferenceChangeListener(this);

        updateEnabledStates(mMainSwitch.isChecked());
    }

    private void sanitizePreferences(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        try {
            String limit = prefs.getString(Constants.KEY_CHARGE_LIMIT,
                    Constants.DEFAULT_CHARGE_LIMIT);
            if (!Constants.CHARGE_LIMIT_FULL.equals(limit)
                    && !Constants.CHARGE_LIMIT_50W.equals(limit)
                    && !Constants.CHARGE_LIMIT_33W.equals(limit)
                    && !Constants.CHARGE_LIMIT_30W.equals(limit)) {
                Log.w(TAG, "Invalid charge limit preference — resetting");
                prefs.edit().remove(Constants.KEY_CHARGE_LIMIT).apply();
            }
        } catch (ClassCastException e) {
            Log.w(TAG, "Corrupt charge limit preference — resetting", e);
            prefs.edit().remove(Constants.KEY_CHARGE_LIMIT).apply();
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        Context context = getContext();
        if (context == null) {
            return false;
        }

        boolean enabled = preference == mMainSwitch
                ? (Boolean) newValue : mMainSwitch.isChecked();
        String limit = preference == mLimitPref
                ? (String) newValue : mLimitPref.getValue();
        if (limit == null) {
            limit = Constants.DEFAULT_CHARGE_LIMIT;
        }

        if (preference == mMainSwitch && enabled
                && Constants.CHARGE_LIMIT_FULL.equals(limit)) {
            // "Full" means no limit, so enabling the switch should fall back
            // to the default protected rate.
            limit = Constants.DEFAULT_CHARGE_LIMIT;
            mLimitPref.setValue(limit);
        }

        if (preference == mLimitPref
                && Constants.CHARGE_LIMIT_FULL.equals(limit)) {
            // Selecting full speed is the same as disabling the limit.
            mMainSwitch.setChecked(false);
            enabled = false;
        }

        // OnPreferenceChange runs before the preference framework persists the
        // new value, so write it before asking the service to re-evaluate.
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit()
                .putBoolean(Constants.KEY_CHARGE_LIMIT_ENABLED, enabled)
                .putString(Constants.KEY_CHARGE_LIMIT, limit)
                .apply();

        updateEnabledStates(enabled);
        HyperChargeService.update(context);
        return true;
    }

    private void updateEnabledStates(boolean masterEnabled) {
        mLimitPref.setEnabled(masterEnabled);
    }
}
