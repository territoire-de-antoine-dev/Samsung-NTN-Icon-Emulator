package fr.territoire.satvisual;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

/** Pont lecture seule : paramètres d'affichage non sensibles. */
public final class ConfigProvider extends ContentProvider {
  @Override public boolean onCreate() { return true; }
  @Override public Bundle call(String method,String arg,Bundle extras) {
    return "state".equals(method) && getContext()!=null
      ? VisualConfig.read(getContext()).toBundle() : Bundle.EMPTY;
  }
  @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sortOrder) { return null; }
  @Override public String getType(Uri uri) { return null; }
  @Override public Uri insert(Uri uri,ContentValues values) { throw new UnsupportedOperationException(); }
  @Override public int update(Uri uri,ContentValues values,String where,String[] args) { throw new UnsupportedOperationException(); }
  @Override public int delete(Uri uri,String where,String[] args) { throw new UnsupportedOperationException(); }
}
