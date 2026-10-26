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
import android.util.Log;

import androidx.preference.PreferenceManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.lineageos.settings.Constants;
import org.lineageos.settings.utils.FileUtils;

public class AutoHbmService extends Service {

    private static final String TAG = "AutoHbmService";
    private static final int FALLBACK_BRIGHTNESS = 200;
    private static boolean mAutoHbmActive = false;

    private ExecutorService mExecutorService;
    private SensorManager mSensorManager;
    private Sensor mLightSensor;
    private SharedPreferences mSharedPrefs;
    private int mLastManualBrightness = FALLBACK_BRIGHTNESS;
    private boolean mIsAutoBrightnessEnabled = false;

    public void activateLightSensorRead() {
        submit(() -> {
            mSensorManager = (SensorManager) getApplicationContext()
                    .getSystemService(Context.SENSOR_SERVICE);
            if (mSensorManager == null) {
                return;
            }
            mLightSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
            if (mLightSensor == null) {
                Log.w(TAG, "No light sensor available");
                return;
            }
            mSensorManager.registerListener(mSensorEventListener, mLightSensor,
                    SensorManager.SENSOR_DELAY_NORMAL);
        });
    }

    public void deactivateLightSensorRead() {
        if (mSensorManager != null) {
            mSensorManager.unregisterListener(mSensorEventListener);
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

    private boolean isCurrentlyEnabled() {
        return HbmUtils.isHbmEnabled();
    }

    private final SensorEventListener mSensorEventListener = new SensorEventListener() {
        private boolean mCrossedThreshold = false;
        private long mCrossedThresholdTime = 0;
        private long mLastTriggerTime = 0;

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
            int timeToEnableHbm = mSharedPrefs.getInt(Constants.KEY_AUTO_HBM_ENABLE_TIME,
                    Constants.AUTO_HBM_DEFAULT_ENABLE_TIME);
            int timeToDisableHbm = mSharedPrefs.getInt(Constants.KEY_AUTO_HBM_DISABLE_TIME,
                    Constants.AUTO_HBM_DEFAULT_DISABLE_TIME);

            if (lux > luxThreshold) {
                if (!mCrossedThreshold) {
                    mCrossedThreshold = true;
                    mCrossedThresholdTime = System.currentTimeMillis();
                } else {
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - mCrossedThresholdTime >= timeToEnableHbm * 1000L
                            && (!mAutoHbmActive || !isCurrentlyEnabled())
                            && !keyguardShowing) {
                        mAutoHbmActive = true;
                        saveCurrentBrightness();
                        HbmUtils.setHbmEnabled(true);
                        setBrightnessDirectly(HbmUtils.getMaxBrightness());
                        mLastTriggerTime = currentTime;
                    }
                }
            } else {
                mCrossedThreshold = false;

                if (mAutoHbmActive) {
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - mLastTriggerTime >= timeToDisableHbm * 1000L) {
                        mAutoHbmActive = false;
                        HbmUtils.setHbmEnabled(false);
                        restoreBrightness();
                    }
                }
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
        mExecutorService = Executors.newSingleThreadExecutor();
        IntentFilter screenStateFilter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        screenStateFilter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(mScreenStateReceiver, screenStateFilter);
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null && pm.isInteractive()) {
            activateLightSensorRead();
        }
    }

    private void submit(Runnable runnable) {
        if (mExecutorService != null) {
            mExecutorService.submit(runnable);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mSharedPrefs == null
                || !mSharedPrefs.getBoolean(Constants.KEY_AUTO_HBM, false)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mScreenStateReceiver);
        deactivateLightSensorRead();
        if (mExecutorService != null) {
            mExecutorService.shutdownNow();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
