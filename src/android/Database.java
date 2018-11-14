/*
 * Copyright 2013 Thomas Hoffmann
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.cordova.pedometer;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Pair;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Calendar;
import android.util.Log;
//import de.j4velin.pedometer.util.Logger;
//import de.j4velin.pedometer.util.Util;
import org.apache.cordova.pedometer.Logger;
import org.apache.cordova.pedometer.StepsUtil;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.MalformedURLException;
import java.io.IOException;
import java.io.*;
//import org.apache.commons.io.IOUtils;
//import javax.json.JsonReader;
import android.util.JsonReader;

public class Database extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "steps.db";
    private static final int DATABASE_VERSION = 2;

    private static final String TABLE_STEPS = "steps";
    private static final String KEY_STEP_ID = "id";
    private static final String KEY_STEP_STEPS = "steps"; // stepscount
    private static final String KEY_STEP_TOTAL = "total"; // total steps count since last boot = STEPS_COUNT
    private static final String KEY_STEP_DATE = "date"; // integer timestamp
    private static final String KEY_STEP_CREATION_DATE = "creationdate"; // Date format is yyyy-dd-mm
    private static final String KEY_STEP_PERIODTIME = "periodtime";
    private static final String KEY_STEP_STARTDATE = "startdate"; // period range in hour:minute
    private static final String KEY_STEP_ENDDATE = "enddate"; // period range in hour:minute
    private static final String KEY_STEP_LASTUPDATE = "lastupdate";
    private static final String KEY_STEP_SYNCED = "synced";
    private static final String KEY_STEP_SYNCEDDATE = "synceddate";

    // //db.execSQL("CREATE TABLE " + TABLE_STEPS + " (date INTEGER, steps
    // INTEGER)");
    private static final String CREATE_TABLE_STEPS = "CREATE TABLE IF NOT EXISTS " + TABLE_STEPS + "(" + KEY_STEP_ID
            + " INTEGER PRIMARY KEY AUTOINCREMENT," + KEY_STEP_DATE + " INTEGER," + KEY_STEP_CREATION_DATE + " TEXT,"
            + KEY_STEP_PERIODTIME + " INTEGER," + KEY_STEP_STARTDATE + " TEXT," + KEY_STEP_ENDDATE + " TEXT,"
            + KEY_STEP_LASTUPDATE + " INTEGER," + KEY_STEP_STEPS + " INTEGER," + KEY_STEP_TOTAL + " INTEGER, "
            + KEY_STEP_SYNCED + " INTEGER, " + KEY_STEP_SYNCEDDATE + " INTEGER)";

    private static Database sInstance;
    private static final AtomicInteger openCounter = new AtomicInteger();

    private static int lastSaveSteps = 0;
    private static int lastPeriodSteps = 0;
    private static long lastSaveTime;
    private SharedPreferences prefs;

    private Database(final Context context) {
        // for private directory
        // super(context, DATABASE_NAME, null, DATABASE_VERSION);

        // to put on /sdcard/Android/data/{package}/files/StepsDatabase.db
        super(context, context.getExternalFilesDir(null).getAbsolutePath() + "/" + DATABASE_NAME, null,
                DATABASE_VERSION);

        prefs = context.getSharedPreferences("pedometer", Context.MODE_PRIVATE);
    }

    public static synchronized Database getInstance(final Context c) {
        if (sInstance == null) {
            sInstance = new Database(c.getApplicationContext());
        }
        openCounter.incrementAndGet();
        return sInstance;
    }

    @Override
    public void close() {
        if (openCounter.decrementAndGet() == 0) {
            super.close();
        }
    }

    @Override
    public void onCreate(final SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_STEPS);
    }

    @Override
    public void onUpgrade(final SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.w(Database.class.getName(), "Upgrading database from version " + oldVersion + " to " + newVersion
                + ", which will destroy all old data");
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_STEPS);
        onCreate(db);

        /*
         * if (oldVersion == 1) { // drop PRIMARY KEY constraint
         * db.execSQL("CREATE TABLE " + TABLE_STEPS +
         * "2 (date INTEGER, steps INTEGER)"); db.execSQL("INSERT INTO " + TABLE_STEPS +
         * "2 (date, steps) SELECT date, steps FROM " + TABLE_STEPS);
         * db.execSQL("DROP TABLE " + TABLE_STEPS); db.execSQL("ALTER TABLE " +
         * TABLE_STEPS + "2 RENAME TO " + TABLE_STEPS + ""); }
         */
    }

    /**
     * Query the 'steps' table. Remember to close the cursor!
     *
     * @param columns       the colums
     * @param selection     the selection
     * @param selectionArgs the selction arguments
     * @param groupBy       the group by statement
     * @param having        the having statement
     * @param orderBy       the order by statement
     * @return the cursor
     */
    public Cursor query(final String[] columns, final String selection, final String[] selectionArgs,
            final String groupBy, final String having, final String orderBy, final String limit) {
        return getReadableDatabase().query(TABLE_STEPS, columns, selection, selectionArgs, groupBy, having, orderBy,
                limit);
    }

    /**
     * Inserts a new entry in the database, if there is no entry for the given date
     * yet. Steps should be the current number of steps and it's negative value will
     * be used as offset for the new date. Also adds 'steps' steps to the previous
     * day, if there is an entry for that date.
     * <p/>
     * This method does nothing if there is already an entry for 'date' - use
     * {@link #updateSteps} in this case.
     * <p/>
     * To restore data from a backup, use {@link #insertDayFromBackup}
     *
     * @param date  the date in ms since 1970
     * @param steps the current step value to be used as negative offset for the new
     *              day; must be >= 0
     */
    public void insertNewDay(long date, int steps) {
        getWritableDatabase().beginTransaction();
        try {
            Cursor c = getReadableDatabase().query(TABLE_STEPS, new String[] { "date" }, "date = ?",
                    new String[] { String.valueOf(date) }, null, null, null);
            if (c.getCount() == 0 && steps >= 0) {

                // add 'steps' to yesterdays count
                addToLastEntry(steps);

                // add today
                ContentValues values = new ContentValues();
                values.put("date", date);
                // use the negative steps as offset
                values.put("steps", -steps);
                getWritableDatabase().insert(TABLE_STEPS, null, values);
            }
            c.close();
            if (StepsUtil.isDebug()) {
                Logger.log("insertDay " + date + " / " + steps);
                logState();
            }
            getWritableDatabase().setTransactionSuccessful();
        } finally {
            getWritableDatabase().endTransaction();
        }
    }

    /**
     * Adds the given number of steps to the last entry in the database
     *
     * @param steps the number of steps to add
     */
    public void addToLastEntry(int steps) {
        getWritableDatabase().execSQL("UPDATE " + TABLE_STEPS + " SET steps = steps + " + steps
                + " WHERE date = (SELECT MAX(date) FROM " + TABLE_STEPS + ")");
    }

    /**
     * Inserts a new entry in the database, overwriting any existing entry for the
     * given date. Use this method for restoring data from a backup.
     *
     * @param date  the date in ms since 1970
     * @param steps the step value for 'date'; must be >= 0
     * @return true if a new entry was created, false if there was already an entry
     *         for 'date' (and it was overwritten)
     */
    public boolean insertDayFromBackup(long date, int steps) {
        getWritableDatabase().beginTransaction();
        boolean newEntryCreated = false;
        try {
            ContentValues values = new ContentValues();
            values.put("steps", steps);
            int updatedRows = getWritableDatabase().update(TABLE_STEPS, values, "date = ?",
                    new String[] { String.valueOf(date) });
            if (updatedRows == 0) {
                values.put("date", date);
                getWritableDatabase().insert(TABLE_STEPS, null, values);
                newEntryCreated = true;
            }
            getWritableDatabase().setTransactionSuccessful();
        } finally {
            getWritableDatabase().endTransaction();
        }
        return newEntryCreated;
    }

    /**
     * Writes the current steps database to the log
     */
    public void logState() {
        if (StepsUtil.isDebug()) {
            Cursor c = getReadableDatabase().query(TABLE_STEPS, null, null, null, null, null, "date DESC", "5");
            Logger.log(c);
            c.close();
        }
    }

    /**
     * Get the total of steps taken without today's value
     *
     * @return number of steps taken, ignoring today
     */
    public int getTotalWithoutToday() {
        Cursor c = getReadableDatabase().query(TABLE_STEPS, new String[] { "SUM(" + KEY_STEP_STEPS + ")" },
                KEY_STEP_STEPS + " > 0 AND date > 0 AND date < ?",
                new String[] { String.valueOf(StepsUtil.getToday()) }, null, null, null);
        c.moveToFirst();
        int re = c.getInt(0);
        c.close();
        return re;
    }

    /**
     * Get the maximum of steps walked in one day
     *
     * @return the maximum number of steps walked in one day
     */
    public int getRecord() {
        Cursor c = getReadableDatabase().query(TABLE_STEPS, new String[] { "MAX(" + KEY_STEP_STEPS + ")" },
                KEY_STEP_DATE + " > 0", null, null, null, null);
        c.moveToFirst();
        int re = c.getInt(0);
        c.close();
        return re;
    }

    /**
     * Get the maximum of steps walked in one day and the date that happend
     *
     * @return a pair containing the date (Date) in millis since 1970 and the step
     *         value (Integer)
     */
    public Pair<Date, Integer> getRecordData() {
        Cursor c = getReadableDatabase().query(TABLE_STEPS, new String[] { KEY_STEP_DATE + ", " + KEY_STEP_STEPS },
                KEY_STEP_DATE + " > 0", null, null, null, KEY_STEP_STEPS + " DESC", "1");
        c.moveToFirst();
        Pair<Date, Integer> p = new Pair<Date, Integer>(new Date(c.getLong(0)), c.getInt(1));
        c.close();
        return p;
    }

    /**
     * Get the number of steps taken for a specific date.
     * <p/>
     * If date is StepsUtil.getToday(), this method returns the offset which needs
     * to be added to the value returned by getCurrentSteps() to get todays steps.
     *
     * @param date the date in millis since 1970
     * @return the steps taken on this date or Integer.MIN_VALUE if date doesn't
     *         exist in the database
     */
    public int getSteps(final long date) {
        Cursor c = getReadableDatabase().query(TABLE_STEPS, new String[] { "" + KEY_STEP_STEPS },
                KEY_STEP_DATE + " = ?", new String[] { String.valueOf(date) }, null, null, null);
        c.moveToFirst();
        int re;
        if (c.getCount() == 0)
            re = Integer.MIN_VALUE;
        else
            re = c.getInt(0);
        c.close();
        return re;
    }

    /**
     * Gets the last num entries in descending order of date (newest first)
     *
     * @param num the number of entries to get
     * @return a list of long,integer pair - the first being the date, the second
     *         the number of steps
     */
    public List<Pair<Long, Integer>> getLastEntries(int num) {
        Cursor c = getReadableDatabase().query(TABLE_STEPS, new String[] { KEY_STEP_DATE, KEY_STEP_STEPS },
                KEY_STEP_DATE + " > 0", null, null, null, KEY_STEP_DATE + " DESC", String.valueOf(num));
        int max = c.getCount();
        List<Pair<Long, Integer>> result = new ArrayList<>(max);
        if (c.moveToFirst()) {
            do {
                result.add(new Pair<>(c.getLong(0), c.getInt(1)));
            } while (c.moveToNext());
        }
        return result;
    }

    /**
     * Get the number of steps taken between 'start' and 'end' date
     * <p/>
     * Note that todays entry might have a negative value, so take care of that if
     * 'end' >= StepsUtil.getToday()!
     *
     * @param start start date in ms since 1970 (steps for this date included)
     * @param end   end date in ms since 1970 (steps for this date included)
     * @return the number of steps from 'start' to 'end'. Can be < 0 as todays entry
     *         might have negative value
     */
    public int getSteps(final long start, final long end) {
        Cursor c = getReadableDatabase().query(TABLE_STEPS, new String[] { "SUM(" + KEY_STEP_STEPS + ")" },
                KEY_STEP_DATE + " >= ? AND " + KEY_STEP_DATE + " <= ?",
                new String[] { String.valueOf(start), String.valueOf(end) }, null, null, null);
        int re;
        if (c.getCount() == 0) {
            re = 0;
        } else {
            c.moveToFirst();
            re = c.getInt(0);
        }
        c.close();
        return re;
    }

    /**
     * Removes all entries with negative values.
     * <p/>
     * Only call this directly after boot, otherwise it might remove the current day
     * as the current offset is likely to be negative
     */
    void removeNegativeEntries() {
        getWritableDatabase().delete(TABLE_STEPS, "steps < ?", new String[] { "0" });
    }

    /**
     * Removes invalid entries from the database.
     * <p/>
     * Currently, an invalid input is such with steps >= 200,000
     */
    public void removeInvalidEntries() {
        getWritableDatabase().delete(TABLE_STEPS, "steps >= ?", new String[] { "200000" });
    }

    /**
     * Get the number of 'valid' days (= days with a step value > 0).
     * <p/>
     * The current day is not added to this number.
     *
     * @return the number of days with a step value > 0, return will be >= 0
     */
    public int getDaysWithoutToday() {
        Cursor c = getReadableDatabase().query(TABLE_STEPS, new String[] { "COUNT(*)" },
                "steps > ? AND date < ? AND date > 0",
                new String[] { String.valueOf(0), String.valueOf(StepsUtil.getToday()) }, null, null, null);
        c.moveToFirst();
        int re = c.getInt(0);
        c.close();
        return re < 0 ? 0 : re;
    }

    /**
     * Get the number of 'valid' days (= days with a step value > 0).
     * <p/>
     * The current day is also added to this number, even if the value in the
     * database might still be < 0.
     * <p/>
     * It is safe to divide by the return value as this will be at least 1 (and not
     * 0).
     *
     * @return the number of days with a step value > 0, return will be >= 1
     */
    public int getDays() {
        // todays is not counted yet
        int re = this.getDaysWithoutToday() + 1;
        return re;
    }

    /**
     * Saves the current 'steps since boot' sensor value in the database.
     *
     * @param steps since boot
     */
    public void saveCurrentSteps(int steps) {
        ContentValues values = new ContentValues();
        values.put(KEY_STEP_STEPS, steps);
        values.put(KEY_STEP_TOTAL, steps);
        if (getWritableDatabase().update(TABLE_STEPS, values, KEY_STEP_DATE + " = -1", null) == 0) {
            values.put(KEY_STEP_DATE, -1);
            getWritableDatabase().insert(TABLE_STEPS, null, values);
        }
        if (StepsUtil.isDebug()) {
            Logger.log("saving steps in db: " + steps);
        }
    }

    /**
     * Reads the latest saved value for the 'steps since boot' sensor value.
     *
     * @return the current number of steps saved in the database or 0 if there is no
     *         entry
     */
    public int getCurrentSteps() {
        int re = getSteps(-1);
        return re == Integer.MIN_VALUE ? 0 : re;
    }

    public boolean createStepsEntryValue(int steps) {
        Log.i(Database.class.getName(), "StepsService Database createStepsEntryValue steps=" + steps);

        boolean algoWithZeroSteps = true;
        boolean isDateAlreadyPresent = false;
        boolean createSuccessful = false;
        int currentDateStepCounts = 0;
        Calendar mCalendar = Calendar.getInstance();
        String todayDate = String.valueOf(mCalendar.get(Calendar.YEAR)) + "-"
                + String.valueOf(mCalendar.get(Calendar.MONTH)) + "-"
                + String.valueOf(mCalendar.get(Calendar.DAY_OF_MONTH));
        Long date = StepsUtil.getToday();
        Long datePeriodTime = StepsUtil.getTodayPeriodTime();

        // period start >= & < end in 5 min interval
        Calendar mCalendarPeriodStart = Calendar.getInstance();
        mCalendarPeriodStart.setTimeInMillis(datePeriodTime);
        String startDate = "";
        if (mCalendarPeriodStart.get(Calendar.HOUR_OF_DAY) < 10)
            startDate += "0" + String.valueOf(mCalendarPeriodStart.get(Calendar.HOUR_OF_DAY));
        else
            startDate += String.valueOf(mCalendarPeriodStart.get(Calendar.HOUR_OF_DAY));
        startDate += ":";
        if (mCalendarPeriodStart.get(Calendar.MINUTE) < 10)
            startDate += "0" + String.valueOf(mCalendarPeriodStart.get(Calendar.MINUTE));
        else
            startDate += String.valueOf(mCalendarPeriodStart.get(Calendar.MINUTE));

        Calendar mCalendarPeriodEnd = Calendar.getInstance();
        mCalendarPeriodEnd.setTimeInMillis(datePeriodTime);
        mCalendarPeriodEnd.add(Calendar.MINUTE, 5);
        String endDate = "";
        if (mCalendarPeriodEnd.get(Calendar.HOUR_OF_DAY) < 10)
            endDate += "0" + String.valueOf(mCalendarPeriodEnd.get(Calendar.HOUR_OF_DAY));
        else
            endDate += String.valueOf(mCalendarPeriodEnd.get(Calendar.HOUR_OF_DAY));
        endDate += ":";
        if (mCalendarPeriodEnd.get(Calendar.MINUTE) < 10)
            endDate += "0" + String.valueOf(mCalendarPeriodEnd.get(Calendar.MINUTE));
        else
            endDate += String.valueOf(mCalendarPeriodEnd.get(Calendar.MINUTE));

        // last save period time
        lastSaveSteps = prefs.getInt("lastSaveSteps", 0); // pauseCount
        if (lastSaveSteps == 0) {
            lastSaveSteps = steps - 5; // first time we decrease 5 steps to init the process
            if (lastSaveSteps < 0)
                lastSaveSteps = 0; // to prevent zero with boot
            // int pauseDifference = steps - getSharedPreferences("pedometer",
            // Context.MODE_PRIVATE).getInt("pauseCount", steps);

            // getSharedPreferences("pedometer",
            // Context.MODE_PRIVATE).edit().putInt("pauseCount", steps).commit();
        }

        int steps_diff = steps - lastSaveSteps;
        if (steps_diff < 0)
            steps_diff = 0;

        if (algoWithZeroSteps || (!algoWithZeroSteps && steps_diff > 0)) {
            String selectQuery = "SELECT " + KEY_STEP_TOTAL + " FROM " + TABLE_STEPS + " WHERE " + KEY_STEP_PERIODTIME
                    + " = " + datePeriodTime;

            /*
             * String selectQuery = "SELECT " + STEPS_COUNT + " FROM " + TABLE_STEPS +
             * " WHERE " + KEY_STEP_CREATION_DATE + " = '" + todayDate + "'";
             */
            try {

                SQLiteDatabase db = this.getReadableDatabase();
                Cursor c = db.rawQuery(selectQuery, null);
                if (c.moveToFirst()) {
                    do {
                        isDateAlreadyPresent = true;
                        currentDateStepCounts = c.getInt((c.getColumnIndex(KEY_STEP_TOTAL)));
                    } while (c.moveToNext());
                }
                db.close();
            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                SQLiteDatabase db = this.getWritableDatabase();
                ContentValues values = new ContentValues();

                values.put(KEY_STEP_SYNCED, 0);
                values.put(KEY_STEP_SYNCEDDATE, 0); // force to 0 for date or -1
                // use the negative steps as offset
                // values.put(KEY_STEP_STEPS, -steps);
                values.put(KEY_STEP_STEPS, steps_diff);
                values.put(KEY_STEP_TOTAL, steps);
                values.put(KEY_STEP_LASTUPDATE, System.currentTimeMillis());

                if (isDateAlreadyPresent) {
                    // values.put(KEY_STEP_TOTAL, ++currentDateStepCounts);
                    // values.put(KEY_STEP_TOTAL, steps);
                    int row = db.update(TABLE_STEPS, values, KEY_STEP_PERIODTIME + " = " + datePeriodTime, null);
                    // int row = db.update(TABLE_STEPS, values, KEY_STEP_CREATION_DATE + " = '" +
                    // todayDate + "'", null);
                    if (row == 1) {
                        createSuccessful = true;
                    }
                    db.close();
                } else {
                    values.put(KEY_STEP_DATE, date);
                    values.put(KEY_STEP_CREATION_DATE, todayDate);
                    values.put(KEY_STEP_PERIODTIME, datePeriodTime);
                    values.put(KEY_STEP_STARTDATE, startDate);
                    values.put(KEY_STEP_ENDDATE, endDate);
                    // values.put(KEY_STEP_TOTAL, 1);
                    // values.put(KEY_STEP_TOTAL, steps);
                    long row = db.insert(TABLE_STEPS, null, values);
                    if (row != -1) {
                        createSuccessful = true;
                    }
                    db.close();

                    prefs.edit().putInt("lastSaveSteps", steps).commit();
                    lastSaveSteps = steps;
                    lastSaveTime = System.currentTimeMillis();
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (!algoWithZeroSteps && steps_diff == 0) {
            /*
            prefs.edit().putInt("lastSaveSteps", steps).commit();
            lastSaveSteps = steps;
            lastSaveTime = System.currentTimeMillis();
            */
        }
        return createSuccessful;
    }

    // public JSONArray getNoSyncResults() {
    public JSONObject getNoSyncResults(boolean strict) {
        // String myPath = DB_PATH + DB_NAME;// Set path to your database
        // SQLiteDatabase myDataBase = SQLiteDatabase.openDatabase(myPath, null,
        // SQLiteDatabase.OPEN_READONLY);
        String selectQuery = "SELECT * FROM " + TABLE_STEPS + " WHERE " + KEY_STEP_SYNCED + " = 0";
        if (!strict) selectQuery += " and "+KEY_STEP_STEPS+" > 0";

        JSONArray resultSet = new JSONArray();
        JSONObject returnObj = new JSONObject();

        try {
            SQLiteDatabase db = this.getReadableDatabase();
            Cursor cursor = db.rawQuery(selectQuery, null);

            cursor.moveToFirst();
            while (cursor.isAfterLast() == false) {

                int totalColumn = cursor.getColumnCount();
                JSONObject rowObject = new JSONObject();

                for (int i = 0; i < totalColumn; i++) {
                    String cName = cursor.getColumnName(i);
                    if (cName != null) {

                        try {
                            switch (cursor.getType(i)) {
                            case Cursor.FIELD_TYPE_INTEGER:
                                int intVal = cursor.getInt(i);
                                long longVal = cursor.getLong(i);
                                if(intVal == longVal) rowObject.put(cName, intVal);
                                else rowObject.put(cName, longVal);
                                //rowObject.put(cName, cursor.getInt(i));
                                break;
                            case Cursor.FIELD_TYPE_FLOAT:
                                float floatVal = cursor.getFloat(i);
                                double doubleVal = cursor.getDouble(i);
                                if(floatVal == doubleVal) rowObject.put(cName, floatVal);
                                else rowObject.put(cName, doubleVal);
                                //rowObject.put(cName, cursor.getFloat(i));
                                break;
                            case Cursor.FIELD_TYPE_STRING:
                                rowObject.put(cName, cursor.getString(i));
                                break;
                            // case Cursor.FIELD_TYPE_BLOB:
                            // rowObject.put(cName, DataUtils.bytesToHexString(cursor.getBlob(i)));
                            // break;
                            default:
                                rowObject.put(cName, "");
                                break;
                            }
                            /*
                             if (cursor.getString(i) != null) { 
                                 Log.d("TAG_NAME", cursor.getString(i));
                             rowObject.put(cName, cursor.getString(i)); 
                            } else { rowObject.put(cName, "");
                             }
                             */
                        } catch (Exception e) {
                            Log.i(Database.class.getName(), "Exception converting cursor column to json field: " + cName);
                            Log.i(Database.class.getName(), e.getMessage());
                        }
                    }

                }

                resultSet.put(rowObject);
                cursor.moveToNext();
            }

            cursor.close();

            // Log.d("TAG_NAME", resultSet.toString());
            Log.i(Database.class.getName(), "StepsService Database getNoSyncResults steps=" + resultSet.toString());

            returnObj.put("items", resultSet);
            returnObj.put("dateUpdate", System.currentTimeMillis());
            returnObj.put("user_id", "1000");
            
            db.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return returnObj;
    }

    /**
     * Adds the given number of steps to the last entry in the database
     *
     * @param oldStatus = 0
     *  @param newStatus = 1
     */
    public void updateLinesSynced(int oldStatus, int newStatus) {
        getWritableDatabase().execSQL("UPDATE " + TABLE_STEPS + " SET "+KEY_STEP_SYNCED+" = "+newStatus+", "+KEY_STEP_SYNCEDDATE+" = " + System.currentTimeMillis()
                + " WHERE "+KEY_STEP_SYNCED+" = "+oldStatus);
    }
    
    /**
     * Api send to server for syncing operations
     */
    public JSONObject sendToServer(String query, String json) throws IOException, JSONException {
        //String query = "https://example.com"; // + user_id
        //String json = "{\"key\":1}";
    
        URL url = new URL(query);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(5000);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        //  conn.setRequestProperty ("Content-Type","application/x-www-form-urlencoded");
        conn.setDoOutput(true);
        conn.setDoInput(true);
        conn.setRequestMethod("POST"); // GET
    
        OutputStream os = conn.getOutputStream();
        os.write(json.getBytes("UTF-8"));
        os.close();
    
        // read the response
        InputStream in = new BufferedInputStream(conn.getInputStream());
        //String result = org.apache.commons.io.IOUtils.toString(in, "UTF-8");
        //JSONObject jsonObject = new JSONObject(result);
        JsonReader reader = new JsonReader(new InputStreamReader(in, "UTF-8")); 
        //JSONObject jsonObject = reader.readObject();
        JSONObject jsonObject = new JSONObject(reader.toString());
        Log.i(Database.class.getName(), "StepsService Database sendToServer response=" + jsonObject.toString());
        reader.close();

        in.close();
        conn.disconnect();
    
        return jsonObject;
    }
}