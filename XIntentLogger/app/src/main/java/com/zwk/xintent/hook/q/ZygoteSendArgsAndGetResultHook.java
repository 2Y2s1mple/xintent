package com.zwk.xintent.hook.q;


import android.util.Log;


import com.elvishew.xlog.XLog;
import com.zwk.xintent.utils.GloblePool;

import java.io.OutputStream;
import java.lang.reflect.Member;
import java.util.Arrays;
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
 * new ZygoteSendArgsAndGetResultHook.hook(Method);
 * or	new ZygoteSendArgsAndGetResultHook.hook(Class,MethodRegx);
 */
public class ZygoteSendArgsAndGetResultHook extends XC_MethodHook {
    public Member method;
    public Object thisObject;
    public Object[] args;
    private Object result = null;

    // Landroid/os/ZygoteProcess;->zygoteSendArgsAndGetResult(Landroid/os/ZygoteProcess$ZygoteState;ZLjava/util/ArrayList;)Landroid/os/Process$ProcessStartResult;
    @Override
    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        gatherInfo(param);

        if (!GloblePool.LogConfig.isLog_processStart) {
            return;
        }
        //    private ProcessStartResult zygoteSendArgsAndGetResult(ZygoteState zygoteState, boolean useUsapPool, ArrayList arg7)
        ArrayList zygoteArgs = (ArrayList) args[2];
        result = param.getResult();
        String res = zygoteArgs.toString();
        try {
            int pid = XposedHelpers.getIntField(result, "pid");
            res = "StartProcess pid=" + pid + " " + zygoteArgs.toString() + "\n";
        } catch (Exception e) {
            Log.e(GloblePool.KTAG, e.getMessage());
        }
        Log.i(GloblePool.KTAG, res);
        if (GloblePool.LogConfig.isLog_saveToStorage) {
            XLog.d(res);
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
