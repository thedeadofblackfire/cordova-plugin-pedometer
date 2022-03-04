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
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.IBinder;

import android.widget.Toast;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.support.annotation.Nullable;

import org.apache.cordova.BuildConfig;
import org.apache.cordova.pedometer.util.API23Wrapper;
import org.apache.cordova.pedometer.util.API26Wrapper;
import org.apache.cordova.pedometer.util.Util;

import java.text.NumberFormat;
import java.util.Date;
import java.util.Locale;

import org.json.JSONObject;

import android.util.Log;
import org.apache.cordova.pedometer.util.Logger;

/**
 * Background service which keeps the step-sensor listener alive to always get
 * the number of steps since boot.
 * <p/>
 * This service won't be needed any more if there is a way to read the
 * step-value without waiting for a sensor event
 */
public class StepsService extends Service implements SensorEventListener {

    private static final String TAG = "cordova-plugin-pedometer";

    public final static int NOTIFICATION_ID = 1;
    private final static long MAX_REPORT_LATENCY_MINUTE = 2; // 5
    private final static long MICROSECONDS_IN_ONE_MINUTE = 60000000;
    private final static long SAVE_OFFSET_TIME = 720000; // 12 min to send to server if slowly activity on step (AlarmManager.INTERVAL_HOUR)
    private final static int SAVE_OFFSET_STEPS = 10; // trigger the send to server if at least 10 steps of difference (500)
    private final static long RESTART_SERVICE_OFFSET_TIME = 150000; // 2 min = 120000, 1H=3600000
    //MAX_REPORT_LATENCY_MINUTE : 1 
    //RESTART_SERVICE_OFFSET_TIME : 3600000

    private static int steps;
    private static int lastSaveSteps;
    private static long lastSaveTime;
	
	private static int notificationIconId = 0;

    private static int protectSensorLastSteps = 0;
    private static long protectSensorLastTime = 0;
    private final static long PROTECT_SENSOR_OFFSET_TIME = 250; // allow to protect fast sensor to do the job x 3 times in a really short period of microseconds

    private static int currentStartId = 0;

    private Context context;

    private final BroadcastReceiver shutdownReceiver = new ShutdownReceiver();

    @Override
    public void onCreate() {
        super.onCreate();

        Log.i(TAG, "StepsService onCreate");
        context = this;   
    }

    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        Log.i(TAG, "StepsService [onStartCommand] id="+startId);
        // Toast.makeText(this, "StepsService Service started...",
        // Toast.LENGTH_LONG).show();

        //https://stackoverflow.com/questions/43251528/android-o-old-start-foreground-service-still-working
        /*
		if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder builder = new Notification.Builder(this, "NOTIFICATION")
            .setContentTitle("Jebooj") //etString(R.string.app_name)
            .setContentText("Booj service ON")
            .setAutoCancel(true);

            Notification notification = builder.build();
            startForeground(NOTIFICATION_ID, notification);
        }
		*/

        this.currentStartId = startId;

        reRegisterSensor();
        registerBroadcastReceiver();
        
        if (!updateIfNecessary()) { 
			showNotification(); 
		}
         
