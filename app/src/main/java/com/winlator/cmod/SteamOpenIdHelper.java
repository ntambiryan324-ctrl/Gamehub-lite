package com.winlator.cmod;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Steam OpenID 2.0 login. Credentials and Steam Guard challenges are entered only on Steam's
 * own HTTPS page. GameHub Lite receives and verifies a signed SteamID assertion; it never sees
 * the user's password, email code, or authenticator code.
 */
public final class SteamOpenIdHelper {
    private static final String OP = "https://steamcommunity.com/openid/login";
    private static final String OP_ENDPOINT = "https://steamcommunity.com/openid/";
    private static final String CALLBACK = "https://ntambiryan324-ctrl.github.io/Gamehub-lite/steam-auth.html";
    private static final String REALM = "https://ntambiryan324-ctrl.github.io/Gamehub-lite/";
    private static final String PREFS = "steam_openid_state";
    private static final String PENDING = "pending_state";
    private static final String LAST_NONCE = "last_response_nonce";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onComplete(String steamId, String error);
    }

    private SteamOpenIdHelper() {}

    public static void launchLogin(Context context) throws Exception {
        byte[] random = new byte[24];
        new SecureRandom().nextBytes(random);
        String state = android.util.Base64.encodeToString(random,
                android.util.Base64.URL_SAFE | android.util.Base64.NO_WRAP | android.util.Base64.NO_PADDING);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(PENDING, state).apply();

        String returnTo = CALLBACK + "?state=" + enc(state);
        Uri.Builder builder = Uri.parse(OP).buildUpon()
                .appendQueryParameter("openid.ns", "http://specs.openid.net/auth/2.0")
                .appendQueryParameter("openid.mode", "checkid_setup")
                .appendQueryParameter("openid.return_to", returnTo)
                .appendQueryParameter("openid.realm", REALM)
                .appendQueryParameter("openid.identity", "http://specs.openid.net/auth/2.0/identifier_select")
                .appendQueryParameter("openid.claimed_id", "http://specs.openid.net/auth/2.0/identifier_select");

        Intent intent = new Intent(Intent.ACTION_VIEW, builder.build());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public static boolean isCallback(Intent intent) {
        if (intent == null || intent.getData() == null) return false;
        Uri uri = intent.getData();
        return "gamehublite".equals(uri.getScheme())
                && "steam-auth".equals(uri.getHost())
                && "callback".equals(uri.getLastPathSegment());
    }

    public static void handleCallback(Context context, Intent intent, Callback callback) {
        if (!isCallback(intent)) return;
        Uri uri = intent.getData();
        if (uri == null) {
            callback.onComplete(null, "Steam sign-in returned an invalid callback.");
            return;
        }
        String suppliedState = uri.getQueryParameter("state");
        String pending = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(PENDING, "");
        if (pending.isEmpty() || suppliedState == null || !constantTimeEquals(pending, suppliedState)) {
            callback.onComplete(null, "Steam sign-in expired or its security state did not match. Start again.");
            return;
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(PENDING).apply();

        IO.execute(() -> {
            String steamId = null;
            String error = null;
            try {
                if (!"id_res".equals(uri.getQueryParameter("openid.mode"))) {
                    throw new Exception("Steam sign-in was cancelled or rejected.");
                }
                String ns = uri.getQueryParameter("openid.ns");
                String endpoint = uri.getQueryParameter("openid.op_endpoint");
                String claimed = uri.getQueryParameter("openid.claimed_id");
                String identity = uri.getQueryParameter("openid.identity");
                String returnTo = uri.getQueryParameter("openid.return_to");
                String nonce = uri.getQueryParameter("openid.response_nonce");
                String signed = uri.getQueryParameter("openid.signed");

                if (!"http://specs.openid.net/auth/2.0".equals(ns)) throw new Exception("Unexpected Steam OpenID version.");
                if (endpoint == null || !endpoint.equals(OP_ENDPOINT)) throw new Exception("Steam identity endpoint did not match.");
                if (claimed == null || identity == null || !claimed.equals(identity)) throw new Exception("Steam identity claim did not match.");
                if (returnTo == null || !returnTo.equals(CALLBACK + "?state=" + enc(pending))) throw new Exception("Steam callback URL did not match this sign-in.");
                if (signed == null || !signedContains(signed, "claimed_id") || !signedContains(signed, "identity")
                        || !signedContains(signed, "return_to") || !signedContains(signed, "response_nonce")
                        || !signedContains(signed, "op_endpoint")) {
                    throw new Exception("Steam response is missing required signed fields.");
                }
                steamId = extractSteamId(claimed);
                if (steamId == null) throw new Exception("Steam returned an invalid account identifier.");
                validateNonce(nonce);
                String previousNonce = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(LAST_NONCE, "");
                if (nonce.equals(previousNonce)) throw new Exception("This Steam sign-in response was already used.");

                String response = verifyWithSteam(uri);
                if (!response.contains("is_valid:true")) throw new Exception("Steam could not verify the sign-in response.");
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(LAST_NONCE, nonce).apply();
            } catch (Exception e) {
                steamId = null;
                error = e.getMessage() == null ? "Steam sign-in failed." : e.getMessage();
            }
            final String resultId = steamId;
            final String resultError = error;
            MAIN.post(() -> callback.onComplete(resultId, resultError));
        });
    }

    private static String verifyWithSteam(Uri uri) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(OP).openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            connection.setRequestProperty("Accept", "text/plain");
            StringBuilder body = new StringBuilder();
            for (String key : uri.getQueryParameterNames()) {
                if (!key.startsWith("openid.") || "openid.mode".equals(key)) continue;
                if (body.length() > 0) body.append('&');
                body.append(enc(key)).append('=').append(enc(uri.getQueryParameter(key)));
            }
            if (body.length() > 0) body.append('&');
            body.append("openid.mode=check_authentication");
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream out = connection.getOutputStream()) { out.write(bytes); }
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
            if (stream == null) throw new Exception("Steam verification endpoint returned HTTP " + status + ".");
            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) response.append(line).append('\n');
            }
            if (status < 200 || status >= 300) throw new Exception("Steam verification failed (HTTP " + status + ").");
            return response.toString();
        } finally {
            connection.disconnect();
        }
    }

    private static void validateNonce(String nonce) throws Exception {
        if (nonce == null || nonce.length() < 20) throw new Exception("Steam response nonce is missing.");
        String timestamp = nonce.substring(0, 20);
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date issued = format.parse(timestamp);
        if (issued == null) throw new Exception("Steam response nonce has an invalid timestamp.");
        long age = System.currentTimeMillis() - issued.getTime();
        if (age < -5 * 60 * 1000L || age > 10 * 60 * 1000L) {
            throw new Exception("Steam sign-in response expired. Start the sign-in again.");
        }
    }

    private static String extractSteamId(String claimedId) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("^https?://steamcommunity\\.com/openid/id/([0-9]{17})/?$")
                .matcher(claimedId);
        return matcher.matches() ? matcher.group(1) : null;
    }

    private static boolean signedContains(String signed, String field) {
        for (String value : signed.split(",")) if (field.equals(value.trim())) return true;
        return false;
    }

    private static String enc(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8");
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        return java.security.MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
