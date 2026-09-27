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

import fr.jebooj.plugins.pedometer.Prefs;

@TargetApi(Build.VERSION_CODES.O)
public class API26Wrapper {

    /**
     * Channel of the service notification, chosen by the {@code notificationImportance} preference
     * ({@link fr.jebooj.plugins.pedometer.Prefs#NOTIFICATION_IMPORTANCE}, set by the app):
     * <ul>
     *   <li>{@code "low"} (default): channel {@code pedometer_steps}, IMPORTANCE_LOW — silent, but
     *       shown expanded in the shade (title, text and progress bar) with its status bar icon;</li>
     *   <li>{@code "min"}: the former channel {@code Notification}, IMPORTANCE_MIN — collapsed to one
     *       line (title and bar, no text), no status bar icon.</li>
     * </ul>
     * An app can lower the importance of an existing channel but never raise it, hence one channel
     * per level. The unused one is deleted once the notification left it (a deleted channel is
     * restored with its former settings if the app switches back).
     */
    public final static String CHANNEL_LOW_ID = "pedometer_steps";
    public final static String CHANNEL_MIN_ID = "Notification";
    private final static String CHANNEL_LOW_NAME = "Podomètre";

    public final static String IMPORTANCE_LOW = "low";
    public final static String IMPORTANCE_MIN = "min";

    /** channel id already cleaned up in this process, to avoid retrying on every draw */
    private static String deletedChannelId = null;

    public static void startForegroundService(final Context context, final Intent intent) {
        context.startForegroundService(intent);
    }

    /** "min" or "low" (default, also for any unknown value). */
    public static String getImportance(final Context context) {
        String value = Prefs.get(context).getString(Prefs.NOTIFICATION_IMPORTANCE, IMPORTANCE_LOW);
        return IMPORTANCE_MIN.equals(value) ? IMPORTANCE_MIN : IMPORTANCE_LOW;
    }

    public static String getChannelId(final Context context) {
        return IMPORTANCE_MIN.equals(getImportance(context)) ? CHANNEL_MIN_ID : CHANNEL_LOW_ID;
    }

    public static Notification.Builder getNotificationBuilder(final Context context) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        boolean min = IMPORTANCE_MIN.equals(getImportance(context));
        String channelId = min ? CHANNEL_MIN_ID : CHANNEL_LOW_ID;

        NotificationChannel channel = min
                // same id, name and importance as the Cordova version, so an update keeps its settings
                ? new NotificationChannel(CHANNEL_MIN_ID, CHANNEL_MIN_ID, NotificationManager.IMPORTANCE_MIN)
                : new NotificationChannel(CHANNEL_LOW_ID, CHANNEL_LOW_NAME, NotificationManager.IMPORTANCE_LOW);
        channel.enableLights(false);
        channel.enableVibration(false);
        channel.setBypassDnd(false);
        if (!min) channel.setShowBadge(false);
        channel.setSound(null, null);
        manager.createNotificationChannel(channel);

        deleteUnusedChannel(manager, min ? CHANNEL_LOW_ID : CHANNEL_MIN_ID);
        return new Notification.Builder(context, channelId);
    }

    /**
     * The system refuses to delete a channel while a foreground service notification still uses it
     * (SecurityException): that is the case on the first draw after a switch, so it is retried on the
     * next draws until it succeeds.
     */
    private static void deleteUnusedChannel(final NotificationManager manager, final String unusedId) {
        if (unusedId.equals(deletedChannelId)) return;
        try {
            if (manager.getNotificationChannel(unusedId) != null) manager.deleteNotificationChannel(unusedId);
            deletedChannelId = unusedId;
        } catch (Exception e) {
            // still attached to the running foreground notification; retried on the next draw
        }
    }

    public static void launchNotificationSettings(final Context context) {
        Intent intent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
        intent.putExtra(Settings.EXTRA_CHANNEL_ID, getChannelId(context));
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
