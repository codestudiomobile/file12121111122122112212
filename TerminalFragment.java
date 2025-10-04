package com.codestudio.mobile;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class TerminalFragment extends Fragment {

    // Scaling constants
    private final float MIN_SCALE = 0.5f;
    private final float MAX_SCALE = 3.0f;
    private boolean executionCompleted = false;
    private TextView output;
    private EditText userInput;
    private ScrollView scrollView;
    private ConsoleInputListener listener;
    private float scaleFactor = 1f;
    private float baseSizeSp;
    private ScaleGestureDetector scaleDetector;
    private Uri selfUri;

    public static TerminalFragment newInstance(Uri uri) {
        TerminalFragment fragment = new TerminalFragment();
        Bundle args = new Bundle();
        args.putParcelable("uri", uri);
        fragment.setArguments(args);
        Log.d("TerminalFragment", "newInstance: created");
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getActivity() instanceof ConsoleInputListener) {
            listener = (ConsoleInputListener) getActivity();
        } else {
            throw new RuntimeException(getActivity().toString() + " must implement ConsoleInputListener");
        }
    }

    public boolean isRunTab() {
        Bundle args = getArguments();
        if (args != null) {
            Uri uri = args.getParcelable("uri");
            return uri != null && "run".equals(uri.getScheme());
        }
        return false;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_terminal, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        output = view.findViewById(R.id.output);
        userInput = view.findViewById(R.id.userInput);
        scrollView = view.findViewById(R.id.consoleScrollView);
        Log.d("TerminalFragment", "onViewCreated: created success");
        Bundle args = getArguments();
        if (args != null) {
            selfUri = args.getParcelable("uri");
        }

        // 1. Initialize Text Scaling Logic (Pinch-to-zoom)
        baseSizeSp = output.getTextSize() / getResources().getDisplayMetrics().scaledDensity;
        scaleDetector = new ScaleGestureDetector(requireContext(), new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(@NonNull ScaleGestureDetector detector) {
                scaleFactor *= detector.getScaleFactor();
                scaleFactor = Math.max(MIN_SCALE, Math.min(scaleFactor, MAX_SCALE));
                float newSizeSp = baseSizeSp * scaleFactor;
                userInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, newSizeSp);
                output.setTextSize(TypedValue.COMPLEX_UNIT_SP, newSizeSp);
                return true;
            }
        });

        output.setOnTouchListener((v, event) -> {
            if (event.getPointerCount() > 1) v.getParent().requestDisallowInterceptTouchEvent(true);
            scaleDetector.onTouchEvent(event);
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)
                v.getParent().requestDisallowInterceptTouchEvent(false);
            return false;
        });

        // 2. Set up Input Listener (Enter/Done key press)
        userInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                    (event != null && event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {

                String input = v.getText().toString();
                v.setText("");

                if (executionCompleted && selfUri != null && listener instanceof MainActivity) {
                    ((MainActivity) listener).removeTabForUri(selfUri);
                    return true;
                }

                if (listener != null) {
                    listener.onUserInputSubmitted(input);
                }

                return true;
            }
            return false;
        });

        setAwaitingInput(false);
        if (isRunTab() && selfUri != null) {
            String command = CommandFetcher.getCommand(requireContext(), selfUri);
            if (command != null && listener != null) {
                appendOutput("Executing: " + command + "\n");
                new TermuxRunner(requireContext()).executeCommandInternally(command, listener);
            }
        }
    }

    public void appendOutput(String newOutput) {
        if (output != null) {
            output.append(newOutput + "\n");

            if (newOutput.contains("Execution finished")) {
                executionCompleted = true;
                output.append("Press any key to continue...\n");
                setAwaitingInput(true);
            }

            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
        }
    }

    // --- Public methods for MainActivity to control the UI ---

    public void clearOutput() {
        if (output != null) {
            output.setText("");
        }
    }

    public void setAwaitingInput(boolean isWaiting) {
        if (userInput != null) {
            if (isWaiting) {
                /*userInput.setVisibility(View.VISIBLE);*/
                userInput.requestFocus();
            } else {
                /*userInput.setVisibility(View.GONE);*/
                userInput.clearFocus();
            }
        }
    }

    public interface ConsoleInputListener {
        void onUserInputSubmitted(String input);

        void onOutputReceived(String output);

        void onExecutionComplete();

    }
}
