package org.apache.cordova.pedometer;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.apache.cordova.pedometer.util.Util;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import org.apache.cordova.pedometer.util.API26Wrapper;

import android.util.Log;
import android.util.Pair;

import android.os.Handler;
import android.os.Build;
import android.os.IBinder;

import java.io.IOException;


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
	
	private int startOffset = 0, todayOffset, total_start, goal, since_boot, total_days;
	public final static NumberFormat formatter = NumberFormat.getInstance(Locale.getDefault());

    private SensorManager sensorManager; // Sensor manager
    private Sensor sensor;             // Pedometer sensor returned by sensor manager

    private CallbackContext callbackContext; // Keeps track of the JS callback context.

    private final int SENSOR_TYPE = Sensor.TYPE_STEP_COUNTER; // TYPE_STEP_DETECTOR or TYPE_STEP_COUNTER

    /**
     * Constructor
     */
    public PedoListener() {
        this.starttimestamp = 0;
        this.startsteps = 0;
        this.setStatus(PedoListener.STOPPED);
				
		Log.i(TAG, "PedoListener Init service for steps");
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
		
		Log.i(TAG, "PedoListener initialize debug="+Util.isDebug());
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
        
		
        if (action.equals("isStepCountingAvailable")) {
			
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
            Boolean canStepCount = deviceHasStepCounter(getActivity().getPackageManager());
            callbackContext.success(canStepCount ? 1 : 0);

        } else if (action.equals("deviceCheckPermissions")) {
            Log.i(TAG, "deviceCheckPermissions is called");
            final AlertDialog dialog = BatteryOptimizationUtil.getBatteryOptimizationDialog(getActivity());
            if (dialog != null) dialog.show();
            this.win(true);
            return true;

        } else if (action.equals("setConfig")) {
            try {
                Log.i(TAG, "setConfig is called");
                //Log.i(TAG, args.toString());
                Log.i(TAG, args.getJSONObject(0).toString());
                Database db = Database.getInstance(getActivity());
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

            Database db = Database.getInstance(getActivity());
            db.setConfig("status_service", "start");
            db.close();

            if (Build.VERSION.SDK_INT >= 26) {
                API26Wrapper.startForegroundService(getActivity(), new Intent(getActivity(), StepsService.class));
            } else {
                getActivity().startService(new Intent(getActivity(), StepsService.class));
            }
            //getActivity().bindService(stepCounterIntent, mConnection, Context.BIND_AUTO_CREATE);
        
            callbackContext.success(1); 

            // should display each time ?
            final AlertDialog dialog = BatteryOptimizationUtil.getBatteryOptimizationDialog(getActivity());
            if (dialog != null) dialog.show();  
            
            return true;              
        } else if (action.equals("startServiceSilent")) {
            Log.i(TAG, "startServiceSilent is called");

            Database db = Database.getInstance(getActivity());
            db.setConfig("status_service", "start");
            db.close();

            if (Build.VERSION.SDK_INT >= 26) {
                API26Wrapper.startForegroundService(getActivity(), new Intent(getActivity(), StepsService.class));
            } else {
                getActivity().startService(new Intent(getActivity(), StepsService.class));
            }
            //activity.bindService(stepCounterIntent, mConnection, Context.BIND_AUTO_CREATE);
        
            callbackContext.success(1); 
            
            return true; 
        } else if (action.equals("stopService")) {
            Log.i(TAG, "stopService is called");

            Database db = Database.getInstance(getActivity());
            db.setConfig("status_service", "stop");
            db.close();

            getActivity().stopService(new Intent(getActivity(), StepsService.class));
            //getActivity().unbindService(mConnection);
            
            callbackContext.success(1);
            
            return true;
        } else if (action.equals("statusService")) {
            Log.i(TAG, "statusService is called");     
            Database db = Database.getInstance(getActivity());
            String statusService = db.getConfig("status_service");
            db.close();

            callbackContext.success(statusService);
        } else if (action.equals("clean")) {
            Log.i(TAG, "clean is called");
            Database db = Database.getInstance(getActivity());
            db.cleanLinesToSync();
            db.close();

            this.win(true);
            return true;
        } else if (action.equals("reset")) {
            Log.i(TAG, "reset is called");
            Database db = Database.getInstance(getActivity());
            db.resetLinesToSync();
            db.close();

            this.win(true);
            return true;
        } else if (action.equals("rollback")) {
            Log.i(TAG, "rollback is called");
            Database db = Database.getInstance(getActivity());
            db.rollbackLinesToSync();
            db.close();

            this.win(true);
            return true;
        } else if (action.equals("sync")) {
            Log.i(TAG, "sync is called");
            Database db = Database.getInstance(getActivity());
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
            Database db = Database.getInstance(getActivity());
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

                Database db = Database.getInstance(getActivity());
                JSONObject dataToSync = db.getNoSyncResults(false);
                db.close();
                this.win(dataToSync);
                //this.win(this.getStepsJSON(steps));
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return true;
		} else if (action.equals("startStepperUpdates")) {
			start(args);
		} else if (action.equals("stopStepperUpdates")) {
			stop();    
		} else if (action.equals("setNotificationLocalizedStrings")) {
			setNotificationLocalizedStrings(args);
			callbackContext.success();
		} else if (action.equals("setGoal")) {
			setGoal(args);
			callbackContext.success(); 
		} else if (action.equals("getSteps")) {
			getSteps(args);
		} else if (action.equals("getStepsByPeriod")) {
			getStepsByPeriod(args);
		} else if (action.equals("getLastEntries")) {
			getLastEntries(args);				
        } else {
            // Unsupported action
            Log.e(TAG, "Invalid action called on class " + TAG + ", " + action);
            //callbackContext.error("Invalid action called on class " + TAG + ", " + action);
            return false;
        }

        return true;
    }

	public void onStart() {
		initSensor();
	}

	public void onPause(boolean multitasking) {
		status = PedoListener.PAUSED;
		uninitSensor();
	}


    /**
     * Called by the Broker when listener is to be shut down.
     * Stop listener.
     */
    public void onDestroy() {
        //super.onDestroy();
		Log.i(TAG, "PedoListener onDestroy");
        //this.stop();
    }

    /**
     * Start listening for pedometers sensor. (mine)
     */
	 /*
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
            this.sensor = list.get(0);
            if (this.sensorManager.registerListener(this, this.sensor, SensorManager.SENSOR_DELAY_FASTEST)) {
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
	*/
	
	private void start(JSONArray args) throws JSONException {
		startOffset = args.getInt(0);
		final JSONObject options = args.getJSONObject(1);

		// If already starting or running, then return
		if ((status == PedoListener.RUNNING) || (status == PedoListener.STARTING)) {
		  return;
		}

		// Set options
		SharedPreferences prefs =
		  getActivity().getSharedPreferences("pedometer", Context.MODE_PRIVATE);

		if (options.has(PEDOMETER_GOAL_REACHED_FORMAT_TEXT)) {
		  prefs.edit().putString(PEDOMETER_GOAL_REACHED_FORMAT_TEXT, options.getString(PEDOMETER_GOAL_REACHED_FORMAT_TEXT)).commit();
		}

		if (options.has(PEDOMETER_IS_COUNTING_TEXT)) {
		  prefs.edit().putString(PEDOMETER_IS_COUNTING_TEXT, options.getString(PEDOMETER_IS_COUNTING_TEXT)).commit();
		}

		if (options.has(PEDOMETER_STEPS_TO_GO_FORMAT_TEXT)) {
		  prefs.edit().putString(PEDOMETER_STEPS_TO_GO_FORMAT_TEXT, options.getString(PEDOMETER_STEPS_TO_GO_FORMAT_TEXT)).commit();
		}

		if (options.has(PEDOMETER_YOUR_PROGRESS_FORMAT_TEXT)) {
		  prefs.edit().putString(PEDOMETER_YOUR_PROGRESS_FORMAT_TEXT, options.getString(PEDOMETER_YOUR_PROGRESS_FORMAT_TEXT)).commit();
		}

		prefs.edit().putInt("startOffset", startOffset).commit();

		if (Build.VERSION.SDK_INT >= 26) {
		  API26Wrapper.startForegroundService(getActivity(),
			new Intent(getActivity(), StepsService.class));
		} else {
		  getActivity().startService(new Intent(getActivity(), StepsService.class));
		}

		initSensor();
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
			
		if (status != PedoListener.STOPPED) {
		  uninitSensor();
		}

/*
		Database db = Database.getInstance(getActivity());
		db.setConfig("status_service", "stop");
		//db.clear(); // delete all datas on table
		db.close();
		*/

		getActivity().stopService(new Intent(getActivity(), StepsService.class));
		status = PedoListener.STOPPED;

		callbackContext.success();
    }

  private void initSensor() {
    // If already starting or running, then return
    if ((status == PedoListener.RUNNING) || (status == PedoListener.STARTING)
      && status != PedoListener.PAUSED) {
      return;
    }

    Database db = Database.getInstance(getActivity());

    todayOffset = db.getSteps(Util.getToday());

    SharedPreferences prefs =
      getActivity().getSharedPreferences("pedometer", Context.MODE_PRIVATE);

    goal = prefs.getInt(PedoListener.GOAL_PREF_INT, PedoListener.DEFAULT_GOAL);
    since_boot = db.getCurrentSteps();
    int pauseDifference = since_boot - prefs.getInt("pauseCount", since_boot);

    // register a sensor listener to live update the UI if a step is taken
    sensorManager = (SensorManager) getActivity().getSystemService(Context.SENSOR_SERVICE);
    sensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
    if (sensor == null) {
      new AlertDialog.Builder(getActivity()).setTitle("R.string.no_sensor")
        .setMessage("R.string.no_sensor_explain")
        .setOnDismissListener(new DialogInterface.OnDismissListener() {
          @Override
          public void onDismiss(final DialogInterface dialogInterface) {
            getActivity().finish();
          }
        }).setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
        @Override
        public void onClick(final DialogInterface dialogInterface, int i) {
          dialogInterface.dismiss();
        }
      }).create().show();
    } else {
      sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI, 0);
    }

    since_boot -= pauseDifference;

    total_start = db.getTotalWithoutToday();
    total_days = db.getDays();

    db.close();

    status = PedoListener.STARTING;

    updateUI();
  }
  
	private void uninitSensor() {
		try {
		  sensorManager.unregisterListener(this);
		} catch (Exception e) {
		  e.printStackTrace();
		}
		Database db = Database.getInstance(getActivity());
		db.saveCurrentSteps(since_boot);
		db.close();
	}

    /**
     * Sensor listener event.
     * @param event
     */
    @Override
    public void onSensorChanged(final SensorEvent event) {
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

		if (event.values[0] > Integer.MAX_VALUE || event.values[0] == 0) {
			return;
		}
	
		// old code below
        float steps = event.values[0];

        if(this.startsteps == 0)
          this.startsteps = steps;

        steps = steps - this.startsteps;

        //this.win(this.getStepsJSON(steps));
		// ------
		
		if (todayOffset == Integer.MIN_VALUE) {
		  // no values for today
		  // we don`t know when the reboot was, so set today`s steps to 0 by
		  // initializing them with -STEPS_SINCE_BOOT
		  todayOffset = -(int) event.values[0];
		  Database db = Database.getInstance(getActivity());
		  db.insertNewDay(Util.getToday(), (int) event.values[0]);
		  db.close();
		}
		since_boot = (int) event.values[0];

		updateUI();
    }

    /**
     * Called when the view navigates.
     */
    @Override
    public void onReset() {
		Log.i(TAG, "onReset");
		/*
        if (this.status == PedoListener.RUNNING) {
            this.stop();
        }
		*/
    }
	
    /**
     * Called when the accuracy of the sensor has changed.
     */
    @Override
    public void onAccuracyChanged(final Sensor sensor, int accuracy) {
      //nothing to do here
      //return;
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

	private void getSteps(JSONArray args) {
		long date = 0;
		try {
		  date = args.getLong(0);
		}
		catch (JSONException e) {
		  e.printStackTrace();
		  return;
		}

		Database db = Database.getInstance(getActivity());
		int steps = db.getSteps(date);
		db.close();

		JSONObject joresult = new JSONObject();
		try {
		  joresult.put("steps", steps);
		}
		catch (JSONException e) {
		  e.printStackTrace();
		  return;
		}
		callbackContext.success(joresult);
	  }

	  private void getStepsByPeriod(JSONArray args) {
		long startdate = 0;
		long endate = 0;
		try {
		  startdate = args.getLong(0);
		  endate = args.getLong(1);
		}
		catch (JSONException e) {
		  e.printStackTrace();
		  return;
		}

		Database db = Database.getInstance(getActivity());
		int steps = db.getSteps(startdate, endate);
		db.close();

		JSONObject joresult = new JSONObject();
		try {
		  joresult.put("steps", steps);
		}
		catch (JSONException e) {
		  e.printStackTrace();
		  return;
		}
		callbackContext.success(joresult);
	  }
	  
		private void getLastEntries(JSONArray args) {
		int num = 0;
		try {
		  num = args.getInt(0);
		}
		catch (JSONException e) {
		  e.printStackTrace();
		  return;
		}

		Database db = Database.getInstance(getActivity());
		List<Pair<Long, Integer>> entries = db.getLastEntries(num);
		db.close();

		JSONObject joresult = new JSONObject();
		try {
		  JSONArray jaEntries = new JSONArray();
		  for (int i = 0; i < entries.size(); i++) {
			JSONObject joEntry = new JSONObject();
			joEntry.put("data", entries.get(i).first);
			joEntry.put("steps", entries.get(i).second);
			jaEntries.put(joEntry);
		  }
		  joresult.put("entries", jaEntries);
		}
		catch (JSONException e) {
		  e.printStackTrace();
		  return;
		}
		callbackContext.success(joresult);
	  }
  
	private void updateUI() {
		// Today offset might still be Integer.MIN_VALUE on first start
		int steps_today = Math.max(todayOffset + since_boot, 0);
		int total = total_start + steps_today;
		int average = (total_start + steps_today) / total_days;

		JSONObject result = new JSONObject();

		try {
		  result.put("steps_today", steps_today);
		  result.put("total", total);
		  result.put("average", average);
		} catch (JSONException e) {
		  e.printStackTrace();
		}
		
		/*
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
		*/
		

		win(result);
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
	
	private Activity getActivity() {
		return cordova.getActivity();
	}
}
