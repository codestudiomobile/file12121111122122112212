package com.codestudio.mobile;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;


public class CodeExecutionManager {

    private static final String TAG = "CodeExecManager";
    private final Context context;
    //    private final TermuxRunner termuxRunner;
    private final CommandFetcher commandFetcher;
    private final ExecutionListener listener;
    private final ExecutorService executorService;
    private final Handler uiHandler;
    private final File termuxCommDir;

    public CodeExecutionManager(Context context, ExecutionListener listener) {
        this.listener = listener;
        this.context = context;
//        this.termuxRunner = new TermuxRunner(context);
        this.commandFetcher = new CommandFetcher(context);
        this.executorService = Executors.newSingleThreadExecutor();
        this.uiHandler = new Handler(Looper.getMainLooper());
        this.termuxCommDir = new File(context.getFilesDir(), "termux_exec_temp");
        if (!this.termuxCommDir.exists()) {
            this.termuxCommDir.mkdirs();
        }
    }

    /**
     * Executes code in a new Termux session using a directly accessible file path.
     *
     * @param termuxFilePath The absolute, Termux-accessible file path (e.g., /sdcard/...).
     * @param mimeType       The file's MIME type.
     * @param fileName       The file's name.
     */
    public void runCodeInNewTermuxSession(final String termuxFilePath, final String mimeType, final String fileName) {
        executorService.submit(() -> {
            try {
                // 1. Get the Execution Command Configuration
                String fileTypeKey = getFileTypeKeyFromMimeType(mimeType);

                Future<ExecutionConfig> configFuture = commandFetcher.fetchConfig(fileTypeKey);
                ExecutionConfig config = configFuture.get();
                if (config == null || config.template == null || config.template.isEmpty()) {
                    listener.onExecutionError("No valid execution config found for file type: " + fileTypeKey);
                    return;
                }
                int argCount = countFormatSpecifiers(config.template);
                String formattedCommand;
                File outputFile = new File(termuxCommDir, fileName + ".log");
                String outputFilePath = outputFile.getAbsolutePath();
                Log.d(TAG, "Template received: " + config.template);
                switch (argCount) {
                    case 1:
                        formattedCommand = String.format(config.template, config.installCommand);
                        break;
                    case 2:
                        formattedCommand = String.format(config.template, config.installCommand, termuxFilePath);
                        break;
                    case 3:
                        formattedCommand = String.format(config.template, config.installCommand, termuxFilePath, outputFilePath);
                        break;
                    case 4:
                        formattedCommand = String.format(config.template, config.installCommand, termuxFilePath, outputFilePath, "Execution Finished");
                        break;
                    default:
                        listener.onExecutionError("Unsupported format specifier count in template.");
                        return;
                }
                uiHandler.post(() -> listener.onExecutionStarted(formattedCommand, fileName));
                Log.d(TAG, "runCodeInNewTermuxSession called with: " + termuxFilePath + ", " + mimeType + ", " + fileName);
                sendInput(formattedCommand);
                Log.d(TAG, "runCodeInNewTermuxSession called with: " + termuxFilePath + ", " + mimeType + ", " + fileName);
            } catch (InterruptedException | ExecutionException e) {
                Log.e(TAG, "Command Fetching Failed: " + e.getMessage());
                uiHandler.post(() -> listener.onExecutionError("Failed to prepare command: " + e.getMessage()));
            } catch (Exception e) {
                Log.e(TAG, "General Execution Failed: " + e.getMessage());
                uiHandler.post(() -> listener.onExecutionError("Failed to launch code: " + e.getMessage()));
            }
        });
    }

    private int countFormatSpecifiers(String template) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("%(\\d+)\\$s").matcher(template);
        java.util.Set<Integer> uniqueIndices = new java.util.HashSet<>();

        while (matcher.find()) {
            uniqueIndices.add(Integer.parseInt(matcher.group(1)));
        }

        return uniqueIndices.size();
    }

    private String getFileTypeKeyFromMimeType(String mimeType) {
        if (mimeType == null) return "";

        mimeType = mimeType.toLowerCase();

        if (mimeType.contains("python")) return "py";
        if (mimeType.contains("java") || mimeType.contains("x-java-source")) return "java";
        if (mimeType.contains("csrc") || mimeType.contains("text/x-c")) return "c";
        if (mimeType.contains("c++") || mimeType.contains("cpp") || mimeType.contains("x-c++src"))
            return "cpp";
        if (mimeType.contains("javascript") || mimeType.contains("node")) return "node";
        if (mimeType.contains("php")) return "php";
        if (mimeType.contains("ruby")) return "ruby";
        if (mimeType.contains("golang") || mimeType.contains("go")) return "go";
        if (mimeType.contains("rust")) return "rust";
        if (mimeType.contains("kotlin")) return "kotlin";
        if (mimeType.contains("shell") || mimeType.contains("bash") || mimeType.contains("sh"))
            return "terminal";
        if (mimeType.contains("vnc")) return "vnc";
        if (mimeType.contains("csharp") || mimeType.contains("x-csharp") || mimeType.contains("ms-csharp"))
            return "csharp";
        if (mimeType.contains("perl")) return "perl";
        if (mimeType.contains("lua")) return "lua";

        return "";
    }

    public void shutdown() {
        executorService.shutdownNow();
        commandFetcher.shutdown();
    }

    /**
     * Copies the content of a SAF Uri to a private, Termux-accessible file.
     *
     * @param fileUri  The SAF content Uri.
     * @param fileName The display name of the file.
     * @return The File object representing the newly created temporary file.
     */
    private File copyUriToPrivateFile(Uri fileUri, String fileName) throws IOException {
        // We clean up everything before creating a new one to prevent clutter
        cleanupTempDirectory();

        // Create the target file in the temp directory
        File tempFile = new File(termuxCommDir, fileName);

        try (InputStream inputStream = context.getContentResolver().openInputStream(fileUri); OutputStream outputStream = new FileOutputStream(tempFile)) {

            if (inputStream == null) {
                throw new IOException("Failed to open input stream for URI: " + fileUri);
            }

            byte[] buffer = new byte[4096];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }

            return tempFile;
        }
    }

    /**
     * Deletes all files in the temporary execution directory.
     */
    public void cleanupTempDirectory() {
        if (termuxCommDir.exists()) {
            File[] files = termuxCommDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    file.delete();
                }
            }
        }
    }

    public void sendInput(String input) {
        ((Activity) context).runOnUiThread(() ->
        {
            executorService.submit(() -> {
                try {
                    Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", input});
                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String finalLine = line;
                        uiHandler.post(() -> listener.onOutputReceived(finalLine + "\n"));
                    }
                    reader.close();
                    uiHandler.post(listener::onExecutionComplete);
                } catch (Exception e) {
                    Log.e(TAG, "sendInput failed: " + e.getMessage());
                    uiHandler.post(() -> listener.onExecutionError("Command failed: " + e.getMessage()));
                }
            });
        });
    }

    /**
     * Simplified interface, only error reporting is practical for external Termux launch.
     */
    public interface ExecutionListener {
        void onExecutionError(String message);

        void onExecutionStarted(String command, String fileName);

        void onOutputReceived(String output);

        void onExecutionComplete();
    }// Inside CodeExecutionManager.java
}
