package com.winlator.cmod;

import android.app.ActivityManager;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Html;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * GameHub Lite discovery surface.
 *
 * Steam storefront metadata is public. Full store indexing and owned-library lookup use the user's
 * own Steam Web API key, encrypted locally with Android Keystore. This screen never asks for a
 * Steam password and does not distribute or embed a shared API key.
 */
public class GameLibraryFragment extends Fragment {
    private static final int BG = Color.rgb(12, 15, 22);
    private static final int SURFACE = Color.rgb(35, 40, 51);
    private static final int TEXT = Color.rgb(246, 248, 252);
    private static final int MUTED = Color.rgb(174, 184, 198);
    private static final int ACCENT = Color.rgb(47, 155, 238);
    private static final int GLASS = 0x88434A59;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<SteamStoreClient.Game> featured = new ArrayList<>();
    private final List<SteamStoreClient.Game> ownedGames = new ArrayList<>();
    private final Set<Integer> ownedIds = new HashSet<>();
    private LinearLayout pageContent;
    private TextView accountStatus;
    private EditText searchField;
    private String currentTab = "Recommended";
    private String currentQuery = "";
    private boolean loadingFeatured = false;
    private boolean searchRunning = false;

    private final Runnable searchRunnable = () -> runSearch(currentQuery);

