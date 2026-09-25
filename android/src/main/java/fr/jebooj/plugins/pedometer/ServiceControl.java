package fr.jebooj.plugins.pedometer;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.content.ContextCompat;

/**
 * Single answer to "should the step service be running?", shared by the plugin, the service and
 * the system receivers (boot, app update, shutdown).
 *
 * Two conditions, both required:
 * <ul>
 *   <li>the user started the pedometer — {@code status_service = "start"} in the settings table.
 *   {@link Database#getConfig(String)} returns {@code ""} (never {@code null}) for a missing key, so
 *   the former {@code status != null && !"stop".equals(status)} checks read "never started" as
 *   "running": the receivers started the service on every app update for users who never enabled it;</li>
 *   <li>{@code ACTIVITY_RECOGNITION} is granted (Android 10+). Without it the step counter delivers no
 *   event, and on Android 14+ {@code startForeground(…, FOREGROUND_SERVICE_TYPE_HEALTH)} throws — the
 *   service then runs as a plain background service that counts nothing until the system kills it.</li>
 * </ul>
 *
 * Gating happens <b>before</b> {@code startForegroundService()}: once that call is made the service
 * must reach {@code startForeground()}, stopping it earlier crashes the app.
 */
public final class ServiceControl {

    private static final String TAG = "capacitor-pedometer";

    public static final String STATUS_KEY = "status_service";
    public static final String STATUS_START = "start";
    public static final String STATUS_STOP = "stop";

    private ServiceControl() {
    }

    /** True only when the user explicitly started the pedometer (and did not stop it since). */
    public static boolean isEnabled(final Context context) {
        Database db = Database.getInstance(context);
        try {
            return STATUS_START.equals(db.getConfig(STATUS_KEY));
        } finally {
            db.close();
        }
    }

    /** ACTIVITY_RECOGNITION only exists from Android 10; below that it is implicitly granted. */
    public static boolean hasActivityPermission(final Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean shouldRun(final Context context) {
        return isEnabled(context) && hasActivityPermission(context);
    }

    /**
     * Start the service if, and only if, {@link #shouldRun(Context)}. Never throws: a receiver must not
     * crash the process over a refused start — START_STICKY and WorkManager still carry the sync.
     *
     * @param reason logged, to tell boot / update / task-removal restarts apart in logcat
     * @return whether a start was attempted
     */
    public static boolean startIfEnabled(final Context context, final String reason) {
        if (!isEnabled(context)) {
            Log.i(TAG, "ServiceControl [" + reason + "] - pedometer not started by the user, skipping");
            return false;
        }
        if (!hasActivityPermission(context)) {
            Log.w(TAG, "ServiceControl [" + reason + "] - ACTIVITY_RECOGNITION not granted, skipping");
            return false;
        }

        try {
            ContextCompat.startForegroundService(context, new Intent(context, StepsService.class));
            Log.i(TAG, "ServiceControl [" + reason + "] - service started");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "ServiceControl [" + reason + "] - could not start the service", e);
            return false;
        }
    }
}
