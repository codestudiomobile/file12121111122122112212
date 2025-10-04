package com.codestudio.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;

public class EditorActivity extends AppCompatActivity {
    public static final String PREFS_NAME = "AppPreferences";
    public static final String KEY_EDITOR_STARTUP = "openEditorOnStartup";
    public static final String KEY_WELCOME_STARTUP = "openWelcomeScreenOnStartup";

    private SwitchCompat openEditorOnStartup;
    private SwitchCompat openWelcomeScreenOnStartup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        openEditorOnStartup = findViewById(R.id.openEditorOnStartup);
        openWelcomeScreenOnStartup = findViewById(R.id.openWelcomeScreenOnStartup);
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean editorStartup = prefs.getBoolean(KEY_EDITOR_STARTUP, false);
        boolean welcomeStartup = prefs.getBoolean(KEY_WELCOME_STARTUP, true);

        openEditorOnStartup.setChecked(editorStartup);
        openWelcomeScreenOnStartup.setChecked(welcomeStartup);

        // Save preferences when toggled
        openEditorOnStartup.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferences.Editor editor = prefs.edit();
            if (!isChecked && !openWelcomeScreenOnStartup.isChecked()) {
                editor.putBoolean(KEY_WELCOME_STARTUP, true);
                openWelcomeScreenOnStartup.setChecked(true);
            } else {
                editor.putBoolean(KEY_EDITOR_STARTUP, isChecked);
            }
            editor.apply();
        });
        openWelcomeScreenOnStartup.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferences.Editor editor = prefs.edit();
            if (!isChecked && !openEditorOnStartup.isChecked()) {
                editor.putBoolean(KEY_EDITOR_STARTUP, false);
                editor.putBoolean(KEY_WELCOME_STARTUP, true);
                openWelcomeScreenOnStartup.setChecked(true);
            } else {
                editor.putBoolean(KEY_WELCOME_STARTUP, isChecked);
            }
            editor.apply();
        });
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        finish();
        return super.onOptionsItemSelected(item);
    }
}
