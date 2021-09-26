package com.zwk.xintent.utils;

import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.zwk.xintent.ExternalFileProvider;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Locale;

import static com.zwk.xintent.utils.FileUtils.readFileToByteArr;

public class DumpUtils {
    private static final String TAG = "DumpUtils";
    public static String basicUri = "content://xintent.sdcard.provider/";
    
    public static void remoteWrite(byte[] content) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault());
        String uri = basicUri + simpleDateFormat.format(System.currentTimeMillis());
        try {
            ParcelFileDescriptor parcelFileDescriptor = GloblePool.getAmsCtx().getContentResolver().openFileDescriptor(Uri.parse(uri), "rwt");
            FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
            FileOutputStream outputStream = new FileOutputStream(fileDescriptor);
            FileChannel fileChannel = outputStream.getChannel();
            fileChannel.write(ByteBuffer.wrap(content));
            fileChannel.force(true);
            fileChannel.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void start() {
        ZipUtils.ZipFolder(GloblePool.xlogFolder, GloblePool.xlogZip);
        byte[] content = readFileToByteArr(GloblePool.xlogZip);
        if (content != null) {
            remoteWrite(content);
        } else {
            remoteWrite("Get xlog zip file fail.".getBytes());
        }

        if (FileUtils.isSDCardMounted()) {
            GloblePool.getAmsCtx().getContentResolver().call(Uri.parse(basicUri), "transit", null, null);
        } else {
            Log.e(TAG, "You don't have available sdcard.");
        }
    }
    

}