        // get service status and launch alarm if no STOP
        Database db = Database.getInstance(context); 
        String statusService = db.getConfig("status_service"); 
        db.close();
        if (statusService != null && !"stop".equals(statusService)) {
            // restart service every hour to save the current step count
            long nextUpdate = Math.min(Util.getTomorrow(), System.currentTimeMillis() + RESTART_SERVICE_OFFSET_TIME); // INTERVAL_HOUR
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
    public IBinder onBind(final Intent intent) {
        return null;
    }

    @Override
    public void onTaskRemoved(final Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        // if (BuildConfig.DEBUG) Logger.log("sensor service task removed");
        Logger.log("StepsService [onTaskRemoved] - sensor service task removed");

        // get service status and launch alarm if no STOP
        Database db = Database.getInstance(context); 
        String statusService = db.getConfig("status_service"); 
        //db.close();
        if (statusService != null && !"stop".equals(statusService)) {
            // Restart service in 500 ms
            ((AlarmManager) getSystemService(Context.ALARM_SERVICE)).set(AlarmManager.RTC, System.currentTimeMillis() + 500,
                    PendingIntent.getService(this, 3, new Intent(this, StepsService.class), 0));
        }
		db.close();
    }

    @Override
    public void onSensorChanged(final SensorEvent event) {
        // Only look at step counter events
        if (event.sensor.getType() != Sensor.TYPE_STEP_COUNTER) {
            return;
        }

        //Logger.log("StepsService [onSensorChanged] - start ["+this.currentStartId+"] - type=" + event.sensor.getType());

        if (event.values[0] > Integer.MAX_VALUE) {
            if (Util.isDebug())
                Logger.log("StepsService [onSensorChanged] - start ["+this.currentStartId+"] - probably not a real value: " + event.values[0]);
       
            return;
        } else {
            // float steps = event.values[0];
            steps = (int) event.values[0];

            if (steps != protectSensorLastSteps || (System.currentTimeMillis() > (protectSensorLastTime + PROTECT_SENSOR_OFFSET_TIME) )) {
                protectSensorLastSteps = steps;
                protectSensorLastTime = System.currentTimeMillis();

                Logger.log("StepsService [onSensorChanged] - start ["+this.currentStartId+"] - steps=" + steps);
                updateIfNecessary();
                Logger.log("StepsService [onSensorChanged] - end ["+this.currentStartId+"] - steps=" + steps);
            } else {
                Logger.log("StepsService [onSensorChanged] - protect ["+this.currentStartId+"] - steps=" + steps);
            }
        }
    }

    /**
     * @return true, if notification was updated
     */
    private boolean updateIfNecessary() {
        //mStepsDBHelper.createStepsEntryValue(steps);
        Database db = Database.getInstance(context); // this
        db.createStepsEntryValue(steps);
        db.close();

        if (steps > lastSaveSteps + SAVE_OFFSET_STEPS
                || (steps > 0 && System.currentTimeMillis() > lastSaveTime + SAVE_OFFSET_TIME)) {
            if (Util.isDebug())
                Logger.log("StepsService [updateIfNecessary] - saving steps: steps=" + steps + " lastSave=" + lastSaveSteps + " lastSaveTime="
                        + new Date(lastSaveTime));
            /*
            //Database db = Database.getInstance(this);
            if (db.getSteps(Util.getToday()) == Integer.MIN_VALUE) {
                int pauseDifference = steps
                        - getSharedPreferences("pedometer", Context.MODE_PRIVATE).getInt("pauseCount", steps);
                db.insertNewDay(Util.getToday(), steps - pauseDifference);
                if (pauseDifference > 0) {
                    // update pauseCount for the new day
                    getSharedPreferences("pedometer", Context.MODE_PRIVATE).edit().putInt("pauseCount", steps).commit();
                }                
            }
            
            db.saveCurrentSteps(steps);
            db.close();
            */

            try {
                Thread thread = new Thread(new Runnable(){
                    @Override
                    public void run() {
                        try {
                            Database db = Database.getInstance(context);
                            JSONObject response = db.syncData();
                            db.close();
                        } catch (Exception e) {
                           //e.printStackTrace();
                           Log.e(TAG, e.getMessage());
                        }
                    }
                });

                thread.start();
            } catch (Exception e) {
                e.printStackTrace();
            }

            lastSaveSteps = steps;
            lastSaveTime = System.currentTimeMillis();     
			showNotification(); // update notification
            return true;
        } else {
			showNotification(); // update notification
            return false;
        }
    }
	
	private void showNotification() {
		if (Build.VERSION.SDK_INT >= 26) {
		  startForeground(NOTIFICATION_ID, getNotification(this));
		} else if (getSharedPreferences("pedometer", Context.MODE_PRIVATE)
		  .getBoolean("notification", true)) {
		  ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
			.notify(NOTIFICATION_ID, getNotification(this));
		}
	}

    @Override
    public void onAccuracyChanged(final Sensor sensor, int accuracy) {
        // nobody knows what happens here: step value might magically decrease
        // when this method is called...
        Log.i(TAG, sensor.getName() + " accuracy changed: " + accuracy);
        // if (BuildConfig.DEBUG) Logger.log(sensor.getName() + " accuracy changed: " +
        // accuracy);
    }

	private static int getNotificationIconId(Context context) {
		int drawableId = context.getResources().getIdentifier("ic_footsteps_silhouette_variant", "drawable",
		  context.getApplicationInfo().packageName);
		if (drawableId == 0) {
		  drawableId = context.getApplicationInfo().icon;
		}
		return drawableId;
	}
  
	public static Notification getNotification(final Context context) {
		SharedPreferences prefs = context.getSharedPreferences("pedometer", Context.MODE_PRIVATE);
		Database db = Database.getInstance(context);
		int today_offset = db.getSteps(Util.getToday());
		if (steps == 0) steps = db.getCurrentSteps(); // use saved value if we haven't anything better
		db.close();
		
		int goal = prefs.getInt(PedoListener.GOAL_PREF_INT, PedoListener.DEFAULT_GOAL);
		
		Notification.Builder notificationBuilder =
		  Build.VERSION.SDK_INT >= 26 ? API26Wrapper.getNotificationBuilder(context) :
			new Notification.Builder(context);

		if (steps > 0) {
            if (today_offset == Integer.MIN_VALUE) today_offset = -steps;
            // if goal = 1, we don't show progress and replace "653 steps to go" by "347 steps today"
            if (goal <= 1) {
                notificationBuilder.setContentText(String.format(prefs.getString(PedoListener.PEDOMETER_STEPS_TO_GO_FORMAT_TEXT, "%s steps today"), NumberFormat.getInstance(Locale.getDefault()).format((today_offset + steps))));
            } else {
		        //if (today_offset == Integer.MIN_VALUE) today_offset = -steps;
		        notificationBuilder.setProgress(goal, today_offset + steps, false).setContentText(
			today_offset + steps >= goal ?
			  String.format(prefs.getString(PedoListener.PEDOMETER_GOAL_REACHED_FORMAT_TEXT, "Goal reached! %s steps and counting"),
				NumberFormat.getInstance(Locale.getDefault())
				  .format((today_offset + steps))) :
			  String.format(prefs.getString(PedoListener.PEDOMETER_STEPS_TO_GO_FORMAT_TEXT, "%s steps to go"),
				NumberFormat.getInstance(Locale.getDefault())
				  .format((goal - today_offset - steps))));
            }
		} else { 
            // still no step value?
		    notificationBuilder.setContentText(prefs.getString(PedoListener.PEDOMETER_YOUR_PROGRESS_FORMAT_TEXT, "Your progress will be shown here soon"));
		}

		PackageManager packageManager = context.getPackageManager();
		Intent launchIntent = packageManager.getLaunchIntentForPackage(context.getPackageName());

		PendingIntent contentIntent = PendingIntent.getActivity(context, 0,
		  launchIntent, PendingIntent.FLAG_UPDATE_CURRENT);

		if (notificationIconId == 0) {
		  notificationIconId = getNotificationIconId(context);
		}

		notificationBuilder.setPriority(Notification.PRIORITY_DEFAULT).setShowWhen(false)
		  .setContentTitle(prefs.getString(PedoListener.PEDOMETER_IS_COUNTING_TEXT, "Pedometer is counting"))
		  .setContentIntent(contentIntent).setSmallIcon(notificationIconId)
		  .setOngoing(true);
		return notificationBuilder.build();
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
        Log.i(TAG, "StepsService [reRegisterSensor] - re-register sensor listener");
        //SensorManager sm = (SensorManager) this.getSystemService(Context.SENSOR_SERVICE);
		SensorManager sm = (SensorManager) getSystemService(SENSOR_SERVICE);

        try {
            sm.unregisterListener(this);
        } catch (Exception e) {
            // if (BuildConfig.DEBUG) Logger.log(e);
            Log.i(TAG, e.toString());
            e.printStackTrace();
        }

        Log.i(TAG, "StepsService [reRegisterSensor] - step sensors: " + sm.getSensorList(Sensor.TYPE_STEP_COUNTER).size());
        
		if (sm.getSensorList(Sensor.TYPE_STEP_COUNTER).size() < 1) return; // emulator
		
        Log.i(TAG, "StepsService [reRegisterSensor] - default: " + sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER).getName());

        /*
         * if (BuildConfig.DEBUG) { Logger.log("step sensors: " +
         * sm.getSensorList(Sensor.TYPE_STEP_COUNTER).size()); if
         * (sm.getSensorList(Sensor.TYPE_STEP_COUNTER).size() < 1) return; // emulator
         * Logger.log("default: " +
         * sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER).getName()); }
         */

        // SensorManager.SENSOR_DELAY_FASTEST
        // SensorManager.SENSOR_DELAY_NORMAL
		// SensorManager.SENSOR_DELAY_UI 

        // enable batching with delay of max 5 min
        sm.registerListener(this, sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER), SensorManager.SENSOR_DELAY_UI,
                (int) (MAX_REPORT_LATENCY_MINUTE * MICROSECONDS_IN_ONE_MINUTE));
    }

}