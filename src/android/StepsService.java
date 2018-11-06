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

import org.json.JSONObject;

public class StepsService extends Service implements SensorEventListener {

  private SensorManager mSensorManager;
  private Sensor mStepDetectorSensor;
  private StepsDBHelper mStepsDBHelper;

  @Override
  public void onCreate() {
    super.onCreate();

    mSensorManager = (SensorManager) this.getSystemService(Context.SENSOR_SERVICE);
    if(mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) != null)
    {
      mStepDetectorSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
      mSensorManager.registerListener(this, mStepDetectorSensor, SensorManager.SENSOR_DELAY_NORMAL);
      mStepsDBHelper = new StepsDBHelper(this);
	  //mStepsDBHelper = StepsDBHelper.getInstance(this);
    }
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
	 //Toast.makeText(this, "Service started...", Toast.LENGTH_LONG).show();
    return Service.START_STICKY;
  }

  
  @Override
  public void onDestroy() {
    //Toast.makeText(this, "Stop service...", Toast.LENGTH_LONG).show();

   }

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  public void onSensorChanged(SensorEvent event) {
    mStepsDBHelper.createStepsEntry();
  }

}