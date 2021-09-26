package com.zwk.xintent;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import com.elvishew.xlog.LogConfiguration;
import com.elvishew.xlog.LogLevel;
import com.elvishew.xlog.XLog;
import com.elvishew.xlog.flattener.PatternFlattener;
import com.elvishew.xlog.printer.Printer;
import com.elvishew.xlog.printer.file.FilePrinter;
import com.elvishew.xlog.printer.file.backup.FileSizeBackupStrategy2;
import com.elvishew.xlog.printer.file.naming.FileNameGenerator;
import com.zwk.xintent.hook.QueryIntentHook;
import com.zwk.xintent.hook.q.BindServiceLockedHook;
import com.zwk.xintent.hook.q.BroadcastIntentLockedHook;
import com.zwk.xintent.hook.q.GetContentProviderImplHook;
import com.zwk.xintent.hook.q.RegisterReceiverHook;
import com.zwk.xintent.hook.q.ReportUidInfoMessageLockedHook;
import com.zwk.xintent.hook.q.StartActivityHook;
import com.zwk.xintent.hook.q.StartServiceLockedHook;
import com.zwk.xintent.hook.q.ZygoteSendArgsAndGetResultHook;
import com.zwk.xintent.hook.r.ExecuteRequestHook;
import com.zwk.xintent.hook.r.RegisterReceiverWithFeatureHook;
import com.zwk.xintent.hook.r.ZygoteProcessStartHook;
import com.zwk.xintent.ipc.TcpServer;
import com.zwk.xintent.utils.GloblePool;
import com.zwk.xintent.utils.PMUtils;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MyXposed implements IXposedHookLoadPackage {

    String targetApp = "android";
    String packageName;
    Boolean isFirstApplication;
    ClassLoader classLoader;
    String processName;
    ApplicationInfo appInfo;
    Context ams_ctx;
    private int AndroidVersion;

    public static PackageManager getPm() {
        return pm;
    }

    public static PackageManager pm;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable {
        if (!loadPackageParam.packageName.equals(targetApp)) return;
        gatherInfo(loadPackageParam);
        AndroidVersion = Build.VERSION.SDK_INT;
        XposedBridge.log("Android Build.VERSION.SDK_INT = " + AndroidVersion);

        Class<?> com_android_server_am_ActivityManagerService = XposedHelpers.findClass("com.android.server.am.ActivityManagerService", classLoader);
        Method ActivityManagerService_systemReady;
        if (AndroidVersion < 30) {
            // Lcom/android/server/am/ActivityManagerService;->systemReady(Ljava/lang/Runnable;Landroid/util/TimingsTraceLog;)V
            ActivityManagerService_systemReady= XposedHelpers.findMethodExact(com_android_server_am_ActivityManagerService, "systemReady", "java.lang.Runnable", "android.util.TimingsTraceLog");
        } else {
            // Lcom/android/server/am/ActivityManagerService;->systemReady(Ljava/lang/Runnable;Lcom/android/server/utils/TimingsTraceAndSlog;)V
            ActivityManagerService_systemReady = XposedHelpers.findMethodExact(com_android_server_am_ActivityManagerService, "systemReady", "java.lang.Runnable", "com.android.server.utils.TimingsTraceAndSlog");
        }

        XposedBridge.hookMethod(ActivityManagerService_systemReady,new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                XposedBridge.log("System ready!");
                ams_ctx = (Context) XposedHelpers.callMethod(
                        XposedHelpers.callStaticMethod(
                                XposedHelpers.findClass("android.app.ActivityThread", classLoader),
                                "currentActivityThread"),
                        "getSystemContext");
                if (ams_ctx == null) {
                    XposedBridge.log("getSystemContext is null.");
                } else if (ams_ctx instanceof Context) {
                    GloblePool.setAmsCtx(ams_ctx);
                    pm = ams_ctx.getPackageManager(); // is null
                    PMUtils.setPm(pm);
                    mainhook();
                } else {
                    XposedBridge.log("ams_ctx(getSystemContext) is not instance of Context.");
                }
            }
        });
    }

    private void mainhook() {
        new Thread(new TcpServer()).start();
        xlogInit();
        GloblePool.LogConfig.tryRestoreConfigs();
        switch (AndroidVersion) {
            case 28:
                hookAndroidP();
            case 29:
                hookAndroidQ();
                break;
            case 30:
                hookAndroidR();
                break;
            default:
                break;
        }
    }

    private void xlogInit() {
        LogConfiguration config = new LogConfiguration.Builder()
                .tag("XIL")
                .logLevel(LogLevel.ALL)
                .build();

        Printer filePrinter = new FilePrinter
                .Builder(GloblePool.xlogFolder)
                .fileNameGenerator(new FileNameGenerator() {
                    ThreadLocal<SimpleDateFormat> mLocalDateFormat = new ThreadLocal<SimpleDateFormat>() {
                        @Override
                        protected SimpleDateFormat initialValue() {
                            return new SimpleDateFormat("yyyy-MM-dd-HH", Locale.US);
                        }
                    };

                    @Override
                    public boolean isFileNameChangeable() {
                        return true;
                    }

                    @Override
                    public String generateFileName(int logLevel, long timestamp) {
                        SimpleDateFormat sdf = mLocalDateFormat.get();
                        sdf.setTimeZone(TimeZone.getDefault());
                        return sdf.format(new Date(timestamp));
                    }
                })
                .backupStrategy(new FileSizeBackupStrategy2(2 * 1024 * 1024, FileSizeBackupStrategy2.NO_LIMIT))
                .flattener(new PatternFlattener("{d} | {m}"))
                .build();

        XLog.init(config, filePrinter);
    }

    private void hookQueryIntent() {
        // seems compatible with Android PQR
        Class<?> com_android_server_pm_PackageManagerService = XposedHelpers.findClass("com.android.server.pm.PackageManagerService", classLoader);
        Method PackageManagerService_queryIntentActivitiesInternal = XposedHelpers.findMethodExact(com_android_server_pm_PackageManagerService, "queryIntentActivitiesInternal", "android.content.Intent", "java.lang.String", int.class, int.class);
        //Method PackageManagerService_queryIntentActivitiesInternal = XposedHelpers.findMethodExact(com_android_server_pm_PackageManagerService, "queryIntentActivitiesInternal", "android.content.Intent", "java.lang.String", int.class, int.class, int.class, int.class, boolean.class, boolean.class);
        Method PackageManagerService_queryIntentContentProvidersInternal = XposedHelpers.findMethodExact(com_android_server_pm_PackageManagerService, "queryIntentContentProvidersInternal", "android.content.Intent", "java.lang.String", int.class, int.class);
        Method PackageManagerService_queryIntentReceiversInternal = XposedHelpers.findMethodExact(com_android_server_pm_PackageManagerService, "queryIntentReceiversInternal", "android.content.Intent", "java.lang.String", int.class, int.class, boolean.class);
        Method PackageManagerService_queryIntentServicesInternal = XposedHelpers.findMethodExact(com_android_server_pm_PackageManagerService, "queryIntentServicesInternal", "android.content.Intent", "java.lang.String", int.class, int.class, int.class, boolean.class);

        new QueryIntentHook().hook(PackageManagerService_queryIntentActivitiesInternal);
        new QueryIntentHook().hook(PackageManagerService_queryIntentContentProvidersInternal);
        new QueryIntentHook().hook(PackageManagerService_queryIntentReceiversInternal);
        new QueryIntentHook().hook(PackageManagerService_queryIntentServicesInternal);
    }

    private void hookAndroidR() {
        Class<?> com_android_server_am_ActivityManagerService = XposedHelpers.findClass("com.android.server.am.ActivityManagerService", classLoader);
        /* Content Provider */
        //Method ActivityManagerService_getContentProvider = XposedHelpers.findMethodExact(com_android_server_am_ActivityManagerService, "getContentProvider", "android.app.IApplicationThread", "java.lang.String", "java.lang.String", int.class, boolean.class);
        Method ActivityManagerService_getContentProviderImpl = XposedHelpers.findMethodExact(com_android_server_am_ActivityManagerService, "getContentProviderImpl", "android.app.IApplicationThread", "java.lang.String", "android.os.IBinder", int.class, "java.lang.String", "java.lang.String", boolean.class, int.class);
        new GetContentProviderImplHook().hook(ActivityManagerService_getContentProviderImpl);

        /* Broadcast receiver */
        Method ActivityManagerService_broadcastIntentLocked = XposedHelpers.findMethodExact(com_android_server_am_ActivityManagerService, "broadcastIntentLocked", "com.android.server.am.ProcessRecord", "java.lang.String", "java.lang.String", "android.content.Intent", "java.lang.String", "android.content.IIntentReceiver", int.class, "java.lang.String", "android.os.Bundle", "java.lang.String[]", int.class, "android.os.Bundle", boolean.class, boolean.class, int.class, int.class, int.class, int.class, int.class, boolean.class, int[].class);
        new com.zwk.xintent.hook.r.BroadcastIntentLockedHook().hook(ActivityManagerService_broadcastIntentLocked);
        Method ActivityManagerService_registerReceiverWithFeature = XposedHelpers.findMethodExact(com_android_server_am_ActivityManagerService, "registerReceiverWithFeature", "android.app.IApplicationThread", "java.lang.String", "java.lang.String", "android.content.IIntentReceiver", "android.content.IntentFilter", "java.lang.String", int.class, int.class);
        new RegisterReceiverWithFeatureHook().hook(ActivityManagerService_registerReceiverWithFeature);

        /* Service Start */
        Class<?> com_android_server_am_ActiveServices = XposedHelpers.findClass("com.android.server.am.ActiveServices", classLoader);
        Method ActiveServices_startServiceLocked = XposedHelpers.findMethodExact(com_android_server_am_ActiveServices, "startServiceLocked", "android.app.IApplicationThread", "android.content.Intent", "java.lang.String", int.class, int.class, boolean.class, "java.lang.String", "java.lang.String", int.class, boolean.class);
        new com.zwk.xintent.hook.r.StartServiceLockedHook().hook(ActiveServices_startServiceLocked);

        /* Service Bind */
        Method ActiveServices_bindServiceLocked = XposedHelpers.findMethodExact(com_android_server_am_ActiveServices, "bindServiceLocked", "android.app.IApplicationThread", "android.os.IBinder", "android.content.Intent", "java.lang.String", "android.app.IServiceConnection", int.class, "java.lang.String", "java.lang.String", int.class);
        new BindServiceLockedHook().hook(ActiveServices_bindServiceLocked);

        /* Activity */
        Class<?> com_android_server_wm_ActivityStarter = XposedHelpers.findClass("com.android.server.wm.ActivityStarter", classLoader);
        //Method ActivityStarter_startActivityInner = XposedHelpers.findMethodExact(com_android_server_wm_ActivityStarter, "startActivityInner", "com.android.server.wm.ActivityRecord", "com.android.server.wm.ActivityRecord", "android.service.voice.IVoiceInteractionSession", "com.android.internal.app.IVoiceInteractor", int.class, boolean.class, "android.app.ActivityOptions", "com.android.server.wm.Task", boolean.class, "com.android.server.uri.NeededUriGrants");
        Method ActivityStarter_executeRequest = XposedHelpers.findMethodExact(com_android_server_wm_ActivityStarter, "executeRequest", "com.android.server.wm.ActivityStarter$Request");
        new ExecuteRequestHook().hook(ActivityStarter_executeRequest);

        /* Process Create */
        Method ActivityManagerService_reportUidInfoMessageLocked = XposedHelpers.findMethodExact(com_android_server_am_ActivityManagerService, "reportUidInfoMessageLocked", "java.lang.String", "java.lang.String", int.class);
        new ReportUidInfoMessageLockedHook().hook(ActivityManagerService_reportUidInfoMessageLocked);

        Class<?> android_os_ZygoteProcess = XposedHelpers.findClass("android.os.ZygoteProcess", classLoader);
        // Method ZygoteProcess_zygoteSendArgsAndGetResult = XposedHelpers.findMethodExact(android_os_ZygoteProcess, "zygoteSendArgsAndGetResult", "android.os.ZygoteProcess$ZygoteState", int.class, "java.util.ArrayList");
        // new com.zwk.xintent.hook.r.ZygoteSendArgsAndGetResultHook().hook(ZygoteProcess_zygoteSendArgsAndGetResult);
        /*
           Hook ZygoteSendArgsAndGetResult keeps crash on [Riru-LSPosed: v1.5.3(5984）](https://github.com/LSPosed/LSPosed/issues/1144)
           Temporary solution：Move hook point up to ZygoteProcess#Start
           Side-effect: May lose some cases spawn from ChildZygote

           Fixed：https://github.com/LSPosed/LSPosed/pull/1166
        */
        Method ZygoteProcess_start = XposedHelpers.findMethodExact(android_os_ZygoteProcess, "start", "java.lang.String", "java.lang.String", int.class, int.class, int[].class, int.class, int.class, int.class, "java.lang.String", "java.lang.String", "java.lang.String", "java.lang.String", "java.lang.String", "java.lang.String", int.class, boolean.class, long[].class, "java.util.Map", "java.util.Map", boolean.class, boolean.class, "java.lang.String[]");
        new ZygoteProcessStartHook().hook(ZygoteProcess_start);

        /* Query Intent */
        hookQueryIntent();
    }

    private void hookAndroidQ() {
        Class<?> com_android_server_am_ActivityManagerService = XposedHelpers.findClass("com.android.server.am.ActivityManagerService", classLoader);
        /* Content Provider */
        //Method ActivityManagerService_getContentProvider = XposedHelpers.findMethodExact(com_android_server_am_ActivityManagerService, "getContentProvider", "android.app.IApplicationThread", "java.lang.String", "java.lang.String", int.class, boolean.class);
        Method ActivityManagerService_getContentProviderImpl = XposedHelpers.findMethodExact(com_android_server_am_ActivityManagerService, "getContentProviderImpl", "android.app.IApplicationThread", "java.lang.String", "android.os.IBinder", int.class, "java.lang.String", "java.lang.String", boolean.class, int.class);
        new GetContentProviderImplHook().hook(ActivityManagerService_getContentProviderImpl);

        /* Broadcast receiver */
        //Method ActivityManagerService_broadcastIntent = XposedHelpers.findMethodExact(com_android_server_am_ActivityManagerService, "broadcastIntent", "android.app.IApplicationThread", "android.content.Intent", "java.lang.String", "android.content.IIntentReceiver", int.class, "java.lang.String", "android.os.Bundle", "java.lang.String[]", int.class, "android.os.Bundle", boolean.class, boolean.class, int.class);
        Method ActivityManagerService_broadcastIntentLocked = XposedHelpers.findMethodExact(com_android_server_am_ActivityManagerService, "broadcastIntentLocked", "com.android.server.am.ProcessRecord", "java.lang.String", "android.content.Intent", "java.lang.String", "android.content.IIntentReceiver", int.class, "java.lang.String", "android.os.Bundle", "java.lang.String[]", int.class, "android.os.Bundle", boolean.class, boolean.class, int.class, int.class, int.class, int.class, int.class, boolean.class);
        new BroadcastIntentLockedHook().hook(ActivityManagerService_broadcastIntentLocked);

        Method ActivityManagerService_registerReceiver = XposedHelpers.findMethodExact(com_android_server_am_ActivityManagerService, "registerReceiver", "android.app.IApplicationThread", "java.lang.String", "android.content.IIntentReceiver", "android.content.IntentFilter", "java.lang.String", int.class, int.class);
        new RegisterReceiverHook().hook(ActivityManagerService_registerReceiver);

        /* Service Start */
        //Method ActivityManagerService_startService = XposedHelpers.findMethodExact(com_android_server_am_ActivityManagerService, "startService", "android.app.IApplicationThread", "android.content.Intent", "java.lang.String", boolean.class, "java.lang.String", int.class);
        //new StartServiceHook().hook(ActivityManagerService_startService);
        Class<?> com_android_server_am_ActiveServices = XposedHelpers.findClass("com.android.server.am.ActiveServices", classLoader);
        Method ActiveServices_startServiceLocked = XposedHelpers.findMethodExact(com_android_server_am_ActiveServices, "startServiceLocked", "android.app.IApplicationThread", "android.content.Intent", "java.lang.String", int.class, int.class, boolean.class, "java.lang.String", int.class, boolean.class);
        new StartServiceLockedHook().hook(ActiveServices_startServiceLocked);

        /* Service Bind */
        // Method ActivityManagerService_bindIsolatedService = XposedHelpers.findMethodExact(com_android_server_am_ActivityManagerService, "bindIsolatedService", "android.app.IApplicationThread", "android.os.IBinder", "android.content.Intent", "java.lang.String", "android.app.IServiceConnection", int.class, "java.lang.String", "java.lang.String", int.class);
        Method ActiveServices_bindServiceLocked = XposedHelpers.findMethodExact(com_android_server_am_ActiveServices, "bindServiceLocked", "android.app.IApplicationThread", "android.os.IBinder", "android.content.Intent", "java.lang.String", "android.app.IServiceConnection", int.class, "java.lang.String", "java.lang.String", int.class);
        new BindServiceLockedHook().hook(ActiveServices_bindServiceLocked);

        /* Activity */
        Class<?> com_android_server_wm_ActivityTaskManagerService = XposedHelpers.findClass("com.android.server.wm.ActivityTaskManagerService", classLoader);
        //Method ActivityTaskManagerService_startActivityAsUser = XposedHelpers.findMethodExact(com_android_server_wm_ActivityTaskManagerService, "startActivityAsUser", "android.app.IApplicationThread", "java.lang.String", "android.content.Intent", "java.lang.String", "android.os.IBinder", "java.lang.String", int.class, int.class, "android.app.ProfilerInfo", "android.os.Bundle", int.class, boolean.class);
        //new StartActivityAsUserHook().hook(ActivityTaskManagerService_startActivityAsUser);
        Class<?> com_android_server_wm_ActivityStarter = XposedHelpers.findClass("com.android.server.wm.ActivityStarter", classLoader);
        Method ActivityStarter_startActivity = XposedHelpers.findMethodExact(com_android_server_wm_ActivityStarter, "startActivity", "android.app.IApplicationThread", "android.content.Intent", "android.content.Intent", "java.lang.String", "android.content.pm.ActivityInfo", "android.content.pm.ResolveInfo", "android.service.voice.IVoiceInteractionSession", "com.android.internal.app.IVoiceInteractor", "android.os.IBinder", "java.lang.String", int.class, int.class, int.class, "java.lang.String", int.class, int.class, int.class, "com.android.server.wm.SafeActivityOptions", boolean.class, boolean.class, "com.android.server.wm.ActivityRecord[]", "com.android.server.wm.TaskRecord", boolean.class, "com.android.server.am.PendingIntentRecord", boolean.class);
        new StartActivityHook().hook(ActivityStarter_startActivity);

        /* Process Create */
        Class<?> android_os_ZygoteProcess = XposedHelpers.findClass("android.os.ZygoteProcess", classLoader);
        Method ZygoteProcess_zygoteSendArgsAndGetResult = XposedHelpers.findMethodExact(android_os_ZygoteProcess, "zygoteSendArgsAndGetResult", "android.os.ZygoteProcess$ZygoteState", boolean.class, "java.util.ArrayList");
        new ZygoteSendArgsAndGetResultHook().hook(ZygoteProcess_zygoteSendArgsAndGetResult);

        Method ActivityManagerService_reportUidInfoMessageLocked = XposedHelpers.findMethodExact(com_android_server_am_ActivityManagerService, "reportUidInfoMessageLocked", "java.lang.String", "java.lang.String", int.class);
        new ReportUidInfoMessageLockedHook().hook(ActivityManagerService_reportUidInfoMessageLocked);

        /* Query Intent */
        hookQueryIntent();
    }

    private void hookAndroidP() {
        // Lcom/android/server/am/ActivityManagerService;
        Class<?> com_android_server_am_ActivityManagerService = XposedHelpers.findClass("com.android.server.am.ActivityManagerService", classLoader);
        /* Content Provider */
        Method ActivityManagerService_getContentProviderImpl = XposedHelpers.findMethodExact(com_android_server_am_ActivityManagerService, "getContentProviderImpl", "android.app.IApplicationThread", "java.lang.String", "android.os.IBinder", boolean.class, int.class);
        new com.zwk.xintent.hook.p.GetContentProviderImplHook().hook(ActivityManagerService_getContentProviderImpl);

        /* Broadcast receiver */
        Method ActivityManagerService_broadcastIntentLocked = XposedHelpers.findMethodExact(com_android_server_am_ActivityManagerService, "broadcastIntentLocked", "com.android.server.am.ProcessRecord", "java.lang.String", "android.content.Intent", "java.lang.String", "android.content.IIntentReceiver", int.class, "java.lang.String", "android.os.Bundle", "java.lang.String[]", int.class, "android.os.Bundle", boolean.class, boolean.class, int.class, int.class, int.class);
        new com.zwk.xintent.hook.p.BroadcastIntentLockedHook().hook(ActivityManagerService_broadcastIntentLocked);
        Method ActivityManagerService_registerReceiver = XposedHelpers.findMethodExact(com_android_server_am_ActivityManagerService, "registerReceiver", "android.app.IApplicationThread", "java.lang.String", "android.content.IIntentReceiver", "android.content.IntentFilter", "java.lang.String", int.class, int.class);
        new RegisterReceiverHook().hook(ActivityManagerService_registerReceiver);

        /* Service Start */
        Class<?> com_android_server_am_ActiveServices = XposedHelpers.findClass("com.android.server.am.ActiveServices", classLoader);
        Method ActiveServices_startServiceLocked = XposedHelpers.findMethodExact(com_android_server_am_ActiveServices, "startServiceLocked", "android.app.IApplicationThread", "android.content.Intent", "java.lang.String", int.class, int.class, boolean.class, "java.lang.String", int.class);
        new com.zwk.xintent.hook.p.StartServiceLockedHook().hook(ActiveServices_startServiceLocked);

        /* Service Bind */
        Method ActiveServices_bindServiceLocked = XposedHelpers.findMethodExact(com_android_server_am_ActiveServices, "bindServiceLocked", "android.app.IApplicationThread", "android.os.IBinder", "android.content.Intent", "java.lang.String", "android.app.IServiceConnection", int.class, "java.lang.String", int.class);
        new com.zwk.xintent.hook.p.BindServiceLockedHook().hook(ActiveServices_bindServiceLocked);

        /* Activity */
        Class<?> com_android_server_am_ActivityStarter = XposedHelpers.findClass("com.android.server.am.ActivityStarter", classLoader);
        Method ActivityStarter_startActivity = XposedHelpers.findMethodExact(com_android_server_am_ActivityStarter, "startActivity", "android.app.IApplicationThread", "android.content.Intent", "android.content.Intent", "java.lang.String", "android.content.pm.ActivityInfo", "android.content.pm.ResolveInfo", "android.service.voice.IVoiceInteractionSession", "com.android.internal.app.IVoiceInteractor", "android.os.IBinder", "java.lang.String", int.class, int.class, int.class, "java.lang.String", int.class, int.class, int.class, "com.android.server.am.SafeActivityOptions", boolean.class, boolean.class, "com.android.server.am.ActivityRecord[]", "com.android.server.am.TaskRecord", boolean.class);
        new com.zwk.xintent.hook.p.StartActivityHook().hook(ActivityStarter_startActivity);

        /* Process Create */
        Class<?> android_os_ZygoteProcess = XposedHelpers.findClass("android.os.ZygoteProcess", classLoader);
        Method ZygoteProcess_zygoteSendArgsAndGetResult = XposedHelpers.findMethodExact(android_os_ZygoteProcess, "zygoteSendArgsAndGetResult", "android.os.ZygoteProcess$ZygoteState", "java.util.ArrayList");
        new com.zwk.xintent.hook.p.ZygoteSendArgsAndGetResultHook().hook(ZygoteProcess_zygoteSendArgsAndGetResult);

        Method ActivityManagerService_reportUidInfoMessageLocked = XposedHelpers.findMethodExact(com_android_server_am_ActivityManagerService, "reportUidInfoMessageLocked", "java.lang.String", "java.lang.String", int.class);
        new ReportUidInfoMessageLockedHook().hook(ActivityManagerService_reportUidInfoMessageLocked);

        /* Query Intent */
        hookQueryIntent();
    }

    private void gatherInfo(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        packageName = loadPackageParam.packageName;
        isFirstApplication = loadPackageParam.isFirstApplication;
        classLoader = loadPackageParam.classLoader;
        processName = loadPackageParam.processName;
        appInfo = loadPackageParam.appInfo;
    }
}
