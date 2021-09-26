package com.zwk.xintent.hook.p;

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

    // Lcom/android/server/am/ActivityManagerService;->broadcastIntentLocked(Lcom/android/server/am/ProcessRecord;Ljava/lang/String;Landroid/content/Intent;Ljava/lang/String;Landroid/content/IIntentReceiver;ILjava/lang/String;Landroid/os/Bundle;[Ljava/lang/String;ILandroid/os/Bundle;ZZIII)I
    @Override
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
        gatherInfo(param);
        if (!GloblePool.LogConfig.isLog_sendBroadcast) {
            return;
        }
        // broadcastIntentLocked(ProcessRecord callerApp0, String callerPackage1, Intent intent2, String resolvedType3, IIntentReceiver resultTo4, int resultCode5,
            // String resultData6, Bundle resultExtras7, String[] requiredPermissions8, int appOp9, Bundle bOptions10, boolean ordered11, boolean sticky12,
            // int callingPid13, int callingUid14, int userId15)

        String callerPackage = (String) args[1];
        Intent intent = (Intent) args[2];
        String resolvedType = (String) args[3];
        int resultCode = (int) args[5];
        String resultData = (String) args[6];
        Bundle resultExtras = (Bundle) args[7];
        String[] requiredPermissions = (String[]) args[8];
        int appOp9 = (int) args[9];
        boolean ordered11 = (boolean) args[11];
        boolean sticky12 = (boolean) args[12];
        int callingPid13 = (int) args[13];
        int callingUid14 = (int) args[14];
        int userId15 = (int) args[15];

        Bundle xb = intent.getExtras();
        String ex = BundleUtils.parseBundle(xb, 0);

        //int uid = Binder.getCallingUid();
        //String uidName = MyXposed.getPm().getNameForUid(uid);  // contains uid, e.g: oppo.uid.launcher:10065

        // For implicit xintent, without cmp, we invoke resolve actively.
        String tcmp = PMUtils.getFirstBroadcastReceiverCmpName(intent);

        String log;
        if (xb == null) {
            log = String.format("sendBroadcast { calling=%s:%d:%d, rType=%s, rCode=%d, rData=%s, rExtras=%s, " +
                            "appOp=%d, ordered=%b, sticky=%b, target=%s, rpermission=%s, intent=%s }\n",
                    callerPackage, callingUid14, callingPid13,
                    resolvedType, resultCode, resultData, resultExtras,
                    appOp9, ordered11, sticky12,
                    tcmp,
                    Arrays.toString(requiredPermissions),
                    intent.toString()
            );
        } else {
            log = String.format("sendBroadcast { calling=%s:%d:%d, rType=%s, rCode=%d, rData=%s, rExtras=%s, " +
                            "appOp=%d, ordered=%b, sticky=%b, target=%s, rpermission=%s, intent=%s extras=%s }\n",
                    callerPackage, callingUid14, callingPid13,
                    resolvedType, resultCode, resultData, resultExtras,
                    appOp9, ordered11, sticky12,
                    tcmp,
                    Arrays.toString(requiredPermissions),
                    intent.toString()
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
