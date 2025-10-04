package com.codestudio.mobile;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;


public class WelcomeFragment extends Fragment {

    private static final Uri ARG_URI = Uri.parse("app://com.diploma.project/welcome");

    public static WelcomeFragment newInstance() {
        return new WelcomeFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_welcome, container, false);
        TextView openFolder = view.findViewById(R.id.openFolderText);
        TextView openFiles = view.findViewById(R.id.openFilesText);
        TextView manageLanguages = view.findViewById(R.id.manageLanguagesSettings);
        TextView openEditor = view.findViewById(R.id.openEditorSettings);
        TextView openSettings = view.findViewById(R.id.openSettings);
        TextView openFileFromInternalStorage = view.findViewById(R.id.openFileFromInternalStorageText);
        openFolder.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openDirectory();
            }
        });
        openFiles.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openLeftNavigation();
            }
        });
        manageLanguages.setOnClickListener(v -> {
            Intent intent = new Intent(view.getContext(), ManageLanguagesActivity.class);
            startActivity(intent);
        });
        openEditor.setOnClickListener(v -> {
            Intent intent = new Intent(view.getContext(), EditorActivity.class);
            startActivity(intent);
        });
        openSettings.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openSettings();
            }
        });
        openFileFromInternalStorage.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openFilePicker();
            }
        });
        return view;
    }
}
