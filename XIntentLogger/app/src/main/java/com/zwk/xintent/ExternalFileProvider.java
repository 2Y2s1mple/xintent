package com.zwk.xintent;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zwk.xintent.utils.FileUtils;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;

public class ExternalFileProvider extends ContentProvider {
    private static final String TAG = "ExternalFileProvider";
    public static final String authority = "xintent.sdcard.provider";
    private static UriMatcher uriMatcher;
    public static String originFile;
    public static String targetFile;

    static {
        uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        uriMatcher.addURI(authority, "*", 0);
    }

    public ExternalFileProvider() {
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // Implement this to handle requests to delete one or more rows.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public String getType(Uri uri) {
        // TODO: Implement this to handle requests for the MIME type of the data
        // at the given URI.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        // TODO: Implement this to handle requests to insert a new row.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public boolean onCreate() {
        // TODO: Implement this to initialize your content provider on startup.
        return false;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        // TODO: Implement this to handle query requests from clients.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        // TODO: Implement this to handle requests to update one or more rows.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Nullable
    @Override
    public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
        int uid = Binder.getCallingUid();
        Log.d(TAG, uid + " openFile: " + uri);
        if (uid == 1000) {
            if (uriMatcher.match(uri) == 0) {
                try {
                    String fp = getContext().getFilesDir().getCanonicalPath() + File.separator + "xintent";
                    File folder = new File(fp);
                    if (!folder.exists()) {
                        folder.mkdirs();
                    }
                    String fileName = File.separator + "xintent-" + uri.getLastPathSegment() + ".zip";
                    originFile = fp + fileName;
                    targetFile = getContext().getExternalFilesDir(null).getCanonicalPath() + fileName;
                    Log.d(TAG, "openFile: " + originFile);
                    File file = new File(originFile);
                    if (fp.equals(file.getCanonicalFile().getParent())) {
                        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_READ_WRITE);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return super.openFile(uri, mode);
    }

    @Nullable
    @Override
    public Bundle call(@NonNull String method, @Nullable String arg, @Nullable Bundle extras) {
        int uid = Binder.getCallingUid();
        Log.d(TAG, uid + " calling " + method + " " + arg);
        if (uid == 1000 || uid == 2000) {
            // here let shell enter for test only
            // adb shell content call --uri content://xintent.sdcard.provider --method transit
            if ("transit".equals(method)) {
                FileUtils.copyFile(originFile, targetFile);
                Bundle res = new Bundle();
                res.putString("Status", "Success");
                return res;
            }
        }
        return super.call(method, arg, extras);
    }
}
