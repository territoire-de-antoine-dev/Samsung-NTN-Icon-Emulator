package fr.territoire.satvisual;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.WeakHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Point d'accroche SystemUI, affichage seulement. Fail-safe et désactivé par défaut. */
public final class ModuleHook implements IXposedHookLoadPackage {
  private static final String TAG = "SatelliteVisual: ";
  private static final WeakHashMap<View,Boolean> attached = new WeakHashMap<>();
  private static final ArrayList<WeakReference<SatelliteDrawable>> drawables = new ArrayList<>();
  private static boolean receiverReady = false;
  private static VisualConfig state = new VisualConfig();

  @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
    if (!"com.android.systemui".equals(lp.packageName)) return;
    // Interception d'une fenêtre TYPE_STATUS_BAR si One UI utilise ce point d'entrée.
    try {
      Class<?> wm = XposedHelpers.findClassIfExists("android.view.WindowManagerGlobal",lp.classLoader);
      if (wm!=null) XposedBridge.hookAllMethods(wm,"addView",new XC_MethodHook() {
        @Override protected void afterHookedMethod(MethodHookParam p) {
          if (p.hasThrowable() || p.args==null || p.args.length<2) return;
          if (!(p.args[0] instanceof View) || !(p.args[1] instanceof WindowManager.LayoutParams)) return;
          WindowManager.LayoutParams params=(WindowManager.LayoutParams)p.args[1];
          if (params.type==WindowManager.LayoutParams.TYPE_STATUS_BAR) attach((View)p.args[0]);
        }
      });
    } catch(Throwable ex) { XposedBridge.log(TAG+"window hook: "+ex); }
    // Classes alternatives utilisées par Samsung et AOSP selon les versions.
    String[] classes = {
      "com.android.systemui.statusbar.phone.PhoneStatusBarView",
      "com.android.systemui.statusbar.phone.StatusBarWindowView",
      "com.android.systemui.statusbar.window.StatusBarWindowView",
      "com.android.systemui.statusbar.phone.SecStatusBarWindowView"
    };
    for(String name:classes) try {
      Class<?> type=XposedHelpers.findClassIfExists(name,lp.classLoader);
      if (type==null) continue;
      XposedBridge.hookAllMethods(type,"onAttachedToWindow",new XC_MethodHook() {
        @Override protected void afterHookedMethod(MethodHookParam p) {
          if (!p.hasThrowable() && p.thisObject instanceof View) attach((View)p.thisObject);
        }
      });
    } catch(Throwable ex) { XposedBridge.log(TAG+name+" hook: "+ex); }
  }

  private static void attach(View root) {
    synchronized(attached) {
      if (attached.containsKey(root)) return;
      attached.put(root,true);
    }
    root.post(()->apply(root));
  }
  private static void apply(View root) {
    try {
      Context ctx=root.getContext();
      if (ctx==null) return;
      if (!receiverReady) {
        state=load(ctx);
        BroadcastReceiver receiver=new BroadcastReceiver() {
          @Override public void onReceive(Context c,Intent i) {
            state=load(c);
            synchronized(drawables) {
              for(int j=drawables.size()-1;j>=0;j--) {
                SatelliteDrawable d=drawables.get(j).get();
                if(d==null) drawables.remove(j); else d.setConfig(state);
              }
            }
          }
        };
        IntentFilter filter=new IntentFilter(VisualConfig.REFRESH);
        if (Build.VERSION.SDK_INT>=33) ctx.registerReceiver(receiver,filter,Context.RECEIVER_EXPORTED);
        else ctx.registerReceiver(receiver,filter);
        receiverReady=true;
      }
      SatelliteDrawable icon=new SatelliteDrawable(ctx.getResources().getDisplayMetrics().density,state);
      synchronized(drawables) { drawables.add(new WeakReference<>(icon)); }
      root.getOverlay().add(icon);
      ViewTreeObserver.OnPreDrawListener listener=()->{
        int w=root.getWidth(),h=root.getHeight();
        if(w!=icon.getBounds().width() || h!=icon.getBounds().height()) icon.setBounds(0,0,w,h);
        return true;
      };
      root.getViewTreeObserver().addOnPreDrawListener(listener);
      root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
        @Override public void onViewAttachedToWindow(View v) {
          icon.setBounds(0,0,v.getWidth(),v.getHeight());
        }
        @Override public void onViewDetachedFromWindow(View v) {
          try { v.getOverlay().remove(icon); } catch(Throwable ignored) {}
          try { if(v.getViewTreeObserver().isAlive()) v.getViewTreeObserver().removeOnPreDrawListener(listener); } catch(Throwable ignored) {}
          synchronized(attached) { attached.remove(v); }
        }
      });
      icon.setBounds(0,0,root.getWidth(),root.getHeight());
      XposedBridge.log(TAG+"overlay attached: "+root.getClass().getName());
    } catch(Throwable ex) { XposedBridge.log(TAG+"attach failed safely: "+ex); }
  }
  private static VisualConfig load(Context ctx) {
    try {
      Bundle b=ctx.getContentResolver().call(Uri.parse(VisualConfig.URI),"state",null,null);
      return VisualConfig.fromBundle(b);
    } catch(Throwable ex) {
      XposedBridge.log(TAG+"settings bridge unavailable: "+ex);
      return new VisualConfig();
    }
  }
}
