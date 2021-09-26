package com.zwk.xintent.hook.q;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Bundle;
import android.util.Log;

import com.elvishew.xlog.XLog;
import com.zwk.xintent.utils.BundleUtils;
import com.zwk.xintent.utils.GloblePool;
import com.zwk.xintent.utils.PMUtils;

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
 * new StartServiceLockedHook.hook(Method);
 * or	new StartServiceLockedHook.hook(Class,MethodRegx);
 */
public class StartServiceLockedHook extends XC_MethodHook {
    public Member method;
    public Object thisObject;
    public Object[] args;
    private Object result = null;

    // Lcom/android/server/am/ActiveServices;->startServiceLocked(Landroid/app/IApplicationThread;Landroid/content/Intent;Ljava/lang/String;IIZLjava/lang/String;IZ)Landroid/content/ComponentName;
    @Override
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
        if (!GloblePool.LogConfig.isLog_startService) {
            return;
        }
        gatherInfo(param);
        result = param.getResult();
        ComponentName ret = (ComponentName) result;
        // ComponentName startServiceLocked(IApplicationThread caller0, Intent service1, String resolvedType2,
            // int callingPid3, int callingUid4, boolean fgRequired5, String callingPackage6, int userId7, boolean allowBackgroundActivityStarts8)

        Intent intent = (Intent) args[1];
        String resolvedType2 = (String) args[2];
        int callingPid = (int) args[3];
        int callingUid = (int) args[4];
        boolean requireForeground = (boolean) args[5];
        String callingPackage = (String) args[6];
        boolean allowBg = (boolean) args[8];

        Bundle xb = intent.getExtras();
        String ex = BundleUtils.parseBundle(xb, 0);

        // For implicit xintent, without cmp, we invoke resolve actively.
        String tcmp = PMUtils.getServiceCmpName(intent);

        String log;
        if (xb == null) {
            log = String.format("startService { callingPkg=%s:%d-%d, target=%s, fgRequired=%b, allowBgA=%b, retCmp=%s, intent=%s }\n",
                    callingPackage,callingUid,callingPid,
                    tcmp,
                    requireForeground,
                    allowBg,
                    ret,
                    intent.toString()
            );
        } else {
            log = String.format("startService { callingPkg=%s:%d-%d, target=%s, fgRequired=%b, allowBgA=%b, retCmp=%s, intent=%s, extras=%s}\n",
                    callingPackage,callingUid,callingPid,
                    tcmp,
                    requireForeground,
                    allowBg,
                    ret,
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
    }
}
