package com.zwk.xintent.hook.q;

import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.util.Log;

import com.elvishew.xlog.XLog;
import com.zwk.xintent.utils.GloblePool;

import java.lang.reflect.Member;
import java.util.Iterator;
import java.util.regex.Pattern;
import java.util.ArrayList;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Hook's type: none
 * Usage:
 * in XposedModule
 * new RegisterReceiverHook.hook(Method);
 * or	new RegisterReceiverHook.hook(Class,MethodRegx);
 */
public class RegisterReceiverHook extends XC_MethodHook {
    public Member method;
    public Object thisObject;
    public Object[] args;
    private Object result = null;

    // Lcom/android/server/am/ActivityManagerService;->registerReceiver(Landroid/app/IApplicationThread;Ljava/lang/String;Landroid/content/IIntentReceiver;Landroid/content/IntentFilter;Ljava/lang/String;II)Landroid/content/Intent;
    @Override
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
        gatherInfo(param);
        if (!GloblePool.LogConfig.isLog_registerReceiver) {
            return;
        }

        // registerReceiver(IApplicationThread caller0, String callerPackage1, IIntentReceiver receiver2,
        //      IntentFilter filter3, String permission4, int userId5, int flags6)

        // ProcessRecord r = getRecordForAppLocked(caller);
        String callerPackage = (String) args[1];
        IntentFilter filter = (IntentFilter) args[3];
        String permission = (String) args[4];

        int uid = Binder.getCallingUid();
        int pid = Binder.getCallingPid();
        StringBuilder sb = new StringBuilder();

        Iterator<String> actions = filter.actionsIterator();
        while(actions.hasNext()) {
            sb.append(actions.next());
            sb.append(", ");
        }

        String log;
        log = String.format("registerReceiver { calling=%s:%d:%d, permission=%s, actions=[%s] }\n",
                callerPackage, uid, pid,
                permission,
                sb.toString()
        );

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
