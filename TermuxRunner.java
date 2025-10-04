package com.codestudio.mobile;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

// This class handles all logic for controlling Termux via Intents
public class TermuxRunner {

    private static final String TERMUX_PACKAGE_NAME = "com.termux";

    // ✅ Constants for the new, successful execution method (RunCommand Intent)
    private static final String RUN_COMMAND_ACTION_NEW = "com.termux.app.RunCommand";
    private static final String EXTRA_COMMAND = "com.termux.app.RunCommand.COMMAND";
    private static final String EXTRA_EXECUTE_IN_NEW_SESSION = "com.termux.app.RunCommand.EXECUTE_IN_NEW_SESSION";
    private static final String EXTRA_SESSION_NAME_NEW = "com.termux.app.RunCommand.NEW_SESSION_NAME";

    private final Context context;
    private BufferedWriter stdinWriter;

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

    public TerminalFragment createBlankTerminal() {
        Uri terminalUri = new Uri.Builder().scheme("run").path("Terminal-" + System.currentTimeMillis()).build();

        return TerminalFragment.newInstance(terminalUri);
    }

    public void launchTerminal(TerminalFragment.ConsoleInputListener listener) {
        if (listener != null) {
            listener.onOutputReceived("CODE STUDIO\nType a command below or run a file to begin.\n");
        }
    }

    public void sendInput(String input) {
        new Thread(() -> {
            try {
                if (stdinWriter != null) {
                    stdinWriter.write(input + "\n");
                    stdinWriter.flush();
                }
            } catch (Exception e) {
                Log.e("TermuxRunner", "Failed to send input", e);
            }
        }).start();
    }

    /**
     * Sends a shell command to the internal execution engine.
     * This replaces Termux's RunCommand intent.
     */
    public void executeCommandInternally(String command, TerminalFragment.ConsoleInputListener listener) {
        new Thread(() -> {
            try {
                Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});

                // Send "y" to stdin
                stdinWriter = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));

                BufferedReader stdout = new BufferedReader(new InputStreamReader(process.getInputStream()));
                BufferedReader stderr = new BufferedReader(new InputStreamReader(process.getErrorStream()));

                String line;
                while ((line = stdout.readLine()) != null) {
                    String finalLine = line;
                    ((Activity) context).runOnUiThread(() -> {
                        if (listener != null) listener.onOutputReceived(finalLine + "\n");
                    });
                }

                while ((line = stderr.readLine()) != null) {
                    String finalLine = line;
                    ((Activity) context).runOnUiThread(() -> {
                        if (listener != null) listener.onOutputReceived("❌ " + finalLine + "\n");
                    });
                }

                stdout.close();
                stderr.close();
                stdinWriter.close();
                process.waitFor();

                ((Activity) context).runOnUiThread(() -> {
                    if (listener != null) listener.onExecutionComplete();
                });

            } catch (Exception e) {
                Log.e("TermuxRunner", "Execution error", e);
                ((Activity) context).runOnUiThread(() -> {
                    if (listener != null)
                        listener.onOutputReceived("❌ Error: " + e.getMessage() + "\n");
                });
            }
        }).start();
    }    // All methods related to Tmux sessions (kill, sendInput) and the old executeTermuxCommand are removed.
}
