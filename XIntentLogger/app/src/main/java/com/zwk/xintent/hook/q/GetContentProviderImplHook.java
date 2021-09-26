package com.zwk.xintent.hook.q;

import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.util.Log;

import com.elvishew.xlog.XLog;
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
 * new GetContentProviderImplHook.hook(Method);
 * or	new GetContentProviderImplHook.hook(Class,MethodRegx);
 */
public class GetContentProviderImplHook extends XC_MethodHook {
    public Member method;
    public Object thisObject;
    public Object[] args;
    private Object result = null;

    // Lcom/android/server/am/ActivityManagerService;->getContentProviderImpl(Landroid/app/IApplicationThread;Ljava/lang/String;Landroid/os/IBinder;ILjava/lang/String;Ljava/lang/String;ZI)Landroid/app/ContentProviderHolder;
    @Override
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
        gatherInfo(param);
        if (!GloblePool.LogConfig.isLog_getContentProvider) {
            return;
        }

        // ContentProviderHolder getContentProviderImpl(IApplicationThread caller0, String name1, IBinder token2, int callingUid3, String callingPackage4,
            // String callingTag5, boolean stable6, int userId7)

        String name = (String) args[1];  // name here is authority
        int callingUid = (int) args[3];
        String callingPackage = (String) args[4];
        String callingTag = (String) args[5];

        //int uid = Binder.getCallingUid();
        int pid = Binder.getCallingPid();
        String tcmp = PMUtils.getContentProviderCmpName(name);

        String log;
        log = String.format("getContentProvider { calling=%s:%d:%d, callingTag=%s, authority=content://%s, target=%s }\n",
                callingPackage, callingUid, pid,
                callingTag,
                name,
                tcmp
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
