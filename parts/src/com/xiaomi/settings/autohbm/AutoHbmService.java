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

import android.app.KeyguardManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.UserHandle;

import androidx.preference.PreferenceManager;

import org.lineageos.settings.Constants;
import org.lineageos.settings.utils.FileUtils;

public class AutoHbmService extends Service {

    private static final int FALLBACK_BRIGHTNESS = 200;

    private SensorManager mSensorManager;
    private Sensor mLightSensor;
    private SharedPreferences mSharedPrefs;
    private boolean mAutoHbmActive;
    private boolean mSensorRegistered;
    private int mLastManualBrightness = FALLBACK_BRIGHTNESS;
    private boolean mIsAutoBrightnessEnabled;

    private void activateLightSensorRead() {
        if (mSensorRegistered || mSensorManager == null || mLightSensor == null) {
            return;
        }
        mSensorRegistered = mSensorManager.registerListener(
                mSensorEventListener, mLightSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    private void deactivateLightSensorRead() {
        if (mSensorManager != null && mSensorRegistered) {
            mSensorManager.unregisterListener(mSensorEventListener);
            mSensorRegistered = false;
        }

        boolean wasActive = mAutoHbmActive;
        mAutoHbmActive = false;
        if (wasActive || HbmUtils.isHbmEnabled()) {
            HbmUtils.setHbmEnabled(false);
            if (wasActive) {
                restoreBrightness();
            }
        }
    }

    private void setBrightnessDirectly(int brightness) {
        FileUtils.writeLine(Constants.NODE_BRIGHTNESS, String.valueOf(brightness));
    }

    private void restoreBrightness() {
        if (mIsAutoBrightnessEnabled) {
            // Auto-brightness will handle the adjustment.
            return;
        }
        setBrightnessDirectly(mLastManualBrightness > 0
                ? mLastManualBrightness : FALLBACK_BRIGHTNESS);
    }

    private static int getExitThreshold(int threshold) {
        return threshold * Constants.AUTO_HBM_EXIT_THRESHOLD_NUMERATOR
                / Constants.AUTO_HBM_EXIT_THRESHOLD_DENOMINATOR;
    }

    private final SensorEventListener mSensorEventListener = new SensorEventListener() {
        private boolean mCrossedThreshold = false;
        private long mCrossedThresholdTime = 0;
        private long mBelowExitThresholdTime = 0;

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (mSharedPrefs == null
                    || !mSharedPrefs.getBoolean(Constants.KEY_AUTO_HBM, false)) {
                return;
            }
            float lux = event.values[0];
            KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
            boolean keyguardShowing = km != null && km.inKeyguardRestrictedInputMode();
            int luxThreshold = mSharedPrefs.getInt(Constants.KEY_AUTO_HBM_THRESHOLD,
                    Constants.AUTO_HBM_DEFAULT_THRESHOLD);
            int exitThreshold = getExitThreshold(luxThreshold);
            int timeToEnableHbm = mSharedPrefs.getInt(Constants.KEY_AUTO_HBM_ENABLE_TIME,
                    Constants.AUTO_HBM_DEFAULT_ENABLE_TIME);
            int timeToDisableHbm = mSharedPrefs.getInt(Constants.KEY_AUTO_HBM_DISABLE_TIME,
                    Constants.AUTO_HBM_DEFAULT_DISABLE_TIME);
            long now = SystemClock.elapsedRealtime();

            if (mAutoHbmActive) {
                // Keep HBM on until the light has stayed below the lower exit
                // threshold for the configured delay. The delay starts when
                // the light crosses the exit level, not when HBM was enabled.
                if (lux > exitThreshold) {
                    mBelowExitThresholdTime = 0;
                    return;
                }
                if (mBelowExitThresholdTime == 0) {
                    mBelowExitThresholdTime = now;
                }
                if (now - mBelowExitThresholdTime >= timeToDisableHbm * 1000L) {
                    mAutoHbmActive = false;
                    mBelowExitThresholdTime = 0;
                    HbmUtils.setHbmEnabled(false);
                    restoreBrightness();
                }
                return;
            }

            if (lux > luxThreshold) {
                if (!mCrossedThreshold) {
                    mCrossedThreshold = true;
                    mCrossedThresholdTime = now;
                } else if (now - mCrossedThresholdTime >= timeToEnableHbm * 1000L
                        && !keyguardShowing) {
                    mAutoHbmActive = true;
                    mCrossedThreshold = false;
                    mBelowExitThresholdTime = 0;
                    saveCurrentBrightness();
                    HbmUtils.setHbmEnabled(true);
                    setBrightnessDirectly(HbmUtils.getMaxBrightness());
                }
            } else {
                mCrossedThreshold = false;
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // Do nothing
        }
    };

    private void saveCurrentBrightness() {
        mLastManualBrightness = HbmUtils.getBrightness();
        if (mLastManualBrightness <= 0) {
            mLastManualBrightness = FALLBACK_BRIGHTNESS;
        }
        mIsAutoBrightnessEnabled = HbmUtils.isAutoBrightnessEnabled(this);
    }

    private final BroadcastReceiver mScreenStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                activateLightSensorRead();
            } else if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                deactivateLightSensorRead();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (mSensorManager != null) {
            mLightSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        }

        IntentFilter screenStateFilter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        screenStateFilter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(mScreenStateReceiver, screenStateFilter);

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null && pm.isInteractive()) {
            activateLightSensorRead();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mSharedPrefs == null
                || !mSharedPrefs.getBoolean(Constants.KEY_AUTO_HBM, false)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null && pm.isInteractive()) {
            activateLightSensorRead();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mScreenStateReceiver);
        deactivateLightSensorRead();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static void setEnabled(Context context, boolean enabled) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean(Constants.KEY_AUTO_HBM, enabled)
                .apply();
        update(context);
    }

    /**
     * Starts or stops the service according to the current preference. The
     * service owns the sensor registration and HBM state; callers only toggle
     * the preference through {@link #setEnabled(Context, boolean)}.
     */
    public static void update(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean enabled = prefs.getBoolean(Constants.KEY_AUTO_HBM, false);

        Intent intent = new Intent(context, AutoHbmService.class);
        if (enabled) {
            // Manual HBM and Auto HBM must not fight over brightness, but do
            // not touch the panel at all when manual HBM is already inactive.
            if (prefs.getBoolean(Constants.KEY_HBM_MANUAL, false)
                    || HbmUtils.isHbmEnabled()) {
                HbmUtils.disableManual(context);
            }
            HbmNotification.cancel(context);
            context.startServiceAsUser(intent, UserHandle.CURRENT);
        } else {
            context.stopServiceAsUser(intent, UserHandle.CURRENT);
        }
    }
}
