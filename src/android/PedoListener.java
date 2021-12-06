package org.apache.cordova.pedometer;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import android.app.Activity;

import android.util.Log;

import android.os.Handler;
import android.os.Build;
import android.os.IBinder;

import java.io.IOException;

import android.app.AlertDialog;

/**
 * This class listens to the pedometer sensor
 */
public class PedoListener extends CordovaPlugin implements SensorEventListener {

	// logger tag
    private static final String TAG = "cordova-plugin-pedometer";
	
    public static int STOPPED = 0;
    public static int STARTING = 1;
    public static int RUNNING = 2;
    public static int ERROR_FAILED_TO_START = 3;
    public static int ERROR_NO_SENSOR_FOUND = 4;
	public static int PAUSED = 5;

    public static int DEFAULT_GOAL = 1000;
  
    public static String GOAL_PREF_INT = "GoalPrefInt";

    public static String PEDOMETER_IS_COUNTING_TEXT = "pedometerIsCountingText";
    public static String PEDOMETER_STEPS_TO_GO_FORMAT_TEXT = "pedometerStepsToGoFormatText";
    public static String PEDOMETER_YOUR_PROGRESS_FORMAT_TEXT = "pedometerYourProgressFormatText";
    public static String PEDOMETER_GOAL_REACHED_FORMAT_TEXT = "pedometerGoalReachedFormatText";

    private int status;     // status of listener
    private float startsteps; //first value, to be substracted
    private long starttimestamp; //time stamp of when the measurement starts

    private SensorManager sensorManager; // Sensor manager
    private Sensor mSensor;             // Pedometer sensor returned by sensor manager

    private CallbackContext callbackContext; // Keeps track of the JS callback context.

    private Handler mainHandler=null;

    private final int SENSOR_TYPE = Sensor.TYPE_STEP_COUNTER; // TYPE_STEP_DETECTOR or TYPE_STEP_COUNTER
	
    private Intent stepCounterIntent;

