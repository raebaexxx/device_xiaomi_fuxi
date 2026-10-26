/*
 * Copyright (C) 2025-2026 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.hypercharge;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.lineageos.settings.Constants;
import org.lineageos.settings.utils.FileUtils;

/**
 * Applies a user-selected charge-current ceiling without ever raising the
 * current above the value chosen by the OEM charger/thermal stack.
 *
 * The service is a ceiling, not a fixed writer: if the OEM or thermal daemon
 * lowers the current further, that lower value is left alone.
 */
public class HyperChargeService extends Service {

    private static final String TAG = "HyperChargeService";

    private static final long POLL_INTERVAL_MS = 2000;
    private static final long HANDSHAKE_DELAY_MS = 2000;

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private SharedPreferences mPrefs;
    private Runnable mTick;
    private Runnable mPendingStart;
    private boolean mMonitoring;

    private long mOriginalCurrent;
    private long mLastApplied;

    private final BroadcastReceiver mPowerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_POWER_CONNECTED.equals(action)) {
                Log.i(TAG, "Charger connected");
                if (isLimitEnabled()) {
                    scheduleStart();
                }
            } else if (Intent.ACTION_POWER_DISCONNECTED.equals(action)) {
                Log.i(TAG, "Charger disconnected");
                // Keep the original current around: the charger may reset the
                // kernel value on the next connection, and the next monitoring
                // pass will re-clamp it if needed.
                stopMonitoring();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        loadState();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        registerReceiver(mPowerReceiver, filter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!isLimitEnabled()) {
            stopMonitoring();
            restoreLimit();
            stopSelf();
            return START_NOT_STICKY;
        }

        if (isPluggedIn()) {
            scheduleStart();
        } else {
            stopMonitoring();
        }
        return START_STICKY;
    }

    private boolean isLimitEnabled() {
        return mPrefs.getBoolean(Constants.KEY_CHARGE_LIMIT_ENABLED, false)
                && getTargetCurrent() > 0;
    }

    private long getTargetCurrent() {
        String value = mPrefs.getString(Constants.KEY_CHARGE_LIMIT,
                Constants.DEFAULT_CHARGE_LIMIT);
        if (value == null || Constants.CHARGE_LIMIT_FULL.equals(value)) {
            return -1;
        }
        try {
            long current = Long.parseLong(value);
            return current > 0 ? current : -1;
        } catch (NumberFormatException e) {
            Log.w(TAG, "Invalid charge limit: " + value);
            return -1;
        }
    }

    private void loadState() {
        mOriginalCurrent = mPrefs.getLong(Constants.KEY_CHARGE_LIMIT_ORIGINAL, 0);
        mLastApplied = mPrefs.getLong(Constants.KEY_CHARGE_LIMIT_APPLIED, 0);

        long current = readCurrent();
        if (current <= 0) {
            return;
        }

        if (mLastApplied > 0 && mOriginalCurrent > 0 && current == mLastApplied) {
            // The service was restarted while its limit was still applied.
            return;
        }

        // The OEM stack owns the current now. Capture it as the ceiling to
        // restore when the user disables the limit.
        mLastApplied = 0;
        mOriginalCurrent = current;
        mPrefs.edit()
                .putLong(Constants.KEY_CHARGE_LIMIT_APPLIED, 0)
                .putLong(Constants.KEY_CHARGE_LIMIT_ORIGINAL, mOriginalCurrent)
                .apply();
    }

    private void scheduleStart() {
        stopMonitoring();
        if (mPendingStart != null) {
            mHandler.removeCallbacks(mPendingStart);
        }
        mPendingStart = () -> {
            mPendingStart = null;
            startMonitoring();
        };
        mHandler.postDelayed(mPendingStart, HANDSHAKE_DELAY_MS);
    }

    private void startMonitoring() {
        if (!isLimitEnabled() || !isPluggedIn()) {
            return;
        }

        if (mOriginalCurrent <= 0) {
            long current = readCurrent();
            if (current > 0) {
                mOriginalCurrent = current;
                mPrefs.edit()
                        .putLong(Constants.KEY_CHARGE_LIMIT_ORIGINAL, mOriginalCurrent)
                        .apply();
            }
        }

        if (mMonitoring) {
            return;
        }

        mMonitoring = true;
        mTick = new Runnable() {
            @Override
            public void run() {
                if (!mMonitoring) {
                    return;
                }
                applyLimit();
                if (mMonitoring) {
                    mHandler.postDelayed(this, POLL_INTERVAL_MS);
                }
            }
        };
        mHandler.post(mTick);
    }

    private void stopMonitoring() {
        if (mPendingStart != null) {
            mHandler.removeCallbacks(mPendingStart);
            mPendingStart = null;
        }
        if (mTick != null) {
            mHandler.removeCallbacks(mTick);
            mTick = null;
        }
        mMonitoring = false;
    }

    private void applyLimit() {
        if (!isLimitEnabled() || !isCharging()) {
            return;
        }

        long target = getTargetCurrent();
        if (target <= 0) {
            return;
        }

        long current = readCurrent();
        if (current <= 0) {
            return;
        }

        if (current == mLastApplied && target > current) {
            // We previously lowered the current. Allow the user to raise it
            // back up, but never above what the OEM stack allowed before.
            if (mOriginalCurrent > current) {
                long desired = Math.min(target, mOriginalCurrent);
                if (desired > current) {
                    writeCurrent(desired);
                }
            }
        } else if (current > target) {
            // Never raise a limit: only clamp when the current is above the
            // user's ceiling. If this is an OEM value (not the value we wrote
            // last), remember it so disabling cannot restore a stale, higher
            // current.
            if (current != mLastApplied) {
                mOriginalCurrent = current;
                mPrefs.edit()
                        .putLong(Constants.KEY_CHARGE_LIMIT_ORIGINAL, mOriginalCurrent)
                        .apply();
            }
            writeCurrent(target);
        } else if (current != mLastApplied) {
            // The OEM/thermal stack lowered the current below our ceiling.
            // Forget our old value so a later target change cannot raise it.
            mLastApplied = 0;
            mPrefs.edit()
                    .putLong(Constants.KEY_CHARGE_LIMIT_APPLIED, 0)
                    .apply();
        }
    }

    private boolean writeCurrent(long value) {
        boolean ok = FileUtils.writeLine(
                Constants.NODE_CONSTANT_CHARGE_CURRENT, String.valueOf(value));
        if (ok) {
            mLastApplied = value;
            mPrefs.edit()
                    .putLong(Constants.KEY_CHARGE_LIMIT_APPLIED, value)
                    .putLong(Constants.KEY_CHARGE_LIMIT_ORIGINAL, mOriginalCurrent)
                    .apply();
            Log.i(TAG, "Charge current limited to " + value + " uA");
        } else {
            Log.w(TAG, "Failed to write charge current limit " + value);
        }
        return ok;
    }

    private void restoreLimit() {
        if (mLastApplied > 0 && mOriginalCurrent > 0) {
            long current = readCurrent();
            if (current == mLastApplied) {
                if (FileUtils.writeLine(Constants.NODE_CONSTANT_CHARGE_CURRENT,
                        String.valueOf(mOriginalCurrent))) {
                    Log.i(TAG, "Restored charge current to " + mOriginalCurrent + " uA");
                }
            }
        }
        mLastApplied = 0;
        mOriginalCurrent = 0;
        mPrefs.edit()
                .remove(Constants.KEY_CHARGE_LIMIT_APPLIED)
                .remove(Constants.KEY_CHARGE_LIMIT_ORIGINAL)
                .apply();
    }

    private boolean isPluggedIn() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, filter);
        if (batteryStatus == null) {
            return false;
        }
        int plugged = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        return plugged == BatteryManager.BATTERY_PLUGGED_AC
                || plugged == BatteryManager.BATTERY_PLUGGED_USB
                || plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS;
    }

    private boolean isCharging() {
        String status = FileUtils.readOneLine(Constants.NODE_BATTERY_STATUS);
        if (status == null || !"Charging".equalsIgnoreCase(status.trim())) {
            return false;
        }

        String capacityValue = FileUtils.readOneLine(Constants.NODE_BATTERY_CAPACITY);
        if (capacityValue == null) {
            return true;
        }
        try {
            return Integer.parseInt(capacityValue.trim()) < 100;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    private static long readCurrent() {
        try {
            String value = FileUtils.readOneLine(Constants.NODE_CONSTANT_CHARGE_CURRENT);
            return value != null ? Long.parseLong(value.trim()) : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Starts or stops the service according to the current preferences.
     */
    public static void update(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean enabled = prefs.getBoolean(Constants.KEY_CHARGE_LIMIT_ENABLED, false);
        String limit = prefs.getString(Constants.KEY_CHARGE_LIMIT,
                Constants.DEFAULT_CHARGE_LIMIT);

        Intent intent = new Intent(context, HyperChargeService.class);
        if (enabled && !Constants.CHARGE_LIMIT_FULL.equals(limit)) {
            context.startService(intent);
        } else {
            context.stopService(intent);
            restoreStaleLimit(context);
        }
    }

    /**
     * Restores a limit left behind if the service was killed before it could
     * run its normal cleanup.
     */
    public static void restoreStaleLimit(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        long applied = prefs.getLong(Constants.KEY_CHARGE_LIMIT_APPLIED, 0);
        long original = prefs.getLong(Constants.KEY_CHARGE_LIMIT_ORIGINAL, 0);
        if (applied <= 0 || original <= 0) {
            return;
        }

        long current = readCurrent();
        if (current == applied) {
            FileUtils.writeLine(Constants.NODE_CONSTANT_CHARGE_CURRENT,
                    String.valueOf(original));
        }
        prefs.edit()
                .remove(Constants.KEY_CHARGE_LIMIT_APPLIED)
                .remove(Constants.KEY_CHARGE_LIMIT_ORIGINAL)
                .apply();
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "Service destroyed");
        unregisterReceiver(mPowerReceiver);
        stopMonitoring();
        restoreLimit();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