    @Nullable
    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        ScrollView scroll = new ScrollView(requireContext());
        scroll.setFillViewport(true);
        scroll.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(20, 25, 37), Color.rgb(15, 20, 30), Color.rgb(27, 23, 34)}));

        pageContent = new LinearLayout(requireContext());
        pageContent.setOrientation(LinearLayout.VERTICAL);
        pageContent.setPadding(dp(18), dp(12), dp(18), dp(30));
        scroll.addView(pageContent);

        buildTopBar();
        buildTabs();
        buildSearch();
        loadFeatured();
        if (getActivity() instanceof AppCompatActivity) {
            ((AppCompatActivity)getActivity()).getSupportActionBar().setTitle("Game Library");
        }
        return scroll;
    }

    @Override
    public void onDestroyView() {
        handler.removeCallbacks(searchRunnable);
        pageContent = null;
        searchField = null;
        accountStatus = null;
        super.onDestroyView();
    }

    private void buildTopBar() {
        LinearLayout bar = new LinearLayout(requireContext());
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout titleBlock = new LinearLayout(requireContext());
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        TextView title = text("LIBRARY", 23, TEXT, true);
        TextView subtitle = text("Discover PC games for GameHub Lite", 11, MUTED, false);
        titleBlock.addView(title);
        titleBlock.addView(subtitle);
        bar.addView(titleBlock, new LinearLayout.LayoutParams(0, -2, 1));

        Button steam = glassButton(SteamAccountStore.isConnected(requireContext()) ? "Steam linked" : "Connect Steam", true);
        steam.setOnClickListener(v -> showSteamAccountDialog());
        bar.addView(steam, new LinearLayout.LayoutParams(-2, dp(42)));
        pageContent.addView(bar);

        accountStatus = text(SteamAccountStore.isConnected(requireContext())
                ? "Steam account ready to sync" : "Browse the Steam Store, or connect your account to show your games.",
                11, MUTED, false);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2);
        statusLp.topMargin = dp(8);
        pageContent.addView(accountStatus, statusLp);
    }

    private void buildTabs() {
        HorizontalScrollView hsv = new HorizontalScrollView(requireContext());
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(requireContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(15), 0, dp(12));
        String[] tabs = {"Recommended", "PC Games", "Steam", "My Games", "Xbox Cloud"};
        for (String tab : tabs) {
            TextView chip = text(tab, 13, tab.equals(currentTab) ? TEXT : MUTED, tab.equals(currentTab));
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(15), dp(10), dp(15), dp(10));
            chip.setBackground(glassBackground(tab.equals(currentTab) ? 0xCC177BC0 : 0x333B4351, 18));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(40));
            lp.rightMargin = dp(8);
            row.addView(chip, lp);
            chip.setOnClickListener(v -> {
                currentTab = tab;
                updateTabStyles(row);
                currentQuery = "";
                if (searchField != null) searchField.setText("");
                showCurrentTab();
            });
        }
        hsv.addView(row);
        pageContent.addView(hsv);
    }

    private void updateTabStyles(LinearLayout row) {
        String[] tabs = {"Recommended", "PC Games", "Steam", "My Games", "Xbox Cloud"};
        for (int i = 0; i < row.getChildCount(); i++) {
            TextView chip = (TextView) row.getChildAt(i);
            boolean selected = tabs[i].equals(currentTab);
            chip.setTextColor(selected ? TEXT : MUTED);
            chip.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
            chip.setBackground(glassBackground(selected ? 0xCC177BC0 : 0x333B4351, 18));
        }
    }

    private void buildSearch() {
        LinearLayout shell = new LinearLayout(requireContext());
        shell.setGravity(Gravity.CENTER_VERTICAL);
        shell.setPadding(dp(12), 0, dp(12), 0);
        shell.setBackground(glassBackground(0x77464F60, 13));
        TextView icon = text("⌕", 24, TEXT, false);
        shell.addView(icon, new LinearLayout.LayoutParams(dp(32), dp(48)));
        searchField = new EditText(requireContext());
        searchField.setSingleLine(true);
        searchField.setTextSize(14);
        searchField.setTextColor(TEXT);
        searchField.setHintTextColor(MUTED);
        searchField.setHint("Search Steam games...");
        searchField.setInputType(InputType.TYPE_CLASS_TEXT);
        searchField.setBackgroundColor(Color.TRANSPARENT);
        shell.addView(searchField, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button clear = glassButton("Search", false);
        shell.addView(clear, new LinearLayout.LayoutParams(-2, dp(36)));
        clear.setOnClickListener(v -> runSearch(searchField.getText().toString().trim()));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(50));
        lp.bottomMargin = dp(14);
        pageContent.addView(shell, lp);

        searchField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentQuery = s.toString().trim();
                handler.removeCallbacks(searchRunnable);
                if (currentQuery.length() >= 2) handler.postDelayed(searchRunnable, 550);
                else if (currentQuery.isEmpty()) showCurrentTab();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void loadFeatured() {
        if (loadingFeatured || !isAdded()) return;
        loadingFeatured = true;
        showLoading("Loading Steam Store highlights...");
        SteamStoreClient.fetchFeatured((games, error) -> {
            loadingFeatured = false;
            if (!isAdded() || pageContent == null) return;
            if (games != null) {
                featured.clear();
                featured.addAll(games);
            }
            showCurrentTab(error);
        });
    }

    private void showCurrentTab() {
        showCurrentTab(null);
    }

    private void showCurrentTab(String error) {
        if (!isAdded() || pageContent == null) return;
        removeDynamicContent();
        if (error != null && featured.isEmpty()) {
            addMessage("Steam Store is unavailable", error, "Retry", this::loadFeatured);
            return;
        }

        if (!currentQuery.isEmpty() && currentQuery.length() >= 2) {
            runSearch(currentQuery);
            return;
        }

        switch (currentTab) {
            case "My Games":
                showOwnedLibrary();
                return;
            case "Xbox Cloud":
                showXboxSection();
                return;
            case "PC Games":
                showPcCatalog();
                return;
            case "Steam":
                showSteamStore();
                return;
            default:
                showRecommended();
        }
    }

    private void showRecommended() {
        if (featured.isEmpty()) {
            addMessage("No catalogue data yet", "Check your connection and refresh the Steam Store catalogue.", "Refresh", this::loadFeatured);
            return;
        }
        addHeroCard(featured.get(0));
        addShelf("Steam Store highlights", featured.subList(0, Math.min(10, featured.size())));
        if (featured.size() > 10) addShelf("More to discover", featured.subList(10, Math.min(22, featured.size())));
        if (featured.size() > 22) addShelf("More Steam games", featured.subList(22, featured.size()));
        addNewsInfo();
    }

    private void showSteamStore() {
        addSectionTitle("Steam Games", "Live featured games from the Steam Store");
        if (featured.isEmpty()) {
            addMessage("Loading Steam games", "Retrieving current storefront categories...", null, null);
            loadFeatured();
        } else {
            addShelf("Top Steam discoveries", featured.subList(0, Math.min(12, featured.size())));
            if (featured.size() > 12) addShelf("Continue exploring", featured.subList(12, Math.min(30, featured.size())));
        }
        addMessage("Search the Steam Store", "Use the search bar above to look up any specific Steam title. Connect a Steam Web API key to index the complete public PC game catalogue.", "Connect Steam", this::showSteamAccountDialog);
    }

    private void showPcCatalog() {
        int count = SteamStoreClient.catalogCount(requireContext());
        addSectionTitle("PC Games", count > 0 ? String.format(Locale.US, "%,d Steam catalogue entries indexed", count) : "Browse PC games from Steam");
        if (count == 0) {
            addGlassInfo("Full Steam catalogue", "The complete Steam PC game list can be indexed using your own Steam Web API key. The key stays encrypted on this device.", "Connect & index catalogue", this::showSteamAccountDialog);
        } else {
            addGlassInfo("Full catalogue ready", "Search above to find games across the indexed Steam catalogue. App details, images, trailers and store links are retrieved when you open a game.", "Refresh catalogue", this::showSteamAccountDialog);
        }
        if (!featured.isEmpty()) addShelf("Popular Windows games", featured.subList(0, Math.min(20, featured.size())));
    }

    private void showOwnedLibrary() {
        addSectionTitle("My Steam Games", SteamAccountStore.isConnected(requireContext())
                ? "Games visible in your linked Steam account" : "Connect a Steam account to sync your library");
        if (!SteamAccountStore.isConnected(requireContext())) {
            addGlassInfo("Connect Steam", "Use Steam's own Web API key and your 64-bit Steam ID. GameHub Lite never asks for your Steam password. Your Steam Game Details privacy must be Public for this API to return the owned games list.", "Connect Steam account", this::showSteamAccountDialog);
            return;
        }
        if (ownedGames.isEmpty()) {
            showLoading("Syncing your Steam library...");
            syncOwnedGames();
        } else {
            addShelf("Your owned PC games", ownedGames);
            addGlassInfo("Library sync", "The list is read from Steam's official GetOwnedGames API. Game files are not downloaded by this catalogue screen.", "Sync again", this::syncOwnedGames);
        }
    }

    private void showXboxSection() {
        addSectionTitle("Xbox Cloud Gaming", "Cloud-stream Xbox titles supported in your region and subscription");
        addLargeExternalCard("Xbox Cloud Gaming", "Browse the cloud-playable Xbox catalogue. These games stream from Microsoft's servers; they are not Xbox-console emulation or local Winlator installs.",
                "https://www.xbox.com/play", "Open Xbox Cloud Gaming");
        addLargeExternalCard("Xbox PC Game Pass", "Browse the official PC Game Pass catalogue. Native PC Game Pass installation is separate from Winlator and depends on the supported Windows client.",
                "https://www.xbox.com/xbox-game-pass/pc-game-pass", "Browse PC Game Pass");
    }

    private void runSearch(String query) {
        if (!isAdded() || pageContent == null || query == null || query.trim().length() < 2 || searchRunning) return;
        searchRunning = true;
        removeDynamicContent();
        addSectionTitle("Search results", "Searching Steam Store for \"" + query + "\"");
        showLoading("Searching...");
        int catalogCount = SteamStoreClient.catalogCount(requireContext());
        if (catalogCount > 0) {
            List<SteamStoreClient.Game> local = SteamStoreClient.searchLocalCatalog(requireContext(), query, 50);
            if (!local.isEmpty()) {
                searchRunning = false;
                removeDynamicContent();
                addSectionTitle("Search results", local.size() + " matches in your indexed Steam catalogue");
                addGrid(local);
                return;
            }
        }
        SteamStoreClient.search(query, (games, error) -> {
            searchRunning = false;
            if (!isAdded() || pageContent == null || !query.equals(currentQuery)) return;
            removeDynamicContent();
            addSectionTitle("Search results", games == null ? "Steam search could not be completed" : games.size() + " results from the Steam Store");
            if (games == null || games.isEmpty()) {
                addMessage("No results", error != null ? error : "Try a different game title.", null, null);
            } else addGrid(games);
        });
    }

    private void syncOwnedGames() {
        if (!SteamAccountStore.isConnected(requireContext())) {
            showSteamAccountDialog();
            return;
        }
        try {
            String key = SteamAccountStore.getApiKey(requireContext());
            String id = SteamAccountStore.getSteamId(requireContext());
            SteamStoreClient.fetchOwnedGames(id, key, (games, error) -> {
                if (!isAdded()) return;
                if (games == null) {
                    Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
                    if (pageContent != null) showOwnedLibraryError(error);
                    return;
                }
                ownedGames.clear();
                ownedGames.addAll(games);
                ownedIds.clear();
                for (SteamStoreClient.Game game : games) ownedIds.add(game.appId);
                if (pageContent != null && "My Games".equals(currentTab)) { removeDynamicContent(); showOwnedLibrary(); }
                Toast.makeText(requireContext(), "Synced " + games.size() + " Steam games.", Toast.LENGTH_SHORT).show();
            });
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Could not decrypt the saved Steam key. Reconnect the account.", Toast.LENGTH_LONG).show();
        }
    }

    private void showOwnedLibraryError(String error) {
        removeDynamicContent();
        addSectionTitle("My Steam Games", "The Steam library could not be loaded");
        addMessage("Steam did not return your games", error, "Account settings", this::showSteamAccountDialog);
    }

    private void showSteamAccountDialog() {
        if (!isAdded()) return;
        Dialog dialog = new Dialog(requireContext());
        LinearLayout root = dialogRoot();
        TextView title = text("Connect Steam", 22, TEXT, true);
        root.addView(title);
        TextView description = text("GameHub Lite uses your own Steam Web API key to read your public game list and index the Steam catalogue. Never enter your Steam password here.", 13, MUTED, false);
        addTop(root, description, 8);

        Button createKey = glassButton("Get a Steam Web API key", true);
        root.addView(createKey, new LinearLayout.LayoutParams(-1, dp(42)));
        createKey.setOnClickListener(v -> openUrl("https://steamcommunity.com/dev/apikey"));

        EditText steamId = field("SteamID64 (17 digits)");
        steamId.setInputType(InputType.TYPE_CLASS_NUMBER);
        steamId.setText(SteamAccountStore.getSteamId(requireContext()));
        addTop(root, steamId, 12);

        EditText apiKey = field("Your Steam Web API key");
        apiKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        addTop(root, apiKey, 8);

        TextView privacy = text("Set Steam Profile > Game Details to Public before syncing. The API key is encrypted with Android Keystore and is not included in the APK.", 11, MUTED, false);
        addTop(root, privacy, 8);

        LinearLayout buttons = new LinearLayout(requireContext());
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        Button save = glassButton("Save & sync games", true);
        Button index = glassButton("Index full catalogue", false);
        LinearLayout.LayoutParams b1 = new LinearLayout.LayoutParams(0, dp(44), 1);
        b1.rightMargin = dp(6);
        buttons.addView(save, b1);
        buttons.addView(index, new LinearLayout.LayoutParams(0, dp(44), 1));
        addTop(root, buttons, 14);

        TextView status = text("", 12, MUTED, false);
        addTop(root, status, 8);

        if (SteamAccountStore.isConnected(requireContext())) {
            Button disconnect = glassButton("Disconnect Steam", false);
            addTop(root, disconnect, 8);
            disconnect.setOnClickListener(v -> {
                SteamAccountStore.clear(requireContext());
                ownedGames.clear();
                ownedIds.clear();
                accountStatus.setText("Steam account disconnected");
                dialog.dismiss();
                showCurrentTab();
            });
        }

        save.setOnClickListener(v -> {
            try {
                SteamAccountStore.save(requireContext(), steamId.getText().toString(), apiKey.getText().toString());
                status.setText("Steam API key saved securely. Syncing your games...");
                accountStatus.setText("Steam account connected - syncing library");
                syncOwnedGames();
            } catch (Exception e) {
                status.setText(e.getMessage());
            }
        });

        index.setOnClickListener(v -> {
            try {
                String key = apiKey.getText().toString().trim();
                if (key.isEmpty()) key = SteamAccountStore.getApiKey(requireContext());
                if (key.isEmpty()) {
                    status.setText("Enter your Steam Web API key first.");
                    return;
                }
                String savedKey = key;
                status.setText("Indexing Steam's public PC game catalogue...");
                SteamStoreClient.syncFullCatalog(requireContext(), savedKey, (count, complete, error) -> {
                    status.setText(error != null ? "Catalogue sync stopped: " + error
                            : complete ? String.format(Locale.US, "Catalogue index complete: %,d apps.", count)
                            : String.format(Locale.US, "Indexed %,d apps...", count));
                });
            } catch (Exception e) {
                status.setText("Could not read the saved Steam key. Re-enter it.");
            }
        });

        showDialog(dialog, root, true);
    }

    private void addHeroCard(SteamStoreClient.Game game) {
        FrameLayout card = new FrameLayout(requireContext());
        card.setBackground(glassBackground(0xAA171E2B, 20));
        card.setClipToOutline(true);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, dp(230));
        cardLp.bottomMargin = dp(18);
        pageContent.addView(card, cardLp);

        ImageView image = new ImageView(requireContext());
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        card.addView(image, new FrameLayout.LayoutParams(-1, -1));
        loadImage(image, game.headerImage);
        if (Build.VERSION.SDK_INT >= 31) image.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(8f, 8f, android.graphics.Shader.TileMode.CLAMP));
        View shade = new View(requireContext());
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x55202738, 0xAA10141D, 0xEE10141D}));
        card.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout content = new LinearLayout(requireContext());
        content.setPadding(dp(20), dp(18), dp(20), dp(18));
        content.setGravity(Gravity.BOTTOM);
        content.setOrientation(LinearLayout.VERTICAL);
        card.addView(content, new FrameLayout.LayoutParams(-1, -1));
        TextView overline = text("FEATURED ON STEAM", 10, 0xFFD8E8FF, true);
        content.addView(overline);
        TextView name = text(game.name, 26, Color.WHITE, true);
        addTop(content, name, 6);
        TextView meta = text(game.price.isEmpty() ? "Discover this PC game" : game.price, 13, 0xFFE4EAF3, false);
        addTop(content, meta, 4);
        Button view = glassButton("View Details", true);
        LinearLayout.LayoutParams buttonLp = new LinearLayout.LayoutParams(dp(160), dp(42));
        buttonLp.topMargin = dp(12);
        content.addView(view, buttonLp);
        view.setOnClickListener(v -> openGameDetails(game));
        card.setOnClickListener(v -> openGameDetails(game));
    }

    private void addShelf(String heading, List<SteamStoreClient.Game> games) {
        if (games == null || games.isEmpty()) return;
        addSectionTitle(heading, "Select a game to see trailers, screenshots and details");
        HorizontalScrollView hsv = new HorizontalScrollView(requireContext());
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (SteamStoreClient.Game game : games) {
            row.addView(gameCard(game), new LinearLayout.LayoutParams(dp(222), dp(164)));
        }
        hsv.addView(row);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(170));
        lp.bottomMargin = dp(16);
        pageContent.addView(hsv, lp);
    }

    private View gameCard(SteamStoreClient.Game game) {
        FrameLayout card = new FrameLayout(requireContext());
        card.setBackground(glassBackground(GLASS, 14));
        card.setClipToOutline(true);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(dp(210), dp(152));
        cardLp.rightMargin = dp(10);
        card.setLayoutParams(cardLp);
        ImageView art = new ImageView(requireContext());
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        card.addView(art, new FrameLayout.LayoutParams(-1, -1));
        loadImage(art, imageFor(game));
        View gradient = new View(requireContext());
        gradient.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x10000000, 0x44000000, 0xF0101219}));
        card.addView(gradient, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout caption = new LinearLayout(requireContext());
        caption.setOrientation(LinearLayout.VERTICAL);
        caption.setGravity(Gravity.BOTTOM);
        caption.setPadding(dp(11), dp(8), dp(11), dp(10));
        FrameLayout.LayoutParams captionLp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        card.addView(caption, captionLp);
        TextView name = text(game.name, 14, Color.WHITE, true);
        name.setMaxLines(2);
        caption.addView(name);
        TextView price = text(game.price.isEmpty() ? "Steam PC game" : game.price, 11, 0xFFE0E7F1, false);
        addTop(caption, price, 3);
        card.setOnClickListener(v -> openGameDetails(game));
        return card;
    }

    private void addGrid(List<SteamStoreClient.Game> games) {
        if (games == null || games.isEmpty()) return;
        LinearLayout grid = new LinearLayout(requireContext());
        grid.setOrientation(LinearLayout.VERTICAL);
        int columns = getResources().getConfiguration().screenWidthDp >= 700 ? 3 : 2;
        for (int i = 0; i < games.size(); i += columns) {
            LinearLayout row = new LinearLayout(requireContext());
            for (int j = 0; j < columns && i + j < games.size(); j++) {
                SteamStoreClient.Game game = games.get(i + j);
                View card = gameCard(game);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(152), 1);
                lp.rightMargin = dp(7);
                lp.bottomMargin = dp(9);
                row.addView(card, lp);
            }
            grid.addView(row);
        }
        pageContent.addView(grid);
    }

    private void addSectionTitle(String title, String subtitle) {
        LinearLayout block = new LinearLayout(requireContext());
        block.setOrientation(LinearLayout.VERTICAL);
        TextView h = text(title, 18, TEXT, true);
        block.addView(h);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView s = text(subtitle, 11, MUTED, false);
            addTop(block, s, 3);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(4);
        lp.bottomMargin = dp(10);
        pageContent.addView(block, lp);
    }

    private void addNewsInfo() {
        addGlassInfo("Game compatibility", "Store listings tell us what the developer targets on Windows. Actual compatibility on this Mali-G57 device must be measured with a real launch test; the app will not invent a frame-rate or compatibility score.", "View device scan", this::showDeviceScan);
    }

    private void addGlassInfo(String title, String body, String action, Runnable onClick) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(glassBackground(0x66434C5C, 16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(5);
        lp.bottomMargin = dp(15);
        pageContent.addView(card, lp);
        card.addView(text(title, 16, TEXT, true));
        TextView description = text(body, 12, MUTED, false);
        addTop(card, description, 6);
        if (action != null) {
            Button button = glassButton(action, true);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, dp(42));
            blp.topMargin = dp(12);
            card.addView(button, blp);
            button.setOnClickListener(v -> onClick.run());
        }
    }

    private void addLargeExternalCard(String title, String body, String url, String action) {
        addGlassInfo(title, body, action, () -> openUrl(url));
    }

    private void addMessage(String title, String message, String action, Runnable runnable) {
        addGlassInfo(title, message, action, runnable);
    }

    private void showLoading(String message) {
        if (pageContent == null) return;
        LinearLayout box = new LinearLayout(requireContext());
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setPadding(dp(12), dp(18), dp(12), dp(18));
        ProgressBar progress = new ProgressBar(requireContext());
        box.addView(progress, new LinearLayout.LayoutParams(dp(26), dp(26)));
        TextView label = text(message, 13, MUTED, false);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(-1, -2);
        labelLp.leftMargin = dp(12);
        box.addView(label, labelLp);
        pageContent.addView(box);
    }

    private void removeDynamicContent() {
        if (pageContent == null) return;
        int keep = 3;
        while (pageContent.getChildCount() > keep) pageContent.removeViewAt(keep);
    }

    private void openGameDetails(SteamStoreClient.Game game) {
        if (!isAdded()) return;
        Dialog dialog = new Dialog(requireContext(), android.R.style.Theme_Material_NoActionBar_Fullscreen);
        LinearLayout root = dialogRoot();
        ScrollView scroll = new ScrollView(requireContext());
        scroll.setFillViewport(true);
        LinearLayout body = new LinearLayout(requireContext());
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18), dp(12), dp(18), dp(30));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        FrameLayout hero = new FrameLayout(requireContext());
        hero.setBackground(glassBackground(0xAA171E2B, 18));
        hero.setClipToOutline(true);
        LinearLayout.LayoutParams heroLp = new LinearLayout.LayoutParams(-1, dp(205));
        heroLp.bottomMargin = dp(14);
        body.addView(hero, heroLp);
        ImageView backdrop = new ImageView(requireContext());
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        hero.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));
        loadImage(backdrop, imageFor(game));
        if (Build.VERSION.SDK_INT >= 31) backdrop.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(7f, 7f, android.graphics.Shader.TileMode.CLAMP));
        View overlay = new View(requireContext());
        overlay.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x33303A4D, 0xCC0C1019, 0xF20C1019}));
        hero.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout heroText = new LinearLayout(requireContext());
        heroText.setOrientation(LinearLayout.VERTICAL);
        heroText.setGravity(Gravity.BOTTOM);
        heroText.setPadding(dp(17), dp(15), dp(17), dp(16));
        hero.addView(heroText, new FrameLayout.LayoutParams(-1, -1));
        heroText.addView(text("STEAM PC GAME", 10, 0xFFD8E8FF, true));
        TextView heroTitle = text(game.name, 25, Color.WHITE, true);
        addTop(heroText, heroTitle, 7);
        addTop(heroText, text(game.price.isEmpty() ? "Open Steam store details" : game.price, 13, 0xFFE4EAF3, false), 4);

        TextView loading = text("Loading full game details...", 12, MUTED, false);
        body.addView(loading);
        addGlassPanel(body, "PC Game Engine", "Game requirements, hardware scan and launch measurements are shown here. No performance score is fabricated.");
        addGlassPanel(body, "Game Introduction", game.description == null || game.description.isEmpty() ? "The Steam Store has not returned a short description yet." : game.description);
        addGallery(body, null, null);

        LinearLayout bottom = new LinearLayout(requireContext());
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(dp(12), dp(8), dp(12), dp(8));
        bottom.setBackground(glassBackground(0xDD202634, 14));
        Button action = glassButton(ownedIds.contains(game.appId) ? "Owned - Steam install setup" : "Buy game on Steam", true);
        bottom.addView(action, new LinearLayout.LayoutParams(-1, dp(48)));
        root.addView(bottom, new LinearLayout.LayoutParams(-1, -2));
        action.setOnClickListener(v -> {
            if (ownedIds.contains(game.appId)) {
                new android.app.AlertDialog.Builder(requireContext())
                        .setTitle("Steam game owned")
                        .setMessage("This title is in the synced Steam library. Local game installation still requires the official Windows Steam client to be installed and signed in inside a GameHub Lite Wine container. That installer bridge is not active in this build.")
                        .setPositiveButton("Open Steam store page", (d, w) -> openUrl(storeUrl(game.appId)))
                        .setNegativeButton("Close", null).show();
            } else {
                openUrl(storeUrl(game.appId));
            }
        });

        showDialog(dialog, root, false);
        SteamStoreClient.fetchDetails(game.appId, (full, error) -> {
            if (!dialog.isShowing()) return;
            if (full == null || full.details == null) {
                loading.setText(error != null ? error : "Full Steam details are unavailable.");
                return;
            }
            JSONObject data = full.details;
            loading.setVisibility(View.GONE);
            body.removeViewAt(body.getChildCount() - 1);
            body.removeViewAt(body.getChildCount() - 1);
            addDetailsMetadata(body, full, data);
            String about = data.optString("about_the_game", data.optString("short_description", ""));
            addHtmlPanel(body, "Game Introduction", about);
            addTrailerAndGallery(body, data);
            addCompatibilityPanel(body, data);
            addSystemRequirements(body, data);
        });
    }

    private void addDetailsMetadata(LinearLayout body, SteamStoreClient.Game game, JSONObject data) {
        LinearLayout meta = new LinearLayout(requireContext());
        meta.setOrientation(LinearLayout.VERTICAL);
        meta.setPadding(dp(14), dp(12), dp(14), dp(12));
        meta.setBackground(glassBackground(0x66434C5C, 15));
        body.addView(meta, new LinearLayout.LayoutParams(-1, -2));
        TextView title = text(game.name, 21, TEXT, true);
        meta.addView(title);
        JSONArray genres = data.optJSONArray("genres");
        if (genres != null) {
            List<String> names = new ArrayList<>();
            for (int i = 0; i < genres.length(); i++) {
                JSONObject g = genres.optJSONObject(i);
                if (g != null) names.add(g.optString("description"));
            }
            if (!names.isEmpty()) addTop(meta, text(String.join("  •  ", names), 12, 0xFFBDD9F6, true), 6);
        }
        addTop(meta, text("Developer: " + joinArray(data.optJSONArray("developers")), 12, MUTED, false), 8);
        addTop(meta, text("Publisher: " + joinArray(data.optJSONArray("publishers")), 12, MUTED, false), 4);
        JSONObject release = data.optJSONObject("release_date");
        addTop(meta, text("Release date: " + (release != null ? release.optString("date", "Unknown") : "Unknown"), 12, MUTED, false), 4);
        addTop(meta, text("Age rating: " + (data.optString("required_age", "0").equals("0") ? "Not specified" : data.optString("required_age") + "+"), 12, MUTED, false), 4);
        JSONObject metaScore = data.optJSONObject("metacritic");
        if (metaScore != null) addTop(meta, text("Metacritic: " + metaScore.optInt("score") + "/100", 12, 0xFFFFD27A, true), 4);
        String shortDesc = data.optString("short_description", "");
        if (!shortDesc.isEmpty()) addTop(meta, text(shortDesc, 13, TEXT, false), 8);
    }

    private void addTrailerAndGallery(LinearLayout body, JSONObject data) {
        JSONArray movies = data.optJSONArray("movies");
        if (movies != null && movies.length() > 0) {
            addSectionTitleTo(body, "Trailers");
            HorizontalScrollView hsv = new HorizontalScrollView(requireContext());
            hsv.setHorizontalScrollBarEnabled(false);
            LinearLayout row = new LinearLayout(requireContext());
            for (int i = 0; i < movies.length(); i++) {
                JSONObject movie = movies.optJSONObject(i);
                if (movie == null) continue;
                String thumb = movie.optString("thumbnail", "");
                String video = "";
                JSONObject mp4 = movie.optJSONObject("mp4");
                if (mp4 != null) video = mp4.optString("max", mp4.optString("480", ""));
                if (video.isEmpty()) video = movie.optString("webm", "");
                final String trailerUrl = video;
                FrameLayout tile = new FrameLayout(requireContext());
                tile.setBackground(glassBackground(GLASS, 12));
                tile.setClipToOutline(true);
                ImageView iv = new ImageView(requireContext());
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                tile.addView(iv, new FrameLayout.LayoutParams(dp(260), dp(145)));
                loadImage(iv, thumb);
                TextView play = text("▶  " + movie.optString("name", "Play trailer"), 12, Color.WHITE, true);
                play.setPadding(dp(10), dp(8), dp(10), dp(8));
                play.setBackground(glassBackground(0xBB10151F, 8));
                FrameLayout.LayoutParams playLp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
                tile.addView(play, playLp);
                tile.setOnClickListener(v -> openUrl(trailerUrl.isEmpty() ? storeUrl(data.optInt("steam_appid")) : trailerUrl));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(260), dp(145));
                lp.rightMargin = dp(10);
                row.addView(tile, lp);
            }
            hsv.addView(row);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(151));
            lp.bottomMargin = dp(12);
            body.addView(hsv, lp);
        }
        JSONArray screenshots = data.optJSONArray("screenshots");
        if (screenshots != null && screenshots.length() > 0) {
            addSectionTitleTo(body, "Gallery");
            HorizontalScrollView hsv = new HorizontalScrollView(requireContext());
            hsv.setHorizontalScrollBarEnabled(false);
            LinearLayout row = new LinearLayout(requireContext());
            for (int i = 0; i < screenshots.length(); i++) {
                JSONObject shot = screenshots.optJSONObject(i);
                if (shot == null) continue;
                String imageUrl = shot.optString("path_full", shot.optString("path_thumbnail", ""));
                ImageView iv = new ImageView(requireContext());
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                iv.setBackground(glassBackground(GLASS, 10));
                iv.setClipToOutline(true);
                loadImage(iv, imageUrl);
                iv.setOnClickListener(v -> openUrl(imageUrl));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(270), dp(152));
                lp.rightMargin = dp(10);
                row.addView(iv, lp);
            }
            hsv.addView(row);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(158));
            lp.bottomMargin = dp(14);
            body.addView(hsv, lp);
        }
    }

    private void addCompatibilityPanel(LinearLayout body, JSONObject details) {
        addSectionTitleTo(body, "Device compatibility");
        LinearLayout panel = new LinearLayout(requireContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(12), dp(14), dp(12));
        panel.setBackground(glassBackground(0x77434C5C, 15));
        body.addView(panel);
        String hardware = hardwareSummary();
        panel.addView(text(hardware, 12, TEXT, false));
        TextView explanation = text("A real compatibility result requires installing the game, launching it and recording crashes, rendering and frame times. Store specifications alone cannot certify that a game will run on this Mali device.", 11, MUTED, false);
        addTop(panel, explanation, 8);
        Button scan = glassButton("Refresh device scan", false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(40));
        lp.topMargin = dp(10);
        panel.addView(scan, lp);
        scan.setOnClickListener(v -> new android.app.AlertDialog.Builder(requireContext())
                .setTitle("Current hardware scan")
                .setMessage(hardwareSummary())
                .setPositiveButton("OK", null).show());
    }

    private void addSystemRequirements(LinearLayout body, JSONObject details) {
        JSONObject req = details.optJSONObject("pc_requirements");
        if (req == null) return;
        String minimum = requirementText(req, "minimum");
        String recommended = requirementText(req, "recommended");
        if (minimum.isEmpty() && recommended.isEmpty()) return;
        addSectionTitleTo(body, "Windows PC requirements");
        if (!minimum.isEmpty()) addHtmlPanel(body, "Minimum", minimum);
        if (!recommended.isEmpty()) addHtmlPanel(body, "Recommended", recommended);
    }

    private String requirementText(JSONObject obj, String key) {
        Object value = obj.opt(key);
        if (value instanceof String) return (String)value;
        if (value instanceof JSONObject) return ((JSONObject)value).optString("minimum", "");
        return "";
    }

    private void addGallery(LinearLayout body, JSONArray images, JSONArray videos) {}

    private void addHtmlPanel(LinearLayout parent, String title, String html) {
        LinearLayout panel = new LinearLayout(requireContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(12), dp(14), dp(12));
        panel.setBackground(glassBackground(0x66434C5C, 14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(10);
        parent.addView(panel, lp);
        panel.addView(text(title, 16, TEXT, true));
        TextView content = text("", 12, MUTED, false);
        if (Build.VERSION.SDK_INT >= 24) content.setText(Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT));
        else content.setText(Html.fromHtml(html));
        content.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        addTop(panel, content, 7);
    }

    private void addGlassPanel(LinearLayout parent, String title, String message) {
        LinearLayout panel = new LinearLayout(requireContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(12), dp(14), dp(12));
        panel.setBackground(glassBackground(0x66434C5C, 14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(10);
        parent.addView(panel, lp);
        panel.addView(text(title, 16, TEXT, true));
        addTop(panel, text(message, 12, MUTED, false), 6);
    }

    private void addSectionTitleTo(LinearLayout parent, String title) {
        TextView h = text(title, 17, TEXT, true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(16);
        lp.bottomMargin = dp(8);
        parent.addView(h, lp);
    }

    private void showDeviceScan() {
        new android.app.AlertDialog.Builder(requireContext())
                .setTitle("Device hardware")
                .setMessage(hardwareSummary())
                .setPositiveButton("OK", null).show();
    }

    private String hardwareSummary() {
        String model = Build.MANUFACTURER + " " + Build.MODEL;
        String soc = "";
        if (Build.VERSION.SDK_INT >= 31) soc = Build.SOC_MODEL;
        if (soc == null || soc.isEmpty()) soc = Build.HARDWARE;
        String gpu = "Not detected";
        try {
            String renderer = com.winlator.cmod.core.GPUInformation.getRenderer(null, requireContext());
            if (renderer != null && !renderer.trim().isEmpty()) gpu = renderer;
        } catch (Throwable ignored) {}
        ActivityManager manager = (ActivityManager) requireContext().getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
        if (manager != null) manager.getMemoryInfo(memory);
        long ramGb = Math.round(memory.totalMem / (1024d * 1024d * 1024d));
        int cores = Runtime.getRuntime().availableProcessors();
        return "Device: " + model + "\nSoC: " + soc + "\nGPU: " + gpu + "\nRAM: " + ramGb + " GB\nCPU logical cores: " + cores;
    }

    private String joinArray(JSONArray values) {
        if (values == null || values.length() == 0) return "Not listed";
        List<String> result = new ArrayList<>();
        for (int i = 0; i < values.length(); i++) {
            String item = values.optString(i, "");
            if (!item.isEmpty()) result.add(item);
        }
        return result.isEmpty() ? "Not listed" : String.join(", ", result);
    }

    private String imageFor(SteamStoreClient.Game game) {
        if (game == null) return "";
        if (game.headerImage != null && !game.headerImage.isEmpty()) return game.headerImage;
        if (game.tinyImage != null && !game.tinyImage.isEmpty()) return game.tinyImage;
        return "https://cdn.akamai.steamstatic.com/steam/apps/" + game.appId + "/header.jpg";
    }

    private String storeUrl(int appId) {
        return "https://store.steampowered.com/app/" + appId + "/";
    }

    private void openUrl(String url) {
        if (url == null || url.isEmpty()) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Could not open the link.", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadImage(ImageView image, String url) {
        if (url == null || url.isEmpty()) return;
        Glide.with(this).load(url).diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .placeholder(R.drawable.cover_art_placeholder)
                .error(R.drawable.cover_art_placeholder)
                .centerCrop().into(image);
    }

    private void addTop(LinearLayout parent, View view, int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(top);
        parent.addView(view, lp);
    }

    private LinearLayout dialogRoot() {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(16), dp(18), dp(16));
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(27, 34, 48), Color.rgb(15, 18, 27), Color.rgb(35, 26, 39)}));
        return root;
    }

    private void showDialog(Dialog dialog, View root, boolean wrapContent) {
        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            if (wrapContent) {
                window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
                window.setGravity(Gravity.CENTER);
            } else {
                window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
                window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
            }
        }
        dialog.show();
        if (window != null) {
            if (wrapContent) window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            else window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        }
    }

    private EditText field(String hint) {
        EditText edit = new EditText(requireContext());
        edit.setHint(hint);
        edit.setTextColor(TEXT);
        edit.setHintTextColor(MUTED);
        edit.setSingleLine(true);
        edit.setPadding(dp(12), 0, dp(12), 0);
        edit.setBackground(glassBackground(0x88464F60, 12));
        return edit;
    }

    private Button glassButton(String value, boolean primary) {
        Button button = new Button(requireContext());
        button.setText(value);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setBackground(glassBackground(primary ? 0xCC087FC1 : 0x66464F60, 12));
        return button;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(requireContext());
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private GradientDrawable glassBackground(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{color, adjustAlpha(color, 0.78f)});
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(1), 0x48FFFFFF);
        return drawable;
    }

    private int adjustAlpha(int color, float factor) {
        int alpha = Math.max(0, Math.min(255, Math.round(Color.alpha(color) * factor)));
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private int dp(float value) {
        return (int)(value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
