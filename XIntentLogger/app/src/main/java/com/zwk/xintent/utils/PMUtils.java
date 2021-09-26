package com.zwk.xintent.utils;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;

import java.util.List;

public class PMUtils {
    public static PackageManager pm;

    public static PackageManager getPm() {
        return pm;
    }

    public static void setPm(PackageManager pm) {
        PMUtils.pm = pm;
    }

    public static String getServiceCmpName(Intent intent) {
//        if (GloblePool.LogConfig.isLog_queryIntent) {
//            return "resolveAbort";
//        }
        String tcmp = "resolveNull";
        if (pm != null) {
            ResolveInfo resolveInfo = pm.resolveService(intent, 0);
            if (resolveInfo != null) {
                ServiceInfo serviceInfo = resolveInfo.serviceInfo;
                tcmp = serviceInfo.packageName + "/" + serviceInfo.name + "<" + serviceInfo.exported + ">";
            }
        }
        return tcmp;
    }

    public static String getFirstBroadcastReceiverCmpName(Intent intent) {
//        if (GloblePool.LogConfig.isLog_queryIntent) {
//            return "resolveAbort";
//        }
        String tcmp = "resolveNull";
        if (pm != null) {
            List<ResolveInfo> resolves = pm.queryBroadcastReceivers(intent, 0);
            if (resolves != null && resolves.size() > 1) {
                ResolveInfo resolveInfo = resolves.get(0);  // for now get first one
                ActivityInfo activityInfo = resolveInfo.activityInfo;
                tcmp = activityInfo.packageName + "/" + activityInfo.name + "<" + activityInfo.exported + ">";
            }
        }
        return tcmp;
    }

    public static String getContentProviderCmpName(String authority) {
//        if (GloblePool.LogConfig.isLog_queryIntent) {
//            return "resolveAbort";
//        }
        String tcmp = "resolveNull";
        if (pm != null) {
            ProviderInfo resolveInfo = pm.resolveContentProvider(authority, 0);
            if (resolveInfo != null) {
                tcmp = resolveInfo.packageName + "/" + resolveInfo.name + "<" + resolveInfo.exported + ">";
            }
        }
        return tcmp;
    }
}
