https://gist.github.com/moopat/e9735fa8b5cff69d003353a4feadcdbc

Usage
The simplest possible usage. Do not forget to check if the dialog is null: if there are no (known) optimizations that the user can turn off the dialog is null.

final AlertDialog dialog = BatteryOptimizationUtil.getBatteryOptimizationDialog(getContext());
if (dialog != null) dialog.show();


You can also implement custom callbacks to e.g. log dialog events. Those callbacks are called additionally to the default callback of the positive button, which always sends the user to the external settings screen.

final AlertDialog dialog = BatteryOptimizationUtil.getBatteryOptimizationDialog(
        getContext(),
        new BatteryOptimizationUtil.OnBatteryOptimizationAccepted() {
            @Override
            public void onBatteryOptimizationAccepted() {
                FirebaseAnalytics.getInstance(getContext()).logEvent("battery_optimizations_accepted", null);
            }
        },
        new BatteryOptimizationUtil.OnBatteryOptimizationCanceled() {
            @Override
            public void onBatteryOptimizationCanceled() {
                FirebaseAnalytics.getInstance(getContext()).logEvent("battery_optimizations_canceled", null);
            }
        });
if (dialog != null) dialog.show();

Additional Manufacturers
If you have information regarding additional manufacturers that have battery optimizations that can be turned off by the user just let me know.