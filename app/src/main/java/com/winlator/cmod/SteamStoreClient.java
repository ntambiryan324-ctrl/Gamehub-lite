package com.winlator.cmod;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Steam Store metadata, optional full catalogue indexing, and the user's own owned-games list. */
public final class SteamStoreClient {
    private static final ExecutorService IO = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final String STORE = "https://store.steampowered.com";
    private static final String API = "https://api.steampowered.com";
    private static final String PARTNER_API = "https://partner.steam-api.com";
    private static final String COUNTRY = "us";
    private static final String LANGUAGE = "english";

    public interface Callback<T> { void onResult(T result, String error); }
    public interface Progress { void onProgress(int indexed, boolean complete, String error); }

    public static final class Game {
        public int appId;
        public String name = "";
        public String headerImage = "";
        public String tinyImage = "";
        public String price = "";
        public String description = "";
        public boolean windows = true;
        public boolean owned = false;
        public int discountPercent = 0;
        public int metacritic = 0;
        public JSONObject details;
    }

    private SteamStoreClient() {}

    public static void fetchFeatured(Callback<List<Game>> callback) {
        IO.execute(() -> {
            try {
                JSONObject root = getJson(STORE + "/api/featuredcategories/?cc=" + COUNTRY + "&l=" + LANGUAGE);
                LinkedHashMap<Integer, Game> unique = new LinkedHashMap<>();
                java.util.Iterator<String> keys = root.keys();
                while (keys.hasNext()) {
                    JSONObject section = root.optJSONObject(keys.next());
                    if (section == null) continue;
                    JSONArray items = section.optJSONArray("items");
                    if (items == null) continue;
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject row = items.optJSONObject(i);
                        Game game = fromStoreRow(row);
                        if (game != null && game.windows) unique.put(game.appId, game);
                    }
                }
                List<Game> result = new ArrayList<>(unique.values());
                deliver(callback, result, result.isEmpty() ? "Steam returned no featured PC games." : null);
            } catch (Exception e) {
                deliver(callback, null, safeMessage(e));
            }
        });
    }

    public static void search(String term, Callback<List<Game>> callback) {
        IO.execute(() -> {
            try {
                String url = STORE + "/api/storesearch/?term=" + URLEncoder.encode(term, "UTF-8")
                        + "&l=" + LANGUAGE + "&cc=" + COUNTRY;
                JSONObject root = getJson(url);
                JSONArray items = root.optJSONArray("items");
                List<Game> result = new ArrayList<>();
                if (items != null) {
                    for (int i = 0; i < items.length(); i++) {
                        Game game = fromStoreRow(items.optJSONObject(i));
                        if (game != null && game.windows) result.add(game);
                    }
                }
                deliver(callback, result, null);
            } catch (Exception e) {
                deliver(callback, null, safeMessage(e));
            }
        });
    }

    public static void fetchDetails(int appId, Callback<Game> callback) {
        IO.execute(() -> {
            try {
                JSONObject root = getJson(STORE + "/api/appdetails?appids=" + appId + "&cc=" + COUNTRY + "&l=" + LANGUAGE);
                JSONObject envelope = root.optJSONObject(String.valueOf(appId));
                if (envelope == null || !envelope.optBoolean("success", false)) throw new Exception("Steam has no public store details for this app.");
                JSONObject data = envelope.optJSONObject("data");
                if (data == null) throw new Exception("Steam returned incomplete game details.");
                Game game = fromDetails(appId, data);
                deliver(callback, game, null);
            } catch (Exception e) {
                deliver(callback, null, safeMessage(e));
            }
        });
    }

    public static void fetchOwnedGames(String steamId, String apiKey, Callback<List<Game>> callback) {
        IO.execute(() -> {
            try {
                String url = PARTNER_API + "/IPlayerService/GetOwnedGames/v1/?key=" + enc(apiKey)
                        + "&steamid=" + enc(steamId)
                        + "&include_appinfo=true&include_played_free_games=true";
                JSONObject root = getJson(url);
                JSONObject response = root.optJSONObject("response");
                JSONArray games = response != null ? response.optJSONArray("games") : null;
                if (games == null) throw new Exception("Steam did not return the game list. Set Game Details to Public in Steam privacy settings and retry.");
                List<Game> result = new ArrayList<>();
                for (int i = 0; i < games.length(); i++) {
                    JSONObject row = games.optJSONObject(i);
                    if (row == null) continue;
                    Game g = new Game();
                    g.appId = row.optInt("appid");
                    g.name = row.optString("name", "App " + g.appId);
                    g.headerImage = STORE + "/apps/" + g.appId + "/header.jpg";
                    g.owned = true;
                    if (g.appId > 0) result.add(g);
                }
                deliver(callback, result, null);
            } catch (Exception e) {
                deliver(callback, null, safeMessage(e));
            }
        });
    }

    /** Indexes the public Steam Store game list in 50,000-app pages. Uses the user's own API key. */
    public static void syncFullCatalog(Context context, String apiKey, Progress progress) {
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            SteamCatalogDb db = new SteamCatalogDb(app);
            int indexed = 0;
            int lastAppId = 0;
            try {
                while (true) {
                    JSONObject input = new JSONObject();
                    input.put("include_games", true);
                    input.put("include_dlc", false);
                    input.put("include_software", false);
                    input.put("include_videos", false);
                    input.put("include_hardware", false);
                    input.put("max_results", 50000);
                    if (lastAppId > 0) input.put("last_appid", lastAppId);
                    String url = PARTNER_API + "/IStoreService/GetAppList/v1/?key=" + enc(apiKey)
                            + "&input_json=" + enc(input.toString());
                    JSONObject root = getJson(url);
                    JSONObject response = root.optJSONObject("response");
                    if (response == null) throw new Exception("Steam catalogue response was not recognized.");
                    JSONArray apps = response.optJSONArray("apps");
                    if (apps == null || apps.length() == 0) break;
                    db.insertApps(apps);
                    indexed += apps.length();
                    int lastInPage = 0;\n                    for (int i = apps.length() - 1; i >= 0 && lastInPage == 0; i--) { JSONObject last = apps.optJSONObject(i); if (last != null) lastInPage = last.optInt("appid"); }\n                    int next = response.optInt("last_appid", lastInPage);
                    boolean more = response.optBoolean("have_more_results", false);
                    postProgress(progress, indexed, false, null);
                    if (!more || next <= lastAppId) break;
                    lastAppId = next;
                }
                postProgress(progress, indexed, true, null);
            } catch (Exception e) {
                postProgress(progress, indexed, true, safeMessage(e));
            } finally {
                db.close();
            }
        });
    }

    public static List<Game> searchLocalCatalog(Context context, String query, int limit) {
        SteamCatalogDb db = new SteamCatalogDb(context.getApplicationContext());
        try { return db.search(query, limit); }
        finally { db.close(); }
    }

    public static int catalogCount(Context context) {
        SteamCatalogDb db = new SteamCatalogDb(context.getApplicationContext());
        try { return db.count(); }
        finally { db.close(); }
    }

    private static Game fromStoreRow(JSONObject row) {
        if (row == null) return null;
        int id = row.optInt("id", row.optInt("appid"));
        String name = row.optString("name", "");
        if (id <= 0 || name.isEmpty()) return null;
        Game g = new Game();
        g.appId = id;
        g.name = name;
        g.headerImage = row.optString("header_image", "");
        g.tinyImage = row.optString("tiny_image", "");
        JSONObject price = row.optJSONObject("price");
        if (price != null) g.price = price.optString("final_formatted", "");
        if (g.price.isEmpty()) g.price = row.optString("final_price", "");
        g.discountPercent = row.optInt("discount_percent", 0);
        g.metacritic = row.optInt("metascore", 0);
        JSONObject platforms = row.optJSONObject("platforms");
        if (platforms != null && platforms.has("windows")) g.windows = platforms.optBoolean("windows", false);
        else if (row.has("windows_available")) g.windows = row.optBoolean("windows_available", false);
        return g;
    }

    private static Game fromDetails(int appId, JSONObject data) {
        Game g = new Game();
        g.appId = appId;
        g.name = data.optString("name", "App " + appId);
        g.headerImage = data.optString("header_image", "");
        g.description = data.optString("short_description", data.optString("about_the_game", ""));
        JSONObject platforms = data.optJSONObject("platforms");
        g.windows = platforms == null || platforms.optBoolean("windows", false);
        JSONObject price = data.optJSONObject("price_overview");
        if (price != null) g.price = price.optString("final_formatted", "");
        JSONObject meta = data.optJSONObject("metacritic");
        if (meta != null) g.metacritic = meta.optInt("score", 0);
        g.details = data;
        return g;
    }

    private static JSONObject getJson(String address) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setRequestProperty("User-Agent", "GameHubLite/0.1 Android");
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setInstanceFollowRedirects(true);
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
            if (stream == null) throw new Exception("Steam request failed (HTTP " + status + ").");
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) body.append(line);
            }
            if (status < 200 || status >= 300) throw new Exception("Steam request failed (HTTP " + status + ").");
            return new JSONObject(body.toString());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String enc(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8");
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.trim().isEmpty() ? "Steam request failed. Check your internet connection and try again." : message;
    }

    private static <T> void deliver(Callback<T> callback, T result, String error) {
        MAIN.post(() -> callback.onResult(result, error));
    }

    private static void postProgress(Progress progress, int indexed, boolean complete, String error) {
        MAIN.post(() -> progress.onProgress(indexed, complete, error));
    }

    private static final class SteamCatalogDb extends SQLiteOpenHelper {
        private static final String DB = "steam_catalog.db";
        SteamCatalogDb(Context context) { super(context, DB, null, 1); }
        @Override public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE apps (appid INTEGER PRIMARY KEY, name TEXT NOT NULL)");
            db.execSQL("CREATE INDEX apps_name_idx ON apps(name COLLATE NOCASE)");
        }
        @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}
        void insertApps(JSONArray apps) throws Exception {
            SQLiteDatabase db = getWritableDatabase();
            db.beginTransaction();
            try {
                for (int i = 0; i < apps.length(); i++) {
                    JSONObject app = apps.optJSONObject(i);
                    if (app == null) continue;
                    int id = app.optInt("appid");
                    String name = app.optString("name", "");
                    if (id <= 0 || name.isEmpty()) continue;
                    ContentValues values = new ContentValues();
                    values.put("appid", id);
                    values.put("name", name);
                    db.insertWithOnConflict("apps", null, values, SQLiteDatabase.CONFLICT_REPLACE);
                }
                db.setTransactionSuccessful();
            } finally { db.endTransaction(); }
        }
        List<Game> search(String query, int limit) {
            List<Game> result = new ArrayList<>();
            String pattern = "%" + query.replace("%", "\\%").replace("_", "\\_") + "%";
            try (Cursor c = getReadableDatabase().rawQuery(
                    "SELECT appid,name FROM apps WHERE name LIKE ? COLLATE NOCASE ORDER BY name LIMIT ?",
                    new String[]{pattern, String.valueOf(Math.max(1, limit))})) {
                while (c.moveToNext()) {
                    Game g = new Game();
                    g.appId = c.getInt(0);
                    g.name = c.getString(1);
                    g.headerImage = STORE + "/apps/" + g.appId + "/header.jpg";
                    result.add(g);
                }
            }
            return result;
        }
        int count() {
            try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM apps", null)) {
                return c.moveToFirst() ? c.getInt(0) : 0;
            }
        }
    }
}
