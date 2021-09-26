package com.zwk.xintent.hook.q;

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
 * new BindServiceLockedHook.hook(Method);
 * or	new BindServiceLockedHook.hook(Class,MethodRegx);
 */
public class BindServiceLockedHook extends XC_MethodHook {
    public Member method;
    public Object thisObject;
    public Object[] args;
    private Object result = null;

    // Lcom/android/server/am/ActiveServices;->bindServiceLocked(Landroid/app/IApplicationThread;Landroid/os/IBinder;Landroid/content/Intent;Ljava/lang/String;Landroid/app/IServiceConnection;ILjava/lang/String;Ljava/lang/String;I)I
    @Override
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
        gatherInfo(param);
        if (!GloblePool.LogConfig.isLog_bindService) {
            return;
        }

        // bindServiceLocked(IApplicationThread caller0, IBinder token1, Intent service2, String resolvedType3,
        //      IServiceConnection connection4, int flags5, String instanceName6, String callingPackage7, int userId8) throws TransactionTooLargeException {

        Intent intent = (Intent) args[2];
        String resolvedType3 = (String) args[3];
        int flags5 = (int) args[5];
        String instanceName6 = (String) args[6];
        String callingPackage7 = (String) args[7];
        int userId8 = (int) args[8];

        Bundle xb = intent.getExtras();
        String ex = BundleUtils.parseBundle(xb, 0);

        int uid = Binder.getCallingUid();
        int pid = Binder.getCallingPid();
        //String uidName = pm.getNameForUid(uid);  // ODMs may contains uid, e.g: oppo.uid.launcher:10065

        // For implicit xintent, without cmp, we invoke resolve actively.
        String tcmp = PMUtils.getServiceCmpName(intent);

        String log;
        if (xb == null) {
            log = String.format("bindService { calling=%s:%d:%d, resolvedType=%s, instanceName=%s, " +
                            "flags=%d, userID=%d, target=%s, intent=%s }\n",
                    callingPackage7, uid, pid,
                    resolvedType3, instanceName6,
                    flags5, userId8,
                    tcmp,
                    intent.toString()
            );
        } else {
            log = String.format("bindService { calling=%s:%d:%d, resolvedType=%s, instanceName=%s, " +
                            "flags=%d, userID=%d, target=%s, intent=%s  extras=%s }\n",
                    callingPackage7, uid, pid,
                    resolvedType3, instanceName6,
                    flags5, userId8,
                    tcmp,
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
