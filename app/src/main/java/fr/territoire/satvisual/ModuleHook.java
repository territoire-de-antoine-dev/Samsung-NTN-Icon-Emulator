package fr.territoire.satvisual;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import java.lang.ref.WeakReference;
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
                // True SystemUI-owned icon slot: unlike V1, this does not use ViewOverlay.
                XposedHelpers.callMethod(controller, "setIcon", SLOT, res,
                        (CharSequence) "Satellite (simulation visuelle, aucune connexion réelle)");
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
                        + " drawable=" + ctx.getResources().getResourceEntryName(res)
                        + " level=" + config.level);
            } catch (Throwable error) {
                XposedBridge.log(TAG + "setIcon unsupported on this Samsung SystemUI: " + error);
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
