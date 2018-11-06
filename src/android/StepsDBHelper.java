package org.apache.cordova.pedometer;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/**
 * {@link SQLiteOpenHelper} that is used as replacement of the localStorage of
 * the webviews.
 * 
 * @details this class should not be used. Everything about the localStorage
 *          through the application is already handled in HTMLFragment.
 * @author Diane taken from
 *         https://github.com/didimoo/AndroidLocalStorage/blob/master
 *         /src/com/example/androidlocalstorage/LocalStorage.java
 */

public class StepsDBHelper extends SQLiteOpenHelper {

    private static StepsDBHelper mInstance;

    /**
     * the name of the table
     */
    public static final String LOCALSTORAGE_TABLE_NAME = "StepsSummary"; // geonotifications

    /**
     * the id column of the table LOCALSTORAGE_TABLE_NAME
     */
    public static final String LOCALSTORAGE_ID = "id"; // _id

    /**
     * the value column of the table LOCALSTORAGE_TABLE_NAME
     */
    public static final String LOCALSTORAGE_VALUE = "value";
	
	private static final String TABLE_STEPS_SUMMARY = "StepsSummary";
	private static final String ID = "id";
	private static final String STEPS_COUNT = "stepscount";
	private static final String CREATION_DATE = "creationdate";//Date format is mm/dd/yyyy


    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "StepsDatabase.db"; // geonotifications.db
	
	private static final String CREATE_TABLE_STEPS_SUMMARY = "CREATE TABLE "
      + TABLE_STEPS_SUMMARY + "(" + ID + " INTEGER PRIMARY KEY AUTOINCREMENT," + CREATION_DATE + " TEXT,"+ STEPS_COUNT + " INTEGER"+")";

			
	/*
	private static final String DICTIONARY_TABLE_CREATE = "CREATE TABLE "
	+ LOCALSTORAGE_TABLE_NAME + " (" + LOCALSTORAGE_ID
	+ " TEXT PRIMARY KEY, " + LOCALSTORAGE_VALUE + " TEXT NOT NULL);";
	*/

    /**
     * Returns an instance of LocalStorage
     * 
     * @param ctx
     *            : a Context used to create the database
     * @return the instance of LocalStorage of the application or a new one if
     *         it has not been created before.
     */
    public static StepsDBHelper getInstance(Context ctx) {
        if (mInstance == null) {
            mInstance = new StepsDBHelper(ctx);
        }
        return mInstance;
    }

    private StepsDBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_STEPS_SUMMARY); // DICTIONARY_TABLE_CREATE
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.w(StepsDBHelper.class.getName(),
                "Upgrading database from version " + oldVersion + " to "
                        + newVersion + ", which will destroy all old data");
        db.execSQL("DROP TABLE IF EXISTS " + LOCALSTORAGE_TABLE_NAME);
        onCreate(db);
    }
	
	public boolean createStepsEntry() {
		boolean isDateAlreadyPresent = false;
		boolean createSuccessful = false;
		int currentDateStepCounts = 0;
		Calendar mCalendar = Calendar.getInstance(); 
		String todayDate = String.valueOf(mCalendar.get(Calendar.MONTH))+"/" + String.valueOf(mCalendar.get(Calendar.DAY_OF_MONTH))+"/"+String.valueOf(mCalendar.get(Calendar.YEAR));
		String selectQuery = "SELECT " + STEPS_COUNT + " FROM " 
		+ TABLE_STEPS_SUMMARY + " WHERE " + CREATION_DATE +" = 
		 '"+ todayDate+"'";
		try {
		  
			  SQLiteDatabase db = this.getReadableDatabase();
			  Cursor c = db.rawQuery(selectQuery, null);
			  if (c.moveToFirst()) {
				  do {
					isDateAlreadyPresent = true;
					currentDateStepCounts = c.getInt((c.getColumnIndex(STEPS_COUNT)));
				  } while (c.moveToNext());
			  }
			  db.close();
		  } catch (Exception e) {
		  e.printStackTrace();
		}
		
		try {
		  SQLiteDatabase db = this.getWritableDatabase();
		  ContentValues values = new ContentValues();
		  values.put(CREATION_DATE, todayDate);
		  if(isDateAlreadyPresent)
		  {
			values.put(STEPS_COUNT, ++currentDateStepCounts);
			int row = db.update(TABLE_STEPS_SUMMARY, values, 
			 CREATION_DATE +" = '"+ todayDate+"'", null);
			if(row == 1)
			{
			  createSuccessful = true;
			}
			db.close();
		  }
		  else
		  {
			values.put(STEPS_COUNT, 1);
			long row = db.insert(TABLE_STEPS_SUMMARY, null, 
			values);
			if(row!=-1)
			{
			  createSuccessful = true;
			}
			db.close();
		  }
		  
		} catch (Exception e) {
		  e.printStackTrace();
		}
		return createSuccessful;
	}
}