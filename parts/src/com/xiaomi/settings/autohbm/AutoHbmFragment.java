/*
 * Copyright (C) 2025-2026 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lineageos.settings.autohbm;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.android.settingslib.widget.MainSwitchPreference;
import com.android.settingslib.widget.UsageProgressBarPreference;

import org.lineageos.settings.Constants;
import org.lineageos.settings.CustomSeekBarPreference;
import org.lineageos.settings.R;
import org.lineageos.settings.utils.TileUtils;

public class AutoHbmFragment extends PreferenceFragmentCompat
        implements OnCheckedChangeListener, SensorEventListener,
        Preference.OnPreferenceChangeListener {

    private static final String[] AUTO_HBM_PREFERENCES = {
            Constants.KEY_AUTO_HBM_THRESHOLD,
            Constants.KEY_AUTO_HBM_ENABLE_TIME,
            Constants.KEY_AUTO_HBM_DISABLE_TIME,
            Constants.KEY_CURRENT_LUX_LEVEL
    };

    private MainSwitchPreference mManualHbmSwitch;
    private MainSwitchPreference mAutoHbmSwitch;
    private CustomSeekBarPreference mAutoHbmThresholdPreference;
    private SensorManager mSensorManager;
    private Sensor mLightSensor;
    private UsageProgressBarPreference mCurrentLuxLevelPreference;
    private int mCurrentLux;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.auto_hbm, rootKey);
        setHasOptionsMenu(true);

        final Context context = requireContext();
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);

        mManualHbmSwitch = findPreference(Constants.KEY_HBM_MANUAL);
        mAutoHbmSwitch = findPreference(Constants.KEY_AUTO_HBM);
        mAutoHbmThresholdPreference = findPreference(Constants.KEY_AUTO_HBM_THRESHOLD);
        mCurrentLuxLevelPreference = findPreference(Constants.KEY_CURRENT_LUX_LEVEL);

        final boolean autoHbmEnabled = sharedPrefs.getBoolean(Constants.KEY_AUTO_HBM, false);
        mAutoHbmSwitch.setChecked(autoHbmEnabled);
        mAutoHbmSwitch.addOnSwitchChangeListener(this);

        mManualHbmSwitch.setChecked(!autoHbmEnabled && HbmUtils.isHbmEnabled());
        mManualHbmSwitch.setEnabled(!autoHbmEnabled);
        mManualHbmSwitch.addOnSwitchChangeListener((buttonView, isChecked) -> {
            if (mAutoHbmSwitch.isChecked()) {
                mManualHbmSwitch.setChecked(false);
                return;
            }
            if (isChecked) {
                if (!HbmUtils.enableManual(context)) {
                    mManualHbmSwitch.setChecked(false);
                }
            } else {
                HbmUtils.disableManual(context);
                HbmNotification.cancel(context);
            }
        });

        mAutoHbmThresholdPreference.setOnPreferenceChangeListener(this);

        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (mSensorManager != null) {
            mLightSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        }

        toggleAutoHbmPreferencesVisibility(autoHbmEnabled);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.auto_hbm_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.add_auto_hbm_tile) {
            TileUtils.requestAddTileService(
                    requireContext(),
                    AutoHbmTileService.class,
                    R.string.auto_hbm_title,
                    R.drawable.ic_auto_hbm_tile
            );
            return true;
        } else if (item.getItemId() == R.id.add_hbm_tile) {
            TileUtils.requestAddTileService(
                    requireContext(),
                    HbmTileService.class,
                    R.string.tile_hbm,
                    R.drawable.ic_hbm_tile
            );
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onResume() {
        super.onResume();
        boolean autoHbmEnabled = mAutoHbmSwitch.isChecked();
        mManualHbmSwitch.setEnabled(!autoHbmEnabled);
        mManualHbmSwitch.setChecked(!autoHbmEnabled && HbmUtils.isHbmEnabled());
        if (autoHbmEnabled && mSensorManager != null && mLightSensor != null) {
            mSensorManager.registerListener(this, mLightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mSensorManager != null && mLightSensor != null) {
            mSensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        final Context context = requireContext();
        AutoHbmService.setEnabled(context, isChecked);

        if (isChecked) {
            // Manual HBM and Auto HBM must not fight over brightness.
            mManualHbmSwitch.setChecked(false);
            mManualHbmSwitch.setEnabled(false);
        } else {
            mManualHbmSwitch.setEnabled(true);
        }

        toggleAutoHbmPreferencesVisibility(isChecked);

        if (mSensorManager == null || mLightSensor == null) {
            return;
        }
        if (isChecked) {
            mSensorManager.registerListener(this, mLightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            mSensorManager.unregisterListener(this);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mAutoHbmThresholdPreference && mCurrentLuxLevelPreference != null) {
            int threshold = (int) newValue;
            updateCurrentLuxLevelPreference(mCurrentLux, threshold);
            return true;
        }
        return false;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_LIGHT || mCurrentLuxLevelPreference == null) {
            return;
        }
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        mCurrentLux = (int) event.values[0];
        int threshold = sharedPrefs.getInt(Constants.KEY_AUTO_HBM_THRESHOLD,
                Constants.AUTO_HBM_DEFAULT_THRESHOLD);
        updateCurrentLuxLevelPreference(mCurrentLux, threshold);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Do nothing
    }

    private void updateCurrentLuxLevelPreference(int currentLux, int threshold) {
        if (mCurrentLuxLevelPreference == null) {
            return;
        }
        mCurrentLuxLevelPreference.setUsageSummary(String.valueOf(currentLux));
        mCurrentLuxLevelPreference.setTotalSummary(String.valueOf(threshold));
        if (currentLux >= threshold) {
            mCurrentLuxLevelPreference.setPercent(100, 100);
        } else {
            mCurrentLuxLevelPreference.setPercent(currentLux, threshold);
        }
    }

    private void toggleAutoHbmPreferencesVisibility(boolean show) {
        for (String prefKey : AUTO_HBM_PREFERENCES) {
            Preference pref = findPreference(prefKey);
            if (pref != null) {
                pref.setVisible(show);
            }
        }
    }
}
