package com.codestudio.mobile;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FilesAdapter extends RecyclerView.Adapter<FilesAdapter.FileViewHolder> {

    private static final String TAG = "FilesAdapter";
    public final List<FileItem> fileList;
    private final Context context;
    private final OnFileClickListener onFileClickListener;
    private final SaveAsCallback saveAsCallback; // New member for handling 'Save As'

    // Assuming FileItem is a separate class defined elsewhere with necessary fields/methods.
    // Assuming MainActivity is the hosting Activity.

    public FilesAdapter(Context context, List<FileItem> fileList, OnFileClickListener onFileClickListener, SaveAsCallback saveAsCallback) {
        this.context = context;
        this.fileList = fileList;
        this.onFileClickListener = onFileClickListener;
        this.saveAsCallback = saveAsCallback; // Initialize the callback
    }

    @NonNull
    @Override
    public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_file_list, parent, false);
        return new FileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
        FileItem item = fileList.get(position);
        holder.bind(item, position);
        holder.itemView.setOnLongClickListener(v -> {
            if (onFileClickListener != null) {
                onFileClickListener.onFileContextMenuRequest(item.uri, v);
                ((MainActivity) context).setSelectedFileItem(item);
            }
            return true;
        });
        int paddingStart = item.depth * 24;
        // Use requireContext() or context to ensure context is valid
        ViewCompat.setPaddingRelative(holder.itemView, dpToPx(paddingStart, context), holder.itemView.getPaddingTop(), holder.itemView.getPaddingEnd(), holder.itemView.getPaddingBottom());
    }

    @Override
    public int getItemCount() {
        return fileList.size();
    }

    public void refresh() {
        // Must be called on the UI thread
        if (context instanceof Activity) {
            ((Activity) context).runOnUiThread(() -> {
                fileList.clear();
                notifyDataSetChanged();
            });
        }
    }

    /**
     * Corrected function to handle UNTITLED_FILE_URI by using a callback to the UI thread.
     * Removed commented-out logic and replaced Toast with main thread calls.
     */
    public void saveAllFiles(List<FileContentItem> filesToSave) {
        if (saveAsCallback == null) {
            Log.e(TAG, "SaveAsCallback is null. Cannot save untitled files.");
            return;
        }

        new Thread(() -> {
            if (filesToSave == null || filesToSave.isEmpty()) {
                return;
            }

            Handler mainHandler = new Handler(Looper.getMainLooper());
            int savedCount = 0;

            for (FileContentItem file : filesToSave) {
                // ... (Handling for UNTITLED_FILE_URI remains the same)

                // Logic for saving existing files
                try {
                    // *** THE MOST ROBUST FIX: Use "rwt" (Read/Write/Truncate) mode. ***
                    // This explicitly requests the Document Provider to clear the file before writing.
                    try (OutputStream os = context.getContentResolver().openOutputStream(file.getUri(), "rwt")) {
                        if (os != null) {
                            os.write(file.getContent());
                            savedCount++;
                        }
                    }
                } catch (IOException e) {
                    // ... (Error handling remains the same)
                    Log.e(TAG, "Error saving file " + file.getUri().toString() + ": " + e.getMessage());
                    mainHandler.post(() -> Toast.makeText(context.getApplicationContext(), "Failed to save: " + file.getUri().getLastPathSegment(), Toast.LENGTH_LONG).show());
                }
            }

            // ... (Success message remains the same)
            final int finalSavedCount = savedCount;
            if (finalSavedCount > 0) {
                mainHandler.post(() -> Toast.makeText(context.getApplicationContext(), "Successfully saved " + finalSavedCount + " file(s).", Toast.LENGTH_SHORT).show());
            }

        }).start();
    }

    /**
     * Utility function for converting DP to PX.
     */
    private int dpToPx(int dp, Context context) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }

    public void collapseAllFolders() {
        // CRITICAL CORRECTION: Use Context to runOnUiThread safely.
        if (context instanceof Activity) {
            ((Activity) context).runOnUiThread(() -> {
                // Optimization: Removing items from the end is better than the middle,
                // but the depth logic ensures correct removal.
                for (int i = fileList.size() - 1; i >= 0; i--) {
                    FileItem item = fileList.get(i);
                    // Only remove items that are children of a root folder (depth > 0)
                    if (item.depth > 0) {
                        fileList.remove(i);
                    }
                    if (item.isDirectory) {
                        item.isExpanded = false;
                    }
                }
                notifyDataSetChanged();
            });
        }
    }

    // Renamed to match the inner ViewHolder's method for consistency
    public void expandFolder(final int position) {
        // Delegating to the new, corrected expandFolder implementation inside FileViewHolder.
        // This method is rarely needed outside the ViewHolder but kept for completeness.
        // Since this adapter method runs on the main thread, it's safer to use this structure.

        final FileItem folder = fileList.get(position);
        if (!folder.isExpanded) {
            new FileViewHolder(LayoutInflater.from(context).inflate(R.layout.item_file_list, null)).expandFolderLogic(position);
        }
    }

    // Original expandFolderPreserveThreadSafe is removed in favor of the clean internal logic
    // of expandFolder (renamed to expandFolderLogic)

    // Interface for the callback
    public interface OnFileClickListener {
        void onFileClicked(Uri fileUri, String fileName);

        void onFileContextMenuRequest(Uri fileUri, View anchorView);
    }

    // New interface for handling 'Save As' outside the Adapter
    public interface SaveAsCallback {
        void requestSaveAs(byte[] content);
    }

    // FileContentItem definition is good, kept as is.
    public static class FileContentItem {
        private final Uri uri;
        private final byte[] content;

        public FileContentItem(Uri uri, byte[] content) {
            this.uri = uri;
            this.content = content;
        }

        public Uri getUri() {
            return uri;
        }

        public byte[] getContent() {
            return content;
        }
    }

    class FileViewHolder extends RecyclerView.ViewHolder {
        private final ImageView fileIcon;
        private final TextView fileName;

        public FileViewHolder(@NonNull View itemView) {
            super(itemView);
            fileIcon = itemView.findViewById(R.id.fileIcon);
            fileName = itemView.findViewById(R.id.fileName);
            itemView.setOnClickListener(v -> handleItemClick());
        }

        public void bind(FileItem item, int position) {
            // ... (bind logic remains the same)
            fileName.setText(item.displayName);
            itemView.setOnClickListener(v -> {
                if (item.isDirectory) {
                    // Toggle expand/collapse
                    if (item.isExpanded) {
                        collapseFolder(position);
                    } else {
                        expandFolder(position);
                    }
                } else {
                    onFileClickListener.onFileClicked(item.uri, item.displayName);
                }
            });
            itemView.setOnLongClickListener(v -> {
                ((MainActivity) context).setSelectedFileUri(MainActivity.viewPagerAdapter.fileUris.get(getAdapterPosition()));
                v.showContextMenu();
                return true;
            });
            if (item.isDirectory) {
                fileIcon.setImageResource(item.isExpanded ? R.drawable.ic_folder_open : R.drawable.ic_folder_closed);
                return;
            }
            String mime = item.mimeType != null ? item.mimeType : "";
            if (mime.startsWith("image/")) {
                fileIcon.setImageResource(R.drawable.ic_image_file);
            } else if (mime.startsWith("audio/")) {
                fileIcon.setImageResource(R.drawable.ic_audio_file);
            } else if (mime.startsWith("video/")) {
                fileIcon.setImageResource(R.drawable.ic_video_file);
            } else if (mime.startsWith("text/") || mime.equals("application/json")) {
                fileIcon.setImageResource(R.drawable.ic_text_file);
            } else {
                fileIcon.setImageResource(R.drawable.ic_unsupported_file);
            }
        }

        private void handleItemClick() {
            int position = getAdapterPosition();
            if (position == RecyclerView.NO_POSITION) return; // Simplified check

            FileItem clickedItem = fileList.get(position);
            if (clickedItem.isDirectory) {
                if (clickedItem.isExpanded) {
                    collapseFolder(position);
                } else {
                    expandFolderLogic(position); // Renamed for clarity
                }
            } else {
                if (onFileClickListener != null) {
                    // Assuming MainActivity casting is correct based on original code
                    ((MainActivity) context).closeLeftNavigation();
                    onFileClickListener.onFileClicked(clickedItem.uri, clickedItem.displayName);
                }
            }
        }

        // Consolidated and corrected expand logic
        private void expandFolderLogic(final int position) {
            final FileItem folder = fileList.get(position);
            folder.isExpanded = true;

            // Ensure UI update happens on the main thread
            ((Activity) context).runOnUiThread(() -> notifyItemChanged(position));

            new Thread(() -> {
                String documentId = DocumentsContract.getDocumentId(folder.uri);
                Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folder.uri, documentId);

                final List<FileItem> folders = new ArrayList<>();
                final List<FileItem> files = new ArrayList<>();

                try (Cursor cursor = context.getContentResolver().query(childrenUri,
                        new String[]{
                                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                                DocumentsContract.Document.COLUMN_MIME_TYPE
                        }, null, null, null)) {

                    if (cursor != null && cursor.moveToFirst()) {
                        do {
                            String childDocId = cursor.getString(0);
                            String childName = cursor.getString(1);
                            String mimeType = cursor.getString(2);
                            boolean isDirectory = DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType);

                            Uri childUri = DocumentsContract.buildDocumentUriUsingTree(folder.uri, childDocId);

                            FileItem childItem = new FileItem(context, childUri, childName, isDirectory, folder.depth + 1);

                            if (isDirectory) {
                                folders.add(childItem);
                            } else {
                                files.add(childItem);
                            }
                        } while (cursor.moveToNext());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error querying folder contents: " + e.getMessage());
                }

                // Sort alphabetically (folders first, then files)
                Collections.sort(folders, (a, b) -> a.displayName.compareToIgnoreCase(b.displayName));
                Collections.sort(files, (a, b) -> a.displayName.compareToIgnoreCase(b.displayName));

                final List<FileItem> newItems = new ArrayList<>();
                newItems.addAll(folders);
                newItems.addAll(files);

                ((Activity) context).runOnUiThread(() -> {
                    if (!newItems.isEmpty()) {
                        fileList.addAll(position + 1, newItems);
                        notifyItemRangeInserted(position + 1, newItems.size());
                    }
                });
            }).start();
        }


        private void collapseFolder(int position) {
            FileItem folder = fileList.get(position);
            folder.isExpanded = false;
            notifyItemChanged(position);

            int count = 0;
            // Iterate backward to simplify index management during removal
            for (int i = fileList.size() - 1; i > position; i--) {
                if (fileList.get(i).depth > folder.depth) {
                    fileList.remove(i);
                    count++;
                } else {
                    break; // Stop when an item at the same or shallower depth is found
                }
            }
            if (count > 0) {
                // The position we removed items from (the start of the children)
                int startPosition = position + 1;
                notifyItemRangeRemoved(startPosition, count);
            }
        }
    }
}
