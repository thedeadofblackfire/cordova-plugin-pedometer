/*
 * MIT License
 *
 * Copyright (c) 2018 Markus Deutsch
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.apache.cordova.pedometer;
//package at.moop.util;

import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.support.annotation.Nullable;
//import android.support.v7.app.AlertDialog;
import android.app.AlertDialog;

import java.util.ArrayList;
import java.util.List;

//import at.moop.R;

/**
 * Get a dialog that informs the user to disable battery optimization for your app.
 * <p>
 * Use the dialog like that:
 * final AlertDialog dialog = BatteryOptimizationUtil.getBatteryOptimizationDialog(context);
 * if(dialog != null) dialog.show();
 * <p>
 * Alter the dialog texts so that they fit your needs. You can provide additional actions that
 * should be performed if the positive or negative button are clicked by using the provided method:
 * getBatteryOptimizationDialog(Context, OnBatteryOptimizationAccepted, OnBatteryOptimizationCanceled)
 * <p>
 * Source: https://gist.github.com/moopat/e9735fa8b5cff69d003353a4feadcdbc
 * <p>
 * @author Markus Deutsch @moopat
 */
public class BatteryOptimizationUtil {

    private static final String dialog_battery_title = "Autoriser Booj"; //Enable AutoStart
    private static final String dialog_battery_message = "Pour utiliser notre capteur interne de pas, merci d'autoriser %s dans les applications protégées ou de cocher la case Démarrage automatique."; // "Please allow AppName to always run in the background,else our services can't be accessed.";
    private static final String dialog_battery_button_positive = "Aller aux paramètres"; // ALLOW or Go to settings
    private static final String dialog_battery_button_negative = "Ignorer"; // CANCEL

    private static String application_name;

    /**
     * Get the battery optimization dialog.
     * By default the dialog will send the user to the relevant activity if the positive button is
     * clicked, and closes the dialog if the negative button is clicked.
     *
     * @param context Context
     * @return the dialog or null if battery optimization is not available on this device
     */
    @Nullable
    public static AlertDialog getBatteryOptimizationDialog(final Context context) {
        return getBatteryOptimizationDialog(context, null, null);
    }

