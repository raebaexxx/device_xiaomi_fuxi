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

package org.lineageos.settings;

public final class Constants {

    private Constants() {
        // This class is not supposed to be instantiated
    }

    // High brightness mode
    public static final String KEY_HBM_MANUAL = "hbm_manual";
    public static final String KEY_AUTO_HBM = "auto_hbm";
    public static final String KEY_AUTO_HBM_THRESHOLD = "auto_hbm_threshold";
    public static final String KEY_AUTO_HBM_ENABLE_TIME = "auto_hbm_enable_time";
    public static final String KEY_AUTO_HBM_DISABLE_TIME = "auto_hbm_disable_time";
    public static final String KEY_CURRENT_LUX_LEVEL = "current_lux_level";
    public static final String KEY_HBM_SAVED_BRIGHTNESS = "hbm_saved_brightness";
    public static final String KEY_HBM_SAVED_AUTO_BRIGHTNESS = "hbm_saved_auto_brightness";

    public static final String NODE_DISP_PARAM =
            "/sys/class/mi_display/disp-DSI-0/disp_param";
    public static final String NODE_DISP_PARAM_FALLBACK =
            "/sys/devices/virtual/mi_display/disp_feature/disp-DSI-0/disp_param";
    public static final String NODE_BRIGHTNESS =
            "/sys/class/backlight/panel0-backlight/brightness";
    public static final String NODE_MAX_BRIGHTNESS =
            "/sys/class/backlight/panel0-backlight/max_brightness";

    public static final int HBM_FEATURE_ID = 1;
    public static final int AUTO_HBM_DEFAULT_THRESHOLD = 8000;
    public static final int AUTO_HBM_DEFAULT_ENABLE_TIME = 0;
    public static final int AUTO_HBM_DEFAULT_DISABLE_TIME = 3;
    // fuxi's df_default.xml uses 8000 lux to enter HBM and 5000 lux to
    // leave it. Keep that ratio when the user changes the trigger level.
    public static final int AUTO_HBM_EXIT_THRESHOLD_NUMERATOR = 5;
    public static final int AUTO_HBM_EXIT_THRESHOLD_DENOMINATOR = 8;
}
