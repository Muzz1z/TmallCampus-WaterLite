package local.waterlite.module;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** UI-only customization. No account, payment, device or network API hooks. */
@SuppressWarnings({"rawtypes", "deprecation"})
public final class WaterLite implements IXposedHookLoadPackage {
    private static final String PKG = "com.tmall.campus.and";
    private static final String MAIN = PKG + ".main.MainActivity";
    private static final String SCAN = "com.tmall.campus.scancode.activity.ToolsCaptureActivity";
    private static final String PANEL = "waterlite.home.panel";
    private static final Set<Activity> prepared = Collections.newSetFromMap(new WeakHashMap<Activity, Boolean>());
    private int installed;

    @Override public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam p) {
        if (!PKG.equals(p.packageName)) return;
        final ClassLoader loader = p.classLoader;
        replace(loader, "com.tmall.campus.ad.AdInitJob", "run", null);
        replace(loader, "com.tmall.campus.ad.CampusAdInitJob", "run", null);
        replace(loader, "com.tmall.campus.ad.CampusAd", "getAdid", null);
        hook(loader, "com.tmall.campus.configcenter.Configuration", "load", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args.length != 1 || !(param.args[0] instanceof String)) return;
                String key = (String) param.args[0];
                if ("advertisement_operate_switch".equals(key) || "advertisement_taku_init_switch".equals(key)
                        || "advertisement_jz_init_switch".equals(key)) param.setResult("false");
            }
        });
        hook(loader, "com.tmall.campus.launcher.SplashActivity", "startWork", new XC_MethodReplacement() {
            @Override protected Object replaceHookedMethod(MethodHookParam param) {
                XposedHelpers.callMethod(param.thisObject, "openMainActivity");
                return null;
            }
        });
        hook(loader, "com.tmall.campus.launcher.HotSplashActivity", "initView", new XC_MethodReplacement() {
            @Override protected Object replaceHookedMethod(MethodHookParam param) {
                ((Activity) param.thisObject).finish();
                return null;
            }
        });
        replace(loader, MAIN, "isPopupEnable", false);
        hook(loader, MAIN, "onCreate", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (!param.hasThrowable()) showHome((Activity) param.thisObject, loader);
            }
        });
        hook(loader, MAIN, "onResume", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (!param.hasThrowable()) showHome((Activity) param.thisObject, loader);
            }
        });
        hook(loader, MAIN, "onBackPressed", new XC_MethodReplacement() {
            @Override protected Object replaceHookedMethod(MethodHookParam param) {
                ((Activity) param.thisObject).finish();
                return null;
            }
        });
        replace(loader, SCAN, "loadIconAd", null);
        replace(loader, SCAN, "showAd", null);
        replace(loader, SCAN, "initGrandPromotion", null);
        hook(loader, SCAN, "onCreate", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                Activity activity = (Activity) param.thisObject;
                Intent intent = activity.getIntent();
                if (intent != null) {
                    intent.putExtra("bootTab", "scan");
                    intent.putExtra("title", "请扫描饮水机上的二维码");
                }
            }
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (!param.hasThrowable()) trimScanner((Activity) param.thisObject);
            }
        });
        hook(loader, "com.tmall.campus.ui.base.BaseActivity", "onResume", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (param.hasThrowable()) return;
                final Activity activity = (Activity) param.thisObject;
                if (!"com.tmall.campus.bizwebview.ui.CampusWebActivity".equals(activity.getClass().getName())) return;
                for (int delay : new int[]{0, 350, 1200}) activity.getWindow().getDecorView().postDelayed(
                    () -> hide(activity, new String[]{"fl_ad_render"}), delay);
            }
        });
        XposedBridge.log("WaterLite: configured hooks=" + installed);
    }

    private void replace(ClassLoader loader, String type, String method, Object value) {
        hook(loader, type, method, XC_MethodReplacement.returnConstant(value));
    }

    private void hook(ClassLoader loader, String type, String method, XC_MethodHook callback) {
        try {
            Class<?> cls = XposedHelpers.findClass(type, loader);
            Set<XC_MethodHook.Unhook> found = XposedBridge.hookAllMethods(cls, method, callback);
            if (found.isEmpty()) XposedBridge.log("WaterLite: missing method " + type + "." + method);
            installed += found.size();
        } catch (Throwable error) {
            XposedBridge.log("WaterLite: hook unavailable " + type + "." + method + " " + error.getClass().getSimpleName());
        }
    }

    private static int dp(Context context, int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }

    private static TextView label(Activity activity, String text, int size, int color) {
        TextView view = new TextView(activity);
        view.setText(text); view.setTextSize(size); view.setTextColor(color);
        view.setGravity(Gravity.CENTER); view.setIncludeFontPadding(true);
        return view;
    }

    private static void showHome(final Activity activity, final ClassLoader loader) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        final ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null) return;
        View existing = content.findViewWithTag(PANEL);
        if (existing != null) { existing.bringToFront(); return; }
        final FrameLayout panel = new FrameLayout(activity);
        panel.setTag(PANEL); panel.setBackgroundColor(Color.rgb(246, 249, 252));
        panel.setClickable(true); panel.setFocusable(true);
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL); body.setGravity(Gravity.CENTER);
        body.setPadding(dp(activity, 28), dp(activity, 36), dp(activity, 28), dp(activity, 28));
        FrameLayout.LayoutParams bodyParams = new FrameLayout.LayoutParams(-1, -1);
        panel.addView(body, bodyParams);
        TextView mark = label(activity, "水", 34, Color.rgb(26, 111, 230));
        mark.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        GradientDrawable badge = new GradientDrawable(); badge.setColor(Color.rgb(226, 238, 255)); badge.setCornerRadius(dp(activity, 28));
        mark.setBackground(badge);
        body.addView(mark, new LinearLayout.LayoutParams(dp(activity, 88), dp(activity, 88)));
        TextView title = label(activity, "扫码接水", 30, Color.rgb(22, 35, 53));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2); titleParams.topMargin = dp(activity, 24);
        body.addView(title, titleParams);
        TextView description = label(activity, "扫描饮水机二维码，进入用水服务", 15, Color.rgb(99, 115, 136));
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(-1, -2); textParams.topMargin = dp(activity, 12);
        body.addView(description, textParams);
        Button scan = new Button(activity); scan.setText("开始扫码"); scan.setTextSize(19); scan.setTextColor(Color.WHITE); scan.setAllCaps(false);
        GradientDrawable buttonBackground = new GradientDrawable(); buttonBackground.setColor(Color.rgb(26, 111, 230)); buttonBackground.setCornerRadius(dp(activity, 18));
        scan.setBackground(buttonBackground);
        LinearLayout.LayoutParams scanParams = new LinearLayout.LayoutParams(-1, dp(activity, 64)); scanParams.topMargin = dp(activity, 38);
        body.addView(scan, scanParams);
        scan.setOnClickListener(view -> {
            try {
                Intent intent = new Intent(); intent.setClassName(PKG, SCAN);
                intent.putExtra("bootTab", "scan");
                intent.putExtra("title", "请扫描饮水机上的二维码");
                activity.startActivity(intent);
            } catch (Throwable error) { Toast.makeText(activity, "暂时无法打开扫码，请重启应用后重试", Toast.LENGTH_LONG).show(); }
        });
        Button account = new Button(activity); account.setText("账户与设置"); account.setTextSize(15); account.setAllCaps(false);
        account.setTextColor(Color.rgb(99, 115, 136)); account.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams accountParams = new LinearLayout.LayoutParams(-1, dp(activity, 52)); accountParams.topMargin = dp(activity, 14);
        body.addView(account, accountParams);
        account.setOnClickListener(view -> {
            try {
                Intent intent = new Intent(); intent.setClassName(PKG, "com.tmall.campus.profile.ui.SettingsActivity"); activity.startActivity(intent);
            } catch (Throwable error) { Toast.makeText(activity, "暂时无法打开设置", Toast.LENGTH_SHORT).show(); }
        });
        TextView note = label(activity, "首次使用请按服务页面完成登录", 12, Color.rgb(132, 145, 161));
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(-1, -2); noteParams.topMargin = dp(activity, 28); body.addView(note, noteParams);
        content.addView(panel, new ViewGroup.LayoutParams(-1, -1));
        activity.getWindow().setStatusBarColor(Color.rgb(246, 249, 252));
        activity.getWindow().setNavigationBarColor(Color.rgb(246, 249, 252));
        if (prepared.add(activity)) for (int delay : new int[]{100, 500, 1500}) panel.postDelayed(() -> { if (!activity.isDestroyed()) panel.bringToFront(); }, delay);
    }

    private static void hide(Activity activity, String[] names) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        for (String name : names) {
            int id = activity.getResources().getIdentifier(name, "id", PKG);
            View view = id == 0 ? null : activity.findViewById(id);
            if (view != null) view.setVisibility(View.GONE);
        }
    }

    private static void trimScanner(Activity activity) {
        hide(activity, new String[]{"fl_ad_container", "cl_ad", "icon_ad_container", "fl_ad_tag", "ll_tab", "rl_tab_selector", "iv_gif", "iv_config"});
    }
}
