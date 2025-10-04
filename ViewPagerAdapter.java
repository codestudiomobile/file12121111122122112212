package com.codestudio.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import java.util.ArrayList;
import java.util.List;

public class ViewPagerAdapter extends FragmentStateAdapter {

    public static final Uri WELCOME_URI = Uri.parse("app://com.diploma.project/welcome");
    public static final Uri UNTITLED_FILE_URI = Uri.parse("app://com.diploma.project/untitled");
    // Removed: public static final Uri CONSOLE_URI = Uri.parse("app://com.diploma.project/console");

    public final List<Uri> fileUris;
    public final List<String> fileNames;
    private final FragmentActivity activity;
    private final List<Fragment> fragments = new ArrayList<>();

    public ViewPagerAdapter(@NonNull FragmentActivity fragmentActivity, @NonNull List<Uri> fileUris) {
        super(fragmentActivity);
        this.activity = fragmentActivity;
        this.fileUris = fileUris;
        this.fileNames = new ArrayList<>();

        if (fileUris.isEmpty()) {
            SharedPreferences prefs = activity.getSharedPreferences(EditorActivity.PREFS_NAME, Context.MODE_PRIVATE);
            boolean editorStartup = prefs.getBoolean(EditorActivity.KEY_EDITOR_STARTUP, false);
            boolean welcomeStartup = prefs.getBoolean(EditorActivity.KEY_WELCOME_STARTUP, true);
            if (welcomeStartup) {
                fileUris.add(WELCOME_URI);
                fileNames.add("Welcome");
            }
            if (editorStartup) {
                fileUris.add(UNTITLED_FILE_URI);
                fileNames.add("Untitled");
            }
        } else {
            for (Uri uri : fileUris) {
                String name = uri.getLastPathSegment();
                fileNames.add(name != null ? name : "Unknown File");
            }
        }
    }
// Inside ViewPagerAdapter class

    public void removeFragment(int position) {
        if (position >= 0 && position < fileUris.size()) {
            fileUris.remove(position);
            fileNames.remove(position);
            notifyDataSetChanged();
        }
    }

    /**
     * Removes the tab associated with the given file URI.
     *
     * @param fileUri The URI of the file whose tab should be closed.
     */
    public void closeTab(Uri fileUri) {
        int position = fileUris.indexOf(fileUri);
        if (position != -1) {
            // Remove the fragment itself from the FragmentManager to free resources
            long itemId = getItemId(position);
            String fragmentTag = "f" + itemId;
            Fragment fragment = activity.getSupportFragmentManager().findFragmentByTag(fragmentTag);

            if (fragment != null) {
                // Check if it's the last tab before removing
                if (getItemCount() == 1) {
                    // If this is the last tab, reset to the Welcome screen
                    fileUris.clear();
                    fileNames.clear();

                    // Reload the startup logic (Welcome or Untitled)
                    SharedPreferences prefs = activity.getSharedPreferences(EditorActivity.PREFS_NAME, Context.MODE_PRIVATE);
                    boolean welcomeStartup = prefs.getBoolean(EditorActivity.KEY_WELCOME_STARTUP, true);

                    if (welcomeStartup) {
                        fileUris.add(WELCOME_URI);
                        fileNames.add("Welcome");
                    } else {
                        fileUris.add(UNTITLED_FILE_URI);
                        fileNames.add("Untitled");
                    }

                    // Directly notify the full change
                    notifyDataSetChanged();
                    return;
                }

                // Standard removal if there are other tabs
                fileUris.remove(position);
                fileNames.remove(position);

                // Notify the adapter of the removal
                notifyItemRemoved(position);
            }
        }
    }

    public void renameTab(Uri oldUri, Uri newUri, String newDisplayName) {
        int position = fileUris.indexOf(oldUri);
        if (position != -1) {
            // 1. Update the stored list data
            fileUris.set(position, newUri);
            fileNames.set(position, newDisplayName);

            // 2. Update the TextFragment's internal state
            long itemId = getItemId(position);
            String fragmentTag = "f" + itemId;
            Fragment fragment = activity.getSupportFragmentManager().findFragmentByTag(fragmentTag);

            if (fragment instanceof TextFragment) {
                // Set the new URI in the fragment (You may need to add a public setter in TextFragment)
                ((TextFragment) fragment).setFileUri(newUri);
            }

            // 3. Notify the adapter/TabLayout to update the displayed tab title
            notifyItemChanged(position);
        }
    }

    /**
     * Corrected method to retrieve the currently active Fragment instance
     * by looking it up in the FragmentManager using the stable ID tag.
     */
    public Fragment getFragment(int position) {
        if (position < 0 || position >= getItemCount()) {
            return null;
        }
        long itemId = getItemId(position);
        // FragmentStateAdapter uses the stable ID to create the tag format: "f" + getItemId(position)
        String fragmentTag = "f" + itemId;
        return activity.getSupportFragmentManager().findFragmentByTag(fragmentTag);
    }

