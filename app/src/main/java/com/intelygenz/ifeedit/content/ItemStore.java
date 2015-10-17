package com.intelygenz.ifeedit.content;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Provides the database used to save items downloaded from the RSS URL.
 */
public class ItemStore {
	/** Table and column names. */
	public static final String DB_TABLE_NAME = "items";
	//
    public static final String DB_COL_ID = "_id";
	public static final String DB_COL_PUB_DATE = "pub_date";
	public static final String DB_COL_TITLE = "title";
    public static final String DB_COL_LINK = "link";
	public static final String DB_COL_DESCRIPTION = "description";

	public static final String[] DB_COLS = new String[] {
        DB_COL_ID,
		DB_COL_PUB_DATE,
		DB_COL_TITLE,
        DB_COL_LINK,
		DB_COL_DESCRIPTION,
	};
	
    /**
     * Creates the object that provides access to the database.
     */
    public ItemStore(Context context) {
        mDatabaseHelper = new DatabaseHelper(context);
        mDb = mDatabaseHelper.getWritableDatabase();
    }
    
    /**
     * Provides the database to run queries on it.
     */
    public SQLiteDatabase get() {
    	return mDb;
    }
    
    /**
     * Closes the database.
     * Note: if required, create a new instance of this object to open again.
     */
    public void close() {
    	try {
	        mDatabaseHelper.close();
	        mDatabaseHelper = null;
	        mDb.releaseReference();
	        mDb = null;
    	}
    	catch (Exception e) { e.printStackTrace(); }
    }

	/** Database file name in private file system. */
    private static final String DATABASE_NAME = "ifeedit.db";
    private static final int DATABASE_VERSION = 1;

    protected SQLiteDatabase mDb;
    protected DatabaseHelper mDatabaseHelper;

    /**
     * This is a standard helper class for constructing the database.
     */
    private class DatabaseHelper extends SQLiteOpenHelper {
        public DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL (
                "CREATE TABLE " + DB_TABLE_NAME +
                "(" + DB_COL_ID 	    	+ " INTEGER " +
                "," + DB_COL_PUB_DATE       + " LONG    " +
                "," + DB_COL_TITLE 			+ " TEXT    " +
                "," + DB_COL_LINK 			+ " TEXT    " +
            	"," + DB_COL_DESCRIPTION	+ " TEXT    " +
            	")"
            );
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        	// Do nothing.
        }
    }
}
