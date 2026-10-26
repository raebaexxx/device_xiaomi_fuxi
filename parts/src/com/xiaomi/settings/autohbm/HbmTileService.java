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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import androidx.preference.PreferenceManager;

import org.lineageos.settings.Constants;
import org.lineageos.settings.R;

public class HbmTileService extends TileService {

    private static final String NOTIFICATION_CHANNEL_ID = "hbm_tile_service_channel";
    private static final int NOTIFICATION_ID = 2;

    private NotificationManager mNotificationManager;
    private boolean mAutoHbmEnabled;

    @Override
    public void onCreate() {
        super.onCreate();
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        setupNotificationChannel();
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mAutoHbmEnabled = sharedPrefs.getBoolean(Constants.KEY_AUTO_HBM, false);
        if (mAutoHbmEnabled || !HbmUtils.isHbmEnabled()) {
            cancelHbmNotification(this);
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
            cancelHbmNotification(this);
        } else {
            HbmUtils.enableManual(this);
            showHbmNotification();
        }
        updateTile();
    }

    @Override
    public void onTileRemoved() {
        super.onTileRemoved();
        if (!mAutoHbmEnabled && HbmUtils.isHbmEnabled()) {
            HbmUtils.disableManual(this);
            cancelHbmNotification(this);
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

    private void setupNotificationChannel() {
        if (mNotificationManager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.hbm_mode_title),
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setBlockable(true);
        mNotificationManager.createNotificationChannel(channel);
    }

    private void showHbmNotification() {
        if (mNotificationManager == null) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle(getString(R.string.hbm_mode_title))
                .setContentText(getString(R.string.hbm_mode_notification))
                .setSmallIcon(R.drawable.ic_hbm_tile)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setFlag(Notification.FLAG_NO_CLEAR, true)
                .build();
        mNotificationManager.notify(NOTIFICATION_ID, notification);
    }

    public static void cancelHbmNotification(Context context) {
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancel(NOTIFICATION_ID);
        }
    }
}
