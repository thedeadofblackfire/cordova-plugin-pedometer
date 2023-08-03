package org.apache.cordova.pedometer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import android.util.Log;
import org.apache.cordova.pedometer.util.API26Wrapper;
//import de.j4velin.pedometer.util.Logger;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(final Context context, final Intent intent) {
		if (intent != null) {
        if (intent.getAction().equalsIgnoreCase(
          Intent.ACTION_BOOT_COMPLETED)) {
			//if (BuildConfig.DEBUG) Logger.log("booted");
			Log.i("cordova-plugin-pedometer", "device booted");

			SharedPreferences prefs = context.getSharedPreferences("pedometer", Context.MODE_PRIVATE);
			
			Database db = Database.getInstance(context);

			if (!prefs.getBoolean("correctShutdown", false)) {
				//if (BuildConfig.DEBUG) Logger.log("Incorrect shutdown");
				Log.i("cordova-plugin-pedometer", "Incorrect shutdown");
				// can we at least recover some steps?
				int steps = Math.max(0, db.getCurrentSteps());
				//if (BuildConfig.DEBUG) Logger.log("Trying to recover " + steps + " steps");
				Log.i("cordova-plugin-pedometer", "Trying to recover " + steps + " steps");
				db.addToLastEntry(steps);
			}
			// last entry might still have a negative step value, so remove that
			// row if that's the case
			db.removeNegativeEntries();
			db.saveCurrentSteps(0);
			db.close();
			prefs.edit().remove("correctShutdown").apply();			
			
			if (Build.VERSION.SDK_INT >= 26) {
				API26Wrapper.startForegroundService(context, new Intent(context, StepsService.class));
			} else {
				context.startService(new Intent(context, StepsService.class));
			}
		  }
		}
    }

}