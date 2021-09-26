package com.zwk.xintent.hook;

import android.content.Intent;
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
 * new QueryIntentHook.hook(Method);
 * or	new QueryIntentHook.hook(Class,MethodRegx);
 */
public class QueryIntentHook extends XC_MethodHook {
    public Member method;
    public Object thisObject;
    public Object[] args;
    private Object result = null;
    public String methodName;

    // Lcom/android/server/pm/PackageManagerService;->queryIntentActivitiesInternal(Landroid/content/Intent;Ljava/lang/String;II)Ljava/util/List;
    // Lcom/android/server/pm/PackageManagerService;->queryIntentContentProvidersInternal(Landroid/content/Intent;Ljava/lang/String;II)Ljava/util/List;
    // Lcom/android/server/pm/PackageManagerService;->queryIntentReceiversInternal(Landroid/content/Intent;Ljava/lang/String;IIZ)Ljava/util/List;
    // Lcom/android/server/pm/PackageManagerService;->queryIntentServicesInternal(Landroid/content/Intent;Ljava/lang/String;IIIZ)Ljava/util/List;

    @Override
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
        if (!GloblePool.LogConfig.isLog_queryIntent) {
            return;
        }
        gatherInfo(param);

        Intent intent = (Intent) args[0];
        String resolvedType = (String) args[1];
        int flags = (int) args[2];
        int userId = (int) args[3];

        int callingPid = Binder.getCallingPid();
        int callingUid = Binder.getCallingUid();
        String callingPackage = PMUtils.getPm().getNameForUid(callingUid);

        String log = String.format("queryIntent { calling=[%d]%s:%d, resolvedType=%s, flags=%s, intent=%s }\n",
                userId,
                callingPackage, callingPid,
                resolvedType,
                Integer.toHexString(flags),
                intent.toString()
        );

        Throwable ex = new Throwable();
        StackTraceElement[] stackElements = ex.getStackTrace();
        if (stackElements != null) {
            for (int i = 3; i < stackElements.length; i++) {

//                Log.i("Dump Stack"+i+": ", stackElements[i].getClassName()
//                        +"----"+stackElements[i].getFileName()
//                        +"----" + stackElements[i].getLineNumber()
//                        +"----" +stackElements[i].getMethodName());

                if (stackElements[i].getFileName() == null || stackElements[i].getFileName().contains("de.robv.android.xposed")) {
                    return;
                }
                if (stackElements[i].getClassName().contains("xposed") || stackElements[i].getMethodName().toLowerCase().contains("hook"))  {
                    return;
                }
            }
        }

        Log.i(GloblePool.KTAG, log);
        if (GloblePool.LogConfig.isLog_queryIntent) {
            XLog.d(log);
        }
    }

    /**
     * @description: Hook specified method.
     * @author: Monkeylord
     **/
    public void hook(Member method) {
        methodName = method.getName().replace("Internal", "");
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
