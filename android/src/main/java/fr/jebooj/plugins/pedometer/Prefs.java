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
    /** text without a daily goal; falls back to {@link #PEDOMETER_STEPS_TO_GO_FORMAT_TEXT} (legacy) */
    public static final String PEDOMETER_STEPS_TODAY_FORMAT_TEXT = "pedometerStepsTodayFormatText";
    public static final String PEDOMETER_CHALLENGE_FORMAT_TEXT = "pedometerChallengeFormatText";

    /**
     * Dynamic title, see {@code StepsService.buildTitle}. Not set (or empty): the static
     * {@link #PEDOMETER_IS_COUNTING_TEXT} title of the Cordova version.
     */
    public static final String PEDOMETER_TITLE_FORMAT_TEXT = "pedometerTitleFormatText";
    public static final String PEDOMETER_TITLE_GOAL_REACHED_FORMAT_TEXT = "pedometerTitleGoalReachedFormatText";
    public static final String PEDOMETER_TITLE_NO_GOAL_FORMAT_TEXT = "pedometerTitleNoGoalFormatText";
    /** below this many steps today, the title stays {@link #PEDOMETER_IS_COUNTING_TEXT} (warm-up) */
    public static final String PEDOMETER_TITLE_WARMUP_STEPS = "pedometerTitleWarmupSteps";
    public static final int DEFAULT_TITLE_WARMUP_STEPS = 10;

    /** challenge goal, see {@link Goals} */
    public static final String CHALLENGE_ID = "challengeId";
    public static final String CHALLENGE_NAME = "challengeName";
    public static final String CHALLENGE_GOAL = "challengeGoal";
    public static final String CHALLENGE_START = "challengeStart";
    public static final String CHALLENGE_END = "challengeEnd";
    public static final String CHALLENGE_BASE_STEPS = "challengeBaseSteps";
    public static final String CHALLENGE_BASE_AT = "challengeBaseAt";

    /**
     * No daily goal until the user sets one. The Cordova version defaulted to 1000, a target nobody
     * chose; values {@code <= 1} mean "no goal".
     */
    public static final int DEFAULT_GOAL = 0;

    private Prefs() {
    }

    public static SharedPreferences get(final Context context) {
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }
}
