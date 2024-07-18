package org.apache.cordova.pedometer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import android.util.Log;
//import org.apache.cordova.BuildConfig;
import org.apache.cordova.pedometer.util.API26Wrapper;
//import de.j4velin.pedometer.util.API26Wrapper;
//import de.j4velin.pedometer.util.Logger;

public class AppUpdatedReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(final Context context, final Intent intent) {
        //if (BuildConfig.DEBUG) Logger.log("app updated");
        Log.i("cordova-plugin-pedometer", "app updated");
		
        if (Build.VERSION.SDK_INT >= 26) {
            API26Wrapper.startForegroundService(context, new Intent(context, StepsService.class));
        } else {
            context.startService(new Intent(context, StepsService.class));
        }
    }

}