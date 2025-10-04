package com.codestudio.mobile;

import android.app.Dialog;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.MimeTypeMap;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.DialogFragment;

import java.util.ArrayList;

public class CreateFileDialog extends DialogFragment {

    private static final String ARG_FOLDER_URIS = "folder_uris";
    private static final String ARG_FOLDER_NAMES = "folder_names";
    private static final String ARG_FILE_CONTENT = "file_content"; // New argument for 'Save As'

    private OnFileCreatedListener listener;

    private ArrayList<Uri> folderUris;
    private ArrayList<String> folderNames;
    private byte[] fileContent; // Holds content if in 'Save As' mode

    /**
     * Factory method for creating a NEW file (standard mode).
     */
    public static CreateFileDialog newInstance(ArrayList<Uri> folderUris, ArrayList<String> folderNames) {
        return newInstance(folderUris, folderNames, null);
    }

    /**
     * Factory method for 'SAVE AS' operation (passes content).
     */
    public static CreateFileDialog newInstance(ArrayList<Uri> folderUris, ArrayList<String> folderNames, @Nullable byte[] content) {
        CreateFileDialog fragment = new CreateFileDialog();
        Bundle args = new Bundle();
        args.putParcelableArrayList(ARG_FOLDER_URIS, folderUris != null ? folderUris : new ArrayList<>());
        args.putStringArrayList(ARG_FOLDER_NAMES, folderNames != null ? folderNames : new ArrayList<>());

        if (content != null) {
            args.putByteArray(ARG_FILE_CONTENT, content);
        }

        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        try {
            listener = (OnFileCreatedListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context + " must implement OnFileCreatedListener");
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            folderUris = getArguments().getParcelableArrayList(ARG_FOLDER_URIS);
            folderNames = getArguments().getStringArrayList(ARG_FOLDER_NAMES);
            fileContent = getArguments().getByteArray(ARG_FILE_CONTENT); // Load content
        }
        if (folderUris == null) folderUris = new ArrayList<>();
        if (folderNames == null) folderNames = new ArrayList<>();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_create_file, container, false);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        return dialog;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView titleTextView = view.findViewById(R.id.dialogTitle); // Assuming you add this ID to your TextView
        EditText fileNameEditText = view.findViewById(R.id.fileName);
        Spinner locationSpinner = view.findViewById(R.id.locationSpinner);
        Button createButton = view.findViewById(R.id.create);
        Button cancelButton = view.findViewById(R.id.cancel);

        // Adjust title and button text based on mode (IMPROVEMENT)
        boolean isSaveAsMode = fileContent != null;
        if (titleTextView != null) {
            titleTextView.setText(isSaveAsMode ? "Save Untitled File" : "Create New File");
        }
        createButton.setText(isSaveAsMode ? "Save" : "Create");

        if (!folderNames.isEmpty()) {
            ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, folderNames);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            locationSpinner.setAdapter(adapter);
        } else {
            Toast.makeText(requireContext(), "No accessible folders available.", Toast.LENGTH_LONG).show();
            createButton.setEnabled(false);
        }

        cancelButton.setOnClickListener(v -> dismiss());

        createButton.setOnClickListener(v -> {
            String fileName = fileNameEditText.getText().toString().trim();

            if (fileName.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter a file name", Toast.LENGTH_SHORT).show();
                return;
            }

            // --- File Name and MIME Type Validation (Same as previous correction) ---
            String extension = "";
            int dotIndex = fileName.lastIndexOf('.');
            if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
                extension = fileName.substring(dotIndex + 1).toLowerCase();
            } else if (dotIndex == -1) {
                // If no extension, append default
                fileName += ".txt";
                extension = "txt";
            }

            String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);

            if (mimeType == null) {
                String[] allowedExtensions = {"txt", "java", "xml", "html", "css", "js", "json", "md", "py", "c", "cpp", "kt"};
                boolean validExtension = false;
                for (String ext : allowedExtensions) {
                    if (extension.equals(ext)) {
                        validExtension = true;
                        mimeType = "text/plain";
                        break;
                    }
                }
                if (!validExtension) {
                    Toast.makeText(requireContext(), "Unsupported file type", Toast.LENGTH_LONG).show();
                    return;
                }
            } else if (!mimeType.startsWith("text/") && !mimeType.equals("application/json") && !mimeType.equals("application/xml")) {
                Toast.makeText(requireContext(), "Unsupported MIME type: " + mimeType, Toast.LENGTH_LONG).show();
                return;
            }
            // --- End of Validation ---

            int selectedPosition = locationSpinner.getSelectedItemPosition();
            if (selectedPosition >= 0 && selectedPosition < folderUris.size()) {
                Uri folderUri = folderUris.get(selectedPosition);
                DocumentFile folder = DocumentFile.fromTreeUri(requireContext(), folderUri);

                if (folder != null && folder.canWrite()) {
                    if (folder.findFile(fileName) != null) {
                        Toast.makeText(requireContext(), "File already exists in this folder", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // 1. Create the new file DocumentFile
                    DocumentFile newFile = folder.createFile(mimeType, fileName);

                    if (newFile != null) {
                        // 2. Notify the listener, passing the content if in Save As mode
                        listener.onFileCreated(fileName, newFile.getUri(), fileContent);
                        dismiss();
                    } else {
                        Toast.makeText(requireContext(), "Failed to create file: Check permissions or filename validity.", Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(requireContext(), "Cannot write to the selected location. Permission error.", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    /**
     * Interface updated to handle both creation (content == null)
     * and Save As (content != null) modes.
     */
    public interface OnFileCreatedListener {
        void onFileCreated(String fileName, Uri fileUri, @Nullable byte[] fileContent);

        void requestSaveAs(byte[] content);
    }
}
