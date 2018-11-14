package org.apache.cordova.pedometer;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import android.widget.Toast;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.support.annotation.Nullable;

import java.text.NumberFormat;
import java.util.Date;
import java.util.Locale;

import org.json.JSONObject;

import android.util.Log;
import org.apache.cordova.pedometer.Logger;

public class StepsService extends Service implements SensorEventListener {

    private static final String TAG = "cordova-plugin-pedometer";

    public final static int NOTIFICATION_ID = 1;
    private final static long MAX_REPORT_LATENCY_MINUTE = 1; // 5
    private final static long MICROSECONDS_IN_ONE_MINUTE = 60000000;
    private final static long SAVE_OFFSET_TIME = AlarmManager.INTERVAL_HOUR;
    private final static int SAVE_OFFSET_STEPS = 2; //500;
    private final static long RESTART_SERVICE_OFFSET_TIME = 120000;

    private SensorManager mSensorManager;
    private Sensor mStepDetectorSensor;
    private StepsDBHelper mStepsDBHelper;

    private static int steps;
    private static int lastSaveSteps;
    private static long lastSaveTime;

    private final BroadcastReceiver shutdownReceiver = new ShutdownReceiver();

    @Override
    public void onCreate() {
        super.onCreate();

        Log.i(TAG, "StepsService onCreate");
        mStepsDBHelper = new StepsDBHelper(this);
        Log.i(TAG, "StepsService onCreate end");
        /*
         * mSensorManager = (SensorManager)
         * this.getSystemService(Context.SENSOR_SERVICE); if
         * (mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null) {
         * mStepDetectorSensor =
         * mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
         * mSensorManager.registerListener(this, mStepDetectorSensor,
         * SensorManager.SENSOR_DELAY_NORMAL); mStepsDBHelper = new StepsDBHelper(this);
         * Log.i(TAG, "StepsService onCreate end"); // mStepsDBHelper =
         * StepsDBHelper.getInstance(this); }
         */
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "StepsService [onStartCommand]");
        // Toast.makeText(this, "StepsService Service started...",
        // Toast.LENGTH_LONG).show();

        reRegisterSensor();
        registerBroadcastReceiver();
        /*
         * if (!updateIfNecessary()) { showNotification(); }
         */

