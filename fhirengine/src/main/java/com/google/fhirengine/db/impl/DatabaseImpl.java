package com.google.fhirengine.db.impl;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;

import com.google.common.base.Joiner;
import com.google.fhirengine.db.Database;
import com.google.fhirengine.db.ResourceAlreadyExistsException;
import com.google.fhirengine.db.ResourceNotFoundException;

import org.hl7.fhir.r4.model.Resource;

import java.lang.reflect.InvocationTargetException;

import javax.inject.Inject;

import ca.uhn.fhir.parser.IParser;

/** Helper class that manages the FHIR resource database, and provides a database connection. */
public class DatabaseImpl extends SQLiteOpenHelper implements Database {

  private static String DB_NAME = "FHIRDB";
  private static int DB_VERSION = 1;

  /** Table names */
  interface Tables {
    String RESOURCES = "resources";
  }

  /** {@link Tables#RESOURCES} columns. */
  interface ResourcesColumns extends BaseColumns {
    String RESOURCE_TYPE = "resource_type";
    String RESOURCE_ID = "resource_id";
    String RESOURCE = "resource";
  }

  /** Unique indices */
  private interface UniqueIndices {
    String RESOURCE_TYPE_RESOURCE_ID_UNIQUE_INDEX =
        Joiner.on("_")
            .join(Tables.RESOURCES, ResourcesColumns.RESOURCE_TYPE, ResourcesColumns.RESOURCE_ID);
  }

  private static String CREATE_RESOURCES_TABLE =
      "CREATE TABLE " + Tables.RESOURCES + " ( " +
          ResourcesColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
          ResourcesColumns.RESOURCE_TYPE + " TEXT NOT NULL," +
          ResourcesColumns.RESOURCE_ID + " INTEGER NOT NULL," +
          ResourcesColumns.RESOURCE + " TEXT NOT NULL);";
  private static String CREATE_INDEX =
      "CREATE UNIQUE INDEX " + UniqueIndices.RESOURCE_TYPE_RESOURCE_ID_UNIQUE_INDEX + " ON " +
          Tables.RESOURCES + " ( " +
          ResourcesColumns.RESOURCE_TYPE + ", " +
          ResourcesColumns.RESOURCE_ID + ")";

  private final IParser iParser;

  @Inject
  DatabaseImpl(Context context, IParser iParser) {
    super(context, DB_NAME, null, DB_VERSION);
    this.iParser = iParser;
  }

  @Override
  public void onCreate(SQLiteDatabase sqLiteDatabase) {
    sqLiteDatabase.execSQL(CREATE_RESOURCES_TABLE);
    sqLiteDatabase.execSQL(CREATE_INDEX);
  }

  @Override
  public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {
    throw new UnsupportedOperationException("Not implemented yet!");
  }

  @Override
  public <R extends Resource> void insert(R resource) throws ResourceAlreadyExistsException {
    String type = resource.getResourceType().name();
    String id = resource.getId();
    ContentValues contentValues = new ContentValues();
    contentValues.put(ResourcesColumns.RESOURCE_TYPE, type);
    contentValues.put(ResourcesColumns.RESOURCE_ID, id);
    contentValues.put(ResourcesColumns.RESOURCE, iParser.encodeResourceToString(resource));
    try {
      getWritableDatabase().insertOrThrow(Tables.RESOURCES, null, contentValues);
    } catch (SQLiteConstraintException e) {
      throw new ResourceAlreadyExistsException(type, id, e);
    }
  }

  @Override
  public <R extends Resource> void update(R resource) {
    throw new UnsupportedOperationException("Not implemented yet!");
  }

  @Override
  public <R extends Resource> R select(Class<R> clazz, String id) throws ResourceNotFoundException {
    String type;
    try {
      type = clazz.getConstructor().newInstance().getResourceType().name();
    } catch (NoSuchMethodException e) {
      throw new IllegalArgumentException("Cannot resolve resource type for " + clazz.getName(), e);
    } catch (IllegalAccessException e) {
      throw new IllegalArgumentException("Cannot resolve resource type for " + clazz.getName(), e);
    } catch (InstantiationException e) {
      throw new IllegalArgumentException("Cannot resolve resource type for " + clazz.getName(), e);
    } catch (InvocationTargetException e) {
      throw new IllegalArgumentException("Cannot resolve resource type for " + clazz.getName(), e);
    }

    String[] columns = new String[]{ResourcesColumns.RESOURCE};
    String whereClause =
        ResourcesColumns.RESOURCE_TYPE + " = ? AND " + ResourcesColumns.RESOURCE_ID + " = ?";
    String[] whereArgs = new String[]{type, id};
    Cursor cursor = getReadableDatabase()
        .query(Tables.RESOURCES, columns, whereClause, whereArgs, null, null, null);
    try {
      if (cursor == null) {
        throw new SQLException("Null cursor!");
      }
      if (cursor.getCount() == 0) {
        throw new ResourceNotFoundException(type, id);
      }
      if (cursor.getCount() > 1) {
        throw new SQLException("Unexpected number of records!");
      }
      cursor.moveToFirst();
      return iParser.parseResource(clazz, cursor.getString(0));
    } finally {
      cursor.close();
    }
  }

  @Override
  public <R extends Resource> void delete(Class<R> clazz, String id) {
    throw new UnsupportedOperationException("Not implemented yet!");
  }
}
