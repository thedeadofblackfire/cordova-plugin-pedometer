package fr.jebooj.plugins.pedometer;

import android.app.Notification;
import android.app.NotificationManager;
// still needed by the notification's contentIntent, even though the AlarmManager scheduling is gone
import android.app.PendingIntent;
import android.app.Service;
import android.app.ForegroundServiceStartNotAllowedException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
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
import androidx.annotation.Nullable;

//import org.apache.cordova.BuildConfig;
import fr.jebooj.plugins.pedometer.util.API23Wrapper;
import fr.jebooj.plugins.pedometer.util.API26Wrapper;
import fr.jebooj.plugins.pedometer.util.Util;

import java.text.NumberFormat;
import java.util.Date;
import java.util.Locale;

import org.json.JSONObject;

import android.util.Log;
import fr.jebooj.plugins.pedometer.util.Logger;

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
    /** onStartCommand runs on every start request: register the shutdown receiver only once. */
    private boolean shutdownReceiverRegistered = false;

    /**
     * Date, clock or timezone changed: "today" moved, so redraw the daily goal now and re-arm the
     * midnight alarm for the new local midnight.
     */
    private final BroadcastReceiver timeChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            // screen on: only when the notification still shows a previous day (midnight alarm
            // deferred by Doze) — the user is about to look at it
            if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                if (lastDrawnDay != Util.getToday()) refreshNotification(ctx);
                return;
            }
            Log.i(TAG, "StepsService [timeChangeReceiver] - " + intent.getAction());
            refreshNotification(ctx);
            MidnightReceiver.schedule(ctx);
        }
    };

    /** local day (Util.getToday()) of the last notification drawn */
    private static long lastDrawnDay = 0;
    private boolean timeChangeReceiverRegistered = false;

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
        // the daily goal restarts from 0 at local midnight: redraw it then, even without a step
        MidnightReceiver.schedule(context);

        if (!updateIfNecessary()) { 
			showNotification(); 
		}
         
        // Keep the autonomous sync alive.
        //
        // The Cordova version re-scheduled this service through an AlarmManager every couple of
        // minutes, purely so that `updateIfNecessary()` would run and push the steps. On Android 12+
        // starting a foreground service from a background alarm throws
        // ForegroundServiceStartNotAllowedException (the catch below in showNotification() is the
        // scar of it). WorkManager does the same job in a supported way: it survives reboots,
        // respects Doze, retries on failure and does not need to wake this service at all.
        Database db = Database.getInstance(context);
        String intervalConfig = db.getConfig("sync_interval_minutes");
        db.close();

        if (ServiceControl.isEnabled(context)) {
            long interval = SyncWorker.DEFAULT_INTERVAL_MINUTES;
            try {
                if (intervalConfig != null && !intervalConfig.isEmpty()) interval = Long.parseLong(intervalConfig);
            } catch (NumberFormatException e) {
                Log.w(TAG, "StepsService [onStartCommand] - invalid sync_interval_minutes=" + intervalConfig);
            }
            SyncWorker.schedule(getApplicationContext(), interval);
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
            if (shutdownReceiverRegistered) unregisterReceiver(shutdownReceiver);
            shutdownReceiverRegistered = false;
            if (timeChangeReceiverRegistered) unregisterReceiver(timeChangeReceiver);
            timeChangeReceiverRegistered = false;

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

        // The service keeps running when the task is swiped away (a foreground service is not
        // killed by task removal), but some OEM skins kill it anyway — hence this restart.
        //
        // The Cordova version scheduled it through an AlarmManager 500 ms later. On Android 12+ that
        // path is refused: starting a foreground service from a background alarm throws
        // ForegroundServiceStartNotAllowedException. Restarting it directly from onTaskRemoved is
        // allowed, because the app still holds a valid start reason at this exact moment.
        if (ServiceControl.shouldRun(context)) {
            try {
                Intent restart = new Intent(this, StepsService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(restart);
                } else {
                    startService(restart);
                }
            } catch (Exception e) {
                // never let a restart attempt crash the process; WorkManager still carries the sync
                // and START_STICKY still asks the system to bring the service back
                Log.w(TAG, "StepsService [onTaskRemoved] - restart refused: " + e);
            }
        }
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
        Database db = Database.getInstance(context); // this
        db.createStepsEntryValue(steps);
        db.close();

        if (steps > lastSaveSteps + SAVE_OFFSET_STEPS
                || (steps > 0 && System.currentTimeMillis() > lastSaveTime + SAVE_OFFSET_TIME)) {
            if (Util.isDebug())
                Logger.log("StepsService [updateIfNecessary] - saving steps: steps=" + steps + " lastSave=" + lastSaveSteps + " lastSaveTime="
                        + new Date(lastSaveTime));
                        
            Database db2 = Database.getInstance(context);
            if (db2.getSteps(Util.getToday()) == Integer.MIN_VALUE) {
                int pauseDifference = steps - getSharedPreferences("pedometer", Context.MODE_PRIVATE).getInt("pauseCount", steps);
                //db.insertNewDay(Util.getToday(), steps - pauseDifference);
                if (pauseDifference > 0) {
                    // update pauseCount for the new day
                    getSharedPreferences("pedometer", Context.MODE_PRIVATE).edit().putInt("pauseCount", steps).commit();
                }                
            }
            
            db2.saveCurrentSteps(steps);
            db2.close();
            /*
            Database db = Database.getInstance(this);
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
				// https://stackoverflow.com/questions/15472383/how-can-i-run-code-on-a-background-thread-on-android
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
						
						//run code on background thread 
						/*
						activity.runOnUiThread(()->{
							//update the UI on main thread
						});
						*/

						//here activity is the reference of activity 
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
	
	// https://stackoverflow.com/questions/70044393/fatal-android-12-exception-startforegroundservice-not-allowed-due-to-mallows
	// https://developer.android.com/develop/background-work/services/foreground-services?hl=fr#kotlin
	// https://developer.android.com/develop/background-work/services/fg-service-types?hl=fr : android:foregroundServiceType = health
	// https://proandroiddev.com/foreground-services-in-android-14-whats-changing-dcd56ad72788
	private void showNotification() {
		if (Build.VERSION.SDK_INT >= 26) {			
		    //startForeground(NOTIFICATION_ID, getNotification(this)); // older
		  
		    // https://medium.com/@domen.lanisnik/guide-to-foreground-services-on-android-9d0127dc8f9a			
			// Build.VERSION_CODES.TIRAMISU (android 13)
			// Build.VERSION_CODES.UPSIDE_DOWN_CAKE (android 14)
		    // Build.VERSION_CODES.Q : Android 10 in September 2019. (29)
			// Build.VERSION_CODES.R : Android 11 in September 2020. (30)
			// Build.VERSION_CODES.S : Android 12 (31)
		    //Specifying the type requires Android Q, so you’ll have to wrap it into a statement in order to use the old startForeground() for android versions lower than Q. If your minSDK is 29 or higher — just use the one with foreground service type.
			
			//https://developer.android.com/develop/background-work/services/foreground-services?hl=fr#java
			
			try {
				int type = 0;
					
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
					type = ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH;				
				} 
				
				// before 200126
				//startForeground(NOTIFICATION_ID, getNotification(this), type);
				
				if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
					// For Android 10 (API 29) and above
					startForeground(NOTIFICATION_ID, getNotification(this), type);
				} else {
					// For Android 9 and below
					startForeground(NOTIFICATION_ID, getNotification(this));
				}
				
			} catch (Exception e) {
				// ForegroundServiceStartNotAllowedException (started from background) or, on Android
				// 14+, SecurityException for the `health` type without ACTIVITY_RECOGNITION. The
				// service then runs as a plain background service that counts nothing until the system
				// kills it — so say it loudly. ServiceControl gates the starts to avoid getting here.
				Log.e(TAG, "StepsService [showNotification] - startForeground refused, the service is not in the foreground", e);
			}
				
			/*
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
				startForeground(NOTIFICATION_ID, getNotification(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH);
				//startForeground(NOTIFICATION_ID, notification.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
			} else {
				startForeground(NOTIFICATION_ID, getNotification(this));
				//startForeground(NOTIFICATION_ID, notification.build(),)
			}
			*/
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
  
	/**
	 * Redraw the persistent notification now — after a goal or text change, at midnight, after a
	 * clock change — instead of waiting for the next sensor event. Same id as the foreground
	 * notification, so it replaces it in place.
	 */
	public static void refreshNotification(final Context context) {
		if (!ServiceControl.shouldRun(context)) return;
		try {
			NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
			if (nm != null) nm.notify(NOTIFICATION_ID, getNotification(context));
		} catch (Exception e) {
			Log.w(TAG, "StepsService [refreshNotification] - " + e);
		}
	}

	/**
	 * The format strings come from the app: a stray '%' used to throw and kill the service. Fall back
	 * to the built-in text instead.
	 */
	private static String safeFormat(final String format, final String fallback, final Object... args) {
		try {
			return String.format(format, args);
		} catch (Exception e) {
			Log.w(TAG, "StepsService [getNotification] - invalid format \"" + format + "\": " + e);
			return String.format(fallback, args);
		}
	}

	public static Notification getNotification(final Context context) {
		SharedPreferences prefs = context.getSharedPreferences("pedometer", Context.MODE_PRIVATE);
		long now = System.currentTimeMillis();

		Database db = Database.getInstance(context);
		// steps of the current local day: the rows of Util.getToday(), so 0 again after midnight.
		// No "+ steps" (raw since-boot counter): that was the offset model of the Cordova version,
		// the period rows already hold the real count.
		lastDrawnDay = Util.getToday();
		int today = db.getSteps(lastDrawnDay);
		if (today == Integer.MIN_VALUE || today < 0) today = 0;
		if (steps == 0) steps = db.getCurrentSteps(); // use saved value if we haven't anything better
		Goals.Challenge challenge = Goals.getActiveChallenge(context, now);
		int challengeSteps = challenge != null ? Goals.getChallengeSteps(db, challenge) : 0;
		db.close();

		int goal = Goals.getDailyGoal(context);
		NumberFormat nf = NumberFormat.getInstance(Locale.getDefault());

		Notification.Builder notificationBuilder =
		  Build.VERSION.SDK_INT >= 26 ? API26Wrapper.getNotificationBuilder(context) :
			new Notification.Builder(context);

		String text;
		if (steps > 0 || today > 0) {
			if (goal <= 0) {
				// no daily goal: no progress bar, "347 steps today"
				String format = prefs.getString(Prefs.PEDOMETER_STEPS_TODAY_FORMAT_TEXT,
					prefs.getString(Prefs.PEDOMETER_STEPS_TO_GO_FORMAT_TEXT, "%s steps today"));
				text = safeFormat(format, "%s steps today", nf.format(today));
			} else {
				notificationBuilder.setProgress(goal, Math.min(today, goal), false);
				text = today >= goal
					? safeFormat(prefs.getString(Prefs.PEDOMETER_GOAL_REACHED_FORMAT_TEXT, "Goal reached! %s steps and counting"),
						"Goal reached! %s steps and counting", nf.format(today))
					: safeFormat(prefs.getString(Prefs.PEDOMETER_STEPS_TO_GO_FORMAT_TEXT, "%s steps to go"),
						"%s steps to go", nf.format(goal - today));
			}
		} else {
			// still no step value?
			text = prefs.getString(Prefs.PEDOMETER_YOUR_PROGRESS_FORMAT_TEXT, "Your progress will be shown here soon");
		}
		notificationBuilder.setContentText(text);

		// the challenge is a second line, visible when the notification is expanded
		if (challenge != null) {
			String name = challenge.name == null || challenge.name.isEmpty() ? "Challenge" : challenge.name;
			String line = safeFormat(prefs.getString(Prefs.PEDOMETER_CHALLENGE_FORMAT_TEXT, "%1$s: %2$s / %3$s steps"),
				"%1$s: %2$s / %3$s steps", name, nf.format(challengeSteps), nf.format(challenge.goal));
			notificationBuilder.setStyle(new Notification.BigTextStyle().bigText(text + "\n" + line));
		}

		PackageManager packageManager = context.getPackageManager();
		Intent launchIntent = packageManager.getLaunchIntentForPackage(context.getPackageName());

		// for the FLAG_MUTABLE yes, it should be S as it was added in SDK 31, FLAG_IMMUTABLE was added in SDK 23, so M is ok 
		
		//PendingIntent contentIntent = PendingIntent.getActivity(context, 0, launchIntent, PendingIntent.FLAG_UPDATE_CURRENT);
		PendingIntent contentIntent = null;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {		
			contentIntent = PendingIntent.getActivity(context, 0, launchIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
		} else {
			contentIntent = PendingIntent.getActivity(context, 0, launchIntent, PendingIntent.FLAG_UPDATE_CURRENT);
		}

		if (notificationIconId == 0) {
		  notificationIconId = getNotificationIconId(context);
		}

		notificationBuilder.setPriority(Notification.PRIORITY_DEFAULT).setShowWhen(false)
		  .setContentTitle(prefs.getString(Prefs.PEDOMETER_IS_COUNTING_TEXT, "Pedometer is counting"))
		  .setContentIntent(contentIntent).setSmallIcon(notificationIconId)
		  .setOngoing(true);
		return notificationBuilder.build();
	}
  
    private void registerBroadcastReceiver() {
        // if (BuildConfig.DEBUG) Logger.log("register broadcastreceiver");
        Log.i(TAG, "StepsService [registerBroadcastReceiver] - register broadcastreceiver");
        // registered twice, ShutdownReceiver would run twice and add the pending steps twice
        if (!shutdownReceiverRegistered) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SHUTDOWN);
            registerReceiver(shutdownReceiver, filter);
            shutdownReceiverRegistered = true;
        }

        // system broadcasts: no RECEIVER_EXPORTED flag needed on Android 14
        if (!timeChangeReceiverRegistered) {
            IntentFilter timeFilter = new IntentFilter();
            timeFilter.addAction(Intent.ACTION_DATE_CHANGED);
            timeFilter.addAction(Intent.ACTION_TIME_CHANGED);
            timeFilter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
            timeFilter.addAction(Intent.ACTION_SCREEN_ON);
            registerReceiver(timeChangeReceiver, timeFilter);
            timeChangeReceiverRegistered = true;
        }
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
		// SensorManager.SENSOR_DELAY_GAME
        // SensorManager.SENSOR_DELAY_NORMAL
		// SensorManager.SENSOR_DELAY_UI 

		// 010823 change from SENSOR_DELAY_UI to SENSOR_DELAY_GAME
        // enable batching with delay of max 2 min
        sm.registerListener(this, sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER), SensorManager.SENSOR_DELAY_GAME,
                (int) (MAX_REPORT_LATENCY_MINUTE * MICROSECONDS_IN_ONE_MINUTE));
    }

}