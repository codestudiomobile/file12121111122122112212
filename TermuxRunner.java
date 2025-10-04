package com.codestudio.mobile;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;

// This class handles all logic for controlling Termux via Intents
public class TermuxRunner {

    private static final String TERMUX_PACKAGE_NAME = "com.termux";

    // ✅ Constants for the new, successful execution method (RunCommand Intent)
    private static final String RUN_COMMAND_ACTION_NEW = "com.termux.app.RunCommand";
    private static final String EXTRA_COMMAND = "com.termux.app.RunCommand.COMMAND";
    private static final String EXTRA_EXECUTE_IN_NEW_SESSION = "com.termux.app.RunCommand.EXECUTE_IN_NEW_SESSION";
    private static final String EXTRA_SESSION_NAME_NEW = "com.termux.app.RunCommand.NEW_SESSION_NAME";

    private final Context context;

    public TermuxRunner(Context context) {
        this.context = context;
    }

    /**
     * Executes a command in a new interactive Termux session (window).
     * This is the recommended, official way for foreground execution.
     *
     * @param command     The shell command to execute.
     * @param sessionName A descriptive name for the new session.
     */
    public void executeCommandInNewSession(String command, String sessionName) {
        try {
            Intent intent = new Intent(RUN_COMMAND_ACTION_NEW);

            // Set the package and the main activity that handles the RunCommand Intent
            intent.setClassName(TERMUX_PACKAGE_NAME, TERMUX_PACKAGE_NAME + ".app.TermuxActivity");

            intent.putExtra(EXTRA_COMMAND, command);
            intent.putExtra(EXTRA_EXECUTE_IN_NEW_SESSION, true); // Opens a new session
            intent.putExtra(EXTRA_SESSION_NAME_NEW, sessionName);

            context.startActivity(intent);

        } catch (Exception e) {
            Log.e("TermuxRunner", "Error sending RunCommand Intent: " + e.getMessage());
            Toast.makeText(context, "Failed to run command. Termux may not be installed or configured.", Toast.LENGTH_LONG).show();
        }
    }

    public boolean isTermuxInstalled() {
        try {
            return context.getPackageManager().getLaunchIntentForPackage(TERMUX_PACKAGE_NAME) != null;
        } catch (Exception e) {
            return false;
        }
    }

    public void launchTerminal(TerminalFragment.ConsoleInputListener listener) {
        if (listener != null) {
            listener.onOutputReceived("CODE STUDIO\nType a command below or run a file to begin.\n");
        }
    }

    /**
     * Sends a shell command to the internal execution engine.
     * This replaces Termux's RunCommand intent.
     */
    public void executeCommandInternally(String command, TerminalFragment.ConsoleInputListener listener) {
        ((Activity) context).runOnUiThread(() -> {
            try {
                Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});

                BufferedReader stdout = new BufferedReader(new InputStreamReader(process.getInputStream()));
                BufferedReader stderr = new BufferedReader(new InputStreamReader(process.getErrorStream()));

                String line;
                while ((line = stdout.readLine()) != null) {
                    if (listener != null) listener.onOutputReceived(line + "\n");
                }
                while ((line = stderr.readLine()) != null) {
                    if (listener != null) listener.onOutputReceived("❌ " + line + "\n");
                }

                stdout.close();
                stderr.close();
                process.waitFor();

                if (listener != null) listener.onExecutionComplete();

            } catch (Exception e) {
                Log.e("TermuxRunner", "Execution error", e);
                if (listener != null)
                    listener.onOutputReceived("❌ Error: " + e.getMessage() + "\n");
            }
        });
    }
    // (You can keep isTermuxInstalled() here if needed elsewhere)
    // All methods related to Tmux sessions (kill, sendInput) and the old executeTermuxCommand are removed.
}
