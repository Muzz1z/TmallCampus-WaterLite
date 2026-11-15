package local.waterlite.module;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebView;
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
import java.util.Map;
import java.lang.ref.WeakReference;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** UI customization plus official SDK session recovery; no credential export or payment API hooks. */
@SuppressWarnings({"rawtypes", "deprecation"})
public final class WaterLite implements IXposedHookLoadPackage {
    private static final String PKG = "com.tmall.campus.and";
    private static final String MAIN = PKG + ".main.MainActivity";
    private static final String SCAN = "com.tmall.campus.scancode.activity.ToolsCaptureActivity";
    private static final String PANEL = "waterlite.home.panel";
    private static final String LOGIN_STATUS = "waterlite.login.status";
    private static final String HOLDER = "com.tmall.campus.webview.holder.WebViewHolder";
    private static final Map<View, PageGate> pageGates = new WeakHashMap<>();
    private static final Set<Activity> prepared = Collections.newSetFromMap(new WeakHashMap<Activity, Boolean>());
    private static WeakReference<Activity> home = new WeakReference<>(null);
    private static long lastRecoveryAttempt;
    private static long lastSessionLog;
    private int installed;

    @Override public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam p) {
        if (!PKG.equals(p.packageName)) return;
        final ClassLoader loader = p.classLoader;
        replace(loader, "com.taobao.update.apk.ApkUpdater", "doUpdate", false);
        replace(loader, "com.tmall.campus.update.ui.UpdateUIConfirmImpl", "alertForConfirm", null);
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
                if (!param.hasThrowable()) {
                    Activity activity = (Activity) param.thisObject;
                    showHome(activity, loader);
                    for (int delay : new int[]{800, 2500, 6000}) activity.getWindow().getDecorView().postDelayed(
                        () -> maintainSession(activity, loader), delay);
                }
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
                for (int delay : new int[]{0, 350, 1200, 4000}) activity.getWindow().getDecorView().postDelayed(
                    () -> { hide(activity, new String[]{"fl_ad_render"}); injectPageTree(activity.getWindow().getDecorView()); }, delay);
            }
        });
        hook(loader, HOLDER, "loadUrl", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                if (!(param.thisObject instanceof View) || param.args.length != 1 || !(param.args[0] instanceof String)) return;
                View holder = (View) param.thisObject;
                String url = (String) param.args[0];
                if (isWaterUrl(url)) beginWaterPage(holder, url);
                else releaseGate(pageGates.get(holder), "navigation");
            }
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (param.hasThrowable() && param.thisObject instanceof View) releaseGate(pageGates.get((View) param.thisObject), "load-error");
            }
        });
        for (String client : new String[]{"android.taobao.windvane.webview.WVWebViewClient", "android.taobao.windvane.extra.uc.WVUCWebViewClient"}) {
            hook(loader, client, "onPageStarted", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args.length < 2 || !(param.args[0] instanceof View) || !(param.args[1] instanceof String)) return;
                    View holder = findHolder((View) param.args[0]);
                    if (holder == null) return;
                    String url = (String) param.args[1];
                    if (isWaterUrl(url)) beginWaterPage(holder, url);
                    else releaseGate(pageGates.get(holder), "redirect");
                }
            });
            hook(loader, client, "onPageFinished", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (param.args.length < 2 || !(param.args[0] instanceof View) || !(param.args[1] instanceof String)) return;
                    View holder = findHolder((View) param.args[0]);
                    PageGate gate = holder == null ? null : pageGates.get(holder);
                    if (gate != null && gate.url.equals(param.args[1])) {
                        gate.documentFinished = true;
                        Log.i("WaterLite", "water document finished behind loading cover");
                    }
                }
            });
        }
        hook(loader, "androidx.fragment.app.FragmentActivity", "onPause", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (param.thisObject.getClass().getName().startsWith("com.tmall.campus.")) flushCookies(loader);
            }
        });
        hook(loader, "com.tmall.campus.user.LoginManager", "onReceive", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (param.hasThrowable() || param.args.length < 2 || !(param.args[1] instanceof Intent)) return;
                String action = ((Intent) param.args[1]).getAction();
                if (action == null || !action.endsWith("NOTIFY_LOGIN_SUCCESS")) return;
                flushCookies(loader);
                Activity activity = home.get();
                if (activity != null) activity.getWindow().getDecorView().postDelayed(() -> maintainSession(activity, loader), 700);
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
        home = new WeakReference<>(activity);
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
        TextView note = label(activity, "正在检查登录状态", 12, Color.rgb(132, 145, 161));
        note.setTag(LOGIN_STATUS);
        note.setOnClickListener(view -> {
            try {
                Class<?> center = XposedHelpers.findClass("com.tmall.campus.user.UserCenter", loader);
                XposedHelpers.callMethod(XposedHelpers.getStaticObjectField(center, "INSTANCE"), "realLogin");
            } catch (Throwable error) { Toast.makeText(activity, "请从账户与设置中登录", Toast.LENGTH_SHORT).show(); }
        });
        note.setClickable(false);
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

    private static void maintainSession(Activity activity, ClassLoader loader) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        ViewGroup content = activity.findViewById(android.R.id.content);
        TextView status = content == null ? null : content.findViewWithTag(LOGIN_STATUS);
        try {
            Class<?> center = XposedHelpers.findClass("com.tmall.campus.user.UserCenter", loader);
            boolean ready = XposedHelpers.getStaticBooleanField(center, "isSDKInited");
            if (!ready) { if (status != null) { status.setText("正在检查登录状态"); status.setClickable(false); } return; }
            Class<?> login = XposedHelpers.findClass("com.taobao.login4android.Login", loader);
            boolean valid = Boolean.TRUE.equals(XposedHelpers.callStaticMethod(login, "checkSessionValid"));
            Object token = XposedHelpers.callStaticMethod(login, "getLoginToken");
            boolean refreshAvailable = token instanceof String && !((String) token).isEmpty();
            long now = SystemClock.elapsedRealtime();
            if (now - lastSessionLog > 10000 || lastSessionLog == 0) {
                Log.i("WaterLite", "session ready=true valid=" + valid + " refreshAvailable=" + refreshAvailable);
                lastSessionLog = now;
            }
            if (valid) { if (status != null) { status.setText("已登录"); status.setClickable(false); } return; }
            if (refreshAvailable && (lastRecoveryAttempt == 0 || now - lastRecoveryAttempt > 15 * 60 * 1000L)) {
                lastRecoveryAttempt = now;
                if (status != null) { status.setText("正在恢复登录，请稍候"); status.setClickable(false); }
                // false requests the official SDK's silent refresh; it does not suppress server validation.
                XposedHelpers.callStaticMethod(login, "login", false);
                activity.getWindow().getDecorView().postDelayed(() -> maintainSession(activity, loader), 6000);
                Log.i("WaterLite", "official silent session refresh requested");
            } else if (status != null) {
                boolean pending = refreshAvailable && now - lastRecoveryAttempt < 8000;
                status.setText(pending ? "正在恢复登录，请稍候" : "登录已失效，点此重新登录");
                status.setClickable(!pending);
            }
        } catch (Throwable error) {
            if (status != null) status.setText("登录状态暂不可用，可从账户与设置检查");
            Log.i("WaterLite", "session status unavailable: " + error.getClass().getSimpleName());
        }
    }

    private static void flushCookies(ClassLoader loader) {
        Thread writer = new Thread(() -> {
            try { CookieManager.getInstance().flush(); } catch (Throwable ignored) {}
            try {
                Class<?> manager = XposedHelpers.findClass("com.uc.webview.export.CookieManager", loader);
                XposedHelpers.callMethod(XposedHelpers.callStaticMethod(manager, "getInstance"), "flush");
            } catch (Throwable ignored) {}
        }, "waterlite-cookie-flush");
        writer.setDaemon(true); writer.start();
    }

    private static void injectPageTree(View view) {
        if ("com.tmall.campus.webview.holder.WebViewHolder".equals(view.getClass().getName())) { injectWaterPage(view); return; }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) injectPageTree(group.getChildAt(i));
        }
    }

    private static void injectWaterPage(View holder) {
        if (!holder.isAttachedToWindow()) return;
        if (pageGates.containsKey(holder)) return; // The gate owns injection until the first styled frame.
        try {
            Object result = XposedHelpers.callMethod(holder, "getUrl");
            if (!(result instanceof String) || !isWaterUrl((String) result)) return;
            XposedHelpers.callMethod(holder, "evaluateJavascript", WaterPageScript.SCRIPT);
        } catch (Throwable error) { Log.i("WaterLite", "water page style unavailable: " + error.getClass().getSimpleName()); }
    }

    private static boolean isWaterUrl(String url) {
        if (url == null) return false;
        android.net.Uri uri = android.net.Uri.parse(url);
        return "https".equals(uri.getScheme()) && "share.confong.cn".equals(uri.getHost())
            && (uri.getPort() == -1 || uri.getPort() == 443)
            && "/app/tmall-xiaoyuan/tmxy-m-share/laundry/waterDetail".equals(uri.getPath());
    }

    private static View findHolder(View view) {
        for (View current = view; current != null;) {
            if (HOLDER.equals(current.getClass().getName())) return current;
            current = current.getParent() instanceof View ? (View) current.getParent() : null;
        }
        return null;
    }

    private static final class PageGate {
        final WeakReference<View> holder;
        final WeakReference<View> web;
        final WeakReference<FrameLayout> cover;
        final String url;
        final float previousAlpha;
        final long started = SystemClock.elapsedRealtime();
        boolean documentFinished, revealing, expired;
        View.OnAttachStateChangeListener attachListener;
        PageGate(View owner, View webView, FrameLayout overlay, String target) {
            holder = new WeakReference<>(owner); web = new WeakReference<>(webView);
            cover = new WeakReference<>(overlay); url = target; previousAlpha = webView.getAlpha();
        }
        boolean active() { View owner = holder.get(); return owner != null && pageGates.get(owner) == this; }
    }

    private static void beginWaterPage(View owner, String url) {
        if (!(owner instanceof FrameLayout)) return;
        releaseGate(pageGates.get(owner), "replaced");
        try {
            Object inner = XposedHelpers.callMethod(owner, "getMWebView");
            if (!(inner instanceof View)) return;
            View web = (View) inner;
            FrameLayout cover = new FrameLayout(owner.getContext());
            cover.setBackgroundColor(Color.rgb(246, 249, 252)); cover.setClickable(true);
            PageGate gate = new PageGate(owner, web, cover, url);
            pageGates.put(owner, gate);
            // A nonzero alpha keeps WebView animation/render callbacks running under the opaque cover.
            web.setAlpha(0.01f);
            ((FrameLayout) owner).addView(cover, new FrameLayout.LayoutParams(-1, -1));
            renderLoading(gate, false);
            gate.attachListener = new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View view) {}
                @Override public void onViewDetachedFromWindow(View view) { releaseGate(gate, "detached"); }
            };
            owner.addOnAttachStateChangeListener(gate.attachListener);
            owner.post(() -> pollWaterPage(gate));
            Log.i("WaterLite", "water loading cover attached before page display");
        } catch (Throwable error) {
            releaseGate(pageGates.get(owner), "setup-error");
            Log.i("WaterLite", "water loading cover unavailable: " + error.getClass().getSimpleName());
        }
    }

    private static void renderLoading(PageGate gate, boolean failed) {
        FrameLayout cover = gate.cover.get();
        if (cover == null || !gate.active()) return;
        cover.removeAllViews();
        Context context = cover.getContext();
        LinearLayout body = new LinearLayout(context); body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER); body.setPadding(dp(context, 28), dp(context, 28), dp(context, 28), dp(context, 28));
        TextView title = new TextView(context); title.setText(failed ? "设备页加载较慢" : "正在加载用水服务…");
        title.setTextSize(18); title.setTextColor(Color.rgb(22, 35, 53)); title.setGravity(Gravity.CENTER);
        body.addView(title, new LinearLayout.LayoutParams(-1, -2));
        if (failed) {
            Button retry = new Button(context); retry.setText("重试");
            body.addView(retry, new LinearLayout.LayoutParams(-1, dp(context, 52)));
            retry.setOnClickListener(view -> {
                View owner = gate.holder.get(); if (owner == null || !gate.active()) return;
                beginWaterPage(owner, gate.url);
                try { XposedHelpers.callMethod(owner, "refresh"); }
                catch (Throwable error) { PageGate next = pageGates.get(owner); if (next != null) { next.expired = true; renderLoading(next, true); } }
            });
            Button original = new Button(context); original.setText("显示原版");
            body.addView(original, new LinearLayout.LayoutParams(-1, dp(context, 52)));
            original.setOnClickListener(view -> {
                View owner = gate.holder.get(); if (owner == null || !gate.active()) return;
                try { XposedHelpers.callMethod(owner, "evaluateJavascript", "(function(){if(window.__waterliteUiV3)window.__waterliteUiV3.setMode('original');})();"); }
                catch (Throwable ignored) {}
                releaseGate(gate, "manual-original");
            });
        }
        cover.addView(body, new FrameLayout.LayoutParams(-1, -1));
    }

    private static void pollWaterPage(PageGate gate) {
        View owner = gate.holder.get();
        if (owner == null || !gate.active() || gate.expired || gate.revealing) return;
        if (SystemClock.elapsedRealtime() - gate.started > 15000) {
            gate.expired = true; renderLoading(gate, true);
            Log.i("WaterLite", "water loading cover timed out; manual fallback available"); return;
        }
        FrameLayout cover = gate.cover.get(); if (cover != null) cover.bringToFront();
        // Keep a single evaluation in flight; a missing callback also reaches the timeout.
        final boolean[] completed = {false};
        owner.postDelayed(() -> { if (!completed[0] && gate.active()) { completed[0] = true; pollWaterPage(gate); } }, 1200);
        try {
            String probe = "(function(){var result=" + WaterPageScript.SCRIPT
                + "return result && result.ready && result.complete ? 'waterlite-ready-v3' : 'waterlite-wait-v3';})()";
            XposedHelpers.callMethod(owner, "evaluateJavascript", probe, (ValueCallback<String>) value -> {
                if (completed[0]) return; completed[0] = true;
                if (!gate.active() || gate.expired) return;
                boolean ready = "\"waterlite-ready-v3\"".equals(value) || "waterlite-ready-v3".equals(value);
                if (ready && gate.documentFinished) revealWaterPage(gate);
                else { View holder = gate.holder.get(); if (holder != null) holder.postDelayed(() -> pollWaterPage(gate), 80); }
            });
        } catch (Throwable error) { completed[0] = true; owner.postDelayed(() -> pollWaterPage(gate), 160); }
    }

    private static WebView findSystemWebView(View view) {
        if (view instanceof WebView) return (WebView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) { WebView found = findSystemWebView(group.getChildAt(i)); if (found != null) return found; }
        }
        return null;
    }

    private static void revealWaterPage(PageGate gate) {
        View owner = gate.holder.get(), web = gate.web.get();
        if (!gate.active() || owner == null || web == null || gate.revealing) return;
        gate.revealing = true;
        // Keep the opaque cover while the renderer submits its styled frame.
        web.setAlpha(gate.previousAlpha);
        Runnable finish = () -> { if (gate.active()) releaseGate(gate, "styled-frame"); };
        WebView system = findSystemWebView(web);
        if (system != null) {
            try { system.postVisualStateCallback(gate.started, new WebView.VisualStateCallback() {
                @Override public void onComplete(long requestId) { finish.run(); }
            }); } catch (Throwable ignored) { owner.postOnAnimation(() -> owner.postOnAnimation(finish)); }
        } else owner.postOnAnimation(() -> owner.postOnAnimation(finish));
        owner.postDelayed(finish, 700); // Styled DOM is ready even if an engine omits its visual callback.
    }

    private static void releaseGate(PageGate gate, String reason) {
        if (gate == null || !gate.active()) return;
        View owner = gate.holder.get(), web = gate.web.get(); FrameLayout cover = gate.cover.get();
        pageGates.remove(owner);
        if (owner != null && gate.attachListener != null) owner.removeOnAttachStateChangeListener(gate.attachListener);
        if (web != null) web.setAlpha(gate.previousAlpha);
        if (cover != null && cover.getParent() instanceof ViewGroup) ((ViewGroup) cover.getParent()).removeView(cover);
        if ("styled-frame".equals(reason)) Log.i("WaterLite", "water styled frame revealed after " + (SystemClock.elapsedRealtime() - gate.started) + "ms");
    }
}