    /**
     * Removed getTerminalFragment() as it relied on the single CONSOLE_URI.
     * MainActivity must now retrieve the Fragment instance using getFragment(position)
     * immediately after adding the new unique run tab.
     */
    // Removed: public TerminalFragment getTerminalFragment() {...}
    // Removed: public int findConsoleTabPosition() {...}
    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position < 0 || position >= fragments.size()) {
            return new WelcomeFragment();
        }
        return fragments.get(position);
    }

    @Override
    public int getItemCount() {
        return fragments.size();
    }
    /*@NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position < 0 || position >= fileUris.size()) {
            return new WelcomeFragment();
        }

        Uri fileUri = fileUris.get(position);
        String fileName = fileNames.get(position);

        if (activity instanceof MainActivity) {
            ((MainActivity) activity).currentFileUri = fileUri;
            ((MainActivity) activity).currentMimeType = ((MainActivity) activity).getMimeType(fileUri);
        }

        try {
            if ("run".equals(fileUri.getScheme())) {
                // Return the tracked TerminalFragment
                return fragments.get(getTerminalIndex(position));
            } else if (fileUri.equals(WELCOME_URI)) {
                return new WelcomeFragment();
            } else if (fileUri.equals(UNTITLED_FILE_URI)) {
                return TextFragment.newInstance(UNTITLED_FILE_URI);
            } else {
                return TextFragment.newInstance(fileUri);
            }
        } catch (Exception e) {
            Log.e("createFragment", "Error creating fragment", e);
            Toast.makeText(activity, "Error opening file for editing.", Toast.LENGTH_SHORT).show();
            return new WelcomeFragment();
        }
    }*/

    private int getTerminalIndex(int position) {
        int count = 0;
        for (int i = 0; i <= position; i++) {
            if ("run".equals(fileUris.get(i).getScheme())) {
                count++;
            }
        }
        return count - 1; // zero-based index
    }

    /* @NonNull
     @Override
     public Fragment createFragment(int position) {
         Uri fileUri = fileUris.get(position);

         if ("run".equals(fileUri.getScheme())) {
             return TerminalFragment.newInstance(fileUri);
         } else if (fileUri.equals(WELCOME_URI)) {
             return WelcomeFragment.newInstance();
         } else if (fileUri.equals(UNTITLED_FILE_URI)) {
             return TextFragment.newInstance(UNTITLED_FILE_URI);
         } else {
             try {
                 return TextFragment.newInstance(fileUri);
             } catch (Exception e) {
                 e.printStackTrace();
                 Toast.makeText(activity, "Error opening file for editing.", Toast.LENGTH_SHORT).show();
                 return WelcomeFragment.newInstance();
             }
         }
     }
 */
    /*@Override
    public int getItemCount() {
        return fileUris.size();
    }*/
    /*@Override
    public int getItemCount() {
        return fragments.size();
    }*/

    public List<Uri> getFileUris() {
        return fileUris;
    }

    public List<String> getFileNames() {
        return fileNames;
    }

    public int addTab(Uri uri, String fileName, boolean isTerminal) {
        if (uri == null) {
            return fileUris.contains(WELCOME_URI) ? fileUris.indexOf(WELCOME_URI) : 0;
        }
//        if (fileUris.contains(uri)) {
//            return fileUris.indexOf(uri);
//        }
        if (isTerminal) {
            removeTerminalFor(uri);
        }

        Fragment fragment;
        if (uri.equals(WELCOME_URI)) {
            fragment = WelcomeFragment.newInstance();
        } else if (uri.equals(UNTITLED_FILE_URI)) {
            fragment = TextFragment.newInstance(uri);
        } else {
            fragment = isTerminal ? TerminalFragment.newInstance(uri) : TextFragment.newInstance(uri);
        }

        int insertIndex = getInsertIndex(uri, isTerminal);
        fileUris.add(insertIndex, uri);
        fileNames.add(insertIndex, fileName);
        fragments.add(insertIndex, fragment);
        Log.i("addTab", "addTab: fileuris" + fileUris);
        Log.i("addTab", "addTab: filenames" + fileName);
        notifyItemInserted(insertIndex);
        return insertIndex;
    }

    private int getInsertIndex(Uri uri, boolean isTerminal) {
        if (!isTerminal || fileUris.isEmpty()) {
            Log.e("ViewPagerAdapter", "getInsertIndex: fileUris size:" + fileUris.size());
            return fileUris.size() - 1;
        }

        String targetName = uri.getLastPathSegment(); // sample.java

        for (int i = 0; i < fileUris.size(); i++) {
            String fileName = fileUris.get(i).getLastPathSegment();
            if (fileName != null && fileName.equals(targetName)) {
                Log.e("ViewPagerAdapter", "getInsertIndex: fileUris size:" + i);
                return i;
            }
        }
        Log.e("ViewPagerAdapter", "getInsertIndex: fileUris size:" + fileUris.size());
        return fileUris.size() - 1; // fallback
    }

    public void removeTerminalFor(Uri fileUri) {
        String targetName = fileUri.getLastPathSegment();

        for (int i = 0; i < fileUris.size(); i++) {
            Uri uri = fileUris.get(i);
            if ("run".equals(uri.getScheme()) && uri.getLastPathSegment().equals(targetName)) {
                fileUris.remove(i);
                fileNames.remove(i);
                fragments.remove(i);
                notifyItemRemoved(i);
                break;
            }
        }
    }

    private Uri getBaseUri(Uri runUri) {
        // Extract original file URI from run://local/sample.java
        String path = runUri.getLastPathSegment();
        return Uri.parse("content://your_base/" + path); // Adjust based on your scheme
    }

    public int findTabPositionByName(String name) {
        for (int i = 0; i < fileNames.size(); i++) {
            if (fileNames.get(i).equals(name)) {
                return i;
            }
        }
        return -1;
    }


    /**
     * Corrected removeTab logic for ViewPager2 stability.
     */
    public void removeTab(int position) {
        if (position >= 0 && position < fileUris.size()) {

            // Removed the check against CONSOLE_URI

            // 1. Remove the data from the lists.
            fileUris.remove(position);
            fileNames.remove(position);

            // 2. Use notifyDataSetChanged() for stability after list removal.
            notifyDataSetChanged();

            // 3. Handle the case where the list becomes empty (Startup Tabs)
            if (fileUris.isEmpty()) {
                SharedPreferences prefs = activity.getSharedPreferences(EditorActivity.PREFS_NAME, Context.MODE_PRIVATE);
                boolean editorStartup = prefs.getBoolean(EditorActivity.KEY_EDITOR_STARTUP, false);
                boolean welcomeStartup = prefs.getBoolean(EditorActivity.KEY_WELCOME_STARTUP, true);
                if (welcomeStartup) {
                    fileUris.add(WELCOME_URI);
                    fileNames.add("Welcome");
                }
                if (editorStartup) {
                    fileUris.add(UNTITLED_FILE_URI);
                    fileNames.add("Untitled");
                }
            }
        }
    }


    @Override
    public long getItemId(int position) {
        // Use the hash code of the URI as a stable ID.
        // This is crucial for the unique URI (with run_id) to create unique fragments.
        return fileUris.get(position).hashCode();
    }

    @Override
    public boolean containsItem(long itemId) {
        for (Uri uri : fileUris) {
            if (uri.hashCode() == itemId) {
                return true;
            }
        }
        return false;
    }

    public void removeAllTabs() {
        fileUris.clear();
        fileNames.clear();

        // Retain startup tabs if list is empty
        if (fileUris.isEmpty()) {
            SharedPreferences prefs = activity.getSharedPreferences(EditorActivity.PREFS_NAME, Context.MODE_PRIVATE);
            boolean editorStartup = prefs.getBoolean(EditorActivity.KEY_EDITOR_STARTUP, false);
            boolean welcomeStartup = prefs.getBoolean(EditorActivity.KEY_WELCOME_STARTUP, true);
            if (welcomeStartup) {
                fileUris.add(WELCOME_URI);
                fileNames.add("Welcome");
            }
            if (editorStartup) {
                fileUris.add(UNTITLED_FILE_URI);
                fileNames.add("Untitled");
            }
        }
        notifyDataSetChanged();
    }

    public void removeOtherTabs(int currentPosition) {
        if (currentPosition < 0 || currentPosition >= fileUris.size()) {
            return;
        }

        Uri currentUri = fileUris.get(currentPosition);
        String currentName = fileNames.get(currentPosition);

        fileUris.clear();
        fileNames.clear();

        fileUris.add(currentUri);
        fileNames.add(currentName);

        notifyDataSetChanged();
    }

    public List<FilesAdapter.FileContentItem> getOpenFilesContent() {
        List<FilesAdapter.FileContentItem> filesToSave = new ArrayList<>();
        for (int i = 0; i < getItemCount(); i++) {
            Uri uri = fileUris.get(i);

            // Skip special URIs that don't need saving (including the Run: tabs)
            if (uri.equals(WELCOME_URI) || uri.equals(UNTITLED_FILE_URI) || fileNames.get(i).startsWith("Run:")) {
                continue;
            }

            long itemId = getItemId(i);
            String fragmentTag = "f" + itemId;
            Fragment fragment = activity.getSupportFragmentManager().findFragmentByTag(fragmentTag);

            if (fragment instanceof TextFragment) {
                TextFragment textFragment = (TextFragment) fragment;
                if (!textFragment.isSaved()) {
                    byte[] content = textFragment.getContents();
                    if (content != null) {
                        filesToSave.add(new FilesAdapter.FileContentItem(uri, content));
                    }
                }
            }
        }
        return filesToSave;
    }
}
