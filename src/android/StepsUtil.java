
/**
 * Pedometer bridge with Cordova, programmed by Dario Salvi </dariosalvi78@gmail.com>
 * Based on the accelerometer plugin: https://github.com/apache/cordova-plugin-device-motion
 * License: MIT
 */
package org.apache.cordova.pedometer;

import java.util.Calendar;

public abstract class StepsUtil {

    /**
     * @return milliseconds since 1.1.1970 for today 0:00:00 local timezone
     */
    public static long getToday() {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(System.currentTimeMillis());
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /**
     * @return milliseconds since 1.1.1970 for tomorrow 0:00:01 local timezone
     */
    public static long getTomorrow() {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(System.currentTimeMillis());
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 1);
        c.set(Calendar.MILLISECOND, 0);
        c.add(Calendar.DATE, 1);
        return c.getTimeInMillis();
    }

    /**
     * Get Period Time (5min)
     * @return milliseconds since 1.1.1970 for today 0:00:00 local timezone
     */
    public static long getTodayPeriodTime() {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(System.currentTimeMillis());
        //c.set(Calendar.HOUR_OF_DAY, 0);
        //c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);

        Integer curmin = c.get(Calendar.MINUTE);
        if (curmin < 5) {
            c.set(Calendar.MINUTE, 0);
        } else if (curmin >= 5 && curmin < 10) {
            c.set(Calendar.MINUTE, 5);
        } else if (curmin >= 10 && curmin < 15) {
            c.set(Calendar.MINUTE, 10);
        } else if (curmin >= 15 && curmin < 20) {
            c.set(Calendar.MINUTE, 15);
        } else if (curmin >= 20 && curmin < 25) {
            c.set(Calendar.MINUTE, 20);
        } else if (curmin >= 25 && curmin < 30) {
            c.set(Calendar.MINUTE, 25);
        } else if (curmin >= 30 && curmin < 35) {
            c.set(Calendar.MINUTE, 30);
        } else if (curmin >= 35 && curmin < 40) {
            c.set(Calendar.MINUTE, 35);
        } else if (curmin >= 40 && curmin < 45) {
            c.set(Calendar.MINUTE, 40);
        } else if (curmin >= 45 && curmin < 50) {
            c.set(Calendar.MINUTE, 45);
        } else if (curmin >= 50 && curmin < 55) {
            c.set(Calendar.MINUTE, 50);
        } else if (curmin >= 55 && curmin < 60) {
            c.set(Calendar.MINUTE, 55);
        }

        return c.getTimeInMillis();
    }

    // https://tekeye.uk/android/examples/android-debug-vs-release-build
    public static boolean isDebug() {
        //BuildConfig.DEBUG
        return true;
    }

}