package fr.jebooj.plugins.pedometer;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Shared-preference keys of the pedometer.
 *
 * These constants used to live on {@code PedoListener}, the Cordova entry point, which made
 * {@link StepsService} depend on the plugin class just to read a notification string. They are
 * pulled out here so the service, the worker and the plugin share them without coupling.
 *
 * The preference file name and every key are kept **identical to the Cordova version**: an app
 * updating from the old plugin must keep its goal, its offsets and its notification texts.
 */
public final class Prefs {

    /** Preference file name — unchanged, an upgrade must not lose the stored values. */
    public static final String NAME = "pedometer";

    public static final String GOAL_PREF_INT = "GoalPrefInt";
    public static final String START_OFFSET = "startOffset";
    public static final String PAUSE_COUNT = "pauseCount";

    public static final String PEDOMETER_IS_COUNTING_TEXT = "pedometerIsCountingText";
    public static final String PEDOMETER_STEPS_TO_GO_FORMAT_TEXT = "pedometerStepsToGoFormatText";
    public static final String PEDOMETER_YOUR_PROGRESS_FORMAT_TEXT = "pedometerYourProgressFormatText";
    public static final String PEDOMETER_GOAL_REACHED_FORMAT_TEXT = "pedometerGoalReachedFormatText";

    public static final int DEFAULT_GOAL = 1000;

    private Prefs() {
    }

    public static SharedPreferences get(final Context context) {
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }
}
