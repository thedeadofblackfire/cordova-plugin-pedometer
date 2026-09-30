package fr.jebooj.plugins.pedometer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import android.util.Log;
import fr.jebooj.plugins.pedometer.util.API26Wrapper;
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

			// No "recover the steps of an incorrect shutdown" any more: it added the pre-reboot
			// counter to every period of the last day (see Database, insertNewDay() removed). The
			// periods already hold their deltas.
			// Cordova-era rows may still carry a negative day offset, so remove them
			db.removeNegativeEntries();
			db.saveCurrentSteps(0);
			db.close();
			// the counter restarted from 0: the next reading is counted from 0, not from the
			// pre-reboot lastSaveSteps (the steps after boot were dropped until it caught up)
			prefs.edit().remove("correctShutdown").putBoolean(Prefs.COUNTER_RESET, true).apply();

			// only when the user had started it and ACTIVITY_RECOGNITION is still granted
			ServiceControl.startIfEnabled(context, "device booted");
		  }
		}
    }

}