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

import android.content.SharedPreferences;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import androidx.preference.PreferenceManager;

import org.lineageos.settings.Constants;
import org.lineageos.settings.R;

public class HbmTileService extends TileService {

    private boolean mAutoHbmEnabled;

    @Override
    public void onStartListening() {
        super.onStartListening();
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mAutoHbmEnabled = sharedPrefs.getBoolean(Constants.KEY_AUTO_HBM, false);
        if (mAutoHbmEnabled || !HbmUtils.isHbmEnabled()) {
            HbmNotification.cancel(this);
        }
        updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        mAutoHbmEnabled = PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(Constants.KEY_AUTO_HBM, false);
        if (mAutoHbmEnabled || !HbmUtils.isHbmSupported()) {
            updateTile();
            return;
        }

        if (HbmUtils.isHbmEnabled()) {
            HbmUtils.disableManual(this);
            HbmNotification.cancel(this);
        } else if (HbmUtils.enableManual(this)) {
            HbmNotification.show(this);
        }
        updateTile();
    }

    @Override
    public void onTileRemoved() {
        super.onTileRemoved();
        if (!mAutoHbmEnabled && HbmUtils.isHbmEnabled()) {
            HbmUtils.disableManual(this);
            HbmNotification.cancel(this);
        }
    }

    private void updateTile() {
        final Tile tile = getQsTile();
        if (tile == null) {
            return;
        }

        if (!HbmUtils.isHbmSupported()) {
            tile.setState(Tile.STATE_UNAVAILABLE);
            tile.setLabel(getString(R.string.tile_hbm_unavailable));
            tile.setSubtitle(null);
            tile.updateTile();
            return;
        }

        if (mAutoHbmEnabled) {
            tile.setState(Tile.STATE_UNAVAILABLE);
            tile.setLabel(getString(R.string.tile_auto_hbm_or_brightness_enabled));
            tile.setSubtitle(null);
        } else {
            boolean enabled = HbmUtils.isHbmEnabled();
            tile.setState(enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            tile.setLabel(getString(R.string.tile_hbm));
            tile.setSubtitle(enabled
                    ? getString(R.string.tile_hbm_on)
                    : getString(R.string.tile_hbm_off));
        }
        tile.updateTile();
    }
}
