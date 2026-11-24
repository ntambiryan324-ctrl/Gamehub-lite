package com.winlator.cmod;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.net.URLEncoder;
import java.util.Locale;

public class PocketWinBrowserActivity extends Activity {
    private WebView browser;
    private EditText addressBar;
    private int windowWidth;
    private int windowHeight;
    private int startWindowX;
    private int startWindowY;
    private float downRawX;
    private float downRawY;
    private int startTouchWidth;
    private int startTouchHeight;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window window = getWindow();
        window.setBackgroundDrawableResource(android.R.color.transparent);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams params = window.getAttributes();
        params.dimAmount = 0.10f;
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = (screenWidth - windowWidth) / 2;
        params.y = (screenHeight - windowHeight) / 2;
        window.setAttributes(params);

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        windowWidth = Math.min(screenWidth - dp(24), Math.max(dp(320), (int)(screenWidth * 0.88f)));
        windowHeight = Math.min(screenHeight - dp(50), Math.max(dp(300), (int)(screenHeight * 0.82f)));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(rounded(0xFFF7F9FC, 0xFFC9D4E2, dp(12)));
        root.setElevation(dp(16));
        root.setClipToOutline(true);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(9), dp(6), dp(8), dp(6));
        toolbar.setBackgroundColor(0xFFEAF0F7);

        TextView logo = new TextView(this);
        logo.setText("◉");
        logo.setTextColor(0xFF2689CE);
        logo.setTextSize(21);
        logo.setGravity(Gravity.CENTER);
        toolbar.addView(logo, new LinearLayout.LayoutParams(dp(28), dp(38)));

        TextView title = new TextView(this);
        title.setText("Chrome");
        title.setTextColor(0xFF26374C);
        title.setTextSize(12);
        title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(dp(58), dp(38));
        toolbar.addView(title, titleParams);
        title.setOnTouchListener(this::moveWindowFromTouch);

        TextView back = toolbarButton("‹");
        back.setOnClickListener(v -> { if (browser != null && browser.canGoBack()) browser.goBack(); });
        toolbar.addView(back, new LinearLayout.LayoutParams(dp(30), dp(34)));

        TextView forward = toolbarButton("›");
        forward.setOnClickListener(v -> { if (browser != null && browser.canGoForward()) browser.goForward(); });
        toolbar.addView(forward, new LinearLayout.LayoutParams(dp(30), dp(34)));

        TextView reload = toolbarButton("⟳");
        reload.setOnClickListener(v -> { if (browser != null) browser.reload(); });
        toolbar.addView(reload, new LinearLayout.LayoutParams(dp(30), dp(34)));

        addressBar = new EditText(this);
        addressBar.setSingleLine(true);
        addressBar.setTextSize(12);
        addressBar.setTextColor(0xFF26374C);
        addressBar.setHintTextColor(0xFF8492A3);
        addressBar.setHint("Search or enter address");
        addressBar.setPadding(dp(12), 0, dp(12), 0);
        addressBar.setBackground(rounded(0xFFFFFFFF, 0xFFD2DBE6, dp(18)));
        addressBar.setImeOptions(EditorInfo.IME_ACTION_GO);
        addressBar.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        LinearLayout.LayoutParams addressParams = new LinearLayout.LayoutParams(0, dp(34), 1);
        addressParams.leftMargin = dp(5);
        addressParams.rightMargin = dp(5);
        toolbar.addView(addressBar, addressParams);

        TextView go = toolbarButton("↵");
        go.setOnClickListener(v -> navigate(addressBar.getText().toString()));
        toolbar.addView(go, new LinearLayout.LayoutParams(dp(31), dp(34)));

        TextView close = toolbarButton("×");
        close.setTextColor(0xFF8B3A3A);
        close.setOnClickListener(v -> finish());
        toolbar.addView(close, new LinearLayout.LayoutParams(dp(34), dp(34)));

        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(52)));

        FrameLayout browserArea = new FrameLayout(this);
        browser = new WebView(this);
        browser.setBackgroundColor(Color.WHITE);
        WebSettings settings = browser.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) settings.setSafeBrowsingEnabled(true);
        String userAgent = WebSettings.getDefaultUserAgent(this);
        userAgent = userAgent.replaceAll("\\\\(Linux; Android[^)]*\\\\)", "(X11; Linux x86_64)");
        userAgent = userAgent.replace("Mobile ", "");
        settings.setUserAgentString(userAgent);
        browser.setWebChromeClient(new WebChromeClient());
        browser.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String scheme = request.getUrl().getScheme() == null ? "" : request.getUrl().getScheme().toLowerCase(Locale.ROOT);
                if ("http".equals(scheme) || "https".equals(scheme)) return false;
                Toast.makeText(PocketWinBrowserActivity.this, "This link type is not supported in the browser window.", Toast.LENGTH_SHORT).show();
                return true;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return !(url != null && (url.startsWith("https://") || url.startsWith("http://")));
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                if (addressBar != null) addressBar.setText(url);
            }
        });
        browserArea.addView(browser, new FrameLayout.LayoutParams(-1, -1));
        TextView resize = new TextView(this);
        resize.setText("◢");
        resize.setTextSize(16);
        resize.setTextColor(0xFF708299);
        resize.setGravity(Gravity.CENTER);
        resize.setBackgroundColor(0x00FFFFFF);
        FrameLayout.LayoutParams resizeParams = new FrameLayout.LayoutParams(dp(28), dp(28), Gravity.BOTTOM | Gravity.RIGHT);
        browserArea.addView(resize, resizeParams);
        resize.setOnTouchListener(this::resizeWindowFromTouch);
        root.addView(browserArea, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView status = new TextView(this);
        status.setText("Chromium · Desktop site mode");
        status.setTextColor(0xFF76869A);
        status.setTextSize(10);
        status.setPadding(dp(12), 0, dp(12), 0);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setBackgroundColor(0xFFF0F3F7);
        root.addView(status, new LinearLayout.LayoutParams(-1, dp(23)));

        setContentView(root);
        addressBar.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER)) {
                navigate(addressBar.getText().toString());
                return true;
            }
            return false;
        });

        WindowManager.LayoutParams finalParams = getWindow().getAttributes();
        finalParams.width = windowWidth;
        finalParams.height = windowHeight;
        finalParams.gravity = Gravity.CENTER;
        getWindow().setAttributes(finalParams);

        navigate(getIntent().getStringExtra("url"));
    }

    private boolean moveWindowFromTouch(View view, MotionEvent event) {
        WindowManager.LayoutParams params = getWindow().getAttributes();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                startWindowX = params.x;
                startWindowY = params.y;
                return true;
            case MotionEvent.ACTION_MOVE:
                params.gravity = Gravity.TOP | Gravity.LEFT;
                params.x = startWindowX + (int)(event.getRawX() - downRawX);
                params.y = startWindowY + (int)(event.getRawY() - downRawY);
                getWindow().setAttributes(params);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                return true;
        }
        return false;
    }

    private boolean resizeWindowFromTouch(View view, MotionEvent event) {
        WindowManager.LayoutParams params = getWindow().getAttributes();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                startTouchWidth = params.width;
                startTouchHeight = params.height;
                return true;
            case MotionEvent.ACTION_MOVE:
                int maxWidth = getResources().getDisplayMetrics().widthPixels - dp(16);
                int maxHeight = getResources().getDisplayMetrics().heightPixels - dp(32);
                params.width = Math.max(dp(320), Math.min(maxWidth, startTouchWidth + (int)(event.getRawX() - downRawX)));
                params.height = Math.max(dp(300), Math.min(maxHeight, startTouchHeight + (int)(event.getRawY() - downRawY)));
                windowWidth = params.width;
                windowHeight = params.height;
                getWindow().setAttributes(params);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                return true;
        }
        return false;
    }

    private void navigate(String raw) {
        if (browser == null) return;
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) value = "https://www.google.com/";
        else if (!(value.startsWith("https://") || value.startsWith("http://"))) {
            if (value.contains(" ") || !value.contains(".")) {
                try {
                    value = "https://www.google.com/search?q=" + URLEncoder.encode(value, "UTF-8");
                } catch (Exception ignored) {
                    value = "https://www.google.com/";
                }
            } else value = "https://" + value;
        }
        addressBar.setText(value);
        browser.loadUrl(value);
    }

    @Override
    public void onBackPressed() {
        if (browser != null && browser.canGoBack()) browser.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (browser != null) {
            browser.stopLoading();
            browser.destroy();
            browser = null;
        }
        super.onDestroy();
    }

    private TextView toolbarButton(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(19);
        view.setTextColor(0xFF40536B);
        view.setGravity(Gravity.CENTER);
        view.setBackground(rounded(0x00FFFFFF, 0x00000000, dp(6)));
        return view;
    }

    private GradientDrawable rounded(int fill, int stroke, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(radius);
        if (stroke != 0) d.setStroke(dp(1), stroke);
        return d;
    }

    private int dp(float value) {
        return (int)(value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