        // restart service every hour to save the current step count
        long nextUpdate = Math.min(StepsUtil.getTomorrow(), System.currentTimeMillis() + RESTART_SERVICE_OFFSET_TIME); // INTERVAL_HOUR
                                                                                                  // INTERVAL_HALF_HOUR=1800000
                                                                                                  // AlarmManager.INTERVAL_FIFTEEN_MINUTES=900000
        Log.i(TAG, "StepsService [onStartCommand] - next update: " + new Date(nextUpdate).toLocaleString());
        // if (BuildConfig.DEBUG) Logger.log("next update: " + new
        // Date(nextUpdate).toLocaleString());
        AlarmManager am = (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = PendingIntent.getService(getApplicationContext(), 2, new Intent(this, StepsService.class),
                PendingIntent.FLAG_UPDATE_CURRENT);

        Log.i(TAG, "StepsService [onStartCommand] - Build.VERSION.SDK_INT=" + Build.VERSION.SDK_INT);
        if (Build.VERSION.SDK_INT >= 23) {
            Log.i(TAG, "StepsService [onStartCommand] - API23Wrapper.setAlarmWhileIdle");
            API23Wrapper.setAlarmWhileIdle(am, AlarmManager.RTC, nextUpdate, pi);
        } else {
            am.set(AlarmManager.RTC, nextUpdate, pi);
        }

        /*
         * sensorManager = (SensorManager) getApplicationContext()
         * .getSystemService(SENSOR_SERVICE); lastUpdate = System.currentTimeMillis();
         * listen = new SensorListen();
         */
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "StepsService onDestroy");
        // Toast.makeText(this, "StepsService Stop service...",
        // Toast.LENGTH_LONG).show();
        // sensorManager.unregisterListener(listen);
        // Toast.makeText(this, "Destroy", Toast.LENGTH_SHORT).show();
        // if (BuildConfig.DEBUG) Logger.log("SensorListener onDestroy");
        try {
            unregisterReceiver(shutdownReceiver);

            SensorManager sm = (SensorManager) getSystemService(SENSOR_SERVICE);
            sm.unregisterListener(this);

        } catch (Exception e) {
            // if (BuildConfig.DEBUG) Logger.log(e);
            Log.i(TAG, e.toString());
            e.printStackTrace();
        }
    }

    // @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onTaskRemoved(final Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        // if (BuildConfig.DEBUG) Logger.log("sensor service task removed");
        //Log.i(TAG, "StepsService [onTaskRemoved] - sensor service task removed");
        Logger.log("StepsService [onTaskRemoved] - sensor service task removed");

        // Restart service in 500 ms
        ((AlarmManager) getSystemService(Context.ALARM_SERVICE)).set(AlarmManager.RTC, System.currentTimeMillis() + 500,
                PendingIntent.getService(this, 3, new Intent(this, StepsService.class), 0));
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // Only look at step counter events
        if (event.sensor.getType() != Sensor.TYPE_STEP_COUNTER) {
            return;
        }

        // Log.i(TAG, "StepsService [onSensorChanged] - start - type=" +
        // event.sensor.getType());
        Logger.log("StepsService [onSensorChanged] - start - type=" + event.sensor.getType());

        if (event.values[0] > Integer.MAX_VALUE) {
            if (StepsUtil.isDebug())
                Logger.log("StepsService [onSensorChanged] - probably not a real value: " + event.values[0]);
            // if (BuildConfig.DEBUG) Logger.log("probably not a real value: " +
            // event.values[0]);
            // Log.i(TAG, "StepsService [onSensorChanged] - probably not a real value: " +
            // event.values[0]);
            return;
        } else {
            // float steps = event.values[0];
            steps = (int) event.values[0];
            updateIfNecessary();
            // mStepsDBHelper.createStepsEntryValue(steps);
            // Log.i(TAG, "StepsService [onSensorChanged] - end - steps=" + steps);
            Logger.log("StepsService [onSensorChanged] - end - steps=" + steps);
        }
    }

    /**
     * @return true, if notification was updated
     */
    private boolean updateIfNecessary() {
        mStepsDBHelper.createStepsEntryValue(steps);

        Database db = Database.getInstance(this);
        db.createStepsEntryValue(steps);

        if (steps > lastSaveSteps + SAVE_OFFSET_STEPS
                || (steps > 0 && System.currentTimeMillis() > lastSaveTime + SAVE_OFFSET_TIME)) {
            if (StepsUtil.isDebug())
                Logger.log("StepsService [updateIfNecessary] - saving steps: steps=" + steps + " lastSave=" + lastSaveSteps + " lastSaveTime="
                        + new Date(lastSaveTime));
                        /*
            //Database db = Database.getInstance(this);
            if (db.getSteps(StepsUtil.getToday()) == Integer.MIN_VALUE) {
                int pauseDifference = steps
                        - getSharedPreferences("pedometer", Context.MODE_PRIVATE).getInt("pauseCount", steps);
                db.insertNewDay(StepsUtil.getToday(), steps - pauseDifference);
                if (pauseDifference > 0) {
                    // update pauseCount for the new day
                    getSharedPreferences("pedometer", Context.MODE_PRIVATE).edit().putInt("pauseCount", steps).commit();
                }                
            }
            
            db.saveCurrentSteps(steps);
            db.close();
            */
            lastSaveSteps = steps;
            lastSaveTime = System.currentTimeMillis();
            /*
            showNotification(); // update notification
            startService(new Intent(this, WidgetUpdateService.class));
            */
            return true;
        } else {
            return false;
        }

        //return true;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // nobody knows what happens here: step value might magically decrease
        // when this method is called...
        Log.i(TAG, sensor.getName() + " accuracy changed: " + accuracy);
        // if (BuildConfig.DEBUG) Logger.log(sensor.getName() + " accuracy changed: " +
        // accuracy);
    }

    private void registerBroadcastReceiver() {
        // if (BuildConfig.DEBUG) Logger.log("register broadcastreceiver");
        Log.i(TAG, "StepsService [registerBroadcastReceiver] - register broadcastreceiver");
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SHUTDOWN);
        registerReceiver(shutdownReceiver, filter);
    }

    private void reRegisterSensor() {
        // if (BuildConfig.DEBUG) Logger.log("re-register sensor listener");
        // SensorManager sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        Log.i(TAG, "StepsService [reRegisterSensor] - re-register sensor listener");
        SensorManager sm = (SensorManager) this.getSystemService(Context.SENSOR_SERVICE);

        try {
            sm.unregisterListener(this);
        } catch (Exception e) {
            // if (BuildConfig.DEBUG) Logger.log(e);
            Log.i(TAG, e.toString());
            e.printStackTrace();
        }

        Log.i(TAG,
                "StepsService [reRegisterSensor] - step sensors: " + sm.getSensorList(Sensor.TYPE_STEP_COUNTER).size());
        if (sm.getSensorList(Sensor.TYPE_STEP_COUNTER).size() < 1)
            return; // emulator
        Log.i(TAG, "StepsService [reRegisterSensor] - default: "
                + sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER).getName());

        /*
         * if (BuildConfig.DEBUG) { Logger.log("step sensors: " +
         * sm.getSensorList(Sensor.TYPE_STEP_COUNTER).size()); if
         * (sm.getSensorList(Sensor.TYPE_STEP_COUNTER).size() < 1) return; // emulator
         * Logger.log("default: " +
         * sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER).getName()); }
         */

        // SensorManager.SENSOR_DELAY_FASTEST
        // SensorManager.SENSOR_DELAY_NORMAL

        // enable batching with delay of max 5 min
        sm.registerListener(this, sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER), SensorManager.SENSOR_DELAY_FASTEST,
                (int) (MAX_REPORT_LATENCY_MINUTE * MICROSECONDS_IN_ONE_MINUTE));
    }

}