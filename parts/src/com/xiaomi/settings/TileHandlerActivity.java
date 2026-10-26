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

package org.lineageos.settings;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.service.quicksettings.TileService;

import java.util.HashMap;
import java.util.Map;

import org.lineageos.settings.autohbm.AutoHbmActivity;
import org.lineageos.settings.autohbm.AutoHbmTileService;
import org.lineageos.settings.autohbm.HbmTileService;
import org.lineageos.settings.hypercharge.HyperChargeSettingsActivity;
import org.lineageos.settings.hypercharge.HyperChargeTileService;

public final class TileHandlerActivity extends Activity {

    private static final Map<String, Class<?>> TILE_ACTIVITY_MAP = new HashMap<>();

    static {
        TILE_ACTIVITY_MAP.put(AutoHbmTileService.class.getName(), AutoHbmActivity.class);
        TILE_ACTIVITY_MAP.put(HbmTileService.class.getName(), AutoHbmActivity.class);
        TILE_ACTIVITY_MAP.put(HyperChargeTileService.class.getName(),
                HyperChargeSettingsActivity.class);
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent intent = getIntent();
        if (intent == null
                || !TileService.ACTION_QS_TILE_PREFERENCES.equals(intent.getAction())) {
            finish();
            return;
        }

        final ComponentName qsTile = intent.getParcelableExtra(Intent.EXTRA_COMPONENT_NAME);
        if (qsTile == null) {
            finish();
            return;
        }

        final String qsName = qsTile.getClassName();
        final Intent targetIntent = new Intent();

        if (TILE_ACTIVITY_MAP.containsKey(qsName)) {
            targetIntent.setClass(this, TILE_ACTIVITY_MAP.get(qsName));
        } else {
            final String packageName = qsTile.getPackageName();
            if (packageName == null) {
                finish();
                return;
            }
            targetIntent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            targetIntent.setData(Uri.fromParts("package", packageName, null));
        }

        targetIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TASK
                | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(targetIntent);
        finish();
    }
}
