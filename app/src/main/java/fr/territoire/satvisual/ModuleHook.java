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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.WeakHashMap;
import java.util.ArrayList;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Display-only LSPosed experiment with a dedicated icon slot and stock Samsung icon resource.
 * No fake telephony connectivity state, satellite modem, SOS, firewall or packet interception.
 * Uses only stock Samsung/AOSP SystemUI drawables. No overlay, no write to the real satellite slot.
 */
public final class ModuleHook implements IXposedHookLoadPackage {
    private static final String TAG = "SatelliteVisualNative: ";
    private static final String SLOT = "satellite_visual_demo";
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
    private static boolean injectionCauseLogged;
    private static boolean busy;
    private static boolean injected;
    private static int lastRes;
    // Only ever remove/update a satellite icon installed by our own module.
    private static final WeakHashMap<Object, Object> ownedIcons = new WeakHashMap<>();

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        // Do not hook screenshot and edge-lighting auxiliary processes.
        if (!"com.android.systemui".equals(lp.packageName)
            || !"com.android.systemui".equals(lp.processName)) return;
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
            // Samsung sometimes registers our settings provider after SystemUI starts.
            scheduleOnMain(() -> {
                config = read(ctx);
                XposedBridge.log(TAG + "CONFIG_BOOT_RETRY enabled=" + config.enabled);
                if (config.enabled) refresh();
            }, 4500);
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
                    XposedBridge.log(TAG + "demo icon switched off");
                }
                return;
            }
            int res = findSamsungSatelliteResource(ctx, config.level, config.mode);
            if (res == 0) {
                XposedBridge.log(TAG + "NO_NATIVE_SATELLITE_DRAWABLE: no built-in Samsung satellite resource matched. Nothing drawn.");
                return;
            }

            try {
                // Samsung's three-argument setIcon updates an existing StatusBarIcon:
                // it throws when the internal icon object has never been registered.
                // Use a private visual slot; never overwrite the OS-owned "satellite" slot.
                Object existing = getSatelliteHolder(controller);
                Object owned = ownedIcons.get(controller);
                if (existing != null && existing != owned) {
                    logOnce("VISUAL_SLOT_CONFLICT: refusing to replace another holder");
                    return;
                }
                if (existing == null && owned != null) {
                    ownedIcons.remove(controller); // The system removed/replaced our icon.
                    owned = null;
                }

                if (existing == null) {
                    Method holderSetter = findHolderSetter(controller.getClass());
                    if (holderSetter == null) {
                        logOnce("NO_HOLDER_SETICON_METHOD: cannot initialize Samsung native satellite slot");
                        return;
                    }
                    Object holder = createNativeIconHolder(holderSetter.getParameterTypes()[1], ctx, res);
                    if (holder == null) return;
                    holderSetter.setAccessible(true);
                    holderSetter.invoke(controller, SLOT, holder);
                    Object installed = getSatelliteHolder(controller);
                    if (installed != holder) {
                        logOnce("SATELLITE_HOLDER_NOT_REGISTERED: Samsung did not retain inserted holder");
                        return;
                    }
                    ownedIcons.put(controller, holder);
                    injected = true;
                    lastRes = res;
                    XposedBridge.log(TAG + "DEMO_NATIVE_HOLDER_REGISTERED slot=" + SLOT + " drawable=" + res);
                } else {
                    Method update = findCompatibleSetIcon(controller.getClass());
                    if (update == null || update.getParameterTypes().length != 3) {
                        logOnce("NO_NATIVE_ICON_UPDATE_METHOD");
                        return;
                    }
                    // This signature was verified from the user's One UI logs.
                    Object[] args;
                    Class<?>[] p = update.getParameterTypes();
                    CharSequence description =
                        "Satellite SIMULÉ : aucune connexion réelle";
                    if (p[0] == CharSequence.class) args = new Object[]{description, SLOT, res};
                    else args = new Object[]{SLOT, res, description};
                    update.setAccessible(true);
                    update.invoke(controller, args);
                    lastRes = res;
                    XposedBridge.log(TAG + "DEMO_NATIVE_ICON_UPDATED slot=" + SLOT
                        + " level=" + config.level);
                }
                XposedHelpers.callMethod(controller, "setIconVisibility", SLOT, true);
            } catch (Throwable error) {
                // InvocationTargetException hides the actual SystemUI exception.
                // Print the root cause once, rather than only its wrapper type.
                if (!injectionCauseLogged) {
                    injectionCauseLogged = true;
                    Throwable cause = error;
                    for (int depth = 0; depth < 5 && cause instanceof InvocationTargetException; depth++) {
                        Throwable target = ((InvocationTargetException) cause).getTargetException();
                        if (target == null || target == cause) break;
                        cause = target;
                    }
                    XposedBridge.log(TAG + "NATIVE_INJECTION_ROOT_CAUSE "
                        + cause.getClass().getName() + ": " + cause.getMessage());
                    XposedBridge.log(cause);
                }
            }
        } catch (Throwable ex) {
            XposedBridge.log(TAG + "apply failed safely: " + ex);
        } finally {
            busy = false;
        }
    }

    private static void removeIfOurs(Object controller) {
        Object owned = ownedIcons.get(controller);
        if (owned == null) return;
        try {
            if (getSatelliteHolder(controller) != owned) {
                ownedIcons.remove(controller);
                injected = !ownedIcons.isEmpty();
                XposedBridge.log(TAG + "SATELLITE_OWNERSHIP_CHANGED: avoiding removal");
                return;
            }
            XposedHelpers.callMethod(controller, "removeIcon", SLOT, 0);
            ownedIcons.remove(controller);
            injected = !ownedIcons.isEmpty();
            lastRes = 0;
        } catch (Throwable e) {
            XposedBridge.log(TAG + "native icon removal failed safely: " + e);
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
                // Samsung One UI on this S23 exposes the reverse-ordered method:
                // setIcon(CharSequence description, String slot, int drawable).
                if (p.length == 3 && p[0] == CharSequence.class && p[1] == String.class
                    && (p[2] == int.class || p[2] == Integer.class)) {
                    return m;
                }
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

    private static Method findHolderSetter(Class<?> cls) {
        for (Class<?> scan = cls; scan != null; scan = scan.getSuperclass()) {
            for (Method m : scan.getDeclaredMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (m.getName().equals("setIcon") && p.length == 2
                    && p[0] == String.class
                    && "com.android.systemui.statusbar.phone.StatusBarIconHolder".equals(p[1].getName())) {
                    return m;
                }
            }
        }
        return null;
    }

    private static Method exactMethod(Class<?> clazz, String name, Class<?>... types) {
        for (Class<?> scan = clazz; scan != null; scan = scan.getSuperclass()) {
            try {
                Method method = scan.getDeclaredMethod(name, types);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) { }
            catch (Throwable error) {
                XposedBridge.log(TAG + "method lookup " + name + ": " + error);
            }
        }
        return null;
    }

    private static boolean listMethodsLogged;

    private static void reportIconListSignatures(Class<?> clazz) {
        if (listMethodsLogged) return;
        listMethodsLogged = true;
        int count = 0;
        for (Class<?> scan = clazz; scan != null; scan = scan.getSuperclass()) {
            for (Method method : scan.getDeclaredMethods()) {
                String name = method.getName();
                if (name.equals("getIcon") || name.equals("getIconHolder")
                    || name.equals("getSlot") || name.equals("getSlotIndex")
                    || name.equals("getSlotName") || name.equals("size")
                    || name.equals("setIcon") || name.equals("getSlots")) {
                    XposedBridge.log(TAG + "ICON_LIST_METHOD " + method.toGenericString());
                    if (++count >= 30) return;
                }
            }
        }
    }

    private static Object getSatelliteHolder(Object controller) throws Exception {
        Object iconList = null;
        for (Class<?> scan = controller.getClass(); scan != null; scan = scan.getSuperclass()) {
            try {
                Field field = scan.getDeclaredField("mStatusBarIconList");
                field.setAccessible(true);
                iconList = field.get(controller);
                break;
            } catch (NoSuchFieldException ignored) { }
        }
        if (iconList == null)
            throw new IllegalStateException("NO_SAMSUNG_ICON_LIST: refuse blind icon injection");

        Class<?> type = iconList.getClass();
        reportIconListSignatures(type);

        // Read the actual signature printed by this Galaxy S23:
        // StatusBarIconList.getIconHolder(int, String).
        Method lookup = exactMethod(type, "getIconHolder", int.class, String.class);
        if (lookup != null) {
            // Check whether the display-only slot already contains any foreign icon,
            // including holders with a non-zero tag.
            Method slotGetter = exactMethod(type, "getSlot", String.class);
            if (slotGetter == null)
                throw new NoSuchMethodException("NO_SLOT_INSPECTION: getSlot(String) missing");
            Object slot = slotGetter.invoke(iconList, SLOT);
            if (slot == null)
                throw new IllegalStateException("NULL_SATELLITE_SLOT");

            Object candidate = lookup.invoke(iconList, 0, SLOT);
            Method hasIcons = exactMethod(slot.getClass(), "hasIconsInSlot");
            if (hasIcons != null && Boolean.TRUE.equals(hasIcons.invoke(slot))
                && candidate == null) {
                // An existing secondary or bindable icon must not be overwritten.
                throw new IllegalStateException("SATELLITE_SLOT_OWNED_BY_ANOTHER_ICON");
            }
            // If hasIconsInSlot isn't available, inspect known holder-list methods
            // rather than treating an unknown occupied slot as empty.
            if (hasIcons == null) {
                Method list = exactMethod(slot.getClass(), "getHolderList");
                if (list == null)
                    throw new NoSuchMethodException("NO_SAFE_SLOT_CONTENT_CHECK");
                Object holders = list.invoke(slot);
                if (holders instanceof java.util.Collection
                    && !((java.util.Collection<?>) holders).isEmpty()
                    && candidate == null) {
                    throw new IllegalStateException("SATELLITE_SLOT_OCCUPIED_WITH_SECONDARY_ICON");
                }
            }
            return candidate;
        }

        // AOSP releases use the reverse signature; keep compatibility.
        lookup = exactMethod(type, "getIconHolder", String.class, int.class);
        if (lookup != null) return lookup.invoke(iconList, SLOT, 0);

        logOnce("NO_COMPATIBLE_ICON_LOOKUP: see ICON_LIST_METHOD signatures");
        throw new NoSuchMethodException("Unknown Samsung getIconHolder signature");
    }

    private static Object createNativeIconHolder(Class<?> holderClass, Context ctx, int drawable) {
        try {
            Class<?> iconType = Class.forName("com.android.internal.statusbar.StatusBarIcon");
            Icon icon = Icon.createWithResource(ctx, drawable);
            Object stockIcon = null;
            for (Constructor<?> ctor : iconType.getDeclaredConstructors()) {
                Class<?>[] p = ctor.getParameterTypes();
                if (p.length == 6 && p[0] == UserHandle.class && p[1] == String.class
                    && p[2] == Icon.class && p[3] == int.class && p[4] == int.class
                    && CharSequence.class.isAssignableFrom(p[5])) {
                    ctor.setAccessible(true);
                    stockIcon = ctor.newInstance(android.os.Process.myUserHandle(), ctx.getPackageName(),
                        icon, 0, 0, (CharSequence) "Satellite SIMULÉ : aucune connexion réelle");
                    break;
                }
            }
            if (stockIcon == null) {
                logOnce("STATUS_BAR_ICON_CONSTRUCTOR_UNAVAILABLE");
                return null;
            }

            for (Method factory : holderClass.getDeclaredMethods()) {
                Class<?>[] p = factory.getParameterTypes();
                if (Modifier.isStatic(factory.getModifiers()) && p.length == 1
                    && p[0].isAssignableFrom(iconType)
                    && holderClass.isAssignableFrom(factory.getReturnType())
                    && factory.getName().equals("fromIcon")) {
                    factory.setAccessible(true);
                    return factory.invoke(null, stockIcon);
                }
            }
            // Kotlin Samsung builds may expose the factory on Companion.
            try {
                Field companionField = holderClass.getDeclaredField("Companion");
                companionField.setAccessible(true);
                Object companion = companionField.get(null);
                if (companion != null) {
                    for (Method factory : companion.getClass().getDeclaredMethods()) {
                        Class<?>[] p = factory.getParameterTypes();
                        if (factory.getName().equals("fromIcon") && p.length == 1
                            && p[0].isAssignableFrom(iconType)
                            && holderClass.isAssignableFrom(factory.getReturnType())) {
                            factory.setAccessible(true);
                            return factory.invoke(companion, stockIcon);
                        }
                    }
                }
            } catch (NoSuchFieldException ignored) { }
            // Alternate versions may provide a StatusBarIconHolder(StatusBarIcon) constructor.
            for (Constructor<?> ctor : holderClass.getDeclaredConstructors()) {
                Class<?>[] p = ctor.getParameterTypes();
                if (p.length == 1 && p[0].isAssignableFrom(iconType)) {
                    ctor.setAccessible(true);
                    return ctor.newInstance(stockIcon);
                }
            }
            for (Method factory : holderClass.getDeclaredMethods()) {
                if (factory.getName().toLowerCase().contains("icon"))
                    XposedBridge.log(TAG + "HOLDER_FACTORY " + factory.toGenericString());
            }
            logOnce("STATUS_BAR_ICON_HOLDER_FACTORY_UNAVAILABLE");
        } catch (Throwable err) {
            XposedBridge.log(TAG + "NATIVE_HOLDER_CREATION_FAILED " + err);
        }
        return null;
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
