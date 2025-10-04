package com.codestudio.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

class ExecutionConfig {
    public final String installCommand;
    public final String template;

    public ExecutionConfig(String installCommand, String template) {
        this.installCommand = installCommand;
        this.template = template;
    }
}

public class CommandFetcher {

    private static final String TAG = "CommandFetcher";
    private static final String CONFIG_FILE_NAME = "commands.json";
    private static final String PREF_NAME = "CommandConfigPrefs";
    private static final String PREF_KEY_UPDATED_CONFIG = "updated_commands_json";
    private final Context context;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public CommandFetcher(Context context) {
        this.context = context.getApplicationContext();
    }

    public static String getFileTypeKey(String fileName) {
        String extension = "";
        int i = fileName.lastIndexOf('.');
        if (i > 0) {
            extension = fileName.substring(i + 1).toLowerCase();
        }

        switch (extension) {
            case "py":
                return "py";
            case "java":
                return "java";
            case "c":
                return "c";
            case "cpp":
            case "cxx":
            case "cc":
                return "cpp";
            case "sh":
            case "bash":
                return "terminal";
            default:
                return "";
        }
    }

    public static String getCommand(Context context, Uri fileUri) {
        String fileName = FileUtils.getFileName(context, fileUri);
        if (fileName == null) return null;

        String extension = "";
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex != -1 && dotIndex < fileName.length() - 1) {
            extension = fileName.substring(dotIndex + 1).toLowerCase();
        }

        String key = mapExtensionToKey(extension);
        if (key == null) {
            Log.e(TAG, "Unsupported file extension: " + extension);
            return "echo 'Unsupported file type'";
        }

        SharedPreferences prefs = context.getSharedPreferences("CommandConfigPrefs", Context.MODE_PRIVATE);
        String json = prefs.getString("updated_commands_json", null);
        if (json == null) {
            Log.e(TAG, "commands.json not found in SharedPreferences");
            return "echo 'Command template missing'";
        }

        try {
            JSONObject commandMap = new JSONObject(json);
            if (!commandMap.has(key)) {
                Log.e(TAG, "Command key not found: " + key);
                return "echo 'Unsupported file type'";
            }

            JSONObject config = commandMap.getJSONObject(key);
            String template = config.getString("template");
            String installCommand = config.optString("install", "");
            String logFilePath = context.getFilesDir().getAbsolutePath() + "/output.txt";
            String completionMarker = "^1004$^";

            return String.format(template, installCommand, fileName, logFilePath, completionMarker);
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing commands.json", e);
            return "echo 'Error reading command template'";
        }
    }

    private static String mapExtensionToKey(String ext) {
        switch (ext) {
            case "py":
                return "py";
            case "java":
                return "java";
            case "c":
                return "c";
            case "cpp":
            case "cxx":
            case "cc":
                return "cpp";
            case "js":
                return "node";
            case "php":
                return "php";
            case "rb":
                return "ruby";
            case "go":
                return "go";
            case "rs":
                return "rust";
            case "kt":
                return "kotlin";
            case "cs":
                return "csharp";
            case "pl":
                return "perl";
            case "lua":
                return "lua";
            case "sh":
            case "bash":
                return "terminal";
            default:
                return null;
        }
    }
    /*private static String mapMimeToKey(String mimeType) {
        Log.d(TAG, "mapMimeToKey: " + mimeType);
        switch (mimeType) {
            case "text/x-python":
                return "py";
            case "text/x-java-source":
                return "java";
            case "text/x-c":
                return "c";
            case "text/x-cpp":
                return "cpp";
            case "application/javascript":
                return "node";
            case "text/x-php":
                return "php";
            case "text/x-ruby":
                return "ruby";
            case "text/x-go":
                return "go";
            case "text/x-kotlin":
                return "kotlin";
            case "text/x-shellscript":
                return "terminal";
            case "text/x-csharp":
                return "csharp";
            case "text/x-perl":
                return "perl";
            case "text/x-lua":
                return "lua";
            default:
                return null;
        }
    }*/

    private boolean isTemplateValid(String template) {
        if (template == null || template.isEmpty()) return false;

        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("%(\\d+)\\$s").matcher(template);
        Set<String> uniqueIndices = new HashSet<>();

        while (matcher.find()) {
            uniqueIndices.add(matcher.group(1));
        }

        int count = uniqueIndices.size();
        return count >= 1 && count <= 4;
    }

    private String fetchRemoteVersion() throws IOException, JSONException {
        URL url = new URL("https://raw.githubusercontent.com/codestudiomobile/termux-commands/main/version.json");
        BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) builder.append(line);
        reader.close();

        JSONObject json = new JSONObject(builder.toString());
        return json.getString("version");
    }

    private String fetchRemoteCommands() throws IOException {
        URL url = new URL("https://raw.githubusercontent.com/codestudiomobile/termux-commands/main/commands.json");
        BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) builder.append(line);
        reader.close();
        return builder.toString();
    }

    public void updateConfigFromRemoteIfNeeded() {
        executorService.submit(() -> {
            try {
                String remoteVersion = fetchRemoteVersion();
                SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                String localVersion = prefs.getString("commands_version", "0.0.0");

                if (!remoteVersion.equals(localVersion)) {
                    String newCommandsJson = fetchRemoteCommands();
                    prefs.edit().putString(PREF_KEY_UPDATED_CONFIG, newCommandsJson).putString("commands_version", remoteVersion).apply();
                    Log.i(TAG, "✅ Commands updated to version " + remoteVersion);
                } else {
                    Log.i(TAG, "🟢 Commands already up to date.");
                }
            } catch (Exception e) {
                Log.e(TAG, "❌ Failed to update command config: " + e.getMessage());
            }
        });
    }

    private String loadConfigurationJson() {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String updatedConfig = prefs.getString(PREF_KEY_UPDATED_CONFIG, null);

        if (updatedConfig != null) {
            Log.d(TAG, "Loaded updated config from SharedPreferences.");
            return updatedConfig;
        }

        try {
            InputStream is = context.getAssets().open(CONFIG_FILE_NAME);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            Log.d(TAG, "Loaded default config from assets.");
            return sb.toString();
        } catch (IOException e) {
            Log.e(TAG, "Could not load " + CONFIG_FILE_NAME + " from assets.", e);
        }

        return null;
    }

    public Future<ExecutionConfig> fetchConfig(final String fileTypeKey) {
        return executorService.submit(new Callable<ExecutionConfig>() {
            @Override
            public ExecutionConfig call() {
                try {
                    String configJson = loadConfigurationJson();
                    if (configJson == null) {
                        Log.e(TAG, "Configuration JSON is null. Cannot fetch template.");
                        return null;
                    }

                    JSONObject fullConfig = new JSONObject(configJson);
                    if (fullConfig.has(fileTypeKey)) {
                        JSONObject langConfig = fullConfig.getJSONObject(fileTypeKey);
                        String install = langConfig.getString("install");
                        String template = langConfig.getString("template");

                        Log.d(TAG, "Looking for fileTypeKey: " + fileTypeKey);
                        Log.d(TAG, "Template received: " + template);

                        if (isTemplateValid(template)) {
                            return new ExecutionConfig(install, template);
                        } else {
                            Log.e(TAG, "Invalid template format for file type: " + fileTypeKey);
                        }
                    } else {
                        Log.w(TAG, "No config found for file type: " + fileTypeKey);
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing configuration JSON.", e);
                }

                return null;
            }
        });
    }

    public void shutdown() {
        executorService.shutdownNow();
    }
}
