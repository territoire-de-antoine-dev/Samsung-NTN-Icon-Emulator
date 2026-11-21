package fr.territoire.satvisual;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.drawable.Icon;
import android.os.UserHandle;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * V2 native SystemUI icon-slot experiment.
 * No fake telephony connectivity state, satellite modem, SOS, firewall or packet interception.
 * Uses only stock Samsung/AOSP SystemUI drawables. Does not draw an overlay.
 */
public final class ModuleHook implements IXposedHookLoadPackage {
    private static final String TAG = "SatelliteVisualNative: ";
    private static final String SLOT = "satellite";
    // Never construct a Handler during static initialization: LSPosed can load
    // this class in zygote/system_server before the main looper exists.
    // The callback is scheduled only once Android has prepared that looper.
    private static void scheduleOnMain(Runnable task, long delayMillis) {
        try {
            Looper looper = Looper.getMainLooper();
            if (looper == null) {
                XposedBridge.log(TAG + "main looper not ready; skip early callback safely");
                return;
            }
            new Handler(looper).postDelayed(task, delayMillis);
        } catch (Throwable ex) {
            XposedBridge.log(TAG + "scheduleOnMain: " + ex);
        }
    }
    private static final ArrayList<WeakReference<Object>> controllers = new ArrayList<>();
    private static Context systemContext;
    private static VisualConfig config = new VisualConfig();
    private static boolean registered;
    private static boolean signaturesLogged;
    private static boolean errorLogged;
    private static boolean busy;
    private static boolean injected;
    private static int lastRes;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if (!"com.android.systemui".equals(lp.packageName)) return;
        String[] variants = {
            "com.android.systemui.statusbar.phone.ui.StatusBarIconControllerImpl",
            "com.android.systemui.statusbar.phone.StatusBarIconControllerImpl",
            "com.android.systemui.statusbar.phone.ui.StatusBarIconControllerImplKt"
        };
        int found = 0;
        for (String name : variants) {
            try {
                Class<?> clazz = XposedHelpers.findClassIfExists(name, lp.classLoader);
                if (clazz == null) continue;
                found++;
                XposedBridge.log(TAG + "controller found " + name);
                printCandidateSignatures(clazz);

                XposedBridge.hookAllConstructors(clazz, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        remember(p.thisObject);
                        scheduleOnMain(() -> apply(p.thisObject), 1300);
                    }
                });
                XposedBridge.hookAllMethods(clazz, "addIconGroup", new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        if (!p.hasThrowable()) {
                            remember(p.thisObject);
                            scheduleOnMain(() -> apply(p.thisObject), 700);
                        }
                    }
                });
                // Samsung's home-screen simplification can hide the OEM satellite slot.
                XposedBridge.hookAllMethods(clazz, "hideBySimplification", new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        try {
                            if (!config.enabled || p.args == null || p.args.length < 2) return;
                            if (SLOT.equals(String.valueOf(p.args[1]))) p.setResult(false);
                        } catch (Throwable err) {
                            XposedBridge.log(TAG + "simplification check: " + err);
                        }
                    }
                });
            } catch (Throwable ex) {
                XposedBridge.log(TAG + "hook " + name + ": " + ex);
            }
        }
        if (found == 0) XposedBridge.log(TAG + "NO_CONTROLLER_CLASS_FOUND: Samsung changed its SystemUI; need device logs.");
    }

    private static void remember(Object controller) {
        synchronized (controllers) {
            for (WeakReference<Object> r : controllers) if (r.get() == controller) return;
            controllers.add(new WeakReference<>(controller));
        }
    }

    private static Context contextOf(Object controller) {
        for (String name : new String[]{"mContext", "context"}) {
            try {
                Object o = XposedHelpers.getObjectField(controller, name);
                if (o instanceof Context) return (Context) o;
            } catch (Throwable ignored) { }
        }
        // Fall back to a Context field without guessing its name.
        Class<?> cl = controller.getClass();
        while (cl != null) {
            for (Field f : cl.getDeclaredFields()) {
                if (Context.class.isAssignableFrom(f.getType())) try {
                    f.setAccessible(true);
                    Object value = f.get(controller);
                    if (value instanceof Context) return (Context) value;
                } catch (Throwable ignored) { }
            }
            cl = cl.getSuperclass();
        }
        return null;
    }

    private static void init(Context ctx) {
        if (registered) return;
        systemContext = ctx;
        config = read(ctx);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                config = read(context);
                scheduleOnMain(ModuleHook::refresh, 0);
            }
        };
        try {
            IntentFilter filter = new IntentFilter(VisualConfig.REFRESH);
            if (Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            else ctx.registerReceiver(receiver, filter);
            registered = true;
            XposedBridge.log(TAG + "settings listener ready");
        } catch (Throwable err) {
            XposedBridge.log(TAG + "receiver registration failed: " + err);
        }
    }

    private static VisualConfig read(Context ctx) {
        try {
            Bundle b = ctx.getContentResolver().call(Uri.parse(VisualConfig.URI), "state", null, null);
            return VisualConfig.fromBundle(b);
        } catch (Throwable e) {
            XposedBridge.log(TAG + "config provider error: " + e);
            return new VisualConfig(); // disabled by default
        }
    }

    private static void refresh() {
        synchronized (controllers) {
            for (int n = controllers.size()-1; n >= 0; n--) {
                Object c = controllers.get(n).get();
                if (c == null) controllers.remove(n);
                else apply(c);
            }
        }
    }

    private static void apply(Object controller) {
        if (controller == null || busy) return;
        busy = true;
        try {
            Context ctx = contextOf(controller);
            if (ctx == null) {
                XposedBridge.log(TAG + "NO_CONTEXT " + controller.getClass());
                return;
            }
            init(ctx);
            if (!config.enabled) {
                if (injected) {
                    removeIfOurs(controller);
                    XposedBridge.log(TAG + "native icon switched off");
                }
                return;
            }
            int res = findSamsungSatelliteResource(ctx, config.level, config.mode);
            if (res == 0) {
                XposedBridge.log(TAG + "NO_NATIVE_SATELLITE_DRAWABLE: no built-in Samsung satellite resource matched. Nothing drawn.");
                return;
            }

            try {
                // The three-argument AOSP setIcon signature is NOT present on this Samsung build.
                // Discover an actually supported signature before invoking anything.
                Method selected = findCompatibleSetIcon(controller.getClass());
                if (selected == null) {
                    logOnce("NO_COMPATIBLE_SETICON_SIGNATURE: consult METHOD lines from this build.");
                    return;
                }
                Class<?>[] p = selected.getParameterTypes();
                Object[] args;
                if (p.length == 3) {
                    args = new Object[]{SLOT, res,
                        (CharSequence) "Satellite SIMULÉ : aucune connexion réelle"};
                } else {
                    Object stockIcon = createNativeStatusBarIcon(p[1], ctx, res);
                    if (stockIcon == null) {
                        logOnce("NO_COMPATIBLE_STATUSBARICON_CONSTRUCTOR: method=" + selected);
                        return;
                    }
                    args = new Object[]{SLOT, stockIcon};
                }
                // Do not overwrite an existing modern/bindable satellite icon slot:
                // it may carry a genuine status (including emergency connectivity).
                if (isManagedByModernPipeline(controller)) {
                    logOnce("SATELLITE_SLOT_IS_MODERN_BINDABLE: native icon is owned by pipeline; leaving it untouched");
                    return;
                }
                selected.setAccessible(true);
                selected.invoke(controller, args);
                try {
                    XposedHelpers.callMethod(controller, "setIconVisibility", SLOT, true);
                } catch (Throwable ignored) {
                    try {
                        XposedHelpers.callMethod(controller, "setIconVisibility", SLOT, true, 0);
                    } catch (Throwable ignoredAgain) { }
                }
                lastRes = res;
                injected = true;
                XposedBridge.log(TAG + "INJECTED_NATIVE_ICON slot=" + SLOT
                    + " method=" + selected + " level=" + config.level);
            } catch (Throwable error) {
                logOnce("NATIVE_INJECTION_FAILED " + error);
            }
        } catch (Throwable ex) {
            XposedBridge.log(TAG + "apply failed safely: " + ex);
        } finally {
            busy = false;
        }
    }

    private static void removeIfOurs(Object controller) {
        try {
            XposedHelpers.callMethod(controller, "removeIcon", SLOT, 0);
            injected = false;
            lastRes = 0;
        } catch (Throwable e) {
            // Some Samsung versions expose only the one-argument method.
            try {
                XposedHelpers.callMethod(controller, "removeIcon", SLOT);
                injected = false;
                lastRes = 0;
            } catch (Throwable err) {
                XposedBridge.log(TAG + "native icon removal failed: " + err);
            }
        }
    }

    private static void logOnce(String warning) {
        if (errorLogged) return;
        errorLogged = true;
        XposedBridge.log(TAG + warning);
    }

    private static void printCandidateSignatures(Class<?> cls) {
        if (signaturesLogged) return;
        signaturesLogged = true;
        int n = 0;
        for (Class<?> scan = cls; scan != null; scan = scan.getSuperclass()) {
            try {
                for (Method m : scan.getDeclaredMethods()) {
                    String name = m.getName();
                    if (name.equals("setIcon") || name.equals("setIconVisibility")
                        || name.equals("setIconFromTile") || name.equals("addBindableIcon")
                        || name.equals("refreshIconGroups") || name.equals("getIconHolder")
                        || name.equals("hideBySimplification")) {
                        XposedBridge.log(TAG + "METHOD " + m.toGenericString());
                        n++;
                        if (n >= 45) return;
                    }
                }
            } catch (Throwable e) {
                XposedBridge.log(TAG + "signature inspection failed: " + e);
            }
        }
        XposedBridge.log(TAG + "METHOD_COUNT " + n);
    }

    private static Method findCompatibleSetIcon(Class<?> cls) {
        Method twoArg = null;
        for (Class<?> scan = cls; scan != null; scan = scan.getSuperclass()) {
            for (Method m : scan.getDeclaredMethods()) {
                if (!m.getName().equals("setIcon")) continue;
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 3 && p[0] == String.class
                    && (p[1] == int.class || p[1] == Integer.class)
                    && CharSequence.class.isAssignableFrom(p[2])) {
                    return m;
                }
                if (p.length == 2 && p[0] == String.class
                    && p[1].getName().equals("com.android.internal.statusbar.StatusBarIcon")) {
                    twoArg = m;
                }
            }
        }
        return twoArg;
    }

    private static Object createNativeStatusBarIcon(Class<?> type, Context ctx, int drawable) {
        try {
            Icon icon = Icon.createWithResource(ctx, drawable);
            for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                Class<?>[] p = constructor.getParameterTypes();
                if (p.length == 6 && p[0] == UserHandle.class && p[1] == String.class
                    && p[2] == Icon.class && p[3] == int.class && p[4] == int.class
                    && CharSequence.class.isAssignableFrom(p[5])) {
                    constructor.setAccessible(true);
                    return constructor.newInstance(UserHandle.of(0), ctx.getPackageName(),
                        icon, 0, 0, (CharSequence) "Satellite SIMULÉ : aucune connexion réelle");
                }
            }
        } catch (Throwable ex) {
            XposedBridge.log(TAG + "StatusBarIcon construction error: " + ex);
        }
        return null;
    }

    private static boolean isManagedByModernPipeline(Object controller) {
        try {
            Object holder = XposedHelpers.callMethod(controller, "getIconHolder", SLOT, 0);
            return holder != null && holder.getClass().getName().toLowerCase().contains("bindable");
        } catch (Throwable ignored) {
            // Unknown Samsung implementation: we don't claim this is a guarantee.
            return false;
        }
    }

    private static int findSamsungSatelliteResource(Context ctx, int level, int mode) {
        // Only load resources that actually ship with the user's firmware.
        // AOSP maps signal level 0=>_0, 1-2=>_1, 3-4=>_2.
        String strength = level == 0 ? "0" : level <= 2 ? "1" : "2";
        String[] names = mode == 3 ?
                new String[]{
                    "ic_satellite_not_connected", "ic_satellite_searching",
                    "stat_sys_satellite_searching", "ic_satellite_connected_0"
                } :
                new String[]{
                    "ic_satellite_connected_" + strength,
                    "stat_sys_satellite_connected_" + strength,
                    "stat_sys_satellite_" + strength,
                    "ic_satellite_connected", "stat_sys_satellite_connected",
                    "stat_sys_satellite", "ic_satellite"
                };
        Resources res = ctx.getResources();
        String owner = ctx.getPackageName();
        for (String name : names) {
            int id = res.getIdentifier(name, "drawable", owner);
            if (id != 0) return id;
        }
        XposedBridge.log(TAG + "resource lookup failed in " + owner);
        return 0;
    }
}
