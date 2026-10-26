/*
 * Copyright (C) 2025-2026 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.hypercharge;

import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import androidx.preference.PreferenceManager;

import org.lineageos.settings.Constants;
import org.lineageos.settings.R;

public class HyperChargeTileService extends TileService {

    private SharedPreferences mSharedPrefs;

    @Override
    public void onCreate() {
        super.onCreate();
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTileState();
    }

    @Override
    public void onClick() {
        super.onClick();
        boolean enabled = mSharedPrefs.getBoolean(Constants.KEY_CHARGE_LIMIT_ENABLED, false);
        String limit = mSharedPrefs.getString(Constants.KEY_CHARGE_LIMIT,
                Constants.DEFAULT_CHARGE_LIMIT);

        if (!enabled) {
            SharedPreferences.Editor editor = mSharedPrefs.edit()
                    .putBoolean(Constants.KEY_CHARGE_LIMIT_ENABLED, true);
            if (Constants.CHARGE_LIMIT_FULL.equals(limit)) {
                editor.putString(Constants.KEY_CHARGE_LIMIT, Constants.DEFAULT_CHARGE_LIMIT);
            }
            editor.apply();
        } else if (Constants.CHARGE_LIMIT_FULL.equals(limit)) {
            mSharedPrefs.edit()
                    .putBoolean(Constants.KEY_CHARGE_LIMIT_ENABLED, true)
                    .putString(Constants.KEY_CHARGE_LIMIT, Constants.DEFAULT_CHARGE_LIMIT)
                    .apply();
        } else {
            mSharedPrefs.edit()
                    .putBoolean(Constants.KEY_CHARGE_LIMIT_ENABLED, false)
                    .apply();
        }

        HyperChargeService.update(this);
        updateTileState();
    }

    private void updateTileState() {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }

        boolean enabled = mSharedPrefs.getBoolean(Constants.KEY_CHARGE_LIMIT_ENABLED, false);
        String limit = mSharedPrefs.getString(Constants.KEY_CHARGE_LIMIT,
                Constants.DEFAULT_CHARGE_LIMIT);
        boolean active = enabled && !Constants.CHARGE_LIMIT_FULL.equals(limit);

        tile.setLabel(getString(R.string.hypercharge_tile_label));
        if (active) {
            tile.setState(Tile.STATE_ACTIVE);
            tile.setIcon(Icon.createWithResource(this, R.drawable.ic_qs_hypercharge_on));
            tile.setSubtitle(getSpeedLabel(limit));
        } else {
            tile.setState(Tile.STATE_INACTIVE);
            tile.setIcon(Icon.createWithResource(this, R.drawable.ic_qs_hypercharge_off));
            tile.setSubtitle(enabled ? getString(R.string.hypercharge_speed_67w)
                    : getString(R.string.tile_off));
        }
        tile.updateTile();
    }

    private String getSpeedLabel(String limit) {
        switch (limit) {
            case Constants.CHARGE_LIMIT_50W:
                return getString(R.string.hypercharge_speed_50w);
            case Constants.CHARGE_LIMIT_33W:
                return getString(R.string.hypercharge_speed_33w);
            case Constants.CHARGE_LIMIT_30W:
                return getString(R.string.hypercharge_speed_30w);
            default:
                return getString(R.string.hypercharge_speed_67w);
        }
    }
}
