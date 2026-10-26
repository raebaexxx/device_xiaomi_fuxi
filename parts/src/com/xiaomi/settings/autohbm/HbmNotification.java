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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.lineageos.settings.R;

public final class HbmNotification {

    private static final String TAG = "HbmNotification";
    private static final String CHANNEL_ID = "hbm_tile_service_channel";
    private static final int NOTIFICATION_ID = 2;

    private HbmNotification() {
        // This class is not supposed to be instantiated
    }

    public static void show(Context context) {
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.hbm_mode_title),
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setBlockable(true);
        notificationManager.createNotificationChannel(channel);

        Intent intent = new Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(context, CHANNEL_ID)
                .setContentTitle(context.getString(R.string.hbm_mode_title))
                .setContentText(context.getString(R.string.hbm_mode_notification))
                .setSmallIcon(R.drawable.ic_hbm_tile)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setFlag(Notification.FLAG_NO_CLEAR, true)
                .build();
        try {
            notificationManager.notify(NOTIFICATION_ID, notification);
        } catch (SecurityException e) {
            Log.w(TAG, "Unable to post HBM notification", e);
        }
    }

    public static void cancel(Context context) {
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancel(NOTIFICATION_ID);
        }
    }
}
