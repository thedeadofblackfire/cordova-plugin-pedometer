package org.apache.cordova.pedometer;


import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
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
import android.os.IBinder;
import android.os.Looper;
import android.support.annotation.Nullable;

import org.json.JSONObject;

import android.util.Log;

public class StepsService extends Service implements SensorEventListener {

  private static final String TAG = "cordova-plugin-pedometer";
  
  private SensorManager mSensorManager;
  private Sensor mStepDetectorSensor;
  private StepsDBHelper mStepsDBHelper;
  
  private static int steps;

  @Override
  public void onCreate() {
    super.onCreate();
    //TYPE_STEP_DETECTOR
	
	Log.i(TAG, "StepsService onCreate");
    mSensorManager = (SensorManager) this.getSystemService(Context.SENSOR_SERVICE);
    if(mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null)
    {
      mStepDetectorSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
      mSensorManager.registerListener(this, mStepDetectorSensor, SensorManager.SENSOR_DELAY_NORMAL);
      mStepsDBHelper = new StepsDBHelper(this);
	  Log.i(TAG, "StepsService onCreate end");
	  //mStepsDBHelper = StepsDBHelper.getInstance(this);
    }
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
	  Log.i(TAG, "StepsService onStartCommand");
	 Toast.makeText(this, "StepsService Service started...", Toast.LENGTH_LONG).show();
	 /*
	  sensorManager = (SensorManager) getApplicationContext()
            .getSystemService(SENSOR_SERVICE);
    lastUpdate = System.currentTimeMillis();
    listen = new SensorListen();
	*/
    return START_STICKY;	
    //return Service.START_STICKY;
  }

  
  @Override
  public void onDestroy() {
	  Log.i(TAG, "StepsService onDestroy");
    Toast.makeText(this, "StepsService Stop service...", Toast.LENGTH_LONG).show();
	//sensorManager.unregisterListener(listen);
    //Toast.makeText(this, "Destroy", Toast.LENGTH_SHORT).show();
    super.onDestroy();
   }

  //@Nullable
  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

    @Override
    public void onTaskRemoved(final Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        //if (BuildConfig.DEBUG) Logger.log("sensor service task removed");
		Log.i(TAG, "StepsService sensor service task removed");
		/*
        // Restart service in 500 ms
        ((AlarmManager) getSystemService(Context.ALARM_SERVICE))
                .set(AlarmManager.RTC, System.currentTimeMillis() + 500, PendingIntent
                        .getService(this, 3, new Intent(this, SensorListener.class), 0));
	*/
    }
	
  @Override
  public void onSensorChanged(SensorEvent event) {
	  Log.i(TAG, "StepsService onSensorChanged type="+event.sensor.getType());
		
        // Only look at step counter events
        if (event.sensor.getType() != Sensor.TYPE_STEP_COUNTER) {
            return;
        }
		
	
    //mStepsDBHelper.createStepsEntry();
	// float steps = event.values[0];
	steps = (int) event.values[0];
	mStepsDBHelper.createStepsEntryValue(steps);
	Log.i(TAG, "StepsService onSensorChanged end steps="+steps);
  }
  
  
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// nobody knows what happens here: step value might magically decrease
        // when this method is called...
        Log.i(TAG, sensor.getName() + " accuracy changed: " + accuracy);
    }

}