package fr.jebooj.plugins.pedometer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import android.util.Log;

/**
 * Restarts the service after the app is updated — only when the user had started it and
 * ACTIVITY_RECOGNITION is still granted (see {@link ServiceControl}).
 */
public class AppUpdatedReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(final Context context, final Intent intent) {
        Log.i("cordova-plugin-pedometer", "app updated");

        ServiceControl.startIfEnabled(context, "app updated");
    }

}
