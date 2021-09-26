package com.zwk.xintent.hook.r;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.elvishew.xlog.XLog;
import com.zwk.xintent.utils.BundleUtils;
import com.zwk.xintent.utils.GloblePool;
import com.zwk.xintent.utils.PMUtils;

import java.lang.reflect.Member;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Pattern;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Hook's type: none
 * Usage:
 * in XposedModule
 * new BroadcastIntentLockedHook.hook(Method);
 * or	new BroadcastIntentLockedHook.hook(Class,MethodRegx);
 */
public class BroadcastIntentLockedHook extends XC_MethodHook {
    public Member method;
    public Object thisObject;
    public Object[] args;
    private Object result = null;

    // Lcom/android/server/am/ActivityManagerService;->broadcastIntentLocked(Lcom/android/server/am/ProcessRecord;Ljava/lang/String;Ljava/lang/String;Landroid/content/Intent;Ljava/lang/String;Landroid/content/IIntentReceiver;ILjava/lang/String;Landroid/os/Bundle;[Ljava/lang/String;ILandroid/os/Bundle;ZZIIIIIZ[I)I
    @Override
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
        gatherInfo(param);
        if (!GloblePool.LogConfig.isLog_sendBroadcast) {
            return;
        }
        // Lcom/android/server/am/ActivityManagerService;->broadcastIntentLocked(Lcom/android/server/am/ProcessRecord;Ljava/lang/String;Ljava/lang/String;Landroid/content/Intent;Ljava/lang/String;Landroid/content/IIntentReceiver;ILjava/lang/String;Landroid/os/Bundle;[Ljava/lang/String;ILandroid/os/Bundle;ZZIIIIIZ[I)I

        // broadcastIntentLocked(ProcessRecord callerApp0, String callerPackage1, String callerFeatureId2, Intent intent3, String resolvedType4,
            // IIntentReceiver resultTo5, int resultCode6, String resultData7, Bundle resultExtras8, String[] requiredPermissions9, int appOp10,
            // Bundle bOptions11, boolean ordered12, boolean sticky13, int callingPid14, int callingUid15, int realCallingUid16, int realCallingPid17,
            // int userId18, boolean allowBackgroundActivityStarts19, int[] broadcastWhitelist20)

        String callerPackage1 = (String) args[1];
        String callerFeatureId2 = (String) args[2];
        Intent intent3 = (Intent) args[3];
        String resolvedType4 = (String) args[4];
        int resultCode6 = (int) args[6];
        String resultData7 = (String) args[7];
        Bundle resultExtras8 = (Bundle) args[8];
        String[] requiredPermissions9 = (String[]) args[9];
        int appOp10 = (int) args[10];
        boolean ordered12 = (boolean) args[12];
        boolean sticky13 = (boolean) args[13];
        int callingPid14 = (int) args[14];
        int callingUid15 = (int) args[15];
        int realCallingUid16 = (int) args[16]; // from PI sendInner
        int realCallingPid17 = (int) args[17]; // from PI sendInner
        int userId18 = (int) args[18];
        boolean allowBackgroundActivityStarts19 = (boolean) args[19];
        int[] broadcastWhitelist20 = (int[]) args[20];

        Bundle xb = intent3.getExtras();
        String ex = BundleUtils.parseBundle(xb, 0);

        //int uid = Binder.getCallingUid();
        //String uidName = MyXposed.getPm().getNameForUid(uid);  // contains uid, e.g: oppo.uid.launcher:10065

        // For implicit xintent, without cmp, we invoke resolve actively.
        String tcmp = PMUtils.getFirstBroadcastReceiverCmpName(intent3);

        String log;
        if (xb == null) {
            log = String.format("sendBroadcast { calling=%s:%d:%d, rc=[%d]%d:%d, cFId=%s, rType=%s, rCode=%d, rData=%s, rExtras=%s, " +
                            "BgStart=%b, appOp=%d, ordered=%b, sticky=%b, whitelist=%s" +
                            "target=%s, rpermission=%s, intent=%s }\n",
                    callerPackage1, callingUid15, callingPid14,
                    userId18, realCallingUid16, realCallingPid17,
                    callerFeatureId2, resolvedType4, resultCode6, resultData7, resultExtras8,
                    allowBackgroundActivityStarts19, appOp10, ordered12, sticky13,
                    Arrays.toString(broadcastWhitelist20),
                    tcmp,
                    Arrays.toString(requiredPermissions9),
                    intent3.toString()
            );
        } else {
            log = String.format("sendBroadcast { calling=%s:%d:%d, rc=[%d]%d:%d, cFId=%s, rType=%s, rCode=%d, rData=%s, rExtras=%s, " +
                            "BgStart=%b, appOp=%d, ordered=%b, sticky=%b, whitelist=%s " +
                            "target=%s, rpermission=%s, intent=%s extras=%s }\n",
                    callerPackage1, callingUid15, callingPid14,
                    userId18, realCallingUid16, realCallingPid17,
                    callerFeatureId2, resolvedType4, resultCode6, resultData7, resultExtras8,
                    allowBackgroundActivityStarts19, appOp10, ordered12, sticky13,
                    Arrays.toString(broadcastWhitelist20),
                    tcmp,
                    Arrays.toString(requiredPermissions9),
                    intent3.toString(),
                    ex
            );
        }
        Log.i(GloblePool.KTAG, log);
        if (GloblePool.LogConfig.isLog_saveToStorage) {
            XLog.d(log);
        }
    }

    /**
     * @description: Hook specified method.
     * @author: Monkeylord
     **/
    public void hook(Member method) {
        XposedBridge.hookMethod(method, this);
    }

    /**
     * @description: Hook all methods in given class, which match given RegExpression.
     * @author: Monkeylord
     **/
    public void hook(Class clz, String methodRegEx) {
        Pattern pattern = Pattern.compile(methodRegEx);
        for (Member method : clz.getDeclaredMethods()) {
            if (pattern.matcher(method.getName()).matches()) hook(method);
        }
    }

    /**
     * @description: If the given class is loaded, hook all matched methods in given class, if not, wait until it load, then hook all matched methods in given class. Warning: It's performance-costly.
     * @author: Monkeylord
     **/
    public void hook(String clz, final String methodRegEx, ClassLoader classLoader) {
        final String clzn = clz;
        try {
            Class clazz = Class.forName(clz, false, classLoader);
            hook(clazz, methodRegEx);
        } catch (ClassNotFoundException e) {
            //XposedBridge.log(clzn+" not Found,waiting.");
            final ArrayList<Unhook> unhooks = new ArrayList<>();
            unhooks.add(XposedHelpers.findAndHookMethod("java.lang.ClassLoader", classLoader, "loadClass", String.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    super.afterHookedMethod(param);
                    String clazz = (String) param.args[0];
                    if (clazz.equals(clzn)) {
                        if (param.getResult() != null) {
                            //XposedBridge.log(clzn+" loaded, hooking");
                            hook((Class) param.getResult(), methodRegEx);
                            unhooks.get(0).unhook();
                        }
                    }
                }
            }));
        }
    }

    private void gatherInfo(MethodHookParam param) {
        method = param.method;
        thisObject = param.thisObject;
        args = param.args;
    }
}
