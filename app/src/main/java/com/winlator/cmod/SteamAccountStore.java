package com.winlator.cmod;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.ByteBuffer;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Keeps the user's own Steam Web API key encrypted with an Android Keystore AES key. */
public final class SteamAccountStore {
    private static final String PREFS = "gamehub_steam_account";
    private static final String KEY_ALIAS = "gamehub_lite_steam_api_key_v1";
    private static final String API_KEY = "encrypted_api_key";
    private static final String STEAM_ID = "steam_id";

    private SteamAccountStore() {}

    public static void saveSteamId(Context context, String steamId) {
        if (steamId == null || !steamId.matches("[0-9]{17}")) throw new IllegalArgumentException("Steam returned an invalid SteamID64.");
        prefs(context).edit().putString(STEAM_ID, steamId.trim()).apply();
    }

    public static boolean hasApiKey(Context context) {
        return !prefs(context).getString(API_KEY, "").isEmpty();
    }

    public static void saveApiKey(Context context, String apiKey) throws Exception {
        if (apiKey == null || apiKey.trim().length() < 20) throw new IllegalArgumentException("Enter a valid Steam Web API key.");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        byte[] encrypted = cipher.doFinal(apiKey.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ByteBuffer payload = ByteBuffer.allocate(4 + cipher.getIV().length + encrypted.length);
        payload.putInt(cipher.getIV().length);
        payload.put(cipher.getIV());
        payload.put(encrypted);
        String encoded = Base64.encodeToString(payload.array(), Base64.NO_WRAP);
        prefs(context).edit().putString(API_KEY, encoded).apply();
    }

    public static void save(Context context, String steamId, String apiKey) throws Exception {
        saveSteamId(context, steamId);
        saveApiKey(context, apiKey);
    }

    public static String getSteamId(Context context) {
        return prefs(context).getString(STEAM_ID, "");
    }

    public static String getApiKey(Context context) throws Exception {
        String encoded = prefs(context).getString(API_KEY, "");
        if (encoded.isEmpty()) return "";
        byte[] payload = Base64.decode(encoded, Base64.NO_WRAP);
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        int ivLength = buffer.getInt();
        if (ivLength < 12 || ivLength > 16 || payload.length <= 4 + ivLength) throw new IllegalStateException("Stored Steam key is damaged.");
        byte[] iv = new byte[ivLength];
        buffer.get(iv);
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(ciphertext), java.nio.charset.StandardCharsets.UTF_8);
    }

    public static boolean isConnected(Context context) {
        return !getSteamId(context).isEmpty();
    }

    public static void clear(Context context) {
        prefs(context).edit().remove(API_KEY).remove(STEAM_ID).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }
}
