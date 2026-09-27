package fr.jebooj.plugins.pedometer.util;

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
     * Channel of the service notification, IMPORTANCE_LOW: silent, but shown expanded in the shade
     * (title, text and progress bar of the daily goal) with its status bar icon.
     *
     * The channel used to be "Notification" at IMPORTANCE_MIN, which the system shows collapsed to
     * one line (title and bar, no text). The importance of an existing channel can only be lowered by
     * an app, never raised, hence a new id — the old channel is deleted once the notification left it.
     */
    public final static String NOTIFICATION_CHANNEL_ID = "pedometer_steps";
    private final static String NOTIFICATION_CHANNEL_NAME = "Podomètre";
    private final static String LEGACY_CHANNEL_ID = "Notification";

    private static boolean legacyChannelDeleted = false;

    public static void startForegroundService(final Context context, final Intent intent) {
        context.startForegroundService(intent);
    }

    public static Notification.Builder getNotificationBuilder(final Context context) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel =
                new NotificationChannel(NOTIFICATION_CHANNEL_ID, NOTIFICATION_CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_LOW);
        channel.enableLights(false);
        channel.enableVibration(false);
        channel.setBypassDnd(false);
        channel.setShowBadge(false);
        channel.setSound(null, null);
        manager.createNotificationChannel(channel);
        deleteLegacyChannel(manager);
        Notification.Builder builder = new Notification.Builder(context, NOTIFICATION_CHANNEL_ID);
        return builder;
    }

    /**
     * The system refuses to delete a channel while a foreground service notification still uses it
     * (SecurityException): that is the case on the first draw after the update, so this is retried
     * on the next draws until it succeeds.
     */
    private static void deleteLegacyChannel(final NotificationManager manager) {
        if (legacyChannelDeleted) return;
        try {
            if (manager.getNotificationChannel(LEGACY_CHANNEL_ID) != null) manager.deleteNotificationChannel(LEGACY_CHANNEL_ID);
            legacyChannelDeleted = true;
        } catch (Exception e) {
            // still attached to the running foreground notification; retried on the next draw
        }
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
