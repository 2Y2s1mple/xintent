package com.zwk.xintent.hook.r;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.util.Log;

import com.elvishew.xlog.XLog;
import com.zwk.xintent.utils.BundleUtils;
import com.zwk.xintent.utils.GloblePool;

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
 * new ExecuteRequestHook.hook(Method);
 * or	new ExecuteRequestHook.hook(Class,MethodRegx);
 */
public class ExecuteRequestHook extends XC_MethodHook {
    public Member method;
    public Object thisObject;
    public Object[] args;
    private Object result = null;

    @Override
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
        gatherInfo(param);
        // int executeRequest(Request request)
        Object request = args[0];
        int callingPid = XposedHelpers.getIntField(request, "callingPid");
        int callingUid = XposedHelpers.getIntField(request, "callingUid");
        int realCallingPid = XposedHelpers.getIntField(request, "realCallingPid");
        int realCallingUid = XposedHelpers.getIntField(request, "realCallingUid");
        int requestCode = XposedHelpers.getIntField(request, "requestCode");
        int startFlags = XposedHelpers.getIntField(request, "startFlags");
        int userId = XposedHelpers.getIntField(request, "userId");
        String flags = Integer.toHexString(startFlags);

        boolean ignoreTargetSecurity = XposedHelpers.getBooleanField(request, "ignoreTargetSecurity");
        String callingPackage = (String) XposedHelpers.getObjectField(request, "callingPackage");
        String callingFeatureId = (String) XposedHelpers.getObjectField(request, "callingFeatureId");
        String reason = (String) XposedHelpers.getObjectField(request, "reason");

        Intent intent = (Intent) XposedHelpers.getObjectField(request, "intent");
        ActivityInfo aInfo = (ActivityInfo) XposedHelpers.getObjectField(request, "activityInfo");

        Bundle xb = intent.getExtras();
        String ex = BundleUtils.parseBundle(xb, 0);

        String log;
        if (xb == null) {
            log = String.format("startActivity { calling=%s:%d:%d, rc=%d:%d, iTS=%b, requestCode=%d, startFlags=%s, target=%s/%s<%b>, intent=%s }\n",
                    callingPackage, callingUid, callingPid,
                    realCallingUid, realCallingPid,
                    ignoreTargetSecurity,
                    requestCode,
                    flags,
                    aInfo.packageName,
                    aInfo.name,
                    aInfo.exported,
                    intent.toString()
            );
        } else {
            log = String.format("startActivity { calling=%s:%d:%d, rc=%d:%d, iTS=%b, requestCode=%d, startFlags=%s, target=%s/%s<%b>, intent=%s, extras=%s }\n",
                    callingPackage, callingUid, callingPid,
                    realCallingUid, realCallingPid,
                    ignoreTargetSecurity,
                    requestCode,
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
    }
}
