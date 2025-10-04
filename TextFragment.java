package com.codestudio.mobile;

import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

public class TextFragment extends Fragment implements TextWatcher {

    private static final String ARG_URI = "file_uri";
    private final float MIN_SCALE = 0.5f;
    private final float MAX_SCALE = 3.0f;

    private TextView lineNumbers;
    private EditText fileContent;
    private float scaleFactor = 1f;
    private float baseSizeSp;
    private ScaleGestureDetector scaleDetector;

    // Change this to private to follow encapsulation
    private boolean isSaved = false;
    private Uri fileUri;

    public static TextFragment newInstance(Uri uri) {
        TextFragment fragment = new TextFragment();
        Bundle args = new Bundle();
        args.putParcelable(ARG_URI, uri);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            fileUri = getArguments().getParcelable(ARG_URI);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_text, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        lineNumbers = view.findViewById(R.id.lineNumbers);
        fileContent = view.findViewById(R.id.fileContent);

        loadFileContent();

        fileContent.getViewTreeObserver().addOnGlobalLayoutListener(() -> updateLineNumbers());

        fileContent.addTextChangedListener(this);

        baseSizeSp = fileContent.getTextSize() / getResources().getDisplayMetrics().scaledDensity;
        scaleDetector = new ScaleGestureDetector(requireContext(), new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(@NonNull ScaleGestureDetector detector) {
                scaleFactor *= detector.getScaleFactor();
                scaleFactor = Math.max(MIN_SCALE, Math.min(scaleFactor, MAX_SCALE));
                float newSizeSp = baseSizeSp * scaleFactor;
                lineNumbers.setTextSize(TypedValue.COMPLEX_UNIT_SP, newSizeSp);
                fileContent.setTextSize(TypedValue.COMPLEX_UNIT_SP, newSizeSp);
                return true;
            }
        });

        fileContent.setOnTouchListener((v, event) -> {
            if (event.getPointerCount() > 1) v.getParent().requestDisallowInterceptTouchEvent(true);
            scaleDetector.onTouchEvent(event);
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)
                v.getParent().requestDisallowInterceptTouchEvent(false);
            return false;
        });
    }

    // --- TextWatcher methods ---
    @Override
    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
    }

    @Override
    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        updateLineNumbers();
    }

    @Override
    public void afterTextChanged(Editable editable) {
        isSaved = false;
    }

    private void updateLineNumbers() {
        if (fileContent != null && lineNumbers != null) {
            int lineCount = fileContent.getLineCount();
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i <= lineCount; i++) sb.append(i).append("\n");
            lineNumbers.setText(sb.toString());
        }
    }

    public byte[] getContents() {
        if (fileContent != null)
            return fileContent.getText().toString().getBytes(StandardCharsets.UTF_8);
        return new byte[0];
    }

    // Public getter method for the isSaved status
    public boolean isSaved() {
        return isSaved;
    }

    // Public setter method for the isSaved status
    public void setSaved(boolean saved) {
        isSaved = saved;
    }

    private boolean isTextFile(Uri uri) {
        String mimeType = requireContext().getContentResolver().getType(uri);
        if (mimeType == null) {
            String ext = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
            if (ext != null)
                mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase());
        }
        if (mimeType == null) return false;
        return mimeType.startsWith("text/") || mimeType.equals("application/json") || mimeType.equals("application/xml");
    }

    private boolean isProbablyText(Uri uri) {
        final int SAMPLE = 1024;
        try (InputStream is = requireContext().getContentResolver().openInputStream(uri)) {
            if (is == null) return false;
            byte[] buf = new byte[SAMPLE];
            int read = is.read(buf);
            if (read <= 0) return false;
            int nonPrintable = 0;
            for (int i = 0; i < read; i++) {
                byte b = buf[i];
                if (b == 9 || b == 10 || b == 13) continue;
                if (b < 0x20 || b > 0x7E) nonPrintable++;
            }
            return ((double) nonPrintable / read) < 0.3;
        } catch (Exception e) {
            return false;
        }
    }

    private void loadFileContent() {
        if (fileUri == null || fileUri.equals(ViewPagerAdapter.UNTITLED_FILE_URI)) return;

        new Thread(() -> {
            boolean readable = isTextFile(fileUri) && isProbablyText(fileUri);

            requireActivity().runOnUiThread(() -> {
                if (!readable) {
                    Toast.makeText(getContext(), "Unsupported or non-text file", Toast.LENGTH_SHORT).show();
                    fileContent.setText("");
                    return;
                }

                try (InputStream inputStream = requireContext().getContentResolver().openInputStream(fileUri)) {
                    if (inputStream != null) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                        String content = reader.lines().collect(Collectors.joining("\n"));
                        reader.close();
                        fileContent.setText(content);
                        updateLineNumbers();
                        isSaved = true;
                    }
                } catch (Exception e) {
                    Toast.makeText(getContext(), "Error reading file", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    public void setFileUri(Uri newUri) {
        this.fileUri = newUri;
        // Optionally update the fragment's arguments as well if you need to survive recreation
        if (getArguments() != null) {
            getArguments().putParcelable(ARG_URI, newUri);
        }
    }

    public void refreshContent() {
        loadFileContent();
    }
}
