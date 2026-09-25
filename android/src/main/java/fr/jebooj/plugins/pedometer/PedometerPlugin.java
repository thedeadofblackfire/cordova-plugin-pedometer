package fr.jebooj.plugins.pedometer;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.util.Pair;

import androidx.activity.result.ActivityResult;
import androidx.core.content.ContextCompat;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import fr.jebooj.plugins.pedometer.util.Util;

/**
 * Capacitor entry point of the pedometer — replaces the Cordova {@code PedoListener}.
 *
 * <p><b>What this plugin is for.</b> It counts steps with a foreground service and pushes them to
 * the server <b>on its own schedule, with the application closed</b>. That autonomy is the reason it
 * exists, so {@link Database#syncData()} and the {@code settings(userid, api)} table are kept: the
 * app configures the target through {@link #configure(PluginCall)} and never posts the data itself.
 *
 * <p><b>Both directions exist.</b> Beside the autonomous push, the app can read the local database
 * back at any time over a date range, synced rows included — {@link #getEntries(PluginCall)}.
 *
 * <p>Differences with the Cordova version, all deliberate:
 * <ul>
 *   <li>runtime permissions: {@code ACTIVITY_RECOGNITION} (Android 10+) and
 *       {@code POST_NOTIFICATIONS} (Android 13+) are now requested, they were not;</li>
 *   <li>step updates are delivered as a {@code stepsUpdate} <b>event</b> instead of a Cordova
 *       callback kept alive with {@code setKeepCallback};</li>
 *   <li>the sensor listener is bound on resume and released on pause, so the plugin no longer holds
 *       it while the app is backgrounded — the service does the counting there.</li>
 * </ul>
 */
@CapacitorPlugin(
    name = "Pedometer",
    permissions = {
        @Permission(alias = PedometerPlugin.ACTIVITY, strings = { Manifest.permission.ACTIVITY_RECOGNITION }),
        @Permission(alias = PedometerPlugin.NOTIFICATIONS, strings = { "android.permission.POST_NOTIFICATIONS" })
    }
)
public class PedometerPlugin extends Plugin implements SensorEventListener {

    private static final String TAG = "capacitor-pedometer";

    static final String ACTIVITY = "activity";
    static final String NOTIFICATIONS = "notifications";

    private static final int SENSOR_TYPE = Sensor.TYPE_STEP_COUNTER;

    private SensorManager sensorManager;
    private Sensor sensor;

    /** Steps already recorded today when the listener attached. */
    private int todayOffset;
    private int sinceBoot;
    private int totalStart;
    private int totalDays;
    private int goal;

    private boolean listening = false;

    // =============================================================================================
    // Availability and permissions
    // =============================================================================================

    @PluginMethod
    public void isAvailable(PluginCall call) {
        PackageManager pm = getContext().getPackageManager();
        boolean stepCounter = pm.hasSystemFeature(PackageManager.FEATURE_SENSOR_STEP_COUNTER);

        SensorManager manager = (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);
        boolean hasSensor = manager != null && manager.getDefaultSensor(SENSOR_TYPE) != null;

        JSObject result = new JSObject();
        result.put("available", stepCounter && hasSensor);
        result.put("stepCounter", stepCounter);
        call.resolve(result);
    }

    /**
     * ACTIVITY_RECOGNITION only exists from Android 10, POST_NOTIFICATIONS from Android 13.
     * Below those versions the base implementation reports "prompt" forever, which would make the
     * app think the permission was refused — so they are reported as granted.
     */
    @Override
    @PluginMethod
    public void checkPermissions(PluginCall call) {
        call.resolve(buildPermissionStatus());
    }

    @Override
    @PluginMethod
    public void requestPermissions(PluginCall call) {
        boolean needsActivity = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && getPermissionState(ACTIVITY) != PermissionState.GRANTED;
        boolean needsNotifications = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && getPermissionState(NOTIFICATIONS) != PermissionState.GRANTED;

        if (needsActivity) {
            requestPermissionForAlias(ACTIVITY, call, "permissionCallback");
            return;
        }
        if (needsNotifications) {
            requestPermissionForAlias(NOTIFICATIONS, call, "permissionCallback");
            return;
        }

        call.resolve(buildPermissionStatus());
    }

