package com.zwk.xintent.utils;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.StringTokenizer;

import javax.security.auth.login.LoginException;

public class GloblePool {
    public static Context amsCtx;
    public static int nsport = 18887;
    public static String KTAG = "XIntentLogger";
    public static String shadowLogConfigs = "/data/system/xintent/configs";
    public static String xlogFolder = "/data/system/xintent/logs/";
    public static String xlogZip = "/data/system/xintent/logs.zip";

    public static Context getAmsCtx() {
        return amsCtx;
    }

    public static void setAmsCtx(Context amsCtx) {
        GloblePool.amsCtx = amsCtx;
    }

    public static class LogConfig {
        private static final String TAG = "LogConfig";

        public static boolean isLog_startActivity = true;
        public static boolean isLog_getContentProvider = true;
        public static boolean isLog_sendBroadcast = true;
        public static boolean isLog_registerReceiver = true;
        public static boolean isLog_startService = true;
        public static boolean isLog_bindService = true;
        public static boolean isLog_processStart = true;
        public static boolean isLog_saveToStorage = true;
        public static boolean isLog_queryIntent = false;

        public static void tryBackupConfigs() {
            try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(new FileOutputStream(shadowLogConfigs))) {
                objectOutputStream.writeBoolean(isLog_startActivity);
                objectOutputStream.writeBoolean(isLog_getContentProvider);
                objectOutputStream.writeBoolean(isLog_sendBroadcast);
                objectOutputStream.writeBoolean(isLog_registerReceiver);
                objectOutputStream.writeBoolean(isLog_startService);
                objectOutputStream.writeBoolean(isLog_bindService);
                objectOutputStream.writeBoolean(isLog_processStart);
                objectOutputStream.writeBoolean(isLog_saveToStorage);
                objectOutputStream.writeBoolean(isLog_queryIntent);
                objectOutputStream.flush();
            } catch (FileNotFoundException e) {
                Log.e(TAG, "shaodow LogConfigs not found.");
                e.printStackTrace();
            } catch (IOException e) {
                Log.e(TAG, "IOException: ");
                e.printStackTrace();
            }
        }

        public static void tryRestoreConfigs() {
            try (ObjectInputStream objectInputStream = new ObjectInputStream(new FileInputStream(shadowLogConfigs))) {
                isLog_startActivity = objectInputStream.readBoolean();
                isLog_getContentProvider = objectInputStream.readBoolean();
                isLog_sendBroadcast = objectInputStream.readBoolean();
                isLog_registerReceiver = objectInputStream.readBoolean();
                isLog_startService = objectInputStream.readBoolean();
                isLog_bindService = objectInputStream.readBoolean();
                isLog_processStart = objectInputStream.readBoolean();
                isLog_saveToStorage = objectInputStream.readBoolean();
                isLog_queryIntent = objectInputStream.readBoolean();
                Log.e(TAG, "tryRestoreConfigs: " + printStatus());
            } catch (FileNotFoundException e) {
                Log.e(TAG, "shaodow LogConfigs not found.");
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public static void updateLogConfig(String key, String value) {
            //sA:false,gCP:false,sB:false,rR:true,sS:false,bS:false,sP:true,xlog:true,qI:true
            switch (key) {
                case "sA":
                    isLog_startActivity = value.equalsIgnoreCase("true"); break;
                case "gCP":
                    isLog_getContentProvider = value.equalsIgnoreCase("true"); break;
                case "sB":
                    isLog_sendBroadcast = value.equalsIgnoreCase("true"); break;
                case "rR":
                    isLog_registerReceiver = value.equalsIgnoreCase("true"); break;
                case "sS":
                    isLog_startService = value.equalsIgnoreCase("true"); break;
                case "bS":
                    isLog_bindService = value.equalsIgnoreCase("true"); break;
                case "xlog":
                    isLog_saveToStorage = value.equalsIgnoreCase("true"); break;
                case "sP":
                    isLog_processStart = value.equalsIgnoreCase("true"); break;
                case "qI":
                    isLog_queryIntent = value.equalsIgnoreCase("true"); break;
                default:
                    Log.e(TAG, "updateLogConfig: unknown key = " + key + " value = " + value);
                    break;
            }
        }

        public static String printStatus() {
            StringBuilder sb = new StringBuilder();
            sb.append("sA:").append(isLog_startActivity).append(" ");
            sb.append("gCP:").append(isLog_getContentProvider).append(" ");
            sb.append("sB:").append(isLog_sendBroadcast).append(" ");
            sb.append("rR:").append(isLog_registerReceiver).append(" ");
            sb.append("sS:").append(isLog_startService).append(" ");
            sb.append("bS:").append(isLog_bindService).append(" ");
            sb.append("sP:").append(isLog_processStart).append(" ");
            sb.append("xlog:").append(isLog_saveToStorage).append(" ");
            sb.append("qI:").append(isLog_queryIntent).append(" ");
            return sb.toString();
        }
    }
}
