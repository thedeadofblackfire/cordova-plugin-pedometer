package fr.jebooj.plugins.pedometer;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import fr.jebooj.plugins.pedometer.util.Util;

/**
 * Redraws the notification right after local midnight, so the daily goal visibly starts again
 * from 0 even when the user takes no step — otherwise the bar stayed full until the next sensor
 * event of the new day.
 *
 * <p>The alarm is <b>inexact</b> ({@code setAndAllowWhileIdle}): a few minutes of delay in Doze
 * are fine for a redraw, and it needs no {@code SCHEDULE_EXACT_ALARM}. It is re-armed on every
 * service start (so after a reboot too) and after each run; clock and timezone changes are caught
 * by the service's dynamic receiver, which calls {@link #schedule(Context)} again.
 */
public class MidnightReceiver extends BroadcastReceiver {

    private static final String TAG = "capacitor-pedometer";

    static final String ACTION = "fr.jebooj.plugins.pedometer.MIDNIGHT";

    /** after midnight, so Util.getToday() already returns the new day */
    private static final long DELAY_AFTER_MIDNIGHT = 5000;

    @Override
    public void onReceive(final Context context, final Intent intent) {
        if (!ServiceControl.shouldRun(context)) return;
        Log.i(TAG, "MidnightReceiver - new day, redrawing the notification");
        StepsService.refreshNotification(context);
        schedule(context);
    }

    public static void schedule(final Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        // Util.getTomorrow() is 0:00:01 of the next local day, DST included
        long at = Util.getTomorrow() + DELAY_AFTER_MIDNIGHT;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent(context));
            } else {
                am.set(AlarmManager.RTC_WAKEUP, at, pendingIntent(context));
            }
        } catch (Exception e) {
            Log.w(TAG, "MidnightReceiver - could not schedule the midnight redraw: " + e);
        }
    }

    public static void cancel(final Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(pendingIntent(context));
    }

    private static PendingIntent pendingIntent(final Context context) {
        Intent intent = new Intent(context, MidnightReceiver.class).setAction(ACTION);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(context, 0, intent, flags);
    }
}