	//private StepsDBHelper mStepsDBHelper;
    
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            Log.i(TAG, "onServiceConnected is called'");
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            Log.i(TAG, "onServiceDisconnected is called'");
        }
    };

    /**
     * Constructor
     */
    public PedoListener() {
        this.starttimestamp = 0;
        this.startsteps = 0;
        this.setStatus(PedoListener.STOPPED);
				
		Log.i(TAG, "PedoListener Init service for steps");
		//pendingIntent = getTransitionPendingIntent();
    }

    /**
     * Sets the context of the Command. This can then be used to do things like
     * get file paths associated with the Activity.
     *
     * @param cordova the context of the main Activity.
     * @param webView the associated CordovaWebView.
     */
    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
		
		Log.i(TAG, "PedoListener initialize debug="+StepsUtil.isDebug());
		
        this.sensorManager = (SensorManager) cordova.getActivity().getSystemService(Context.SENSOR_SERVICE);
		
		/*
		Intent mStepsIntent = new Intent(cordova.getActivity(), StepsService.class); // context
        //logger.log(Log.DEBUG, "StepsService Intent created!");
		Log.i(TAG, "PedoListener StepsService Intent created!");
        cordova.getActivity().startService(mStepsIntent);
        */
				
		//mStepsDBHelper = new StepsDBHelper(cordova.getActivity());
    }

    /**
     * Executes the request.
     *
     * @param action the action to execute.
     * @param args the exec() arguments.
     * @param callbackContext the callback context used when calling back into JavaScript.
     * @return whether the action was valid.
     */
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        this.callbackContext = callbackContext;
		
        Log.i(TAG, "PedoListener execute action=" + action);
        
        Activity activity = this.cordova.getActivity();
        stepCounterIntent = new Intent(activity, StepsService.class);

        if (action.equals("isStepCountingAvailable")) {
			/*
			this.mSensor = this.sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
			if (this.mSensor != null) {
				this.win(true);
                return true;
			} else {
				this.setStatus(PedoListener.ERROR_NO_SENSOR_FOUND);
                this.win(false);
                return true;
			}
			*/
			
            List<Sensor> list = this.sensorManager.getSensorList(this.SENSOR_TYPE);
            if ((list != null) && (list.size() > 0)) {
                this.win(true);
                return true;
            } else {
                this.setStatus(PedoListener.ERROR_NO_SENSOR_FOUND);
                this.win(false);
                return true;
            }
			
        } else if (action.equals("isDistanceAvailable")) {
            //distance is never available in Android
			//mStepsDBHelper.createStepsEntry();
            this.win(false);
            return true;
        } else if (action.equals("isFloorCountingAvailable")) {
            //floor counting is never available in Android
            this.win(false);
            return true;
        } else if (action.equals("startPedometerUpdates")) {
            if (this.status != PedoListener.RUNNING) {
                // If not running, then this is an async call, so don't worry about waiting
                // We drop the callback onto our stack, call start, and let start and the sensor callback fire off the callback down the road
                this.start();
            }
            PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT, "");
            result.setKeepCallback(true);
            callbackContext.sendPluginResult(result);
            return true;
        } else if (action.equals("stopPedometerUpdates")) {
            if (this.status == PedoListener.RUNNING) {
                this.stop();
            }
            this.win(null);
            return true;

        } else if (action.equals("deviceCanCountSteps")) {
            Log.i(TAG, "deviceCanCountSteps is called");
            Boolean canStepCount = deviceHasStepCounter(activity.getPackageManager());
            callbackContext.success(canStepCount ? 1 : 0);

        } else if (action.equals("deviceCheckPermissions")) {
            Log.i(TAG, "deviceCheckPermissions is called");
            final AlertDialog dialog = BatteryOptimizationUtil.getBatteryOptimizationDialog(activity);
            if (dialog != null) dialog.show();
            this.win(true);
            return true;

        } else if (action.equals("setConfig")) {
            try {
                Log.i(TAG, "setConfig is called");
                //Log.i(TAG, args.toString());
                Log.i(TAG, args.getJSONObject(0).toString());
                Database db = Database.getInstance(activity);
                if (args.getJSONObject(0).has("userid")) db.setConfig("userid", args.getJSONObject(0).getString("userid"));
                if (args.getJSONObject(0).has("api")) db.setConfig("api", args.getJSONObject(0).getString("api"));
                db.close();
                this.win(null);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return true;
        } else if (action.equals("startService")) {
            Log.i(TAG, "startService is called");

            Database db = Database.getInstance(activity);
            db.setConfig("status_service", "start");
            db.close();

            if (Build.VERSION.SDK_INT >= 26) {
                API26Wrapper.startForegroundService(activity, stepCounterIntent);
            } else {
                activity.startService(stepCounterIntent);
            }
            activity.bindService(stepCounterIntent, mConnection, Context.BIND_AUTO_CREATE);
        
            callbackContext.success(1); 

            // should display each time ?
            final AlertDialog dialog = BatteryOptimizationUtil.getBatteryOptimizationDialog(activity);
            if (dialog != null) dialog.show();  
            
            return true;              
        } else if (action.equals("startServiceSilent")) {
            Log.i(TAG, "startServiceSilent is called");

            Database db = Database.getInstance(activity);
            db.setConfig("status_service", "start");
            db.close();

            if (Build.VERSION.SDK_INT >= 26) {
                API26Wrapper.startForegroundService(activity, stepCounterIntent);
            } else {
                activity.startService(stepCounterIntent);
            }
            activity.bindService(stepCounterIntent, mConnection, Context.BIND_AUTO_CREATE);
        
            callbackContext.success(1); 
            
            return true; 
        } else if (action.equals("stopService")) {
            Log.i(TAG, "stopService is called");

            Database db = Database.getInstance(activity);
            db.setConfig("status_service", "stop");
            db.close();

            activity.stopService(stepCounterIntent);
            activity.unbindService(mConnection);
            
            callbackContext.success(1);
            
            return true;
        } else if (action.equals("statusService")) {
            Log.i(TAG, "statusService is called");     
            Database db = Database.getInstance(activity);
            String statusService = db.getConfig("status_service");
            db.close();

            callbackContext.success(statusService);
        } else if (action.equals("clean")) {
            Log.i(TAG, "clean is called");
            Database db = Database.getInstance(activity);
            db.cleanLinesToSync();
            db.close();

            this.win(true);
            return true;
        } else if (action.equals("reset")) {
            Log.i(TAG, "reset is called");
            Database db = Database.getInstance(activity);
            db.resetLinesToSync();
            db.close();

            this.win(true);
            return true;
        } else if (action.equals("rollback")) {
            Log.i(TAG, "rollback is called");
            Database db = Database.getInstance(activity);
            db.rollbackLinesToSync();
            db.close();

            this.win(true);
            return true;
        } else if (action.equals("sync")) {
            Log.i(TAG, "sync is called");
            Database db = Database.getInstance(activity);
            try {
                JSONObject response = db.syncData();
                this.win(response);
            } catch (Exception e) {
                e.printStackTrace();
            }
            db.close();
            return true;
        } else if (action.equals("debug")) {
            Log.i(TAG, "debug is called");
            Database db = Database.getInstance(activity);
            db.exportDatabase();
            db.close();

            this.win(true);
            return true;
        } else if (action.equals("queryData")) {
            try {
                Log.i(TAG, "queryData is called");
                Log.i(TAG, args.toString());
                Log.i(TAG, args.getJSONObject(0).toString());
                //JSONObject jo = args.getJSONObject(0);
                //JSONObject jo = args[0].getJSONObject(0);
                //Log.i(TAG, "execute: jo=" + jo.toString());

                Database db = Database.getInstance(activity);
                JSONObject dataToSync = db.getNoSyncResults(false);
                db.close();
                this.win(dataToSync);
                //this.win(this.getStepsJSON(steps));
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return true;
		} else if (action.equals("setNotificationLocalizedStrings")) {
			setNotificationLocalizedStrings(args);
			callbackContext.success();
		} else if (action.equals("setGoal")) {
			setGoal(args);
			callbackContext.success();    
        } else {
            // Unsupported action
            Log.e(TAG, "Invalid action called on class " + TAG + ", " + action);
            //callbackContext.error("Invalid action called on class " + TAG + ", " + action);
            return false;
        }

        return true;
    }

    /**
     * Called by the Broker when listener is to be shut down.
     * Stop listener.
     */
    public void onDestroy() {
        super.onDestroy();
		Log.i(TAG, "PedoListener onDestroy");
        //this.stop();
    }

    /**
     * Start listening for pedometers sensor.
     */
    private void start() {
        // If already starting or running, then return
        if ((this.status == PedoListener.RUNNING) || (this.status == PedoListener.STARTING)) {
            return;
        }

        starttimestamp = System.currentTimeMillis();
        this.startsteps = 0;
        this.setStatus(PedoListener.STARTING);

        // Get pedometer from sensor manager
        List<Sensor> list = this.sensorManager.getSensorList(this.SENSOR_TYPE);

        // If found, then register as listener
        if ((list != null) && (list.size() > 0)) {
            this.mSensor = list.get(0);
            if (this.sensorManager.registerListener(this, this.mSensor, SensorManager.SENSOR_DELAY_FASTEST)) {
                this.setStatus(PedoListener.STARTING);
				Log.i(TAG, "PedoListener start status=" + this.status);
            } else {
                this.setStatus(PedoListener.ERROR_FAILED_TO_START);
                this.fail(PedoListener.ERROR_FAILED_TO_START, "Device sensor returned an error.");
                return;
            };
        } else {
            this.setStatus(PedoListener.ERROR_FAILED_TO_START);
            this.fail(PedoListener.ERROR_FAILED_TO_START, "No sensors found to register step counter listening to.");
            return;
        }
    }

    /**
     * Stop listening to sensor.
     */
    private void stop() {
		/*
        if (this.status != PedoListener.STOPPED) {
            this.sensorManager.unregisterListener(this);
        }
        this.setStatus(PedoListener.STOPPED);
		*/
    }
	
    /**
     * Called when the accuracy of the sensor has changed.
     */
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
      //nothing to do here
      return;
    }

    /**
     * Sensor listener event.
     * @param event
     */
    @Override
    public void onSensorChanged(SensorEvent event) {
		Log.i(TAG, "PedoListener onSensorChanged");
		
        // Only look at step counter events
        if (event.sensor.getType() != this.SENSOR_TYPE) {
            return;
        }

        // If not running, then just return
        if (this.status == PedoListener.STOPPED) {
            return;
        }
        this.setStatus(PedoListener.RUNNING);

        float steps = event.values[0];

        if(this.startsteps == 0)
          this.startsteps = steps;

        steps = steps - this.startsteps;

        this.win(this.getStepsJSON(steps));
    }

    /**
     * Called when the view navigates.
     */
    @Override
    public void onReset() {
        if (this.status == PedoListener.RUNNING) {
            this.stop();
        }
    }

    // Sends an error back to JS
    private void fail(int code, String message) {
        // Error object
        JSONObject errorObj = new JSONObject();
        try {
            errorObj.put("code", code);
            errorObj.put("message", message);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        PluginResult err = new PluginResult(PluginResult.Status.ERROR, errorObj);
        err.setKeepCallback(true);
        callbackContext.sendPluginResult(err);
    }

    private void win(JSONObject message) {
        // Success return object
        PluginResult result;
        if(message != null)
            result = new PluginResult(PluginResult.Status.OK, message);
        else
            result = new PluginResult(PluginResult.Status.OK);

        result.setKeepCallback(true);
        callbackContext.sendPluginResult(result);
    }

    private void win(boolean success) {
        // Success return object
        PluginResult result;
        result = new PluginResult(PluginResult.Status.OK, success);

        result.setKeepCallback(true);
        callbackContext.sendPluginResult(result);
    }

    private void setStatus(int status) {
        this.status = status;
    }

    private JSONObject getStepsJSON(float steps) {
        JSONObject r = new JSONObject();
        // pedometerData.startDate; -> ms since 1970
        // pedometerData.endDate; -> ms since 1970
        // pedometerData.numberOfSteps;
        // pedometerData.distance;
        // pedometerData.floorsAscended;
        // pedometerData.floorsDescended;
        try {
            r.put("startDate", this.starttimestamp);
            r.put("endDate", System.currentTimeMillis());
            r.put("numberOfSteps", steps);
			r.put("status", this.status);
			r.put("startsteps", this.startsteps);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return r;
    }
	
	/*
     * Create a PendingIntent that triggers an IntentService in your app when a
     * geofence transition occurs.
     */
	 /*
    private PendingIntent getTransitionPendingIntent() {
        Intent mStepsIntent = new Intent(context, StepsService.class);
        //logger.log(Log.DEBUG, "StepsService Intent created!");
		Log.d(TAG, "StepsService Intent created!");
		this.cordova.getActivity().startService(mStepsIntent);
        //return PendingIntent.getService(context, 0, mStepsIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    }
    */
    
    public static boolean deviceHasStepCounter(PackageManager pm) {
        // Require at least Android KitKat
        int currentApiVersion = Build.VERSION.SDK_INT;

        // Check that the device supports the step counter and detector sensors
        return currentApiVersion >= 19
                && pm.hasSystemFeature(PackageManager.FEATURE_SENSOR_STEP_COUNTER);
        /*
        return currentApiVersion >= 19
                && pm.hasSystemFeature(PackageManager.FEATURE_SENSOR_STEP_COUNTER)
                && pm.hasSystemFeature(PackageManager.FEATURE_SENSOR_STEP_DETECTOR);
        */                
    }
		
	private void setNotificationLocalizedStrings(JSONArray args) {
		String pedometerIsCounting;
		String stepsToGo;
		String yourProgress;
		String goalReached;

		try {
		  JSONObject joStrings = args.getJSONObject(0);
		  pedometerIsCounting = joStrings.getString("pedometerIsCounting");
		  stepsToGo = joStrings.getString("stepsToGo");
		  yourProgress = joStrings.getString("yourProgress");
		  goalReached = joStrings.getString("goalReached");
		}
		catch (JSONException e) {
		  e.printStackTrace();
		  return;
		}

		SharedPreferences prefs = cordova.getContext().getSharedPreferences("pedometer", Context.MODE_PRIVATE);

		if (pedometerIsCounting != null) {
		  prefs.edit().putString(PedoListener.PEDOMETER_IS_COUNTING_TEXT, pedometerIsCounting).apply();
		}
		if (stepsToGo != null) {
		  prefs.edit().putString(PedoListener.PEDOMETER_STEPS_TO_GO_FORMAT_TEXT, stepsToGo).apply();
		}
		if (yourProgress != null) {
		  prefs.edit().putString(PedoListener.PEDOMETER_YOUR_PROGRESS_FORMAT_TEXT, yourProgress).apply();
		}
		if (goalReached != null) {
		  prefs.edit().putString(PedoListener.PEDOMETER_GOAL_REACHED_FORMAT_TEXT, goalReached).apply();
		}
	}
  
    private void setGoal(JSONArray args) {
		try {
		  goal = args.getInt(0);
		}
		catch (JSONException e) {
		  e.printStackTrace();
		  return;
		}

		SharedPreferences prefs = cordova.getContext().getSharedPreferences("pedometer", Context.MODE_PRIVATE);
		if (goal > 0) {
		  prefs.edit().putInt(PedoListener.GOAL_PREF_INT, goal).apply();
		}
	}


}
