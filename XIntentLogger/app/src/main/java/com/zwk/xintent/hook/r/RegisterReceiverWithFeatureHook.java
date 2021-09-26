package com.zwk.xintent.hook.r;

import android.content.IntentFilter;
import android.os.Binder;
import android.util.Log;

import com.elvishew.xlog.XLog;
import com.zwk.xintent.utils.GloblePool;

import java.lang.reflect.Member;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.regex.Pattern;

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
public class RegisterReceiverWithFeatureHook extends XC_MethodHook {
    public Member method;
    public Object thisObject;
    public Object[] args;
    private Object result = null;

    // Lcom/android/server/am/ActivityManagerService;->registerReceiverWithFeature(Landroid/app/IApplicationThread;Ljava/lang/String;Ljava/lang/String;Landroid/content/IIntentReceiver;Landroid/content/IntentFilter;Ljava/lang/String;II)Landroid/content/Intent;
    @Override
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
        gatherInfo(param);
        if (!GloblePool.LogConfig.isLog_registerReceiver) {
            return;
        }

        // Intent registerReceiverWithFeature(IApplicationThread caller0, String callerPackage1, String callerFeatureId2,
        // IIntentReceiver receiver3, IntentFilter filter4, String permission5, int userId6, int flags7) {

        // ProcessRecord r = getRecordForAppLocked(caller);
        String callerPackage1 = (String) args[1];
        String callerFeatureId2 = (String) args[2];
        IntentFilter filter4 = (IntentFilter) args[4];
        String permission5 = (String) args[5];
        int userId6 = (int) args[6];
        int flags7 = (int) args[7];

        int uid = Binder.getCallingUid();
        int pid = Binder.getCallingPid();
        StringBuilder sb = new StringBuilder();

        Iterator<String> actions = filter4.actionsIterator();
        while(actions.hasNext()) {
            sb.append(actions.next());
            sb.append(", ");
        }

        String log;
        log = String.format("registerReceiver { calling=%s:%d:%d, cFID=%s, userId=%d, flags=%d, permission=%s, actions=[%s] }\n",
                callerPackage1, uid, pid,
                callerFeatureId2, userId6, flags7,
                permission5,
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