    /**
     * Get the battery optimization dialog.
     * By default the dialog will send the user to the relevant activity if the positive button is
     * clicked, and closes the dialog if the negative button is clicked. Callbacks can be provided
     * to perform additional actions on either button click.
     *
     * @param context          Context
     * @param positiveCallback additional callback for the positive button. can be null.
     * @param negativeCallback additional callback for the negative button. can be null.
     * @return the dialog or null if battery optimization is not available on this device
     */
    @Nullable
    public static AlertDialog getBatteryOptimizationDialog(
            final Context context,
            @Nullable final OnBatteryOptimizationAccepted positiveCallback,
            @Nullable final OnBatteryOptimizationCanceled negativeCallback) {

            application_name = context.getApplicationInfo().loadLabel(context.getPackageManager()).toString();
        /*
         * If there is no resolvable component return right away. We do not use
         * isBatteryOptimizationAvailable() for this check in order to avoid checking for
         * resolvable components twice.
         */
        final ComponentName componentName = getResolveableComponentName(context);
        if (componentName == null) return null;

        // R.string.dialog_battery_title
        return new AlertDialog.Builder(context)
                //.setIcon(Android.Resource.Drawable.IcDialogAlert)
                .setTitle(dialog_battery_title)
                //.setMessage(dialog_battery_message)
                .setMessage(String.format(dialog_battery_message, application_name))
                .setNegativeButton(dialog_battery_button_negative, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (negativeCallback != null)
                            negativeCallback.onBatteryOptimizationCanceled();
                    }
                })
                .setPositiveButton(dialog_battery_button_positive, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (positiveCallback != null)
                            positiveCallback.onBatteryOptimizationAccepted();

                        final Intent intent = new Intent();
                        intent.setComponent(componentName);
                        context.startActivity(intent);
                    }
                }).create();
    }

    /**
     * Find out if battery optimization settings are available on this device.
     *
     * @param context Context
     * @return true if battery optimization is available
     */
    public static boolean isBatteryOptimizationAvailable(final Context context) {
        return getResolveableComponentName(context) != null;
    }

    @Nullable
    private static ComponentName getResolveableComponentName(final Context context) {
        for (ComponentName componentName : getComponentNames()) {
            final Intent intent = new Intent();
            intent.setComponent(componentName);
            if (context.getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null)
                return componentName;
        }
        return null;
    }

    /**
     * Get a list of all known ComponentNames that provide battery optimization on different
     * devices.
     * Based on Shivam Oberoi's answer on StackOverflow: https://stackoverflow.com/a/48166241/2143225
     * https://stackoverflow.com/questions/31638986/protected-apps-setting-on-huawei-phones-and-how-to-handle-it
     *
     * @return list of ComponentName
     */
    private static List<ComponentName> getComponentNames() {
        final List<ComponentName> names = new ArrayList<>();
        names.add(new ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"));
        names.add(new ComponentName("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity"));
        names.add(new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"));
        names.add(new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"));
        names.add(new ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"));        
        names.add(new ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"));
        names.add(new ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"));

        // extra
        names.add(new ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"));
        names.add(new ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"));
        names.add(new ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"));
        names.add(new ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.entry.FunctionActivity"));
        names.add(new ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"));
        names.add(new ComponentName("com.htc.pitroad", "com.htc.pitroad.landingpage.activity.LandingPageActivity"));
        names.add(new ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.MainActivity"));
        names.add(new ComponentName("com.dewav.dwappmanager", "com.dewav.dwappmanager.memory.SmartClearupWhiteList"));
        
        return names;
    }

    public interface OnBatteryOptimizationAccepted {

        /**
         * Called if the user clicks the "OK" button of the battery optimization dialog. This does
         * not mean that the user has performed the necessary steps to exclude the app from
         * battery optimizations.
         */
        void onBatteryOptimizationAccepted();

    }

    public interface OnBatteryOptimizationCanceled {

        /**
         * Called if the user clicks the "Cancel" button of the battery optimization dialog.
         */
        void onBatteryOptimizationCanceled();

    }

}

/*  
  private static List<Intent> POWERMANAGER_INTENTS = new List<Intent>()
    {
        new Intent().SetComponent(new ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
        new Intent().SetComponent(new ComponentName("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity")),
        new Intent().SetComponent(new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")),
        new Intent().SetComponent(new ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
        new Intent().SetComponent(new ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")),
        new Intent().SetComponent(new ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")),
        new Intent().SetComponent(new ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
        new Intent().SetComponent(new ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")),
        new Intent().SetComponent(new ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
        new Intent().SetComponent(new ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.entry.FunctionActivity")).SetData(Android.Net.Uri.Parse("mobilemanager://function/entry/AutoStart"))
    };

  

public static void StartPowerSaverIntent(Context context)
    {
        ISharedPreferences settings = context.GetSharedPreferences("ProtectedApps", FileCreationMode.Private);
        bool skipMessage = settings.GetBoolean("skipAppListMessage", false);
        if (!skipMessage)
        {
            ISharedPreferencesEditor editor = settings.Edit();
            foreach (Intent intent in POWERMANAGER_INTENTS)
            {
                if (context.PackageManager.ResolveActivity(intent, PackageInfoFlags.MatchDefaultOnly) != null)
                {
                    var dontShowAgain = new Android.Support.V7.Widget.AppCompatCheckBox(context);
                    dontShowAgain.Text = "Do not show again";
                    dontShowAgain.CheckedChange += (object sender, CompoundButton.CheckedChangeEventArgs e) =>
                    {
                        editor.PutBoolean("skipAppListMessage", e.IsChecked);
                        editor.Apply();
                    };

                    new AlertDialog.Builder(context)
                    .SetIcon(Android.Resource.Drawable.IcDialogAlert)
                    .SetTitle(string.Format("Add {0} to list", context.GetString(Resource.String.app_name)))
                    .SetMessage(string.Format("{0} requires to be enabled/added in the list to function properly.\n", context.GetString(Resource.String.app_name)))
                    .SetView(dontShowAgain)
                    .SetPositiveButton("Go to settings", (o, d) => context.StartActivity(intent))
                    .SetNegativeButton(Android.Resource.String.Cancel, (o, d) => { })
                    .Show();

                    break;
                }
            }
        }
    }
*/