    @PermissionCallback
    private void permissionCallback(PluginCall call) {
        // the notification permission is asked in a second pass, once activity recognition is settled
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && getPermissionState(ACTIVITY) == PermissionState.GRANTED
                && getPermissionState(NOTIFICATIONS) != PermissionState.GRANTED) {
            requestPermissionForAlias(NOTIFICATIONS, call, "notificationPermissionCallback");
            return;
        }

        call.resolve(buildPermissionStatus());
    }

    @PermissionCallback
    private void notificationPermissionCallback(PluginCall call) {
        call.resolve(buildPermissionStatus());
    }

    private JSObject buildPermissionStatus() {
        JSObject status = new JSObject();

        // ACTIVITY_RECOGNITION did not exist before Android 10
        status.put("activity", Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? getPermissionState(ACTIVITY).toString() : PermissionState.GRANTED.toString());
        // POST_NOTIFICATIONS did not exist before Android 13
        status.put("notifications", Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ? getPermissionState(NOTIFICATIONS).toString() : PermissionState.GRANTED.toString());

        return status;
    }

    // =============================================================================================
    // Autonomous sync configuration (ex-setConfig)
    // =============================================================================================

    /**
     * Write the autonomous sync target into the plugin's SQLite settings.
     *
     * The service keeps posting to whatever is stored here even after the user logs out, so the app
     * must call this again whenever the user or the API base changes.
     */
    @PluginMethod
    public void configure(PluginCall call) {
        String userId = call.getString("userId");
        String apiUrl = call.getString("apiUrl");
        Integer interval = call.getInt("syncIntervalMinutes");

        Database db = Database.getInstance(getContext());
        try {
            if (userId != null) db.setConfig("userid", userId);
            if (apiUrl != null) db.setConfig("api", apiUrl);
            if (interval != null && interval > 0) db.setConfig("sync_interval_minutes", String.valueOf(interval));
        } finally {
            db.close();
        }

        // apply a changed interval immediately rather than at the next service start
        if (interval != null && interval > 0) SyncWorker.schedule(getContext(), interval);

        call.resolve();
    }

    @PluginMethod
    public void getConfig(PluginCall call) {
        Database db = Database.getInstance(getContext());
        JSObject result = new JSObject();
        try {
            result.put("userId", db.getConfig("userid"));
            result.put("apiUrl", db.getConfig("api"));

            String interval = db.getConfig("sync_interval_minutes");
            result.put("syncIntervalMinutes", interval == null ? SyncWorker.DEFAULT_INTERVAL_MINUTES : Long.parseLong(interval));
        } catch (NumberFormatException e) {
            result.put("syncIntervalMinutes", SyncWorker.DEFAULT_INTERVAL_MINUTES);
        } finally {
            db.close();
        }
        call.resolve(result);
    }

    // =============================================================================================
    // Service lifecycle
    // =============================================================================================

    @PluginMethod
    public void start(PluginCall call) {
        // the service cannot count without ACTIVITY_RECOGNITION; fail loudly rather than run blind
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && getPermissionState(ACTIVITY) != PermissionState.GRANTED) {
            call.reject("PERMISSION_DENIED: ACTIVITY_RECOGNITION is required to count steps");
            return;
        }

        SharedPreferences prefs = Prefs.get(getContext());
        SharedPreferences.Editor editor = prefs.edit();

        Integer startOffset = call.getInt("startOffset");
        if (startOffset != null) editor.putInt(Prefs.START_OFFSET, startOffset);

        Integer requestedGoal = call.getInt("goal");
        if (requestedGoal != null && requestedGoal > 0) editor.putInt(Prefs.GOAL_PREF_INT, requestedGoal);

        JSObject notification = call.getObject("notification");
        if (notification != null) applyNotificationStrings(editor, notification);

        editor.apply();

        Database db = Database.getInstance(getContext());
        String interval = db.getConfig("sync_interval_minutes");
        db.setConfig(ServiceControl.STATUS_KEY, ServiceControl.STATUS_START);
        db.close();

        try {
            Intent intent = new Intent(getContext(), StepsService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(getContext(), intent);
            } else {
                getContext().startService(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "start: could not start the foreground service", e);
            call.reject("SERVICE_START_FAILED: " + e.getMessage());
            return;
        }

        // the autonomous sync is what makes the plugin useful with the app closed
        long minutes = SyncWorker.DEFAULT_INTERVAL_MINUTES;
        try {
            if (interval != null && !interval.isEmpty()) minutes = Long.parseLong(interval);
        } catch (NumberFormatException ignored) {
        }
        SyncWorker.schedule(getContext(), minutes);

        attachSensor();
        call.resolve();
    }

    @PluginMethod
    public void stop(PluginCall call) {
        detachSensor();

        Database db = Database.getInstance(getContext());
        db.setConfig(ServiceControl.STATUS_KEY, ServiceControl.STATUS_STOP);
        db.close();

        getContext().stopService(new Intent(getContext(), StepsService.class));
        SyncWorker.cancel(getContext());

        call.resolve();
    }

    @PluginMethod
    public void getStatus(PluginCall call) {
        // getConfig() returns "" for a missing key: a pedometer never started is "stopped", not
        // "running"; a revoked ACTIVITY_RECOGNITION means nothing is counted, so "stopped" too
        JSObject result = new JSObject();
        result.put("status", ServiceControl.shouldRun(getContext()) ? "running" : "stopped");
        call.resolve(result);
    }

    // =============================================================================================
    // Settings
    // =============================================================================================

    @PluginMethod
    public void setGoal(PluginCall call) {
        Integer value = call.getInt("goal");
        if (value == null || value <= 0) {
            call.reject("INVALID_GOAL: goal must be a positive number");
            return;
        }

        goal = value;
        Prefs.get(getContext()).edit().putInt(Prefs.GOAL_PREF_INT, value).apply();
        call.resolve();
    }

    @PluginMethod
    public void setNotificationStrings(PluginCall call) {
        SharedPreferences.Editor editor = Prefs.get(getContext()).edit();
        applyNotificationStrings(editor, call.getData());
        editor.apply();
        call.resolve();
    }

    private void applyNotificationStrings(SharedPreferences.Editor editor, JSONObject source) {
        if (source == null) return;

        String isCounting = source.optString("isCounting", null);
        if (isCounting != null) editor.putString(Prefs.PEDOMETER_IS_COUNTING_TEXT, isCounting);

        String stepsToGo = source.optString("stepsToGoFormat", null);
        if (stepsToGo != null) editor.putString(Prefs.PEDOMETER_STEPS_TO_GO_FORMAT_TEXT, stepsToGo);

        String progress = source.optString("yourProgressFormat", null);
        if (progress != null) editor.putString(Prefs.PEDOMETER_YOUR_PROGRESS_FORMAT_TEXT, progress);

        String goalReached = source.optString("goalReachedFormat", null);
        if (goalReached != null) editor.putString(Prefs.PEDOMETER_GOAL_REACHED_FORMAT_TEXT, goalReached);
    }

    // =============================================================================================
    // Reading the local database
    // =============================================================================================

    @PluginMethod
    public void getSteps(PluginCall call) {
        Long date = call.getLong("date");
        if (date == null) date = Util.getToday();

        Database db = Database.getInstance(getContext());
        int steps = db.getSteps(date);
        db.close();

        JSObject result = new JSObject();
        result.put("steps", steps == Integer.MIN_VALUE ? 0 : steps);
        call.resolve(result);
    }

    @PluginMethod
    public void getStepsByPeriod(PluginCall call) {
        Long start = call.getLong("start");
        Long end = call.getLong("end");
        if (start == null || end == null) {
            call.reject("INVALID_RANGE: start and end are required");
            return;
        }

        Database db = Database.getInstance(getContext());
        int steps = db.getSteps(start, end);
        db.close();

        JSObject result = new JSObject();
        result.put("steps", steps == Integer.MIN_VALUE ? 0 : steps);
        call.resolve(result);
    }

    @PluginMethod
    public void getLastEntries(PluginCall call) {
        Integer count = call.getInt("count");
        if (count == null || count <= 0) count = 7;

        Database db = Database.getInstance(getContext());
        List<Pair<Long, Integer>> entries = db.getLastEntries(count);
        db.close();

        JSArray array = new JSArray();
        for (Pair<Long, Integer> entry : entries) {
            JSObject row = new JSObject();
            row.put("date", entry.first);
            row.put("steps", entry.second);
            array.put(row);
        }

        JSObject result = new JSObject();
        result.put("entries", array);
        call.resolve(result);
    }

    /**
     * Rows over a date range, <b>synced or not</b>.
     *
     * This is the read half of the contract: the service pushes on its own, and the app can still
     * inspect what the device actually recorded — including what is still pending.
     */
    @PluginMethod
    public void getEntries(PluginCall call) {
        Long startArg = call.getLong("start");
        Long endArg = call.getLong("end");
        Integer limit = call.getInt("limit");

        long start = startArg == null ? 0L : startArg;
        long end = endArg == null ? 0L : endArg;

        int synced = syncFilterFrom(call.getString("synced", "all"));

        Database db = Database.getInstance(getContext());
        JSONArray entries = db.getEntries(start, end, synced, limit == null ? 0 : limit);
        db.close();

        JSObject result = new JSObject();
        try {
            result.put("entries", entries);
        } catch (Exception e) {
            call.reject("READ_FAILED: " + e.getMessage());
            return;
        }
        call.resolve(result);
    }

    /** `all` (default) reads every row; the other values map to the `synced` column. */
    private int syncFilterFrom(String value) {
        if ("pending".equals(value)) return Database.SYNC_PENDING;
        if ("queued".equals(value)) return Database.SYNC_QUEUED;
        if ("synced".equals(value)) return Database.SYNC_SYNCED;
        return -1;
    }

    // =============================================================================================
    // Sync
    // =============================================================================================

    /**
     * Force a flush now. Optional: the service syncs on its own with the app closed; this only
     * shortens the wait when the user is looking at their steps.
     *
     * Runs off the main thread — it performs a blocking HTTP request.
     */
    @PluginMethod
    public void sync(final PluginCall call) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Database db = Database.getInstance(getContext());
                try {
                    int before = db.countBySyncState(Database.SYNC_PENDING);
                    db.syncData();
                    int after = db.countBySyncState(Database.SYNC_PENDING);

                    JSObject result = new JSObject();
                    result.put("sent", Math.max(0, before - after));
                    result.put("pending", after);

                    long lastSync = db.getLastSyncedAt();
                    result.put("lastSyncAt", lastSync > 0 ? lastSync : null);

                    call.resolve(result);
                } catch (Exception e) {
                    Log.e(TAG, "sync failed", e);
                    call.reject("SYNC_FAILED: " + e.getMessage());
                } finally {
                    db.close();
                }
            }
        }).start();
    }

    @PluginMethod
    public void getSyncStatus(PluginCall call) {
        Database db = Database.getInstance(getContext());
        try {
            JSObject result = new JSObject();
            result.put("sent", db.countBySyncState(Database.SYNC_SYNCED));
            result.put("pending", db.countBySyncState(Database.SYNC_PENDING));

            long lastSync = db.getLastSyncedAt();
            result.put("lastSyncAt", lastSync > 0 ? lastSync : null);

            call.resolve(result);
        } finally {
            db.close();
        }
    }

    // =============================================================================================
    // Battery optimisation
    // =============================================================================================

    /**
     * OEM "protected apps / autostart" screen when the manufacturer has one; otherwise the system
     * battery optimisation list. `BatteryOptimizationUtil` only knows OEM screens: on a Pixel or any
     * stock Android the dialog is null, and this method used to do nothing at all.
     */
    @PluginMethod
    public void openBatteryOptimizationSettings(final PluginCall call) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    AlertDialog dialog = BatteryOptimizationUtil.getBatteryOptimizationDialog(getActivity());
                    if (dialog != null) {
                        dialog.show();
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        getActivity().startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                    }
                    call.resolve();
                } catch (Exception e) {
                    call.reject("SETTINGS_FAILED: " + e.getMessage());
                }
            }
        });
    }

    // =============================================================================================
    // Debug
    // =============================================================================================

    /**
     * Snapshot of the local SQLite database ({@code steps.db}), for inspection in a SQLite viewer.
     *
     * The WAL is checkpointed first (Android enables write-ahead logging by default on 9+), otherwise
     * the copy would miss the rows still sitting in {@code steps.db-wal}. The copy lands in the cache,
     * under {@code pedometer-export/} — covered by the app's FileProvider {@code cache-path}, so the
     * returned {@code uri} can be handed to {@code @capacitor/share}. Only the latest export is kept.
     */
    @PluginMethod
    public void exportDatabase(PluginCall call) {
        File source = getContext().getDatabasePath(Database.DATABASE_NAME);
        if (!source.exists()) {
            call.reject("EXPORT_FAILED: " + Database.DATABASE_NAME + " does not exist yet");
            return;
        }

        File dir = new File(getContext().getCacheDir(), "pedometer-export");
        String name = "steps-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".db";
        File target = new File(dir, name);

        // the reference is held for the whole copy: no other close() can shut the helper meanwhile
        Database db = Database.getInstance(getContext());
        try {
            Cursor checkpoint = db.getWritableDatabase().rawQuery("PRAGMA wal_checkpoint(FULL)", null);
            checkpoint.moveToFirst();
            checkpoint.close();

            if (!dir.exists() && !dir.mkdirs()) throw new java.io.IOException("cannot create " + dir);
            File[] previous = dir.listFiles();
            if (previous != null) for (File file : previous) file.delete();

            try (InputStream in = new FileInputStream(source); OutputStream out = new FileOutputStream(target)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            }
        } catch (Exception e) {
            Log.e(TAG, "exportDatabase failed", e);
            call.reject("EXPORT_FAILED: " + e.getMessage());
            return;
        } finally {
            db.close();
        }

        JSObject result = new JSObject();
        result.put("name", name);
        result.put("path", target.getAbsolutePath());
        result.put("uri", Uri.fromFile(target).toString());
        result.put("size", target.length());
        call.resolve(result);
    }

    @PluginMethod
    public void getBatteryOptimizationStatus(PluginCall call) {
        call.resolve(buildBatteryStatus());
    }

    /**
     * The standard "Allow the app to always run in the background?" system dialog.
     *
     * Being exempt is what makes the counting permanent: an exempt app may start its foreground
     * service from the background, so START_STICKY and onTaskRemoved can bring the service back
     * after its process was killed. Needs REQUEST_IGNORE_BATTERY_OPTIMIZATIONS (plugin manifest) —
     * a Play Console declaration: step counting in the background is the app's core feature.
     */
    @PluginMethod
    public void requestIgnoreBatteryOptimizations(PluginCall call) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || isIgnoringBatteryOptimizations()) {
            call.resolve(buildBatteryStatus());
            return;
        }

        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getContext().getPackageName()));
        try {
            startActivityForResult(call, intent, "batteryOptimizationResult");
        } catch (ActivityNotFoundException e) {
            // some OEM builds drop the dialog: fall back to the system list
            try {
                getActivity().startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception ignored) {
            }
            call.resolve(buildBatteryStatus());
        }
    }

    @ActivityCallback
    private void batteryOptimizationResult(PluginCall call, ActivityResult result) {
        if (call == null) return;
        // the dialog's result code is unreliable across OEMs: read the actual state instead
        call.resolve(buildBatteryStatus());
    }

    private boolean isIgnoringBatteryOptimizations() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        PowerManager pm = (PowerManager) getContext().getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(getContext().getPackageName());
    }

    private JSObject buildBatteryStatus() {
        JSObject status = new JSObject();
        status.put("ignoring", isIgnoringBatteryOptimizations());
        status.put("oemSettingsAvailable", BatteryOptimizationUtil.isBatteryOptimizationAvailable(getContext()));
        return status;
    }

    // =============================================================================================
    // Live updates while the app is in the foreground
    // =============================================================================================

    @Override
    protected void handleOnResume() {
        super.handleOnResume();
        // only when the user actually started the pedometer
        if (ServiceControl.shouldRun(getContext())) {
            // bring the service back if it was killed and could not restart itself (OEM killer, user
            // "force stop", battery optimisation). The app is in the foreground, so the start is
            // always allowed; on a running service it only re-runs onStartCommand.
            ServiceControl.startIfEnabled(getContext(), "app resumed");
            attachSensor();
        }
    }

    @Override
    protected void handleOnPause() {
        super.handleOnPause();
        detachSensor();
    }

    /**
     * Bind the step counter so the UI updates live. The service keeps counting in the background:
     * this listener only exists to emit {@code stepsUpdate} while a screen is visible.
     */
    private void attachSensor() {
        if (listening) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && getPermissionState(ACTIVITY) != PermissionState.GRANTED) return;

        sensorManager = (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager == null) return;

        sensor = sensorManager.getDefaultSensor(SENSOR_TYPE);
        if (sensor == null) {
            Log.w(TAG, "attachSensor: no TYPE_STEP_COUNTER on this device");
            return;
        }

        Database db = Database.getInstance(getContext());
        SharedPreferences prefs = Prefs.get(getContext());

        todayOffset = db.getSteps(Util.getToday());
        goal = prefs.getInt(Prefs.GOAL_PREF_INT, Prefs.DEFAULT_GOAL);
        // legacy `initSensor`: the steps counted while the service was paused must not be replayed
        sinceBoot = db.getCurrentSteps();
        int pauseDifference = sinceBoot - prefs.getInt(Prefs.PAUSE_COUNT, sinceBoot);
        sinceBoot -= pauseDifference;
        totalStart = db.getTotalWithoutToday();
        totalDays = db.getDays();

        db.close();

        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI, 0);
        listening = true;
    }

    private void detachSensor() {
        if (!listening) return;

        try {
            if (sensorManager != null) sensorManager.unregisterListener(this);
        } catch (Exception e) {
            Log.w(TAG, "detachSensor", e);
        }

        Database db = Database.getInstance(getContext());
        db.saveCurrentSteps(sinceBoot);
        db.close();

        listening = false;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != SENSOR_TYPE) return;
        if (event.values[0] > Integer.MAX_VALUE || event.values[0] == 0) return;

        if (todayOffset == Integer.MIN_VALUE) {
            // no row for today yet: we do not know when the reboot happened, so today starts at
            // -stepsSinceBoot — same reasoning as the Cordova version
            todayOffset = -(int) event.values[0];

            Database db = Database.getInstance(getContext());
            db.insertNewDay(Util.getToday(), (int) event.values[0]);
            db.close();
        }

        sinceBoot = (int) event.values[0];
        emitStepsUpdate();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // nothing to do: a step counter does not report accuracy changes
    }

    private void emitStepsUpdate() {
        int stepsToday = Math.max(todayOffset + sinceBoot, 0);
        int total = totalStart + stepsToday;
        // guard kept from the legacy: total_days was 0 on a fresh install and divided by zero
        int days = totalDays <= 0 ? 1 : totalDays;

        JSObject event = new JSObject();
        event.put("stepsToday", stepsToday);
        event.put("total", total);
        event.put("average", total / days);
        event.put("timestamp", System.currentTimeMillis());

        notifyListeners("stepsUpdate", event);
    }
}
