package org.apache.cordova.pedometer;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.app.Service;

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
	 //Toast.makeText(this, "Service started...", Toast.LENGTH_LONG).show();
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
    //Toast.makeText(this, "Stop service...", Toast.LENGTH_LONG).show();
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
  public void onSensorChanged(SensorEvent event) {
	  Log.i(TAG, "StepsService onSensorChanged event=" + JSON.stringify(event));
		
        // Only look at step counter events
        if (event.sensor.getType() != this.SENSOR_TYPE) {
            return;
        }
		
	Log.i(TAG, "StepsService onSensorChanged " + event.sensor.getType());
    mStepsDBHelper.createStepsEntry();
	float steps = event.values[0];
	Log.i(TAG, "StepsService onSensorChanged end steps="+steps);
  }
  
  
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // TODO Auto-generated method stub
		
    }

}