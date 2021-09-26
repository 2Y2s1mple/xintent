package com.zwk.xintent.hook.r;

import android.util.Log;

import com.elvishew.xlog.XLog;
import com.zwk.xintent.utils.GloblePool;

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
 * new ZygoteProcessStartHook.hook(Method);
 * or	new ZygoteProcessStartHook.hook(Class,MethodRegx);
 */
public class ZygoteProcessStartHook extends XC_MethodHook {
    public Member method;
    public Object thisObject;
    public Object[] args;
    private Object result = null;

    // Landroid/os/ZygoteProcess;->start(Ljava/lang/String;Ljava/lang/String;II[IIIILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;IZ[JLjava/util/Map;Ljava/util/Map;ZZ[Ljava/lang/String;)Landroid/os/Process$ProcessStartResult;
    @Override
    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        gatherInfo(param);
        if (!GloblePool.LogConfig.isLog_processStart) {
            return;
        }

        result = param.getResult();
        // ProcessStartResult start(String processClass0, String niceName1, int uid2, int gid3, int[] gids4,
            // int runtimeFlags5, int mountExternal6, int targetSdkVersion7, String seInfo8, String abi9,
            // String instructionSet10, String appDataDir11, String invokeWith12, String packageName13,
            // int zygotePolicyFlags14, boolean isTopApp15, long[] disabledCompatChanges16,
            // Map pkgDataInfoMap17, Map whitelistedDataInfoMap18, boolean bindMountAppsData19,
            // boolean bindMountAppStorageDirs20, String[] zygoteArgs21)

        String processClass0 = (String) args[0];
        String niceName1 = (String) args[1];
        int uid2 = (int) args[2];
        int gid3 = (int) args[3];
        int[] gids4 = (int[]) args[4];
        int runtimeFlags5 = (int) args[5];
        int mountExternal6 = (int) args[6];
        int targetSdkVersion7 = (int) args[7];
        String mE = null;
        switch (mountExternal6) {
            case 1: mE = "--mount-external-default"; break;
            case 2: mE = "--mount-external-read"; break;
            case 3: mE = "--mount-external-write"; break;
            case 4: mE = "--mount-external-full"; break;
            case 5: mE = "--mount-external-installer"; break;
            case 6: mE = "--mount-external-legacy"; break;
            case 7: mE = "--mount-external-pass-through"; break;
            case 8: mE = "--mount-external-android-writable"; break;
        }
        String seInfo8 = (String) args[8];
        String abi9 = (String) args[9];
        String instructionSet10 = (String) args[10];
        String appDataDir11 = (String) args[11];
        String packageName13 = (String) args[13];
        int zygotePolicyFlags14 = (int) args[14];
        String[] zygoteArgs21 = (String[]) args[21];

        int pid = XposedHelpers.getIntField(result, "pid");
        String log =
            String.format("StartProcess pid=%d zPFlags=%s abi=%s [--nice-nam=%s, --setuid=%d, --setgid=%d, --runtime-flags=%d, %s " +
                            "--target-sdk-version=%d, --setgroups=%s, " +
                            " --seinfo=%s, --instruction-set=%s " +
                            "--app-data-dir=%s, --package-name=%s, %s, %s]\n",
                pid, Integer.toHexString(zygotePolicyFlags14), abi9,
                niceName1, uid2, gid3, runtimeFlags5, mE,
                targetSdkVersion7, Arrays.toString(gids4),
                seInfo8, instructionSet10, appDataDir11, packageName13,
                processClass0, Arrays.toString(zygoteArgs21)
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
