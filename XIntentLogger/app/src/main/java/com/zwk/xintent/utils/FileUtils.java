package com.zwk.xintent.utils;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class FileUtils {
    private static final String TAG = "FileUtils";

    public static boolean isSDCardMounted(){
        return Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
    }

    // android.os.FileUtils#copy RequiresApi(Q)
    public static void copyFile(String srcFilePath, String dstFilePath) {
        try (InputStream inputStream = new FileInputStream(srcFilePath);
             OutputStream outputStream = new FileOutputStream(dstFilePath);
             ){
            byte[] buffer = new byte[4096];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, len);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public static byte[] readFileToByteArr(String path) {
        File file = new File(path);
        if (!file.exists()) {
            Log.e(TAG, "readFileToByteArr: File doesn't exist!");
            return null;
        }
        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            long fileSize = fileInputStream.getChannel().size();
            if (fileSize == 0) {
                Log.e(TAG, "readFileToByteArr: The FileInputStream has no content!");
                return null;
            }
            byte[] buffer = new byte[fileInputStream.available()];
            fileInputStream.read(buffer);
            return buffer;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
