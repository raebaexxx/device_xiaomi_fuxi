/*
 * Copyright (C) 2026 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.lineageos.settings.autohbm.AutoHbmFragment;
import org.lineageos.settings.autohbm.HbmUtils;
import org.lineageos.settings.hypercharge.HyperChargeService;

public class BootCompletedReceiver extends BroadcastReceiver {

    private static final String TAG = "FuxiParts";
    private static final boolean DEBUG = true;

    @Override
    public void onReceive(final Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "Received boot completed intent");
        }

        PreferenceManager.setDefaultValues(context, R.xml.auto_hbm, false);
        PreferenceManager.setDefaultValues(context, R.xml.hypercharge_settings, false);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean autoHbmEnabled = prefs.getBoolean(Constants.KEY_AUTO_HBM, false);

        // Manual HBM is intentionally not restored after a reboot.
        if (!autoHbmEnabled && prefs.getBoolean(Constants.KEY_HBM_MANUAL, false)) {
            HbmUtils.disableManual(context);
        }

        try {
            AutoHbmFragment.toggleAutoHbmService(context);
        } catch (Exception e) {
            Log.e(TAG, "Failed to restore Auto HBM state", e);
        }

        try {
            HyperChargeService.update(context);
        } catch (Exception e) {
            Log.e(TAG, "Failed to restore charging limit", e);
        }
    }
}
