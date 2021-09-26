package com.zwk.xintent.hook.q;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Binder;
import android.os.Bundle;
import android.util.Log;

import com.elvishew.xlog.XLog;
import com.zwk.xintent.utils.BundleUtils;
import com.zwk.xintent.utils.GloblePool;
import com.zwk.xintent.utils.PMUtils;

import java.io.FileOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Member;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Hook's type: none
 * Usage:
 * in XposedModule
 * new startActivityHook.hook(Method);
 * or	new startActivityHook.hook(Class,MethodRegx);
 */
public class StartActivityHook extends XC_MethodHook {
    public Member method;
    public Object thisObject;
    public Object[] args;
    private Object result = null;
    private PackageManager pm;
    private Context ctx;

    // Lcom/android/server/wm/ActivityStarter;->startActivity(Landroid/app/IApplicationThread;Landroid/content/Intent;Landroid/content/Intent;Ljava/lang/String;Landroid/content/pm/ActivityInfo;Landroid/content/pm/ResolveInfo;Landroid/service/voice/IVoiceInteractionSession;Lcom/android/internal/app/IVoiceInteractor;Landroid/os/IBinder;Ljava/lang/String;IIILjava/lang/String;IIILcom/android/server/wm/SafeActivityOptions;ZZ[Lcom/android/server/wm/ActivityRecord;Lcom/android/server/wm/TaskRecord;ZLcom/android/server/am/PendingIntentRecord;Z)I
    @Override
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
        gatherInfo(param);
        if (!GloblePool.LogConfig.isLog_startActivity) {
            return;
        }
        // int startActivity(IApplicationThread caller0, Intent intent1, Intent ephemeralIntent2, String resolvedType3, ActivityInfo aInfo4, ResolveInfo rInfo5,
            // IVoiceInteractionSession voiceSession6, IVoiceInteractor voiceInteractor7, IBinder resultTo8, String resultWho9, int requestCode10,
            // int callingPid11, int callingUid12, String callingPackage13, int realCallingPid14, int realCallingUid15, int startFlags16,
            // SafeActivityOptions options17, boolean ignoreTargetSecurity18, boolean componentSpecified19, ActivityRecord[] outActivity20,
            // TaskRecord inTask21, boolean allowPendingRemoteAnimationRegistryLookup22, PendingIntentRecord originatingPendingIntent23, boolean allowBackgroundActivityStart24)
        Intent intent = (Intent) args[1];
        Intent eintent = (Intent) args[2];
        String resolvedType3 = (String) args[3];
        ActivityInfo aInfo = (ActivityInfo) args[4];
        ResolveInfo rInfo = (ResolveInfo) args[5];

        int requestCode10 = (int) args[10];
        int callingPid11 = (int) args[11];
        int callingUid12 = (int) args[12];
        String callingPackage13 = (String) args[13];
        int realCallingPid14 = (int) args[14]; // from PI sendInner
        int realCallingUid15 = (int) args[15]; // from PI sendInner
        int startFlags16 = (int) args[16];  // is not intent.flags

        boolean ignoreTargetSecurity18 = (boolean) args[18]; // am->checkComponentPermission
        boolean componentSpecified19 = (boolean) args[19];  // useless, just intent.getComponent() == null ? 0 : 1
        boolean allowBackgroundActivityStart24 = (boolean) args[24];

        Bundle xb = intent.getExtras();
        String ex = BundleUtils.parseBundle(xb, 0);

        String flags = Integer.toHexString(startFlags16); // contains in xintent.toString()

        String log;
        if (xb == null) {
            log = String.format("startActivity { calling=%s:%d:%d, rc=%d:%d, iTS=%b, requestCode=%d, startFlags=%s, target=%s/%s<%b>, intent=%s }\n",
                    callingPackage13, callingUid12, callingPid11,
                    realCallingUid15, realCallingPid14,
                    ignoreTargetSecurity18,
                    requestCode10,
                    flags,
                    aInfo.packageName,
                    aInfo.name,
                    aInfo.exported,
                    intent.toString()
            );
        } else {
            log = String.format("startActivity { calling=%s:%d-%d, rc=%d-%d, iTS=%b, requestCode=%d, startFlags=%s, target=%s/%s<%b>, intent=%s, extras=%s }\n",
                    callingPackage13, callingUid12, callingPid11,
                    realCallingUid15, realCallingPid14,
                    ignoreTargetSecurity18,
                    requestCode10,
                    flags,
                    aInfo.packageName,
                    aInfo.name,
                    aInfo.exported,
                    intent.toString(),
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
        pm = PMUtils.getPm();
        ctx = GloblePool.getAmsCtx();
    }
}
