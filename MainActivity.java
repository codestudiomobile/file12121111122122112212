package com.codestudio.mobile;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.navigation.NavigationView;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements TabLayout.OnTabSelectedListener, FilesAdapter.OnFileClickListener, CreateFileDialog.OnFileCreatedListener, TerminalFragment.ConsoleInputListener, CodeExecutionManager.ExecutionListener {

    private static final int REQUEST_CODE_OPEN_DIRECTORY = 1;
    private static final String PREFS_NAME = "MyPrefsFile";
    private static final String LAST_FOLDER_URI_KEY = "lastFolderUri";
    private static final int REQUEST_CODE_OPEN_FILE = 2001;
    public static Uri currentDirectoryUri = null;
    public static ViewPagerAdapter viewPagerAdapter;
    public final ArrayList<Uri> folderUris = new ArrayList<>();
    public final ArrayList<String> folderNames = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<FileItem> fileItems = new ArrayList<>();
    public Uri currentFileUri;
    public String currentMimeType;
    private TerminalFragment terminalFragment;
    private TabLayout tabLayout;
    private ViewPager2 viewPager2;
    private DrawerLayout drawerLayout;
    private NavigationView leftNavigation;
    private RecyclerView filesList;
    private FilesAdapter filesAdapter;
    private TextView currentFolderTitle;
    private ImageButton refreshFolder;
    private ImageButton collapseAllFolders;
    private View headerView;
    private boolean runMenuVisible = false;
    private boolean editMenuVisible = false;
    private boolean stopMenuVisible = false;
    private Uri folderUri = null;
    private CodeExecutionManager executionManager;
    private Uri selectedFileUri;
    private FileItem selectedFileItem;
    private ProgressBar progressBar;

    private boolean extensionAllowsRun(Uri fileUri) {
        String last = fileUri.getLastPathSegment();
        if (last == null) return false;
        String lower = last.toLowerCase();

        return lower.endsWith(".c") || lower.endsWith(".cpp") || lower.endsWith(".java") || lower.endsWith(".py") || lower.endsWith(".js") || lower.endsWith(".ts") || lower.endsWith(".html") || lower.endsWith(".xml") || lower.endsWith(".rb") || lower.endsWith(".go") || lower.endsWith(".rs") || lower.endsWith(".php") || lower.endsWith(".sh") || lower.endsWith(".swift") || lower.endsWith(".kt") || lower.endsWith(".scala") || lower.endsWith(".pl") || lower.endsWith(".lua") || lower.endsWith(".sql") || lower.endsWith(".r") || lower.endsWith(".dart") || lower.endsWith(".cs");       // C#
    }

    public void prepareFolderDataForDialog() {
        folderUris.clear();
        folderNames.clear();

        folderUris.add(null);
        folderNames.add("App Storage (Default)");

        if (currentDirectoryUri != null) {
            DocumentFile parentDirectory = DocumentFile.fromTreeUri(this, currentDirectoryUri);
            if (parentDirectory != null && parentDirectory.isDirectory()) {
                for (DocumentFile df : parentDirectory.listFiles()) {
                    if (df.isDirectory()) {
                        folderUris.add(df.getUri());
                        String n = df.getName();
                        folderNames.add(n != null ? n : df.getUri().getLastPathSegment());
                    }
                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            CommandUpdater.checkForUpdates(this);
        }, 1000);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        drawerLayout = findViewById(R.id.drawerLayout);
        leftNavigation = findViewById(R.id.leftNavigation);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        headerView = leftNavigation.getHeaderView(0);
        currentFolderTitle = headerView.findViewById(R.id.currentFolderTitle);
        refreshFolder = headerView.findViewById(R.id.refreshFilesFolders);
        collapseAllFolders = headerView.findViewById(R.id.collapseAllFolders);

        tabLayout = findViewById(R.id.tabLayout);
        viewPager2 = findViewById(R.id.viewPager2);

        List<Uri> initialUris = new ArrayList<>();
        viewPagerAdapter = new ViewPagerAdapter(this, initialUris);
        viewPager2.setAdapter(viewPagerAdapter);

        tabLayout.addOnTabSelectedListener(this);
        progressBar = findViewById(R.id.progressBar);
        new TabLayoutMediator(tabLayout, viewPager2, (tab, position) -> {
            tab.setText(viewPagerAdapter.fileNames.get(position));
            tab.view.setOnLongClickListener(v -> {
                currentFileUri = viewPagerAdapter.getFileUris().get(position);
                currentMimeType = getMimeType(viewPagerAdapter.fileUris.get(position));
                showPopupMenu(v, position);
                return true;
            });
        }).attach();

        handleIntent(getIntent());

        restoreLastFolder();
        executionManager = new CodeExecutionManager(this, this);
        terminalFragment = null;
        Intent intent = getIntent();
        String action = intent.getAction();
        Uri uri = intent.getData();

        if (Intent.ACTION_VIEW.equals(action) && uri != null) {
            handleFileIntent(uri);
        }
        SharedPreferences prefs = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE);
        boolean showEditor = prefs.getBoolean("openEditorOnStartup", false);
        boolean showWelcome = prefs.getBoolean("openWelcomeScreenOnStartup", true);

        if (viewPagerAdapter.getItemCount() == 0) {
            int welcomeIndex = -1;
            int editorIndex = -1;

            if (showWelcome) {
                welcomeIndex = viewPagerAdapter.addTab(ViewPagerAdapter.WELCOME_URI, "Welcome", false);
            }
            if (showEditor) {
                editorIndex = viewPagerAdapter.addTab(ViewPagerAdapter.UNTITLED_FILE_URI, "Untitled", false);
            }

            // Decide which tab to show first
            int defaultIndex = (editorIndex != -1) ? editorIndex : welcomeIndex;
            if (defaultIndex != -1) {
                viewPager2.setCurrentItem(defaultIndex, false);
                tabLayout.selectTab(tabLayout.getTabAt(defaultIndex));
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        // Check if the new Intent is for opening a file
        String action = intent.getAction();
        Uri uri = intent.getData();

        if (Intent.ACTION_VIEW.equals(action) && uri != null) {
            // App was already running, and a new file was opened
            handleFileIntent(uri);
        }
    }

    private void handleFileIntent(Uri uri) {
        if (uri != null) {
            String fileName = getFileName(uri);
            if (fileName != null) {
                String fileTypeKey = CommandFetcher.getFileTypeKey(fileName);

                // Log for debugging
                Log.d("MainActivity", "Processing file: " + fileName + ", Type Key: " + fileTypeKey);

                // You must now call your CodeExecutionManager's startExecution
                // The method signature is: startExecution(Uri fileUri, String fileName, String fileTypeKey)
//                executionManager.startExecution(uri, fileName, fileTypeKey);

            } else {
                Toast.makeText(this, "Could not determine file name.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
        // ⬇️ NEW: Shutdown execution manager's executor
        if (executionManager != null) {
            executionManager.shutdown();
        }
    }

    private void showPopupMenu(View view, int position) {
        PopupMenu popup = new PopupMenu(this, view);
        popup.getMenuInflater().inflate(R.menu.tab_menu, popup.getMenu());
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.close_tab) {
                viewPagerAdapter.removeTab(position);
            } else if (id == R.id.close_other_tabs) {
                viewPagerAdapter.removeOtherTabs(position);
            } else if (id == R.id.close_all_tabs) {
                viewPagerAdapter.removeAllTabs();
            }
            return true;
        });
        popup.show();
    }

    private void handleIntent(Intent intent) {
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri fileUri = intent.getData();
            if (fileUri != null) {
                String fileName = getFileName(fileUri);
                int tabIndex = viewPagerAdapter.addTab(fileUri, fileName, false);
                if (tabIndex != -1) {
                    tabLayout.selectTab(tabLayout.getTabAt(tabIndex));
                    viewPager2.setCurrentItem(tabIndex);
                }
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem runItem = menu.findItem(R.id.runFile);
        MenuItem editItem = menu.findItem(R.id.editFile);
        MenuItem stopExecutionItem = menu.findItem(R.id.stopExecution);
        if (runItem != null) {
            runItem.setVisible(runMenuVisible);
        }
        if (stopExecutionItem != null) {
            stopExecutionItem.setVisible(stopMenuVisible);
        }
        if (editItem != null) {
            editItem.setVisible(editMenuVisible);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.runFile) {
            if (selectedFileItem != null) {
                runFile(selectedFileItem);
                return true;
            } else {
                Toast.makeText(this, "No file selected", Toast.LENGTH_SHORT).show();
                return false;
            }
        } else if (id == R.id.openNewTerminal) {
            TermuxRunner runner = new TermuxRunner(this);
            runner.launchTerminal(this);
            return true;
        } /*else if (id == R.id.stopExecution) {
            if (executionManager != null) {
                executionManager.stopExecution();
                // UI will be updated by onExecutionComplete/onExecutionError callback
            }
            return true;
        } */ else if (id == R.id.editFile) {
            int currentTabPos = tabLayout.getSelectedTabPosition();
            if (currentTabPos != -1) {
                String currentTabName = viewPagerAdapter.fileNames.get(currentTabPos);

                // Check if the current tab is a "Run" tab
                if (currentTabName.startsWith("Running")) {
                    // Find the original file's tab by its name
                    String originalFileName = currentTabName.substring("Running".length());
                    int originalFileTabPos = viewPagerAdapter.findTabPositionByName(originalFileName);

                    // Switch to the original file's tab and remove the "Run" tab
                    if (originalFileTabPos != -1) {
                        tabLayout.selectTab(tabLayout.getTabAt(originalFileTabPos));
                        viewPager2.setCurrentItem(originalFileTabPos);
                        viewPagerAdapter.removeTab(currentTabPos);
                    }
                }
            }
            // Update menu visibility
            runMenuVisible = true;
            stopMenuVisible = false;
            editMenuVisible = false;
            invalidateOptionsMenu();
            return true;
        } else if (id == R.id.openWelcomeScreen) {
            int newTabIndex = viewPagerAdapter.addTab(ViewPagerAdapter.WELCOME_URI, "Welcome", false);
            if (newTabIndex != -1) {
                tabLayout.selectTab(tabLayout.getTabAt(newTabIndex));
            }
            return true;
        } else if (id == R.id.saveFiles) {
            List<FilesAdapter.FileContentItem> filesToSave = viewPagerAdapter.getOpenFilesContent();
            if (filesAdapter != null && !filesToSave.isEmpty()) {
                filesAdapter.saveAllFiles(filesToSave);
            }
        } else if (id == R.id.settings) {
            openSettings();
            return true;
        } else if (id == R.id.about) {
            Intent intent = new Intent(getApplicationContext(), AboutActivity.class);
            startActivity(intent);
            return true;
        } else if (id == R.id.openFolder) {
            openDirectory();
            return true;
        } else if (id == R.id.refreshFilesFolders) {
            refreshAll();
            return true;
        } else if (id == R.id.openFile) {
            openFilePicker();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void openHtmlInBrowser(Uri fileUri) {
        // 1. Check if the URI can be opened by a browser/viewer
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(fileUri, "text/html");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); // Critical for content URIs

        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No application found to view HTML files.", Toast.LENGTH_LONG).show();
        }
    }

    private void runFile(FileItem item) {
        if (item == null || item.uri == null) {
            Toast.makeText(this, "Please select a file first.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (item.mimeType != null && (item.mimeType.equals("text/html") || item.mimeType.equals("application/xhtml+xml"))) {
            openHtmlInBrowser(item.uri);
            return;
        }

        String absoluteFilePath = getAbsolutePathFromUri(this, item.uri);
        String fileName = item.displayName;

        if (absoluteFilePath == null || fileName == null) {
            Toast.makeText(this, "Execution failed: Cannot resolve file path.", Toast.LENGTH_LONG).show();
            Log.e("MainActivity", "Failed to resolve URI: " + item.uri);
            return;
        }

        String command = CommandFetcher.getCommand(this, item.uri);
        Uri fileUri = item.uri;
        Uri runUri = new Uri.Builder().scheme("run").authority("local").appendPath(fileName).build();

        // UI: Show progress bar
        progressBar.setActivated(true);
        progressBar.setVisibility(View.VISIBLE);

        // Step 1: Ensure file tab exists
        int fileIndex = viewPagerAdapter.addTab(fileUri, fileName, false);

        // Step 2: Remove old terminal tab if it exists
        viewPagerAdapter.removeTerminalFor(fileUri);

        // Step 3: Add new terminal tab next to file tab
        int terminalIndex = viewPagerAdapter.addTab(runUri, "Running (" + fileName + ")", true);

        // Step 4: Switch to terminal tab
        viewPager2.setCurrentItem(terminalIndex, false);
        tabLayout.selectTab(tabLayout.getTabAt(terminalIndex));

        // Step 5: Run command
        new TermuxRunner(this).executeCommandInternally(command, this); // 'this' must implement ConsoleInputListener
    }

    @Nullable
    public String getAbsolutePathFromUri(Context context, Uri uri) {
        if (uri == null) return null;

        final String scheme = uri.getScheme();

        // 1. Handle file:// URIs
        if ("file".equalsIgnoreCase(scheme)) {
            return uri.getPath();
        }

        // 2. Handle content:// URIs from MediaStore
        if ("content".equalsIgnoreCase(scheme) && !DocumentsContract.isDocumentUri(context, uri)) {
            String[] projection = {MediaStore.Images.Media.DATA};
            try (Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                    return cursor.getString(columnIndex);
                }
            } catch (Exception e) {
                Log.w("MainActivity", "Failed to resolve content URI via MediaStore", e);
            }
        }

        // 3. Handle DocumentProvider URIs
        if (DocumentsContract.isDocumentUri(context, uri)) {
            String docId = DocumentsContract.getDocumentId(uri);
            String[] split = docId.split(":");
            String type = split[0];
            String relativePath = split.length > 1 ? split[1] : "";

            if ("primary".equalsIgnoreCase(type)) {
                return Environment.getExternalStorageDirectory() + "/" + relativePath;
            }

            // Handle non-primary volumes (e.g., SD cards)
            File[] externalDirs = context.getExternalFilesDirs(null);
            for (File file : externalDirs) {
                if (file != null && file.getAbsolutePath().contains(type)) {
                    String basePath = file.getAbsolutePath().split("/Android")[0];
                    return basePath + "/" + relativePath;
                }
            }
        }

        return null; // Resolution failed
    }

    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception e) {
                Log.e("MainActivity", "Error getting file name from cursor.", e);
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result;
    }

    public void setSelectedFileUri(Uri uri) {
        this.selectedFileUri = uri;
    }

    public boolean showSaveDialog() {
        // 1. Prepare data (must populate folderUris and folderNames)
        prepareFolderDataForDialog();

        // 2. Check if there are any folders to prevent empty spinner
        if (folderUris == null || folderUris.isEmpty()) {
            Toast.makeText(this, "Please select at least one folder with write permission first.", Toast.LENGTH_LONG).show();
            return false;
        }

        // 3. Show dialog
        CreateFileDialog dialog = CreateFileDialog.newInstance(folderUris, folderNames);
        dialog.show(getSupportFragmentManager(), "CreateFileDialog");
        return true;
    }

    public void openSettings() {
        Intent settings = new Intent(getApplicationContext(), SettingsActivity.class);
        startActivity(settings);
    }

    public void openDirectory() {
        Intent selectFolder = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(selectFolder, REQUEST_CODE_OPEN_DIRECTORY);
    }

    @Override
    public void onTabSelected(@NonNull TabLayout.Tab tab) {
        viewPager2.setCurrentItem(tab.getPosition());
        int pos = tab.getPosition();
        if (pos < viewPagerAdapter.fileUris.size()) {
            Uri uri = viewPagerAdapter.fileUris.get(pos);
            if (uri != null) {
                // Set menu visibility based on file URI
                FileItem item = FileUtils.getFileItemFromUri(this, uri); // You may already have this method
                setSelectedFileItem(item); // This sets the correct file for runFile()
                boolean allowedToRun = extensionAllowsRun(uri);
                if (allowedToRun) {
                    runMenuVisible = true;
                    stopMenuVisible = false;
                    editMenuVisible = false;
                } else {
                    runMenuVisible = false;
                    stopMenuVisible = false;
                    editMenuVisible = false;
                }
            } else {
                // Handle null URI (e.g., for the "Welcome" tab)
                runMenuVisible = false;
                stopMenuVisible = false;
                editMenuVisible = false;
            }
        } else {
            // Handle index out of bounds, should not happen with proper checks
            runMenuVisible = false;
            stopMenuVisible = false;
            editMenuVisible = false;
        }
        invalidateOptionsMenu();
    }

    @Override
    public void onUserInputSubmitted(String input) {
        // 1. Send the input to Termux via the manager
        if (executionManager != null) {
            executionManager.sendInput(input);
        }

        // 2. Hide input bar temporarily
        if (terminalFragment != null) {
            terminalFragment.setAwaitingInput(false);
        }

        if (terminalFragment != null) {
            terminalFragment.appendOutput("\n$ " + input + "\n");
        }

    }

    @Override
    public void onOutputReceived(String output) {
        int currentTab = tabLayout.getSelectedTabPosition();
        Fragment fragment = viewPagerAdapter.createFragment(currentTab);
        if (fragment instanceof TerminalFragment) {
            ((TerminalFragment) fragment).appendOutput(output);
        }
    }

    @Override
    public void onExecutionComplete() {
        TerminalFragment fragment = getTerminalFragment();
        if (fragment != null) {
            fragment.appendOutput("\nExecution completed successfully.\n");
            fragment.setAwaitingInput(true);
        }
    }

    private TerminalFragment getTerminalFragment() {
        return (TerminalFragment) getSupportFragmentManager().findFragmentByTag("TerminalFragment");
    }

    @Override
    public void onExecutionError(String message) {
        if (terminalFragment != null) {
            terminalFragment.appendOutput("\n[ERROR] " + message + "\n");
            terminalFragment.setAwaitingInput(false);
        }
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(this, "Execution Error.", Toast.LENGTH_LONG).show());
        Log.d("Executionerror", "onExecutionError: " + message);
        // Reset menu visibility
        runMenuVisible = true;
        stopMenuVisible = false;
        editMenuVisible = false;
        invalidateOptionsMenu();
    }

    @Override
    public void onExecutionStarted(String command, String fileName) {
        // Open TerminalFragment and pass the command or output path
        TerminalFragment fragment = new TerminalFragment();
        Bundle args = new Bundle();
        args.putString("command", command);
        fragment.setArguments(args);
        Uri runUri = Uri.parse("run://" + fileName + "?ts=" + System.currentTimeMillis());
        viewPagerAdapter.addTab(runUri, "Running(" + fileName + ")", false);
        viewPager2.setCurrentItem(viewPagerAdapter.getItemCount() - 1, true);
    }

    @Override
    public void onTabUnselected(TabLayout.Tab tab) {
        // no-op
    }

    @Override
    public void onTabReselected(TabLayout.Tab tab) {
        // no-op
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // 0. Handle cancellation and null data case first
        if (resultCode != RESULT_OK || data == null) {
            return;
        }

        // Get the URI once, if available
        Uri uri = data.getData();
        if (uri == null) {
            return;
        }

        // --- LOGIC FOR OPENING A DIRECTORY (Existing Code) ---
        if (requestCode == REQUEST_CODE_OPEN_DIRECTORY) {
            // Since we checked for resultCode/data/uri above, we can simplify this block

            // 1. Persist URI Permission
            final int takeFlags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

            try {
                getContentResolver().takePersistableUriPermission(uri, takeFlags);
            } catch (SecurityException e) {
                Log.e("MainActivity", "Failed to take persistable URI permission.", e);
                Toast.makeText(this, "Failed to get persistent access to folder.", Toast.LENGTH_LONG).show();
                return;
            }

            // 2. Update state variables
            folderUri = uri;
            currentDirectoryUri = uri;
            saveLastFolder(uri);

            // 3. Initialize UI Components if not already initialized
            ProgressBar filesLoadingProgressBar = findViewById(R.id.filesLoadingProgress);

            if (filesAdapter == null) {
                filesList = findViewById(R.id.filesList);
                refreshFolder = findViewById(R.id.refreshFilesFolders);
                collapseAllFolders = findViewById(R.id.collapseAllFolders);

                filesAdapter = new FilesAdapter(MainActivity.this, fileItems, this, this::requestSaveAs);

                filesList.setLayoutManager(new LinearLayoutManager(this));
                filesList.setAdapter(filesAdapter);

                // Set initial visibility
                if (filesLoadingProgressBar != null)
                    filesLoadingProgressBar.setVisibility(View.VISIBLE);
                if (refreshFolder != null) refreshFolder.setVisibility(View.GONE);
                if (collapseAllFolders != null) collapseAllFolders.setVisibility(View.GONE);

                // Set Listeners
                if (collapseAllFolders != null) {
                    collapseAllFolders.setOnClickListener(v -> filesAdapter.collapseAllFolders());
                }
                if (refreshFolder != null) {
                    refreshFolder.setOnClickListener(v -> refreshFileList());
                }
            } else {
                // If adapter is already initialized, ensure we show the loading bar now
                if (filesLoadingProgressBar != null)
                    filesLoadingProgressBar.setVisibility(View.VISIBLE);
            }

            // 4. Execute file loading in the background
            if (executor != null) {
                executor.execute(() -> {
                    populateFileList(uri, 0);

                    // 5. Update UI on Main Thread after loading
                    runOnUiThread(() -> {
                        if (filesAdapter != null) {
                            filesAdapter.notifyDataSetChanged();
                        }

                        if (filesLoadingProgressBar != null)
                            filesLoadingProgressBar.setVisibility(View.GONE);
                        if (refreshFolder != null) refreshFolder.setVisibility(View.VISIBLE);
                        if (collapseAllFolders != null)
                            collapseAllFolders.setVisibility(View.VISIBLE);

                        openLeftNavigation();
                    });
                });
            }

            // --- LOGIC FOR OPENING A SINGLE FILE (New Code) ---
        } else if (requestCode == REQUEST_CODE_OPEN_FILE) {

            // 1. Grant persistent READ access for the single file URI
            final int takeFlags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
            try {
                // Granting READ permission is enough for viewing/editing the file content
                getContentResolver().takePersistableUriPermission(uri, takeFlags);
            } catch (SecurityException e) {
                Log.e("MainActivity", "Failed to take persistable URI permission for file.", e);
                Toast.makeText(this, "Failed to get persistent read access to file.", Toast.LENGTH_LONG).show();
            }

            // 2. Get file name (Ensure the getFileName(Uri) helper method exists)
            String fileName = getFileName(uri);

            // 3. Open file in a new tab
            if (viewPagerAdapter != null) {
                int position = viewPagerAdapter.addTab(uri, fileName, false);
                // Assuming viewPager2 is an instance member of MainActivity
                if (viewPager2 != null) {
                    viewPager2.setCurrentItem(position, true);
                }
            }

            // 4. Close the navigation drawer/sidebar
            closeLeftNavigation();
        }
    }

    private void refreshFileList() {
        if (folderUri != null && filesAdapter != null) {
            runOnUiThread(() -> {
                ProgressBar filesLoadingProgressBar = findViewById(R.id.filesLoadingProgress);
                filesLoadingProgressBar.setVisibility(View.VISIBLE);
                refreshFolder.setVisibility(View.GONE);
                collapseAllFolders.setVisibility(View.GONE);
            });

            executor.execute(() -> {
                filesAdapter.refresh();
                populateFileList(folderUri, 0);
                runOnUiThread(() -> {
                    filesAdapter.notifyDataSetChanged();
                    ProgressBar filesLoadingProgressBar = findViewById(R.id.filesLoadingProgress);
                    filesLoadingProgressBar.setVisibility(View.GONE);
                    refreshFolder.setVisibility(View.VISIBLE);
                    collapseAllFolders.setVisibility(View.VISIBLE);
                });
            });
        }
    }

    @Override
    public void onFileClicked(Uri fileUri, String fileName) {
        // 1. Get MIME type from ContentResolver (most reliable source)
        String mimeType = getApplicationContext().getContentResolver().getType(fileUri);

        // 2. Assume we need to open externally unless proven otherwise (for null/unknown)
        boolean isExternalViewType = false;

        if (mimeType != null) {
            // Convert to lowercase for reliable comparison
            String lowerMimeType = mimeType.toLowerCase();

            // Check for major non-editable, non-textual categories
            if (lowerMimeType.startsWith("image/") || lowerMimeType.startsWith("video/") || lowerMimeType.startsWith("audio/") || // Web pages are typically viewed externally
                    lowerMimeType.equals("application/pdf") || // PDF documents
                    lowerMimeType.contains("msword") || // Word documents
                    lowerMimeType.contains("vnd.openxmlformats-officedocument") || // Modern Office documents
                    lowerMimeType.contains("zip") || // Archives
                    lowerMimeType.contains("rar") || lowerMimeType.contains("octet-stream")) // Generic binary data
            {
                isExternalViewType = true;
            }
        }

        // Note: If mimeType is null (common for code files) or if it's "text/plain",
        // isExternalViewType remains false, correctly triggering the text editor.

        if (isExternalViewType) {
            // LAUNCH EXTERNAL INTENT
            Intent viewIntent = new Intent(Intent.ACTION_VIEW);

            // If MIME type was null but we triggered the check based on extension,
            // try to get a reliable MIME type for the intent here.
            if (mimeType == null) {
                String extension = MimeTypeMap.getFileExtensionFromUrl(fileName);
                if (extension != null) {
                    mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
                }
            }

            // Fallback to generic type if resolution fails for the intent
            if (mimeType == null) {
                mimeType = "*/*";
            }

            viewIntent.setDataAndType(fileUri, mimeType);
            viewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            try {
                startActivity(viewIntent);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, "No application found to view this file type.", Toast.LENGTH_LONG).show();
            }
        } else {
            // OPEN AS TAB FOR EDITING (Code, Text, XML, JSON, Unknown)
            int tabIndex = viewPagerAdapter.addTab(fileUri, fileName, false);
            if (tabIndex != -1) {
                tabLayout.selectTab(tabLayout.getTabAt(tabIndex));
                viewPager2.setCurrentItem(tabIndex);
                drawerLayout.closeDrawer(GravityCompat.START);
            }
        }
    }

    @Override
    public void onFileContextMenuRequest(Uri fileUri, View anchorView) {
        FileItem fileItem = findFileItemByUri(fileUri); // <-- Replace with your actual lookup method
        if (fileItem != null) {
            showFileContextMenu(fileItem, anchorView);
        }
    }

    @Nullable
    private FileItem findFileItemByUri(Uri uri) {
        if (viewPagerAdapter.fileUris == null || viewPagerAdapter.fileNames == null) return null;

        // Check the list of currently open files/tabs
        for (int i = 0; i < viewPagerAdapter.getFileUris().size(); i++) {
            if (viewPagerAdapter.getFileUris().get(i).equals(uri)) {
                // NOTE: This only creates a FileItem object, it doesn't retrieve the one from the Drawer.
                // But it contains the necessary info (Uri, Name) for the context menu.
                return new FileItem(this, uri, viewPagerAdapter.getFileNames().get(i), false, // Not a directory
                        0);
            }
        }

        // If you have a separate list of files/folders (e.g., in your FilesAdapter),
        // you would iterate through that list here as well.
        // For now, assume it's one of the open tabs.
        return null;
    }

    private void showFileContextMenu(FileItem fileItem, View anchorView) {
        // 1. Create a PopupMenu
        PopupMenu popup = new PopupMenu(this, anchorView);
        popup.getMenuInflater().inflate(R.menu.file_menu, popup.getMenu());

        // Optional: Disable 'New' for non-directory items
        popup.getMenu().findItem(R.id.new_file).setVisible(fileItem.isDirectory);
        this.selectedFileUri = fileItem.uri;
        this.currentFileUri = fileItem.uri;
        this.currentMimeType = fileItem.mimeType;
        if (fileItem.isDirectory) {
            this.folderUri = fileItem.uri;
        }
        this.selectedFileItem = fileItem;

        // Optional: Add menu item for code execution
        if (!fileItem.isDirectory && isRunnableFile(fileItem.mimeType)) {
            popup.getMenu().findItem(R.id.run_file).setVisible(true);
        }

        // 2. Set the click listener
        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.new_file) {
                showCreateFileDialog(folderUri);
                return true;
            } else if (itemId == R.id.delete_file) {
                return deleteFile();
            } else if (itemId == R.id.import_file) {
                return false;
            } else if (itemId == R.id.rename_file) {
                renameFile(fileItem);
                return true;
            } else if (itemId == R.id.run_file) {
                runFile(fileItem);
                return true;
            }
            return false;
        });

        // 3. Show the menu
        popup.show();
    }

    private void renameFile(FileItem fileItem) {
        if (fileItem.uri == null) return;

        final EditText input = new EditText(this);
        input.setText(fileItem.displayName); // Pre-fill with current name
        input.setSelectAllOnFocus(true); // Select the entire name for easy editing

        new AlertDialog.Builder(this).setTitle("Rename " + fileItem.displayName).setView(input).setPositiveButton("Rename", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (newName.isEmpty()) {
                Toast.makeText(this, "Name cannot be empty.", Toast.LENGTH_SHORT).show();
                return;
            }

            new Thread(() -> {
                DocumentFile fileToRename = DocumentFile.fromSingleUri(this, fileItem.uri);
                if (fileToRename != null && fileToRename.renameTo(newName)) {
                    Uri newUri = fileToRename.getUri(); // Get the new URI after rename
                    runOnUiThread(() -> {
                        Toast.makeText(this, fileItem.displayName + " renamed to " + newName, Toast.LENGTH_SHORT).show();

                        // Update UI: File Browser and open tab
                        refreshAll();
                        viewPagerAdapter.renameTab(fileItem.uri, newUri, newName);
                    });
                } else {
                    runOnUiThread(() -> Toast.makeText(this, "Failed to rename file. Check permissions or new name validity.", Toast.LENGTH_LONG).show());
                }
            }).start();
        }).setNegativeButton(android.R.string.cancel, null).show();
    }

    private boolean deleteFile() {
        if (selectedFileUri == null) {
            Toast.makeText(this, "No file selected", Toast.LENGTH_SHORT).show();
            return false;
        }

        try {
            DocumentFile file = DocumentFile.fromSingleUri(this, selectedFileUri);
            if (file != null && file.exists()) {
                boolean deleted = file.delete();
                if (deleted) {
                    Toast.makeText(this, "File deleted", Toast.LENGTH_SHORT).show();
                    removeTabForUri(selectedFileUri); // Optional: close tab
                    refreshFileList(); // Optional: update UI
                    return true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error deleting file", Toast.LENGTH_SHORT).show();
        }

        return false;
    }

    public void removeTabForUri(Uri uri) {
        int index = viewPagerAdapter.fileUris.indexOf(uri);
        if (index != -1) {
            viewPagerAdapter.removeFragment(index);
        }
    }

    // You can use a single folderUri or the existing list of root URIs
    private void showCreateFileDialog(Uri folderUri) {
        ArrayList<Uri> folderUris = new ArrayList<>();
        ArrayList<String> folderNames = new ArrayList<>();

        // For simplicity, just use the requested folder and its parent/root name
        DocumentFile folder = DocumentFile.fromTreeUri(this, folderUri);
        if (folder != null) {
            folderUris.add(folderUri);
            folderNames.add(folder.getName()); // Use the folder's display name
        } else {
            Toast.makeText(this, "Could not access folder.", Toast.LENGTH_SHORT).show();
            return;
        }

        CreateFileDialog dialog = CreateFileDialog.newInstance(folderUris, folderNames, null);
        dialog.show(getSupportFragmentManager(), "CreateFileDialog");
    }

    // Add the utility method to check if a file is runnable (based on your existing logic)
    private boolean isRunnableFile(String mimeType) {
        return mimeType != null && (mimeType.contains("python") || mimeType.contains("java") || mimeType.contains("csrc") || mimeType.contains("c++src") || mimeType.contains("javascript") || mimeType.contains("html"));
    }

    public void populateFileList(final Uri uri, final int depth) {
        new Thread(() -> {
            try {
                String documentId = DocumentsContract.getTreeDocumentId(uri);
                Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, documentId);

                final List<FileItem> folders = new ArrayList<>();
                final List<FileItem> files = new ArrayList<>();
                final String folderName = DocumentsContract.getTreeDocumentId(uri);
                String folderNameToDisplay = "Storage/" + folderName.substring(8);
                Log.d("FileName", "populateFileList: folder name:" + folderNameToDisplay);
                try (Cursor cursor = getContentResolver().query(childrenUri, new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {

                    if (cursor != null && cursor.moveToFirst()) {
                        do {
                            String childDocId = cursor.getString(0);
                            String childName = cursor.getString(1);
                            String mimeType = cursor.getString(2);
                            boolean isDirectory = DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType);
                            Uri childUri = DocumentsContract.buildDocumentUriUsingTree(uri, childDocId);
                            if (isDirectory) {
                                folders.add(new FileItem(childUri, childName, true, depth, mimeType));
                            } else {
                                files.add(new FileItem(childUri, childName, false, depth, mimeType));
                            }
                        } while (cursor.moveToNext());
                    }
                }

                runOnUiThread(() -> {
                    currentFolderTitle.setText(folderNameToDisplay);
                    fileItems.clear();
                    folders.sort((a, b) -> a.displayName.compareToIgnoreCase(b.displayName));
                    files.sort((a, b) -> a.displayName.compareToIgnoreCase(b.displayName));
                    fileItems.addAll(folders);
                    fileItems.addAll(files);
                    filesAdapter.notifyDataSetChanged();
                });

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void openLeftNavigation() {
        if (drawerLayout != null) {
            drawerLayout.openDrawer(GravityCompat.START);
        }
    }

    public void closeLeftNavigation() {
        if (drawerLayout != null) {
            drawerLayout.closeDrawer(GravityCompat.START);
        }
    }

    public boolean createNewFile() {
        prepareFolderDataForDialog();
        // Use the 2-argument newInstance: fileContent will be null
        CreateFileDialog dialog = CreateFileDialog.newInstance(folderUris, folderNames);
        dialog.show(getSupportFragmentManager(), "CreateFileDialog");
        return true;
    }

    public boolean saveUntitledFile(byte[] currentContent) {
        prepareFolderDataForDialog();
        // Use the 3-argument newInstance: content is passed
        CreateFileDialog dialog = CreateFileDialog.newInstance(folderUris, folderNames, currentContent);
        dialog.show(getSupportFragmentManager(), "CreateFileDialog");
        return true;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        ArrayList<String> uris = new ArrayList<>();
        ArrayList<String> names = new ArrayList<>();
        for (Uri uri : viewPagerAdapter.fileUris) uris.add(uri.toString());
        names.addAll(viewPagerAdapter.fileNames);

        outState.putStringArrayList("tab_uris", uris);
        outState.putStringArrayList("tab_names", names);
        outState.putInt("current_tab", tabLayout.getSelectedTabPosition());
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        ArrayList<String> uris = savedInstanceState.getStringArrayList("tab_uris");
        ArrayList<String> names = savedInstanceState.getStringArrayList("tab_names");
        int currentTab = savedInstanceState.getInt("current_tab", 0);

        if (uris != null && names != null) {
            List<Uri> restoredUris = new ArrayList<>();
            for (String s : uris) restoredUris.add(Uri.parse(s));

            viewPagerAdapter = new ViewPagerAdapter(this, restoredUris);
            viewPagerAdapter.fileNames.clear();
            viewPagerAdapter.fileNames.addAll(names);

            viewPager2.setAdapter(viewPagerAdapter);

            new TabLayoutMediator(tabLayout, viewPager2, (tab, position) -> tab.setText(names.get(position))).attach();

            if (currentTab < viewPagerAdapter.getItemCount()) {
                tabLayout.selectTab(tabLayout.getTabAt(currentTab));
                viewPager2.setCurrentItem(currentTab);
            }
        }
    }

    private void refreshAll() {
        // 1. Initial check for essential components
        if (folderUri == null || filesAdapter == null || viewPagerAdapter == null || executor == null)
            return;

        // 2. Start UI updates (must run on Main Thread)
        runOnUiThread(() -> {
            // Find views only once if they are not already instance members
            ProgressBar filesLoadingProgressBar = findViewById(R.id.filesLoadingProgress);

            if (filesLoadingProgressBar != null)
                filesLoadingProgressBar.setVisibility(View.VISIBLE);
            if (refreshFolder != null) refreshFolder.setVisibility(View.GONE);
            if (collapseAllFolders != null) collapseAllFolders.setVisibility(View.GONE);
        });

        // 3. Execute file system/list manipulation on the background thread
        executor.execute(() -> {

            // ... (File system and file list logic remains the same)

            // Get URIs of open and potentially modified files to preserve their state/tab.
            List<Uri> openFileUris = new ArrayList<>();
            for (FilesAdapter.FileContentItem file : viewPagerAdapter.getOpenFilesContent()) {
                openFileUris.add(file.getUri());
            }

            // 4. Capture current expanded state using URIs
            List<Uri> expandedFolders = new ArrayList<>();
            // Iterate over a copy or use the adapter's list safely
            for (FileItem item : filesAdapter.fileList) {
                if (item.isDirectory && item.isExpanded) {
                    expandedFolders.add(item.uri);
                }
            }

            // 5. Refresh the list data (Thread-safe operation on adapter's list)
            filesAdapter.fileList.clear(); // Clear the underlying data list directly
            populateFileList(folderUri, 0); // Repopulate the list

            // 6. Final UI Updates and Re-expansion (must run on Main Thread)
            runOnUiThread(() -> {

                // Re-expand folders and refresh file icons
                for (int i = 0; i < filesAdapter.fileList.size(); i++) {
                    FileItem item = filesAdapter.fileList.get(i);

                    // Re-expansion: If the folder was expanded before refresh, expand it again.
                    if (item.isDirectory && expandedFolders.contains(item.uri)) {
                        // Call the correct, thread-safe expand method, which will queue the children load
                        filesAdapter.expandFolder(i);
                    }

                    // Icon Logic: This logic should ideally be inside the FileItem constructor or adapter's bind,
                    // but if it must be here, ensure 'getMimeType' is accessible and correct.
                    if (!item.isDirectory) {
                        // Note: If you have a cleaner way to get the MimeType outside the list loop, use it.
                        String type = item.mimeType != null ? item.mimeType : getMimeType(item.uri);
                        // Assuming FileItem has an updateIconResource method
                        // item.updateIconResource(type);
                    }
                }

                // *** NEW LOGIC: Refresh the content of the currently visible fragment ***
                int currentTabPos = tabLayout.getSelectedTabPosition();
                if (currentTabPos != -1) {
                    // ASSUMPTION: The adapter has a getFragment() method to retrieve the instance.
                    // You MUST implement this in your ViewPagerAdapter.
                    Fragment currentFragment = viewPagerAdapter.getFragment(currentTabPos);

                    // ASSUMPTION: TextFragment implements a public refreshContent() method.
                    // Cast and call the refresh method if the fragment type is correct.
                    if (currentFragment instanceof TextFragment) {
                        // Assuming TextFragment is your class name for the editor fragment
                        ((TextFragment) currentFragment).refreshContent();
                    }
                }
                // *** END NEW LOGIC ***

                filesAdapter.notifyDataSetChanged();

                // Set final visibility
                ProgressBar filesLoadingProgressBar = findViewById(R.id.filesLoadingProgress);
                if (filesLoadingProgressBar != null)
                    filesLoadingProgressBar.setVisibility(View.GONE);
                if (refreshFolder != null) refreshFolder.setVisibility(View.VISIBLE);
                if (collapseAllFolders != null) collapseAllFolders.setVisibility(View.VISIBLE);
            });
        });
    }

    public String getMimeType(Uri uri) {
        String type = getContentResolver().getType(uri);
        if (type == null) {
            String ext = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
            if (ext != null) {
                type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase());
            }
        }
        return type;
    }

    private void saveLastFolder(Uri uri) {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        if (uri != null) {
            editor.putString(LAST_FOLDER_URI_KEY, uri.toString());
        } else {
            editor.remove(LAST_FOLDER_URI_KEY);
        }
        editor.apply();
    }

    private void restoreLastFolder() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String uriString = prefs.getString(LAST_FOLDER_URI_KEY, null);
        if (uriString != null) {
            try {
                Uri lastFolderUri = Uri.parse(uriString);
                int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                getContentResolver().takePersistableUriPermission(lastFolderUri, takeFlags);

                folderUri = lastFolderUri;
                currentDirectoryUri = lastFolderUri;

                if (filesList == null) {
                    filesList = findViewById(R.id.filesList);
                    filesList.setLayoutManager(new LinearLayoutManager(this));
                    filesAdapter = new FilesAdapter(MainActivity.this, fileItems, this, this::requestSaveAs);
                    filesList.setAdapter(filesAdapter);
                    collapseAllFolders.setOnClickListener(v -> filesAdapter.collapseAllFolders());
                    refreshFolder.setOnClickListener(v -> refreshFileList());
                }

                ProgressBar filesLoadingProgressBar = findViewById(R.id.filesLoadingProgress);
                filesLoadingProgressBar.setVisibility(View.VISIBLE);
                refreshFolder.setVisibility(View.GONE);
                collapseAllFolders.setVisibility(View.GONE);

                executor.execute(() -> {
                    populateFileList(lastFolderUri, 0);
                    runOnUiThread(() -> {
                        if (filesAdapter != null) {
                            filesAdapter.notifyDataSetChanged();
                        }
                        filesLoadingProgressBar.setVisibility(View.GONE);
                        refreshFolder.setVisibility(View.VISIBLE);
                        collapseAllFolders.setVisibility(View.VISIBLE);
                    });
                });
            } catch (SecurityException e) {
                e.printStackTrace();
                Toast.makeText(this, "Permission to access the last folder was revoked.", Toast.LENGTH_LONG).show();
                saveLastFolder(null);
            }
        }
    }

    @Override
    public void onFileCreated(String fileName, Uri fileUri, @Nullable byte[] fileContent) {
        // 1. Handle "Save As" for untitled content
        if (fileContent != null) {
            // If content is present, this was a "Save As" operation.

            // Save the content to the newly created fileUri on a background thread
            // (This is crucial as File I/O can block the UI)
            saveContentToFile(fileUri, fileContent, fileName);

        } else {
            // 2. Handle "Create New File" operation (content is null, file is empty)
            openFileInViewPager(fileUri, fileName);
        }
    }

    private void saveContentToFile(Uri fileUri, byte[] content, String fileName) {
        new Thread(() -> {
            try {
                // Write the content to the new URI
                try (OutputStream os = getContentResolver().openOutputStream(fileUri)) {
                    if (os != null) {
                        os.write(content);
                    }
                }

                // After successful save, update the UI on the main thread
                runOnUiThread(() -> {
                    // Remove the old 'Untitled' tab and open the new file tab
                    // First, find and remove the 'Untitled' tab
                    int untitledPos = viewPagerAdapter.findTabPositionByName("Untitled");
                    if (untitledPos != -1) {
                        viewPagerAdapter.removeTab(untitledPos);
                    }

                    // Now, open the newly saved file
                    openFileInViewPager(fileUri, fileName);
                    Toast.makeText(this, "File saved successfully!", Toast.LENGTH_SHORT).show();
                });

            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error saving file: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }// In EditorActivity.java

    private void openFileInViewPager(Uri fileUri, String fileName) {
        // 1. Use the adapter to add the tab or find its existing position
        int position = viewPagerAdapter.addTab(fileUri, fileName, false);

        // 2. Switch the ViewPager2 to the newly created/found position
        viewPager2.setCurrentItem(position, true);

        // 3. Force the menu to update (in case the new tab has different buttons)
        invalidateOptionsMenu();
    }

    @Override
    public void requestSaveAs(byte[] content) {
        // 1. Ensure you have folders to save to
        prepareFolderDataForDialog(); // Call the method to populate folderUris/folderNames

        if (folderUris.isEmpty()) {
            Toast.makeText(this, "Please select a folder with write permission first.", Toast.LENGTH_LONG).show();
            return;
        }

        // 2. Launch the CreateFileDialog in "Save As" mode
        // The dialog will use the content to save to the new location.
        CreateFileDialog dialog = CreateFileDialog.newInstance(folderUris, folderNames, content);
        dialog.show(getSupportFragmentManager(), "SaveAsFileDialog");
    }// In MainActivity.java

    public void openFilePicker() {
        // This intent opens the system's file picker
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);

        // Files must be openable (readable)
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        // Set type to text and code files, or use "*/*" for everything
        intent.setType("*/*");

        // Optionally, suggest specific MIME types for the picker to filter by
        String[] mimeTypes = {"text/*",                   // Accept all files starting with "text/" (plain, html, css, markdown, etc.)
                "application/json",         // JSON files
                "application/xml",          // XML files
                "application/javascript",   // JavaScript files
                "application/x-java-source",// Java source files
                "text/x-csrc",              // C source files
                "text/x-c++src",            // C++ source files
                "text/x-python",            // Python source files
                "image/*",                  // All image types
                "audio/*",                  // All audio types
                "video/*"                   // All video types
        };
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);

        startActivityForResult(intent, REQUEST_CODE_OPEN_FILE);
    }// In MainActivity.java

    public FileItem getSelectedFileItem() {
        return selectedFileItem;
    }

    public void setSelectedFileItem(FileItem selectedFileItem) {
        this.selectedFileItem = selectedFileItem;
    }

//    private String getFileName(Uri uri) {
//        String result = null;
//        if (uri.getScheme().equals("content")) {
//            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
//                if (cursor != null && cursor.moveToFirst()) {
//                    // Get the file's display name
//                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
//                    if (nameIndex != -1) {
//                        result = cursor.getString(nameIndex);
//                    }
//                }
//            } catch (Exception e) {
//                Log.e("MainActivity", "Error getting file name from cursor.", e);
//            }
//        }
//
//        // Fallback to the last path segment if content resolver failed or URI scheme is not 'content'
//        if (result == null) {
//            result = uri.getLastPathSegment();
//        }
//        return result;
//    }
}
