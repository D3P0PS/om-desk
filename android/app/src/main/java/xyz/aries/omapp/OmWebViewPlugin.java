package xyz.aries.omapp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * openmarket.xyz full screen in a second, native WebView over the app's own WebView
 * (the site forbids iframes: X-Frame-Options SAMEORIGIN). The site keeps its own UI and
 * navigation; the app adds one small draggable button that opens its panel (alerts,
 * settings). The view keeps its state when hidden and the login cookies across restarts.
 * The app never runs its own code inside the site.
 */
@CapacitorPlugin(name = "OmWebView")
public class OmWebViewPlugin extends Plugin {
    private static final String HOME = "https://openmarket.xyz/chart";
    /** Hosts the site needs inside the view (auth, captcha, payments); everything else opens in the browser. */
    private static final String[] INSIDE = {
            "openmarket.xyz", "kiyotaka.ai", "privy.io", "privy.systems", "cloudflare.com",
            "stripe.com", "walletconnect.com", "walletconnect.org",
    };
    private static final int FAB_DP = 44;

    private WebView web;
    private ImageButton fab;
    private Insets bars = Insets.NONE;
    private float fabFraction = -1;

    @SuppressLint("SetJavaScriptEnabled")
    private WebView ensureWeb() {
        if (web != null) return web;
        web = new WebView(getContext());
        web.setBackgroundColor(Color.parseColor("#0b0c0e"));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setSupportMultipleWindows(false); // window.open → same view
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        // The site never needs local files; no script is ever injected into it, and no
        // JavaScript bridge is attached to this view (only to Capacitor's own WebView).
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true); // Privy login runs in a third-party frame

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                String host = u.getHost() == null ? "" : u.getHost();
                if (host.equals("accounts.google.com")) {
                    // Google refuses OAuth inside embedded WebViews (disallowed_useragent).
                    Toast.makeText(getContext(), getContext().getString(R.string.google_blocked), Toast.LENGTH_LONG).show();
                    return true;
                }
                if (!"https".equals(u.getScheme()) && !"http".equals(u.getScheme())) {
                    openExternal(u);
                    return true;
                }
                for (String h : INSIDE) {
                    if (host.equals(h) || host.endsWith("." + h)) return false;
                }
                openExternal(u);
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                CookieManager.getInstance().flush();
            }
        });

        ViewGroup parent = (ViewGroup) getBridge().getWebView().getParent();
        parent.addView(web, new CoordinatorLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ViewCompat.setOnApplyWindowInsetsListener(web, (v, insets) -> {
            // The keyboard is already handled by Capacitor's SystemBars (it pads the whole
            // window by the IME height), so only the system bars matter here; adding the IME
            // again would count the keyboard twice.
            bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            layout();
            return insets;
        });
        web.setVisibility(View.GONE);
        web.loadUrl(HOME);
        ensureFab(parent);
        return web;
    }

    /** Below the status bar, above the navigation bar (the keyboard: see the insets listener). */
    private void layout() {
        if (web == null) return;
        CoordinatorLayout.LayoutParams lp = (CoordinatorLayout.LayoutParams) web.getLayoutParams();
        lp.topMargin = bars.top;
        lp.bottomMargin = bars.bottom;
        web.setLayoutParams(lp);
        placeFab();
    }

    // --- floating button -----------------------------------------------------------

    private SharedPreferences prefs() {
        return getContext().getSharedPreferences("omapp.ui", Context.MODE_PRIVATE);
    }

    private int dp(float v) {
        return Math.round(v * getContext().getResources().getDisplayMetrics().density);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void ensureFab(ViewGroup parent) {
        if (fab != null) return;
        fab = new ImageButton(getContext());
        fab.setImageResource(R.drawable.ic_stat_alert);
        fab.setColorFilter(Color.WHITE);
        fab.setContentDescription(getContext().getString(R.string.fab_desc));
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.parseColor("#CC1F2228"));
        bg.setStroke(dp(1), Color.parseColor("#33FFFFFF"));
        fab.setBackground(bg);
        fab.setPadding(dp(11), dp(11), dp(11), dp(11));
        fab.setElevation(dp(6));
        CoordinatorLayout.LayoutParams lp = new CoordinatorLayout.LayoutParams(dp(FAB_DP), dp(FAB_DP));
        lp.gravity = Gravity.TOP | Gravity.END;
        parent.addView(fab, lp);
        fab.setVisibility(View.GONE);

        // Tap opens the panel; a vertical drag moves the button (remembered), so it never
        // has to sit on top of something the site needs.
        final float[] start = new float[2];
        final boolean[] dragging = { false };
        fab.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    start[0] = e.getRawY();
                    start[1] = currentFabFraction();
                    dragging[0] = false;
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dy = e.getRawY() - start[0];
                    if (Math.abs(dy) > dp(6)) dragging[0] = true;
                    if (dragging[0]) setFabFraction(start[1] + dy / Math.max(1, usableHeight()), false);
                    return true;
                }
                case MotionEvent.ACTION_UP:
                    if (dragging[0]) setFabFraction(currentFabFraction(), true);
                    else notifyListeners("menu", new JSObject());
                    return true;
                default:
                    return true;
            }
        });
        placeFab();
    }

    private float usableHeight() {
        View root = (View) fab.getParent();
        return root.getHeight() - bars.top - bars.bottom - dp(FAB_DP);
    }

    private float currentFabFraction() {
        if (fabFraction < 0) fabFraction = prefs().getFloat("fabY", 0.62f);
        return fabFraction;
    }

    private void setFabFraction(float f, boolean save) {
        fabFraction = Math.max(0.02f, Math.min(0.98f, f));
        if (save) prefs().edit().putFloat("fabY", fabFraction).apply();
        placeFab();
    }

    private void placeFab() {
        if (fab == null) return;
        View root = (View) fab.getParent();
        if (root.getHeight() == 0) {
            root.post(this::placeFab);
            return;
        }
        CoordinatorLayout.LayoutParams lp = (CoordinatorLayout.LayoutParams) fab.getLayoutParams();
        lp.topMargin = bars.top + Math.round(currentFabFraction() * usableHeight());
        lp.rightMargin = bars.right + dp(6);
        fab.setLayoutParams(lp);
    }

    // --- misc ------------------------------------------------------------------------

    private void openExternal(Uri u) {
        try {
            getActivity().startActivity(new Intent(Intent.ACTION_VIEW, u));
        } catch (Exception e) {
            Toast.makeText(getContext(), getContext().getString(R.string.no_app_for, u.toString()), Toast.LENGTH_SHORT).show();
        }
    }

    @PluginMethod
    public void show(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            WebView w = ensureWeb();
            ViewCompat.requestApplyInsets(w);
            w.setVisibility(View.VISIBLE);
            fab.setVisibility(View.VISIBLE);
            w.onResume();
            call.resolve();
        });
    }

    @PluginMethod
    public void hide(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            if (web != null) {
                web.setVisibility(View.GONE);
                fab.setVisibility(View.GONE);
                web.onPause();
                CookieManager.getInstance().flush();
            }
            call.resolve();
        });
    }

    @PluginMethod
    public void back(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            JSObject r = new JSObject();
            boolean can = web != null && web.getVisibility() == View.VISIBLE && web.canGoBack();
            if (can) web.goBack();
            r.put("handled", can);
            call.resolve(r);
        });
    }

    @PluginMethod
    public void reload(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            ensureWeb().reload();
            call.resolve();
        });
    }

    @PluginMethod
    public void navigate(PluginCall call) {
        String url = call.getString("url", HOME);
        getActivity().runOnUiThread(() -> {
            ensureWeb().loadUrl(url);
            call.resolve();
        });
    }

    /** Ko-fi and other outside links: always the phone's browser, only https. */
    @PluginMethod
    public void openExternal(PluginCall call) {
        String url = call.getString("url", "");
        Uri u = Uri.parse(url);
        if (!"https".equals(u.getScheme())) {
            call.reject("only https links");
            return;
        }
        getActivity().runOnUiThread(() -> {
            openExternal(u);
            call.resolve();
        });
    }

    /** Native toast: when the OpenMarket view is up it covers the app's own page. */
    @PluginMethod
    public void toast(PluginCall call) {
        String text = call.getString("text", "");
        getActivity().runOnUiThread(() -> {
            Toast.makeText(getContext(), text, Toast.LENGTH_LONG).show();
            call.resolve();
        });
    }

    @Override
    protected void handleOnPause() {
        CookieManager.getInstance().flush();
    }
}
