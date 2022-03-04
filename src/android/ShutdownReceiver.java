package org.apache.cordova.pedometer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import android.util.Log;
//import de.j4velin.pedometer.util.Logger;
import org.apache.cordova.pedometer.util.Util;
import org.apache.cordova.pedometer.util.API26Wrapper;

public class ShutdownReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(final Context context, final Intent intent) {
        //if (BuildConfig.DEBUG) Logger.log("shutting down");
        Log.i("cordova-plugin-pedometer", "shutting down");

        //Intent stepCounterServiceIntent = new Intent(context, StepCounterService.class);
        //context.startService(stepCounterServiceIntent);

		// old line
        //context.startService(new Intent(context, StepsService.class)); // SensorListener.class
		
		if (Build.VERSION.SDK_INT >= 26) {
            API26Wrapper.startForegroundService(context, new Intent(context, StepsService.class));
        } else {
            context.startService(new Intent(context, StepsService.class));
        }

        // if the user used a root script for shutdown, the DEVICE_SHUTDOWN
        // broadcast might not be send. Therefore, the app will check this
        // setting on the next boot and displays an error message if it's not
        // set to true        
        context.getSharedPreferences("pedometer", Context.MODE_PRIVATE).edit()
                .putBoolean("correctShutdown", true).commit();                
                
        Database db = Database.getInstance(context);
        // if it's already a new day, add the temp. steps to the last one
        if (db.getSteps(Util.getToday()) == Integer.MIN_VALUE) {
            int steps = db.getCurrentSteps();
            db.insertNewDay(Util.getToday(), steps);
        } else {
            db.addToLastEntry(db.getCurrentSteps());
        }
        // current steps will be reset on boot @see BootReceiver
        db.close();        
    }

}