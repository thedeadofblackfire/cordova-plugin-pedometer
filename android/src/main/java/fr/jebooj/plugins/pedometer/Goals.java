package fr.jebooj.plugins.pedometer;

import android.content.Context;
import android.content.SharedPreferences;

import com.getcapacitor.JSObject;

/**
 * The two goals shown in the persistent notification.
 *
 * <ul>
 *   <li><b>Daily goal</b> ({@link Prefs#GOAL_PREF_INT}): steps of the current <b>local</b> day, the
 *       progress bar. It needs no reset of its own: {@link Database#getSteps(long)} sums the rows of
 *       {@code Util.getToday()}, so the count starts again from 0 at local midnight — only the
 *       notification has to be redrawn, see {@link MidnightReceiver}. {@code <= 1} means no goal
 *       (the legacy Start'R value 1 kept its "no progress bar" meaning).</li>
 *   <li><b>Challenge goal</b>: a multi-day target shown as a text line while
 *       {@code start <= now <= end}. Its count is the server total the app last read
 *       ({@code baseSteps}, at {@code baseAt}) plus the steps recorded locally since — the server may
 *       also count steps from other sources, so it stays the reference.</li>
 * </ul>
 */
public final class Goals {

    private Goals() {
    }

    public static final class Challenge {
        public String id;
        public String name;
        public int goal;
        /** epoch ms */
        public long start;
        /** epoch ms */
        public long end;
        /** server total at {@link #baseAt}; -1 when the app gave none */
        public int baseSteps = -1;
        /** epoch ms */
        public long baseAt;

        public boolean isActive(long now) {
            return goal > 0 && now >= start && (end <= 0 || now <= end);
        }

        JSObject toJSObject(int steps, long now) {
            JSObject o = new JSObject();
            o.put("id", id);
            o.put("name", name);
            o.put("goal", goal);
            o.put("start", start);
            o.put("end", end);
            if (baseSteps >= 0) {
                o.put("baseSteps", baseSteps);
                o.put("baseAt", baseAt);
            }
            o.put("steps", steps);
            o.put("active", isActive(now));
            return o;
        }
    }

    /** The stored daily goal, 0 when there is none. */
    public static int getDailyGoal(final Context context) {
        int goal = Prefs.get(context).getInt(Prefs.GOAL_PREF_INT, Prefs.DEFAULT_GOAL);
        return goal <= 1 ? 0 : goal;
    }

    /** The stored challenge, active or not; null when none was set. */
    public static Challenge getChallenge(final Context context) {
        SharedPreferences prefs = Prefs.get(context);
        if (!prefs.contains(Prefs.CHALLENGE_GOAL)) return null;

        Challenge c = new Challenge();
        c.id = prefs.getString(Prefs.CHALLENGE_ID, "");
        c.name = prefs.getString(Prefs.CHALLENGE_NAME, "");
        c.goal = prefs.getInt(Prefs.CHALLENGE_GOAL, 0);
        c.start = prefs.getLong(Prefs.CHALLENGE_START, 0);
        c.end = prefs.getLong(Prefs.CHALLENGE_END, 0);
        c.baseSteps = prefs.getInt(Prefs.CHALLENGE_BASE_STEPS, -1);
        c.baseAt = prefs.getLong(Prefs.CHALLENGE_BASE_AT, 0);
        return c;
    }

    /** The stored challenge if it is running right now, otherwise null. */
    public static Challenge getActiveChallenge(final Context context, final long now) {
        Challenge c = getChallenge(context);
        return c != null && c.isActive(now) ? c : null;
    }

    public static void setChallenge(final Context context, final Challenge c) {
        SharedPreferences.Editor editor = Prefs.get(context).edit();
        editor.putString(Prefs.CHALLENGE_ID, c.id == null ? "" : c.id);
        editor.putString(Prefs.CHALLENGE_NAME, c.name == null ? "" : c.name);
        editor.putInt(Prefs.CHALLENGE_GOAL, c.goal);
        editor.putLong(Prefs.CHALLENGE_START, c.start);
        editor.putLong(Prefs.CHALLENGE_END, c.end);
        editor.putInt(Prefs.CHALLENGE_BASE_STEPS, c.baseSteps);
        editor.putLong(Prefs.CHALLENGE_BASE_AT, c.baseAt);
        editor.apply();
    }

    public static void clearChallenge(final Context context) {
        Prefs.get(context).edit()
                .remove(Prefs.CHALLENGE_ID)
                .remove(Prefs.CHALLENGE_NAME)
                .remove(Prefs.CHALLENGE_GOAL)
                .remove(Prefs.CHALLENGE_START)
                .remove(Prefs.CHALLENGE_END)
                .remove(Prefs.CHALLENGE_BASE_STEPS)
                .remove(Prefs.CHALLENGE_BASE_AT)
                .apply();
    }

    /**
     * Steps of the challenge: the server total plus the local 5-minute periods that start after it
     * was read (the period straddling {@code baseAt} is left out rather than counted twice). Without
     * a server total, the local steps between {@code start} and {@code end}.
     */
    public static int getChallengeSteps(final Database db, final Challenge c) {
        long to = c.end > 0 ? c.end : Long.MAX_VALUE;
        if (c.baseSteps >= 0) {
            return c.baseSteps + Math.max(0, db.getStepsByPeriodTime(Math.max(c.baseAt, c.start), to));
        }
        return Math.max(0, db.getStepsByPeriodTime(c.start, to));
    }
}
