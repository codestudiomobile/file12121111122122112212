package com.codestudio.mobile;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class SettingsActivity extends AppCompatActivity implements AdapterView.OnItemClickListener {
    String[] itemsList = {"Manage libraries", "Editor", "About"};
    ArrayAdapter<String> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        ListView settingsList = findViewById(R.id.settingsList);
        adapter = new ArrayAdapter<>(getApplicationContext(), android.R.layout.simple_list_item_1, itemsList);
        settingsList.setAdapter(adapter);
        settingsList.setOnItemClickListener(SettingsActivity.this);
    }


    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        String selectedItem = (String) parent.getItemAtPosition(position);
        Intent intent;
        if (selectedItem.equals("Manage libraries")) {
            intent = new Intent(getApplicationContext(), ManageLanguagesActivity.class);
            startActivity(intent);
        } else if (selectedItem.equals("Editor")) {
            intent = new Intent(getApplicationContext(), EditorActivity.class);
            startActivity(intent);
        } else if (selectedItem.equals("About")) {
            intent = new Intent(getApplicationContext(), AboutActivity.class);
            startActivity(intent);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        finish();
        return super.onOptionsItemSelected(item);
    }
}
