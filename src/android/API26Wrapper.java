/*
 * Copyright 2016 Thomas Hoffmann
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.cordova.pedometer;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

@TargetApi(Build.VERSION_CODES.O)
public class API26Wrapper {

    /**
     * The identifier for the notification displayed for the foreground service.
     */
    private static final int NOTIFICATION_ID = 12345678;


    public final static String NOTIFICATION_CHANNEL_ID = "Notification";

    public static void startForegroundService(final Context context, final Intent intent) {
        context.startForegroundService(intent);
        //We only need to call this for SDK 26+, since startForeground always has to be called after startForegroundService.
        startForeground(NOTIFICATION_ID, getNotificationBuilder(context).build()); //getNotification, try to fix bug on samsung
    }

    // error on samsung note 9 > api 28
    //06-19 16:22:13.142: E/ActivityManager(4940): Reason: Context.startForegroundService() did not then call Service.startForeground(): ServiceRecord{caa6dcf u0 fr.jebooj.app/org.apache.cordova.pedometer.StepsService}
    // https://stackoverflow.com/questions/55136846/android-how-to-use-startforegroundservice-and-startforeground-for-api-28
    //https://github.com/googlesamples/android-play-location/blob/master/LocationUpdatesForegroundService/app/src/main/java/com/google/android/gms/location/sample/locationupdatesforegroundservice/LocationUpdatesService.java

    public static Notification.Builder getNotificationBuilder(final Context context) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel =
                new NotificationChannel(NOTIFICATION_CHANNEL_ID, NOTIFICATION_CHANNEL_ID,
                        NotificationManager.IMPORTANCE_NONE);
        channel.setImportance(NotificationManager.IMPORTANCE_MIN); // ignored by Android O ... => IMPORTANCE_LOW
        channel.enableLights(false);
        channel.enableVibration(false);
        channel.setBypassDnd(false);
        channel.setSound(null, null);
        manager.createNotificationChannel(channel);
        Notification.Builder builder = new Notification.Builder(context, NOTIFICATION_CHANNEL_ID);
        return builder;
    }

    public static void launchNotificationSettings(final Context context) {
        Intent intent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
        intent.putExtra(Settings.EXTRA_CHANNEL_ID, NOTIFICATION_CHANNEL_ID);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context,
                    "Settings not found - please search for the notification settings in the Android settings manually",
                    Toast.LENGTH_LONG).show();
        }
    }

}