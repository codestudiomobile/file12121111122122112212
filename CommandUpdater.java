package com.codestudio.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CommandUpdater {
    private static final String VERSION_URL = "https://raw.githubusercontent.com/codestudiomobile/termux-commands/main/version.json";
    private static final String LOCAL_PREF_KEY = "updated_commands_json";
    private static final String COMMANDS_URL = "https://raw.githubusercontent.com/codestudiomobile/termux-commands/main/commands.json";

    public static void checkForUpdates(Context context) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            try {
                String remoteVersion = fetchRemoteVersion();
                String localVersion = getLocalVersion(context);

                if (!remoteVersion.equals(localVersion)) {
                    // Only download if version changed
                    String newCommands = fetchRemoteCommands();
                    saveToPrefs(context, newCommands);
                    saveVersion(context, remoteVersion);
                    Log.i("CommandUpdater", "Commands updated to version: " + remoteVersion);
                } else {
                    Log.i("CommandUpdater", "No update needed. Version unchanged.");
                }

            } catch (Exception e) {
                Log.e("CommandUpdater", "Update failed: " + e.getMessage());
            }
        });
    }

    private static String fetchRemoteVersion() throws IOException, JSONException {
        URL url = new URL(VERSION_URL);
        BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) builder.append(line);
        reader.close();
        JSONObject json = new JSONObject(builder.toString());
        return json.getString("version");
    }

    private static String fetchRemoteCommands() throws IOException {
        URL url = new URL(COMMANDS_URL);
        BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) builder.append(line);
        return builder.toString();
    }

    private static void saveToPrefs(Context context, String json) {
        SharedPreferences prefs = context.getSharedPreferences("CommandConfigPrefs", Context.MODE_PRIVATE);
        prefs.edit().putString(LOCAL_PREF_KEY, json).apply();
    }

    private static void saveVersion(Context context, String version) {
        SharedPreferences prefs = context.getSharedPreferences("CommandConfigPrefs", Context.MODE_PRIVATE);
        prefs.edit().putString("commands_version", version).apply();
    }

    private static String getLocalVersion(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("CommandConfigPrefs", Context.MODE_PRIVATE);
        return prefs.getString("commands_version", "0.0.0");
    }
}
