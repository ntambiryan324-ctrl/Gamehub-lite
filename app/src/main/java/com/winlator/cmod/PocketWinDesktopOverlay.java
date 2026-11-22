package com.winlator.cmod;

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import com.winlator.cmod.container.Container;
import com.winlator.cmod.winhandler.WinHandler;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PocketWinDesktopOverlay extends FrameLayout {
    private static final String GOOGLE_HOME = "https://www.google.com/";
    private static final String MARKET_URL = "https://www.mql5.com/en/market/mt5";
    private static final String CHROME_MSI_URL = "https://dl.google.com/dl/chrome/install/googlechromestandaloneenterprise64.msi";
    private static final String CHROME_MSI_NAME = "googlechromestandaloneenterprise64.msi";
    private final XServerDisplayActivity host;
    private FrameLayout startMenu;
    private BrowserWindow browser;
    private DownloadManager downloads;
    private long chromeDownloadId = -1;
    private BroadcastReceiver chromeReceiver;

    public PocketWinDesktopOverlay(XServerDisplayActivity host) {
        super(host);
        this.host = host;
        setClipChildren(false);
        setClipToPadding(false);
        setClickable(false);
        setFocusable(false);
        buildIcons();
        buildTaskbar();
    }

    private void buildIcons() {
        addDesktopIcon("▣", "This PC", 14, () -> runWine("explorer.exe"));
        addDesktopIcon("◉", "Chrome", 104, this::openChrome);
        addDesktopIcon("M", "MQL5 Market", 194, () -> openBrowser(MARKET_URL));
    }

    private void addDesktopIcon(String glyph, String title, int x, Runnable action) {
        LinearLayout icon = new LinearLayout(host);
        icon.setOrientation(LinearLayout.VERTICAL);
        icon.setGravity(Gravity.CENTER_HORIZONTAL);
        icon.setPadding(dp(4), dp(5), dp(4), dp(5));
        icon.setBackground(shape(0x1EFFFFFF, 11));
        TextView tile = text(glyph, 25, Color.WHITE, true);
        tile.setGravity(Gravity.CENTER);
        GradientDrawable tileBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xC03D7BB9, 0xC0193964});
        tileBg.setCornerRadius(dp(13));
        tileBg.setStroke(dp(1), 0x80D7ECFF);
        tile.setBackground(tileBg);
        icon.addView(tile, new LinearLayout.LayoutParams(dp(48), dp(44)));
        TextView caption = text(title, 10, Color.WHITE, false);
        caption.setGravity(Gravity.CENTER);
        caption.setShadowLayer(3f, 1f, 1f, 0xFF061326);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.topMargin = dp(5);
        icon.addView(caption, cp);
        icon.setOnClickListener(v -> action.run());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(78), dp(84), Gravity.TOP | Gravity.LEFT);
        lp.leftMargin = dp(x);
        lp.topMargin = dp(15);
        addView(icon, lp);
    }

    private void buildTaskbar() {
        LinearLayout bar = new LinearLayout(host);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(9), dp(7), dp(9), dp(7));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xE92B3C53, 0xE9182A40});
        bg.setCornerRadius(dp(19));
        bg.setStroke(dp(1), 0x88D7EAFF);
        bar.setBackground(bg);
        bar.setElevation(dp(16));
        bar.addView(taskButton("⊞", "Start", this::toggleStartMenu), buttonParams());
        bar.addView(taskButton("⌕", "Search", () -> openBrowser(GOOGLE_HOME)), buttonParams());
        bar.addView(taskButton("◉", "Chrome", this::openChrome), buttonParams());
        bar.addView(taskButton("▣", "Explorer", () -> runWine("explorer.exe")), buttonParams());
        bar.addView(taskButton("M", "MQL5 Market", () -> openBrowser(MARKET_URL)), buttonParams());
        TextClock clock = new TextClock(host);
        clock.setFormat12Hour("h:mm a");
        clock.setFormat24Hour("HH:mm");
        clock.setTextColor(0xFFF4F8FF);
        clock.setTextSize(11);
        clock.setGravity(Gravity.CENTER);
        clock.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        bar.addView(clock, new LinearLayout.LayoutParams(dp(59), dp(40)));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, dp(58), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = dp(12);
        addView(bar, lp);
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(42), dp(42));
        lp.leftMargin = dp(2);
        lp.rightMargin = dp(2);
        return lp;
    }

    private TextView taskButton(String glyph, String title, Runnable action) {
        TextView v = text(glyph, 21, Color.WHITE, true);
        v.setGravity(Gravity.CENTER);
        v.setContentDescription(title);
        v.setTooltipText(title);
        v.setBackground(shape(0x22FFFFFF, 12));
        v.setOnClickListener(view -> action.run());
        return v;
    }

    private void toggleStartMenu() {
        if (startMenu != null) {
            removeView(startMenu);
            startMenu = null;
            return;
        }
        startMenu = new FrameLayout(host);
        startMenu.setPadding(dp(18), dp(18), dp(18), dp(18));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xF02A3D57, 0xF014263B});
        bg.setCornerRadius(dp(24));
        bg.setStroke(dp(1), 0x88D2E8FF);
        startMenu.setBackground(bg);
        startMenu.setElevation(dp(24));
        startMenu.setClickable(true);

        LinearLayout content = new LinearLayout(host);
        content.setOrientation(LinearLayout.VERTICAL);
        startMenu.addView(content, new FrameLayout.LayoutParams(-1, -2));
        LinearLayout heading = new LinearLayout(host);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(text("Start", 22, Color.WHITE, true), new LinearLayout.LayoutParams(0, -2, 1));
        heading.addView(text("POCKETWIN", 9, 0xFFBBD5EF, true));
        content.addView(heading);

        EditText search = new EditText(host);
        search.setSingleLine(true);
        search.setTextSize(13);
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(0xFFB5C9DD);
        search.setHint("Search the web or enter a website");
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        search.setPadding(dp(13), 0, dp(13), 0);
        search.setBackground(shape(0xFF30465F, 13));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, dp(44));
        sp.topMargin = dp(14);
        content.addView(search, sp);
        search.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_GO
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                String query = search.getText().toString().trim();
                if (!query.isEmpty()) openSearch(query);
                return true;
            }
            return false;
        });

        TextView pinned = text("Pinned", 13, 0xFFE6F1FF, true);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, -2);
        pp.topMargin = dp(16);
        content.addView(pinned, pp);
        LinearLayout row1 = new LinearLayout(host);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.addView(startTile("◉", "Chrome", this::openChrome), new LinearLayout.LayoutParams(0, dp(76), 1));
        row1.addView(startTile("⌕", "Web search", () -> openBrowser(GOOGLE_HOME)), new LinearLayout.LayoutParams(0, dp(76), 1));
        row1.addView(startTile("M", "MQL5 Market", () -> openBrowser(MARKET_URL)), new LinearLayout.LayoutParams(0, dp(76), 1));
        LinearLayout.LayoutParams r1 = new LinearLayout.LayoutParams(-1, -2);
        r1.topMargin = dp(7);
        content.addView(row1, r1);

        LinearLayout row2 = new LinearLayout(host);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.addView(startTile("▣", "This PC", () -> runWine("explorer.exe")), new LinearLayout.LayoutParams(0, dp(76), 1));
        row2.addView(startTile("↓", "Install Chrome", this::confirmChromeInstall), new LinearLayout.LayoutParams(0, dp(76), 1));
        row2.addView(startTile("⚙", "Wine Control", () -> runWine("control.exe")), new LinearLayout.LayoutParams(0, dp(76), 1));
        LinearLayout.LayoutParams r2 = new LinearLayout.LayoutParams(-1, -2);
        r2.topMargin = dp(5);
        content.addView(row2, r2);

        TextView footer = text("Web search opens a browser window. Windows programs continue running in Wine.",
                10, 0xFFB3C8DF, false);
        footer.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(-1, -2);
        fp.topMargin = dp(13);
        content.addView(footer, fp);

        int width = Math.min(dp(520), getResources().getDisplayMetrics().widthPixels - dp(28));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = dp(82);
        addView(startMenu, lp);
        startMenu.setAlpha(0f);
        startMenu.setScaleX(.96f);
        startMenu.setScaleY(.96f);
        startMenu.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(170).start();
    }

    private View startTile(String glyph, String title, Runnable action) {
        LinearLayout tile = new LinearLayout(host);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setBackground(shape(0x23FFFFFF, 12));
        TextView icon = text(glyph, 23, Color.WHITE, true);
        icon.setGravity(Gravity.CENTER);
        tile.addView(icon, new LinearLayout.LayoutParams(dp(38), dp(38)));
        TextView caption = text(title, 10, 0xFFE4EFFB, false);
        caption.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.topMargin = dp(4);
        tile.addView(caption, cp);
        tile.setOnClickListener(v -> {
            removeView(startMenu);
            startMenu = null;
            action.run();
        });
        return tile;
    }

    private void openChrome() {
        String path = installedChromePath();
        if (path != null) {
            runWine("\"" + path + "\"");
            return;
        }
        new AlertDialog.Builder(host)
                .setTitle("Google Chrome")
                .setMessage("Chrome for Windows is not installed in this container. Try Google's official Windows 64-bit MSI under Wine, or browse now in the built-in web window. Windows Chrome under Wine is experimental on this ARM/Mali device.")
                .setPositiveButton("Try Windows installer", (dialog, which) -> confirmChromeInstall())
                .setNeutralButton("Open web window", (dialog, which) -> openBrowser(GOOGLE_HOME))
                .setNegativeButton("Android Chrome", (dialog, which) -> launchAndroidChrome())
                .show();
    }

    private String installedChromePath() {
        Container c = host.getContainer();
        if (c == null) return null;
        File driveC = new File(c.getRootDir(), ".wine/drive_c");
        String[] paths = {"Program Files/Google/Chrome/Application/chrome.exe",
                "Program Files (x86)/Google/Chrome/Application/chrome.exe"};
        for (String path : paths) {
            File exe = new File(driveC, path);
            if (exe.isFile()) return exe.getAbsolutePath();
        }
        return null;
    }

    private void launchAndroidChrome() {
        try {
            Intent launch = host.getPackageManager().getLaunchIntentForPackage("com.android.chrome");
            if (launch == null) throw new IllegalStateException("Chrome is not installed");
            host.startActivity(launch);
        } catch (Exception e) {
            openBrowser(GOOGLE_HOME);
        }
    }

    private void confirmChromeInstall() {
        new AlertDialog.Builder(host)
                .setTitle("Install Windows Chrome")
                .setMessage("This downloads Google's Windows 64-bit Enterprise MSI into Android Downloads, then starts the installer inside this container. Chrome is designed for x86-64 Windows or Windows 11 on ARM; this Wine/Box64 installation is experimental and may fail.")
                .setPositiveButton("Download installer", (dialog, which) -> downloadChromeInstaller())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void downloadChromeInstaller() {
        if (chromeDownloadId != -1L) {
            Toast.makeText(host, "Chrome installer download is already running.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            downloads = (DownloadManager) host.getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(CHROME_MSI_URL));
            request.setTitle("Google Chrome for Windows");
            request.setDescription("Downloading the official 64-bit Chrome installer");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(false);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, CHROME_MSI_NAME);
            chromeDownloadId = downloads.enqueue(request);
            registerChromeReceiver();
            Toast.makeText(host, "Chrome installer download started.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            chromeDownloadId = -1L;
            Toast.makeText(host, "Unable to start Chrome download: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void registerChromeReceiver() {
        if (chromeReceiver != null) return;
        chromeReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
                if (id != chromeDownloadId || downloads == null) return;
                Cursor cursor = downloads.query(new DownloadManager.Query().setFilterById(id));
                if (cursor == null) return;
                try {
                    if (!cursor.moveToFirst()) return;
                    int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        chromeDownloadId = -1L;
                        Toast.makeText(host, "Download complete. Starting the installer in Wine.", Toast.LENGTH_LONG).show();
                        WinHandler handler = host.getWinHandler();
                        if (handler != null) handler.exec("msiexec.exe /i D:\\" + CHROME_MSI_NAME);
                    } else if (status == DownloadManager.STATUS_FAILED) {
                        chromeDownloadId = -1L;
                        Toast.makeText(host, "Chrome download failed. Check the connection and retry.", Toast.LENGTH_LONG).show();
                    }
                } finally {
                    cursor.close();
                }
            }
        };
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= 33) host.registerReceiver(chromeReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else host.registerReceiver(chromeReceiver, filter);
    }

    private void runWine(String command) {
        WinHandler handler = host.getWinHandler();
        if (handler != null) handler.exec(command);
    }

    private void openSearch(String raw) {
        String query = raw.trim();
        if (query.isEmpty()) return;
        String lower = query.toLowerCase(Locale.ROOT);
        if (lower.startsWith("https://") || lower.startsWith("http://")) openBrowser(query);
        else if (!query.contains(" ") && query.matches("^[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(/.*)?$")) openBrowser("https://" + query);
        else openBrowser("https://www.google.com/search?q=" + Uri.encode(query));
    }

    private void openBrowser(String url) {
        if (startMenu != null) {
            removeView(startMenu);
            startMenu = null;
        }
        if (browser == null) {
            browser = new BrowserWindow(host);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER);
            lp.setMargins(dp(12), dp(12), dp(12), dp(78));
            addView(browser, lp);
        }
        browser.loadUrl(url);
    }

    public boolean handleBack() {
        if (startMenu != null) {
            removeView(startMenu);
            startMenu = null;
            return true;
        }
        if (browser != null) {
            if (browser.current != null && browser.current.canGoBack()) browser.current.goBack();
            else browser.closeBrowser();
            return true;
        }
        return false;
    }

    public void destroy() {
        if (browser != null) {
            browser.destroy();
            browser = null;
        }
        if (chromeReceiver != null) {
            try { host.unregisterReceiver(chromeReceiver); } catch (Exception ignored) {}
            chromeReceiver = null;
        }
    }

    private final class BrowserWindow extends LinearLayout {
        private final FrameLayout pagesHost;
        private final EditText address;
        private final ProgressBar progress;
        private final List<WebView> pages = new ArrayList<>();
        private WebView current;

        BrowserWindow(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setPadding(dp(1), dp(1), dp(1), dp(1));
            GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{0xFF182B41, 0xFF09182A});
            bg.setCornerRadius(dp(19));
            bg.setStroke(dp(1), 0xA9C0D9F0);
            setBackground(bg);
            setElevation(dp(20));
            setClipToOutline(true);
            setClickable(true);

            LinearLayout toolbar = new LinearLayout(context);
            toolbar.setOrientation(HORIZONTAL);
            toolbar.setGravity(Gravity.CENTER_VERTICAL);
            toolbar.setPadding(dp(8), dp(7), dp(8), dp(7));
            toolbar.setBackgroundColor(0xFF20354D);
            addView(toolbar, new LinearLayout.LayoutParams(-1, dp(54)));
            toolbar.addView(tool("‹", "Back", () -> { if (current != null && current.canGoBack()) current.goBack(); }), buttonParams(34));
            toolbar.addView(tool("›", "Forward", () -> { if (current != null && current.canGoForward()) current.goForward(); }), buttonParams(34));
            toolbar.addView(tool("↻", "Reload", () -> { if (current != null) current.reload(); }), buttonParams(34));
            toolbar.addView(tool("⌂", "Home", () -> loadUrl(GOOGLE_HOME)), buttonParams(34));

            address = new EditText(context);
            address.setSingleLine(true);
            address.setTextSize(12);
            address.setTextColor(Color.WHITE);
            address.setHintTextColor(0xFFB3C6D9);
            address.setHint("Search Google or enter a website");
            address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            address.setImeOptions(EditorInfo.IME_ACTION_GO);
            address.setPadding(dp(12), 0, dp(12), 0);
            address.setBackground(shape(0xFF30465E, 11));
            toolbar.addView(address, new LinearLayout.LayoutParams(0, dp(38), 1));
            address.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_SEARCH
                        || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    loadUrl(normalizeAddress(address.getText().toString()));
                    return true;
                }
                return false;
            });
            toolbar.addView(tool("→", "Go", () -> loadUrl(normalizeAddress(address.getText().toString()))), buttonParams(34));
            toolbar.addView(tool("×", "Close", this::closeBrowser), buttonParams(36));

            progress = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
            progress.setMax(100);
            progress.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFF59A9FF));
            addView(progress, new LinearLayout.LayoutParams(-1, dp(2)));
            pagesHost = new FrameLayout(context);
            pagesHost.setBackgroundColor(Color.WHITE);
            addView(pagesHost, new LinearLayout.LayoutParams(-1, 0, 1));
            createPage(true);
        }

        private LinearLayout.LayoutParams buttonParams(int width) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(width), dp(38));
            lp.rightMargin = dp(3);
            return lp;
        }

        private TextView tool(String glyph, String description, Runnable action) {
            TextView v = text(glyph, 20, Color.WHITE, true);
            v.setGravity(Gravity.CENTER);
            v.setContentDescription(description);
            v.setTooltipText(description);
            v.setBackground(shape(0x20FFFFFF, 9));
            v.setOnClickListener(view -> action.run());
            return v;
        }

        private WebView createPage(boolean activate) {
            WebView web = new WebView(host);
            WebSettings s = web.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setDatabaseEnabled(true);
            s.setJavaScriptCanOpenWindowsAutomatically(true);
            s.setSupportMultipleWindows(true);
            s.setLoadsImagesAutomatically(true);
            s.setLoadWithOverviewMode(true);
            s.setUseWideViewPort(true);
            s.setMediaPlaybackRequiresUserGesture(true);
            s.setBuiltInZoomControls(true);
            s.setDisplayZoomControls(false);
            s.setAllowFileAccess(false);
            s.setAllowContentAccess(false);
            web.setBackgroundColor(Color.WHITE);
            web.setOverScrollMode(View.OVER_SCROLL_NEVER);
            CookieManager.getInstance().setAcceptCookie(true);
            CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
            web.setWebViewClient(new WebViewClient() {
                @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    Uri uri = request.getUrl();
                    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
                    if (scheme.equals("https") || scheme.equals("http")) return false;
                    try { host.startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (Exception ignored) {}
                    return true;
                }
                @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                    activatePage(view);
                    address.setText(url);
                    progress.setVisibility(VISIBLE);
                    progress.setProgress(5);
                }
                @Override public void onPageFinished(WebView view, String url) {
                    if (view == current) address.setText(url);
                }
            });
            web.setWebChromeClient(new WebChromeClient() {
                @Override public void onProgressChanged(WebView view, int value) {
                    if (view == current) {
                        progress.setProgress(value);
                        progress.setVisibility(value >= 100 ? GONE : VISIBLE);
                    }
                }
                @Override public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
                    WebView popup = createPage(false);
                    WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                    transport.setWebView(popup);
                    resultMsg.sendToTarget();
                    return true;
                }
            });
            web.setDownloadListener((url, userAgent, disposition, mimeType, length) ->
                    downloadWebFile(url, userAgent, disposition, mimeType));
            pagesHost.addView(web, new FrameLayout.LayoutParams(-1, -1));
            pages.add(web);
            if (activate) activatePage(web);
            else web.setVisibility(GONE);
            return web;
        }

        private void activatePage(WebView web) {
            if (current == web) return;
            current = web;
            for (WebView page : pages) page.setVisibility(page == web ? VISIBLE : GONE);
            address.setText(web.getUrl() == null ? "" : web.getUrl());
        }

        private void loadUrl(String url) {
            WebView web = current == null ? createPage(true) : current;
            web.loadUrl(url);
            address.setText(url);
        }

        private String normalizeAddress(String raw) {
            String value = raw == null ? "" : raw.trim();
            if (value.isEmpty()) return GOOGLE_HOME;
            String lower = value.toLowerCase(Locale.ROOT);
            if (lower.startsWith("https://") || lower.startsWith("http://")) return value;
            if (!value.contains(" ") && value.matches("^[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(/.*)?$")) return "https://" + value;
            return "https://www.google.com/search?q=" + Uri.encode(value);
        }

        private void downloadWebFile(String url, String userAgent, String disposition, String mimeType) {
            try {
                DownloadManager manager = (DownloadManager) host.getSystemService(Context.DOWNLOAD_SERVICE);
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setTitle(URLUtil.guessFileName(url, disposition, mimeType));
                request.setDescription("PocketWin browser download");
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                if (userAgent != null) request.addRequestHeader("User-Agent", userAgent);
                String cookie = CookieManager.getInstance().getCookie(url);
                if (cookie != null) request.addRequestHeader("Cookie", cookie);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, disposition, mimeType));
                manager.enqueue(request);
                Toast.makeText(host, "Download started. Wine can access the file through drive D:.", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(host, "Download failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }

        private void closeBrowser() {
            PocketWinDesktopOverlay.this.removeView(this);
            if (browser == this) browser = null;
            destroy();
        }

        void destroy() {
            for (WebView web : pages) {
                try {
                    web.stopLoading();
                    web.loadUrl("about:blank");
                    web.removeAllViews();
                    web.destroy();
                } catch (Exception ignored) {}
            }
            pages.clear();
            current = null;
        }
    }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(radius));
        bg.setStroke(dp(1), 0x35FFFFFF);
        return bg;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(host);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        view.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return view;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
