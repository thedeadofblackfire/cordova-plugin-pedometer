package fr.jebooj.plugins.pedometer;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

/**
 * Periodic worker carrying the **autonomous** sync — the whole point of this plugin: pushing the
 * recorded steps to the server without the application ever being opened.
 *
 * It replaces two things from the Cordova version:
 *
 * <ul>
 *   <li>the {@code AlarmManager} that re-started {@link StepsService} every couple of minutes. On
 *       Android 12+ starting a foreground service from a background alarm throws
 *       {@code ForegroundServiceStartNotAllowedException} — the old code already had to catch it.
 *       WorkManager is the supported way to run periodic background work, it survives reboots and
 *       respects Doze;</li>
 *   <li>the sync that {@code StepsService.updateIfNecessary()} fired on a bare {@link Thread}. The
 *       POST now runs on the worker's own thread, with a network constraint, and WorkManager retries
 *       it on failure instead of losing the batch until the next step event.</li>
 * </ul>
 *
 * The HTTP call itself is unchanged: {@link Database#syncData()} still posts to the URL stored in
 * the {@code settings} table by {@code configure()}.
 */
public class SyncWorker extends Worker {

    private static final String TAG = "capacitor-pedometer";

    /** Unique name, so re-enqueuing keeps a single periodic chain. */
    public static final String WORK_NAME = "fr.jebooj.pedometer.sync";

    /** Android refuses a period below 15 minutes for periodic work. */
    public static final long MIN_INTERVAL_MINUTES = 15;
    public static final long DEFAULT_INTERVAL_MINUTES = 15;

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Database db = null;
        try {
            db = Database.getInstance(getApplicationContext());

            // nothing configured yet: the app has not called configure() — not a failure
            String api = db.getConfig("api");
            if (api == null || api.isEmpty()) {
                Log.i(TAG, "SyncWorker: no api configured, skipping");
                return Result.success();
            }

            JSONObject response = db.syncData();
            Log.i(TAG, "SyncWorker: sync done response=" + (response == null ? "null" : response.toString()));
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "SyncWorker: sync failed, will retry", e);
            // WorkManager backs off and tries again; the rows stay unsynced meanwhile
            return Result.retry();
        } finally {
            if (db != null) {
                try {
                    db.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * Schedule (or reschedule) the periodic sync.
     *
     * @param intervalMinutes requested period; clamped to the 15 min Android floor
     */
    public static void schedule(final Context context, long intervalMinutes) {
        long interval = Math.max(MIN_INTERVAL_MINUTES, intervalMinutes <= 0 ? DEFAULT_INTERVAL_MINUTES : intervalMinutes);

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(SyncWorker.class, interval, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .addTag(WORK_NAME)
                .build();

        // UPDATE keeps the existing schedule when the interval did not change, so restarting the
        // service does not reset the countdown on every app launch
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request);

        Log.i(TAG, "SyncWorker: scheduled every " + interval + " min");
    }

    /** Stop the autonomous sync — used when the pedometer is switched off. */
    public static void cancel(final Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME);
        Log.i(TAG, "SyncWorker: cancelled");
    }
}
