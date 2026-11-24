package com.winlator.cmod;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class PocketWinDesktopActivity extends Activity {
    private WebView desktop;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().setNavigationBarColor(android.graphics.Color.BLACK);
        getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);

        desktop = new WebView(this);
        desktop.setBackgroundColor(0xFF071426);
        WebSettings settings = desktop.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportMultipleWindows(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) settings.setSafeBrowsingEnabled(true);

        // Request desktop site layouts from the system Chromium WebView engine.
        String userAgent = WebSettings.getDefaultUserAgent(this);
        userAgent = userAgent.replaceAll("\\\\(Linux; Android[^)]*\\\\)", "(X11; Linux x86_64)");
        userAgent = userAgent.replace("Mobile ", "");
        settings.setUserAgentString(userAgent);

        desktop.addJavascriptInterface(new DesktopBridge(this), "PocketWin");
        desktop.setWebChromeClient(new WebChromeClient());
        desktop.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
                return !("https".equals(scheme) || "http".equals(scheme) || "file".equals(scheme));
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url == null) return true;
                return !(url.startsWith("https://") || url.startsWith("http://")
                        || url.startsWith("file:///android_asset/"));
            }
        });
        setContentView(desktop);
        desktop.loadUrl("file:///android_asset/pocketwin_desktop/index.html");
    }

    @Override
    public void onBackPressed() {
        if (desktop != null) {
            desktop.evaluateJavascript("window.PocketWinUI && window.PocketWinUI.handleBack()", null);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (desktop != null) {
            desktop.removeJavascriptInterface("PocketWin");
            desktop.destroy();
            desktop = null;
        }
        super.onDestroy();
    }

    private void openMainSection(int sectionId) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("selected_menu_item_id", sectionId);
        startActivity(intent);
    }

    private static String iconAsBase64(PackageManager pm, ResolveInfo info) {
        Drawable drawable = info.loadIcon(pm);
        if (drawable == null) return "";
        Bitmap bitmap = Bitmap.createBitmap(88, 88, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, 88, 88);
        drawable.draw(canvas);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, stream);
        bitmap.recycle();
        return android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.NO_WRAP);
    }

    private class DesktopBridge {
        private final Activity activity;

        DesktopBridge(Activity activity) {
            this.activity = activity;
        }

        @JavascriptInterface
        public String getApps() {
            JSONArray apps = new JSONArray();
            try {
                PackageManager pm = getPackageManager();
                Intent query = new Intent(Intent.ACTION_MAIN);
                query.addCategory(Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> found = pm.queryIntentActivities(query, 0);
                Set<String> seen = new HashSet<>();
                ArrayList<ResolveInfo> sorted = new ArrayList<>(found);
                Collections.sort(sorted, (a, b) -> String.CASE_INSENSITIVE_ORDER.compare(
                        String.valueOf(a.loadLabel(pm)), String.valueOf(b.loadLabel(pm))));
                for (ResolveInfo info : sorted) {
                    if (info.activityInfo == null) continue;
                    String packageName = info.activityInfo.packageName;
                    if (packageName == null || packageName.equals(getPackageName()) || !seen.add(packageName)) continue;
                    JSONObject item = new JSONObject();
                    item.put("name", String.valueOf(info.loadLabel(pm)));
                    item.put("packageName", packageName);
                    item.put("icon", iconAsBase64(pm, info));
                    apps.put(item);
                }
            } catch (Exception e) {
                android.util.Log.e("PocketWinDesktop", "Could not enumerate Android apps", e);
            }
            return apps.toString();
        }

        @JavascriptInterface
        public void launchApp(String packageName) {
            activity.runOnUiThread(() -> {
                try {
                    Intent launch = getPackageManager().getLaunchIntentForPackage(packageName);
                    if (launch == null) {
                        Toast.makeText(activity, "No launch activity is available for this app.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(launch);
                } catch (Exception e) {
                    Toast.makeText(activity, "Could not open " + packageName, Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public void openWindowsApps() {
            activity.runOnUiThread(() -> openMainSection(R.id.main_menu_shortcuts));
        }

        @JavascriptInterface
        public void openContainers() {
            activity.runOnUiThread(() -> openMainSection(R.id.main_menu_containers));
        }

        @JavascriptInterface
        public void openFileManager() {
            activity.runOnUiThread(() -> openMainSection(R.id.main_menu_file_manager));
        }

        @JavascriptInterface
        public void openPocketWinSettings() {
            activity.runOnUiThread(() -> openMainSection(R.id.main_menu_settings));
        }

        @JavascriptInterface
        public void openAndroidSettings() {
            activity.runOnUiThread(() -> {
                try {
                    startActivity(new Intent(Settings.ACTION_SETTINGS));
                } catch (Exception e) {
                    Toast.makeText(activity, "Android Settings could not be opened.", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public void showMessage(String message) {
            activity.runOnUiThread(() -> Toast.makeText(activity, message, Toast.LENGTH_SHORT).show());
        }

        @JavascriptInterface
        public String getDeviceLabel() {
            return Build.MANUFACTURER + " " + Build.MODEL;
        }
    }
}
