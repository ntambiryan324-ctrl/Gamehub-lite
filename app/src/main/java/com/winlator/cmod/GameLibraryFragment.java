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
    private static final int BG = Color.rgb(244, 247, 252);
    private static final int SURFACE = Color.rgb(255, 255, 255);
    private static final int TEXT = Color.rgb(31, 43, 61);
    private static final int MUTED = Color.rgb(101, 117, 139);
    private static final int ACCENT = Color.rgb(45, 116, 190);
    private static final int GLASS = 0xDFFFFFFF;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private static final java.util.concurrent.ExecutorService CATALOG_IO = java.util.concurrent.Executors.newSingleThreadExecutor();
    private final List<SteamStoreClient.Game> featured = new ArrayList<>();
    private final List<SteamStoreClient.Game> ownedGames = new ArrayList<>();
    private final Set<Integer> ownedIds = new HashSet<>();
    private LinearLayout pageContent;
    private TextView accountStatus;
    private EditText searchField;
    private ScrollView libraryScroll;
    private LinearLayout catalogList;
    private boolean catalogLoading = false;
    private boolean catalogHasMore = false;
    private int catalogOffset = 0;
    private String catalogQuery = "";
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
                new int[]{Color.rgb(250, 252, 255), Color.rgb(236, 243, 251), Color.rgb(245, 241, 250)}));
        libraryScroll = scroll;
        scroll.setOnScrollChangeListener((View v, int x, int y, int oldX, int oldY) -> {
            if (catalogList == null || !catalogHasMore || catalogLoading) return;
            View child = scroll.getChildAt(0);
            if (child != null && child.getBottom() - scroll.getHeight() - y < dp(280)) loadMoreCatalog();
        });

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
        libraryScroll = null;
        catalogList = null;
        catalogLoading = false;
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
        addSectionTitle("Steam Games", "Browse the Steam PC store catalogue");
        if (featured.isEmpty()) {
            addMessage("Loading Steam games", "Retrieving current storefront categories...", null, null);
            loadFeatured();
        } else {
            addShelf("Trending on Steam", featured.subList(0, Math.min(12, featured.size())));
        }
        addGlassInfo("Explore the complete catalogue", "Open the full indexed Steam games list. Once the catalogue has been indexed, you can keep scrolling through the list or search by title.", "Browse all Steam games", () -> {
            currentTab = "PC Games";
            showCurrentTab();
        });
    }

    private void showPcCatalog() {
        int count = SteamStoreClient.catalogCount(requireContext());
        addSectionTitle("All Steam PC Games", count > 0
                ? String.format(Locale.US, "%,d games indexed locally • scroll to load more", count)
                : "Build the full catalogue once, then browse it offline");
        if (count == 0) {
            addGlassInfo("Full Steam catalogue", "Steam provides a paginated public catalogue through its Web API. A standard Steam Web API key is needed for this one-time indexing step; it is encrypted locally and never uploaded by GameHub Lite.", "Set up Steam catalogue access", this::showApiKeyDialog);
            if (!featured.isEmpty()) addShelf("Store highlights while you set it up", featured.subList(0, Math.min(12, featured.size())));
            return;
        }
        beginCatalogList("");
    }

    private void showOwnedLibrary() {
        boolean linked = SteamAccountStore.isConnected(requireContext());
        addSectionTitle("My Steam Games", linked
                ? "Steam account linked: " + SteamAccountStore.getSteamId(requireContext())
                : "Link your Steam account to view your library");
        if (!linked) {
            addGlassInfo("Sign in with Steam", "Continue to Steam's own secure sign-in page. Steam handles your password and any email or Steam Guard verification code. GameHub Lite receives only the verified SteamID.", "Sign in with Steam", this::showSteamAccountDialog);
            return;
        }
        if (!SteamAccountStore.hasApiKey(requireContext())) {
            addGlassInfo("Steam account linked", "Your Steam identity is verified. To retrieve the owned-games list through Valve's official GetOwnedGames API, the app also needs a standard Web API key and your Steam Game Details privacy must allow access.", "Add Steam Web API key", this::showApiKeyDialog);
            return;
        }
        if (ownedGames.isEmpty()) {
            showLoading("Syncing your Steam library...");
            syncOwnedGames();
        } else {
            addShelf("Your Steam library", ownedGames);
            addGlassInfo("Library sync", "Synced from Steam's GetOwnedGames API. Installing the actual game files through the official Steam client is a separate integration step.", "Sync again", this::syncOwnedGames);
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
        if (!isAdded() || pageContent == null || query == null || query.trim().length() < 2) return;
        removeDynamicContent();
        addSectionTitle("Search results", "Searching for \"" + query + "\" across the Steam catalogue");
        int indexedCount = SteamStoreClient.catalogCount(requireContext());
        if (indexedCount > 0) {
            beginCatalogList(query);
            return;
        }
        showLoading("Searching the Steam Store...");
        SteamStoreClient.search(query, (games, error) -> {
            if (!isAdded() || pageContent == null || !query.equals(currentQuery)) return;
            removeDynamicContent();
            addSectionTitle("Search results", games == null ? "Steam search could not be completed" : games.size() + " quick matches");
            if (games == null || games.isEmpty()) {
                addMessage("No results", error != null ? error : "Try a different title.", null, null);
            } else {
                addGrid(games);
                addGlassInfo("Search every Steam game", "These are quick storefront matches. Index the complete catalogue once to search and continuously scroll through the full Steam PC listing.", "Index full catalogue", this::showApiKeyDialog);
            }
        });
    }

    private void syncOwnedGames() {
        if (!SteamAccountStore.isConnected(requireContext())) {
            showSteamAccountDialog();
            return;
        }
        if (!SteamAccountStore.hasApiKey(requireContext())) {
            showApiKeyDialog();
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
                if (pageContent != null && "My Games".equals(currentTab)) {
                    removeDynamicContent();
                    showOwnedLibrary();
                }
                Toast.makeText(requireContext(), "Synced " + games.size() + " Steam games.", Toast.LENGTH_SHORT).show();
            });
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Could not decrypt the saved Steam key. Add it again.", Toast.LENGTH_LONG).show();
        }
    }

    public void onSteamAccountLinked() {
        if (!isAdded()) return;
        if (accountStatus != null) accountStatus.setText("Steam account linked: " + SteamAccountStore.getSteamId(requireContext()));
        if ("My Games".equals(currentTab) && pageContent != null) {
            removeDynamicContent();
            showOwnedLibrary();
        }
    }

    private void showSteamAccountDialog() {
        if (!isAdded()) return;
        Dialog dialog = new Dialog(requireContext());
        LinearLayout root = dialogRoot();
        root.addView(text("Connect Steam", 22, TEXT, true));
        addTop(root, text("Steam sign-in opens on Steam's official website. Enter your username, password and any email or Steam Guard verification code there—not inside GameHub Lite. The app never receives or stores those credentials.", 13, MUTED, false), 8);

        Button signIn = glassButton(SteamAccountStore.isConnected(requireContext()) ? "Reconnect Steam account" : "Sign in with Steam", true);
        LinearLayout.LayoutParams signLp = new LinearLayout.LayoutParams(-1, dp(46));
        signLp.topMargin = dp(14);
        root.addView(signIn, signLp);
        signIn.setOnClickListener(v -> {
            dialog.dismiss();
            try {
                SteamOpenIdHelper.launchLogin(requireContext());
            } catch (Exception e) {
                Toast.makeText(requireContext(), "Could not open Steam sign-in: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        if (SteamAccountStore.isConnected(requireContext())) {
            addTop(root, text("Linked SteamID64: " + SteamAccountStore.getSteamId(requireContext()), 12, MUTED, false), 10);
            Button api = glassButton("Steam Web API key and catalogue settings", false);
            addTop(root, api, 10);
            api.setOnClickListener(v -> { dialog.dismiss(); showApiKeyDialog(); });
            Button disconnect = glassButton("Disconnect Steam account", false);
            addTop(root, disconnect, 8);
            disconnect.setOnClickListener(v -> {
                SteamAccountStore.clear(requireContext());
                ownedGames.clear();
                ownedIds.clear();
                if (accountStatus != null) accountStatus.setText("Steam account disconnected");
                dialog.dismiss();
                showCurrentTab();
            });
        } else {
            addTop(root, text("After Steam confirms the login, you will return here automatically and the account will be linked.", 11, MUTED, false), 10);
        }
        showDialog(dialog, root, true);
    }

    private void showApiKeyDialog() {
        if (!isAdded()) return;
        if (!SteamAccountStore.isConnected(requireContext())) {
            showSteamAccountDialog();
            return;
        }
        Dialog dialog = new Dialog(requireContext());
        LinearLayout root = dialogRoot();
        root.addView(text("Steam Web API access", 21, TEXT, true));
        addTop(root, text("Steam OpenID confirms your account identity, but does not issue an API access token. Valve's owned-games and full catalogue APIs still require a standard Web API key. This key is encrypted locally with Android Keystore.", 12, MUTED, false), 8);
        Button openKey = glassButton("Get Web API key from Steam", true);
        addTop(root, openKey, 10);
        openKey.setOnClickListener(v -> openUrl("https://steamcommunity.com/dev/apikey"));
        EditText keyField = field("Paste your Steam Web API key");
        keyField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        addTop(root, keyField, 10);
        TextView status = text("", 11, MUTED, false);
        addTop(root, status, 8);
        Button save = glassButton("Save key securely", true);
        addTop(root, save, 12);
        save.setOnClickListener(v -> {
            try {
                SteamAccountStore.save(requireContext(), SteamAccountStore.getSteamId(requireContext()), keyField.getText().toString());
                status.setText("Key saved securely on this device.");
                dialog.dismiss();
                if ("My Games".equals(currentTab)) syncOwnedGames();
                else if ("PC Games".equals(currentTab) || "Steam".equals(currentTab)) {
                    Toast.makeText(requireContext(), "Starting the full Steam catalogue index...", Toast.LENGTH_SHORT).show();
                    indexFullCatalog();
                }
            } catch (Exception e) {
                status.setText(e.getMessage());
            }
        });
        showDialog(dialog, root, true);
    }

    private void indexFullCatalog() {
        if (!SteamAccountStore.hasApiKey(requireContext())) {
            showApiKeyDialog();
            return;
        }
        try {
            String key = SteamAccountStore.getApiKey(requireContext());
            Toast.makeText(requireContext(), "Indexing the Steam catalogue. This is a one-time download.", Toast.LENGTH_LONG).show();
            SteamStoreClient.syncFullCatalog(requireContext(), key, (count, complete, error) -> {
                if (!isAdded()) return;
                if (error != null) {
                    Toast.makeText(requireContext(), "Catalogue sync stopped: " + error, Toast.LENGTH_LONG).show();
                } else if (complete) {
                    Toast.makeText(requireContext(), String.format(Locale.US, "Indexed %,d Steam apps.", count), Toast.LENGTH_LONG).show();
                    if (pageContent != null) {
                        removeDynamicContent();
                        if ("My Games".equals(currentTab)) showOwnedLibrary();
                        else showPcCatalog();
                    }
                }
            });
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Could not read the saved Steam key.", Toast.LENGTH_LONG).show();
        }
    }

    private void beginCatalogList(String query) {
        catalogQuery = query == null ? "" : query;
        catalogOffset = 0;
        catalogHasMore = true;
        catalogLoading = false;
        catalogList = new LinearLayout(requireContext());
        catalogList.setOrientation(LinearLayout.VERTICAL);
        pageContent.addView(catalogList, new LinearLayout.LayoutParams(-1, -2));
        loadMoreCatalog();
    }

    private void loadMoreCatalog() {
        if (!isAdded() || catalogList == null || catalogLoading || !catalogHasMore) return;
        catalogLoading = true;
        final LinearLayout target = catalogList;
        final String query = catalogQuery;
        final int offset = catalogOffset;
        CATALOG_IO.execute(() -> {
            List<SteamStoreClient.Game> batch;
            try {
                batch = SteamStoreClient.searchLocalCatalog(requireContext().getApplicationContext(), query, offset, 36);
            } catch (Exception e) {
                batch = new ArrayList<>();
            }
            final List<SteamStoreClient.Game> result = batch;
            handler.post(() -> {
                if (!isAdded() || catalogList != target) return;
                catalogLoading = false;
                if (result.isEmpty()) {
                    catalogHasMore = false;
                    if (catalogOffset == 0) {
                        TextView empty = text("No games matched this search.", 13, MUTED, false);
                        empty.setPadding(dp(8), dp(20), dp(8), dp(20));
                        target.addView(empty);
                    } else {
                        TextView end = text("You have reached the end of the catalogue.", 11, MUTED, false);
                        end.setGravity(Gravity.CENTER);
                        end.setPadding(dp(8), dp(16), dp(8), dp(20));
                        target.addView(end);
                    }
                    return;
                }
                appendCatalogGrid(result);
                catalogOffset += result.size();
                catalogHasMore = result.size() == 36;
                if (!catalogHasMore) {
                    TextView end = text(String.format(Locale.US, "Showing all %,d matching games.", catalogOffset), 11, MUTED, false);
                    end.setGravity(Gravity.CENTER);
                    end.setPadding(dp(8), dp(16), dp(8), dp(20));
                    target.addView(end);
                }
            });
        });
    }

    private void appendCatalogGrid(List<SteamStoreClient.Game> games) {
        if (catalogList == null || games == null || games.isEmpty()) return;
        LinearLayout grid = new LinearLayout(requireContext());
        grid.setOrientation(LinearLayout.VERTICAL);
        int columns = getResources().getConfiguration().screenWidthDp >= 700 ? 3 : 2;
        for (int i = 0; i < games.size(); i += columns) {
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int j = 0; j < columns && i + j < games.size(); j++) {
                View card = gameCard(games.get(i + j));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(174), 1);
                lp.rightMargin = dp(8);
                lp.bottomMargin = dp(10);
                row.addView(card, lp);
            }
            grid.addView(row);
        }
        catalogList.addView(grid);
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
            row.addView(gameCard(game), new LinearLayout.LayoutParams(dp(222), dp(190)));
        }
        hsv.addView(row);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(194));
        lp.bottomMargin = dp(16);
        pageContent.addView(hsv, lp);
    }

    private View gameCard(SteamStoreClient.Game game) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(6), dp(6), dp(6), dp(7));
        card.setBackground(glassBackground(0xEFFFFFFF, 16));
        card.setClipToOutline(true);

        ImageView art = new ImageView(requireContext());
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setBackground(glassBackground(0xFFE7EDF5, 11));
        art.setClipToOutline(true);
        card.addView(art, new LinearLayout.LayoutParams(-1, dp(104)));
        loadImage(art, imageFor(game));

        LinearLayout caption = new LinearLayout(requireContext());
        caption.setOrientation(LinearLayout.VERTICAL);
        caption.setPadding(dp(7), dp(7), dp(7), dp(2));
        card.addView(caption, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView name = text(game.name, 13, TEXT, true);
        name.setMaxLines(2);
        caption.addView(name);
        TextView price = text(game.price.isEmpty() ? "Steam PC game" : game.price, 11, MUTED, false);
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
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(180), 1);
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
        card.setBackground(glassBackground(0xEFFFFFFF, 16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(7);
        lp.bottomMargin = dp(13);
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
        catalogList = null;
        catalogLoading = false;
        catalogHasMore = false;
        catalogOffset = 0;
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
        bottom.setBackground(glassBackground(0xF7FFFFFF, 14));
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
        panel.setBackground(glassBackground(0xEFFFFFFF, 15));
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
        panel.setBackground(glassBackground(0xEFFFFFFF, 14));
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
                new int[]{Color.rgb(255, 255, 255), Color.rgb(240, 246, 253), Color.rgb(246, 240, 250)}));
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
        edit.setBackground(glassBackground(0xEEFFFFFF, 12));
        return edit;
    }

    private Button glassButton(String value, boolean primary) {
        Button button = new Button(requireContext());
        button.setText(value);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setTextColor(primary ? Color.WHITE : TEXT);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setBackground(glassBackground(primary ? 0xFF327FBE : 0xE4FFFFFF, 12));
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
        drawable.setStroke(dp(1), 0xB5FFFFFF);
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
