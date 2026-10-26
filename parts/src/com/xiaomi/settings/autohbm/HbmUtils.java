/*
 * Copyright (C) 2026 The LineageOS Project
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
import android.provider.Settings;
import android.util.Log;

import androidx.preference.PreferenceManager;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.lineageos.settings.Constants;
import org.lineageos.settings.utils.FileUtils;

public final class HbmUtils {

    private static final String TAG = "HbmUtils";

    private static final Pattern HBM_STATE_PATTERN =
            Pattern.compile("hbm\\[01\\]\\s*:\\s*([01])");

    private HbmUtils() {
        // This class is not supposed to be instantiated
    }

    private static String getDispParamNode() {
        if (FileUtils.fileExists(Constants.NODE_DISP_PARAM)) {
            return Constants.NODE_DISP_PARAM;
        }
        return Constants.NODE_DISP_PARAM_FALLBACK;
    }

    public static boolean isHbmSupported() {
        return FileUtils.fileExists(Constants.NODE_DISP_PARAM)
                || FileUtils.fileExists(Constants.NODE_DISP_PARAM_FALLBACK);
    }

    public static boolean isHbmEnabled() {
        String content = FileUtils.readAll(getDispParamNode());
        Matcher matcher = HBM_STATE_PATTERN.matcher(content);
        return matcher.find() && "1".equals(matcher.group(1));
    }

    public static boolean setHbmEnabled(boolean enabled) {
        if (!isHbmSupported()) {
            Log.w(TAG, "HBM control node is not available");
            return false;
        }
        String value = Constants.HBM_FEATURE_ID + " " + (enabled ? "1" : "0");
        boolean ok = FileUtils.writeLine(getDispParamNode(), value);
        if (!ok) {
            Log.w(TAG, "Failed to write HBM state: " + value);
        }
        return ok;
    }

    public static int getMaxBrightness() {
        return readInt(Constants.NODE_MAX_BRIGHTNESS, 4095);
    }

    public static int getBrightness() {
        return readInt(Constants.NODE_BRIGHTNESS, 0);
    }

    public static boolean setBrightness(int brightness) {
        int max = getMaxBrightness();
        int value = Math.max(0, Math.min(brightness, max));
        return FileUtils.writeLine(Constants.NODE_BRIGHTNESS, String.valueOf(value));
    }

    public static boolean isAutoBrightnessEnabled(Context context) {
        try {
            return Settings.System.getInt(context.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE)
                    == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC;
        } catch (Settings.SettingNotFoundException e) {
            return false;
        }
    }

    /**
     * Turns on HBM for the manual (quick settings) path and remembers the
     * brightness that was active before HBM was requested.
     */
    public static boolean enableManual(Context context) {
        if (!isHbmSupported()) {
            return false;
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (!isHbmEnabled()) {
            prefs.edit()
                    .putInt(Constants.KEY_HBM_SAVED_BRIGHTNESS, getBrightness())
                    .putBoolean(Constants.KEY_HBM_SAVED_AUTO_BRIGHTNESS,
                            isAutoBrightnessEnabled(context))
                    .apply();
        }
        prefs.edit().putBoolean(Constants.KEY_HBM_MANUAL, true).apply();

        boolean ok = setHbmEnabled(true);
        setBrightness(getMaxBrightness());
        return ok;
    }

    /**
     * Turns off the manual HBM path and restores the previous brightness unless
     * automatic brightness is enabled.
     */
    public static void disableManual(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        int savedBrightness = prefs.getInt(Constants.KEY_HBM_SAVED_BRIGHTNESS, -1);
        boolean savedAutoBrightness = prefs.getBoolean(
                Constants.KEY_HBM_SAVED_AUTO_BRIGHTNESS, false);

        setHbmEnabled(false);
        if (!savedAutoBrightness && savedBrightness >= 0) {
            setBrightness(savedBrightness);
        }

        prefs.edit()
                .putBoolean(Constants.KEY_HBM_MANUAL, false)
                .remove(Constants.KEY_HBM_SAVED_BRIGHTNESS)
                .remove(Constants.KEY_HBM_SAVED_AUTO_BRIGHTNESS)
                .apply();
    }

    private static int readInt(String path, int fallback) {
        try {
            String value = FileUtils.readOneLine(path);
            return value != null ? Integer.parseInt(value.trim()) : fallback;
        } catch (NumberFormatException e) {
            Log.w(TAG, "Invalid integer in " + path, e);
            return fallback;
        }
    }
}
