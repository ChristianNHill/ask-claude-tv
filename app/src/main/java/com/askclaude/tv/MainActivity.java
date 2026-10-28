package com.askclaude.tv;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolUseBlock;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Listens through Google's voice input, asks Claude which action fits, and runs it:
 * open a web page, open an app, search YouTube, or show a short answer.
 */
public class MainActivity extends Activity {
    private static final String TAG = "AskClaude";
    private static final int REQUEST_MIC = 1;
    private static final String MODEL = "claude-opus-5-5";
    private static final String YOUTUBE = "com.google.android.youtube.tv";
    private static final String TV_BRO = "com.phlox.tvwebbrowser";

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private static final int DARK = 0xFF141413;
    private static final int IVORY = 0xFFFAF9F5;
    private static final int MID_GRAY = 0xFFB0AEA5;

    private static final int ORANGE = 0xFFD97757;
    private static final int BORDER = 0xFF3D3D3A;

    private EditText input;
    private Button send;
    private Button speak;
    private TextView status;
    private ImageView spark;
    private ObjectAnimator pulse;
    private SpeechRecognizer recognizer;
    private boolean busy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Anthropic brand palette (dark variant) with Poppins headings and Lora body text.
        getWindow().setBackgroundDrawable(new ColorDrawable(DARK));
        Typeface poppins = getResources().getFont(R.font.poppins_medium);
        Typeface lora = getResources().getFont(R.font.lora_regular);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(dp(96), dp(64), dp(96), dp(48));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        spark = new ImageView(this);
        spark.setImageResource(R.drawable.spark);
        header.addView(spark, new LinearLayout.LayoutParams(dp(36), dp(36)));
        TextView title = new TextView(this);
        title.setText("Ask Claude");
        title.setTypeface(poppins);
        title.setTextColor(IVORY);
        title.setTextSize(28);
        title.setPadding(dp(16), 0, 0, 0);
        header.addView(title);

        // The request: filled by voice, editable with the on-screen keyboard; Enter sends it.
        input = new EditText(this);
        input.setTypeface(lora);
        input.setTextColor(IVORY);
        input.setHintTextColor(MID_GRAY);
        input.setHint("Say or type a request…");
        input.setTextSize(28);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setPadding(dp(24), dp(16), dp(24), dp(16));
        input.setBackground(outline(0x00000000, BORDER, IVORY));
        input.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE || enter) {
                submit();
                return true;
            }
            return false;
        });
        LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        fieldParams.topMargin = dp(40);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setPadding(0, dp(24), 0, 0);
        send = button("Send", poppins, outline(ORANGE, ORANGE, IVORY), DARK);
        send.setOnClickListener(v -> submit());
        speak = button("Speak", poppins, outline(0x00000000, BORDER, IVORY), IVORY);
        speak.setOnClickListener(v -> listen());
        buttons.addView(send);
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        gap.leftMargin = dp(16);
        buttons.addView(speak, gap);

        status = new TextView(this);
        status.setTypeface(lora);
        status.setTextColor(IVORY);
        status.setTextSize(24);
        status.setLineSpacing(0, 1.25f);
        status.setPadding(0, dp(32), 0, dp(24));

        TextView hint = new TextView(this);
        hint.setText("Up  edit the request      Enter or Send  run it      Back  close");
        hint.setTypeface(poppins);
        hint.setTextColor(MID_GRAY);
        hint.setTextSize(14);
        hint.setLetterSpacing(0.08f);

        root.addView(header);
        root.addView(input, fieldParams);
        root.addView(buttons);
        root.addView(status, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(hint);
        setContentView(root);
        // Keep the keyboard closed until the person moves Up to the text box and presses OK.
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        speak.requestFocus();

        pulse = ObjectAnimator.ofFloat(spark, "alpha", 1f, 0.3f);
        pulse.setDuration(700);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.setRepeatCount(ValueAnimator.INFINITE);

        if (apiKey() == null) {
            status.setText("No API key yet. Run ./set-key.sh on your computer, then open this again.");
        } else {
            listen();
        }
    }

    /** Rounded rectangle: fill and border, with an ivory border while focused. */
    private StateListDrawable outline(int fill, int border, int focusedBorder) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, rounded(fill, focusedBorder, 3));
        states.addState(new int[]{}, rounded(fill, border, 2));
        return states;
    }

    private GradientDrawable rounded(int fill, int border, int borderDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(14));
        d.setStroke(dp(borderDp), border);
        return d;
    }

    private Button button(String label, Typeface font, StateListDrawable background, int textColor) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTypeface(font);
        b.setTextSize(18);
        b.setTextColor(textColor);
        b.setBackground(background);
        b.setStateListAnimator(null);
        b.setPadding(dp(36), dp(10), dp(36), dp(10));
        return b;
    }

    /** Sends whatever is in the text box to Claude. */
    private void submit() {
        if (busy) return;
        String request = input.getText().toString().trim();
        if (request.isEmpty()) {
            status.setText("Say or type a request first.");
            return;
        }
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        handle(request);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    /** The spark pulses while the mic is open or Claude is thinking. */
    private void setActive(boolean active) {
        if (active) {
            if (!pulse.isStarted()) pulse.start();
        } else {
            pulse.cancel();
            spark.setAlpha(1f);
        }
    }

    private String apiKey() {
        return getSharedPreferences(KeyReceiver.PREFS, Context.MODE_PRIVATE).getString(KeyReceiver.KEY, null);
    }

    private void listen() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_MIC);
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.setText("Voice input isn't available. Turn Google Assistant back on (it provides speech-to-text).");
            return;
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(new Listener());
        }
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        busy = true;
        setActive(true);
        status.setText("Starting the mic…");
        recognizer.startListening(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (requestCode != REQUEST_MIC) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            listen();
        } else {
            status.setText("Ask Claude needs microphone access to hear you. Choose Speak to allow it, or type a request.");
        }
    }

    private class Listener implements RecognitionListener {
        @Override public void onReadyForSpeech(Bundle params) { status.setText("Listening… speak now."); }
        @Override public void onBeginningOfSpeech() { }
        @Override public void onRmsChanged(float rmsdB) { }
        @Override public void onBufferReceived(byte[] buffer) { }
        @Override public void onEndOfSpeech() { status.setText("Got it…"); }
        @Override public void onEvent(int eventType, Bundle params) { }

        @Override
        public void onPartialResults(Bundle partial) {
            List<String> words = partial.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (words != null && !words.isEmpty() && !words.get(0).isEmpty()) {
                input.setText(words.get(0));
            }
        }

        @Override
        public void onResults(Bundle results) {
            busy = false;
            setActive(false);
            List<String> heard = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (heard == null || heard.isEmpty() || heard.get(0).trim().isEmpty()) {
                status.setText("Didn't catch that. Choose Speak to try again, or type it.");
                speak.requestFocus();
                return;
            }
            input.setText(heard.get(0));
            input.setSelection(input.length());
            status.setText("Press OK to send it, or Up to edit first.");
            send.requestFocus();
        }

        @Override
        public void onError(int error) {
            busy = false;
            setActive(false);
            String why = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                    ? "Didn't hear anything."
                    : "Voice input failed (error " + error + ").";
            status.setText(why + " Choose Speak to try again, or type it.");
            speak.requestFocus();
        }
    }

    private void handle(String request) {
        Log.i(TAG, "request \"" + request + "\"");
        busy = true;
        setActive(true);
        status.setText("Thinking…");
        String key = apiKey();
        String apps = installedApps();
        String sites = getSharedPreferences(KeyReceiver.PREFS, Context.MODE_PRIVATE)
                .getString(KeyReceiver.SITES, null);
        worker.execute(() -> {
            String outcome;
            try {
                outcome = askClaude(key, apps, sites, request);
            } catch (UnauthorizedException e) {
                outcome = "The API key was rejected. Run ./set-key.sh again with a valid key.";
            } catch (RateLimitException e) {
                outcome = "Rate limited by the API. Wait a moment and send it again.";
            } catch (AnthropicServiceException e) {
                outcome = "The API returned an error (" + e.statusCode() + "). Send it again to retry.";
            } catch (RuntimeException e) {
                outcome = "Couldn't reach Claude: " + e.getMessage();
            }
            String shown = outcome;
            runOnUiThread(() -> {
                busy = false;
                setActive(false);
                if (shown != null) status.setText(shown);
                speak.requestFocus();
            });
        });
    }

    /** Lists launchable TV apps as "Label = package" lines for the system prompt. */
    private String installedApps() {
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<String> lines = new ArrayList<>();
        for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            lines.add(ri.loadLabel(pm) + " = " + pkg);
        }
        Collections.sort(lines);
        return String.join("\n", lines);
    }

    private static Tool tool(String name, String description, String param, String paramDescription) {
        return Tool.builder()
                .name(name)
                .description(description)
                .strict(true)
                .inputSchema(Tool.InputSchema.builder()
                        .properties(Tool.InputSchema.Properties.builder()
                                .putAdditionalProperty(param, JsonValue.from(Map.of(
                                        "type", "string", "description", paramDescription)))
                                .build())
                        .required(List.of(param))
                        .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                        .build())
                .build();
    }

    /** Runs on the worker thread. Returns text to show, or null when an action took over the screen. */
    private String askClaude(String key, String apps, String sites, String request) {
        AnthropicClient client = AnthropicOkHttpClient.builder().apiKey(key).build();
        String system = "You control an Android TV projector for its owner, who speaks a request through the remote. "
                + "Pick the single tool that best carries out the request and call it once. "
                + "For web pages, give a full https URL to the most relevant official page. "
                + "For apps, use a package name from the installed list exactly as written. "
                + "Use youtube_search for anything to watch on YouTube. "
                + "Use reply only when no other tool fits, with one or two short sentences.\n"
                + "When the owner names a specific website, go to that site and nowhere else. "
                + "If it is in the saved sites, use that exact address. "
                + "If you don't know its address, don't substitute a different site: use reply to say "
                + "you don't know that site and that it can be added to the saved sites.\n\n"
                + "Saved sites:\n" + (sites == null || sites.isEmpty() ? "(none)" : sites) + "\n\n"
                + "Installed apps:\n" + apps;

        MessageCreateParams params = MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(4096L)
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
                // Server-side fallback if a request is declined by a safety classifier.
                .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                .system(system)
                .addTool(tool("open_url", "Open a web page in the TV browser.", "url", "Full https URL"))
                .addTool(tool("open_app", "Open an installed app.", "package_name", "Package name from the installed list"))
                .addTool(tool("youtube_search", "Search YouTube on the TV.", "query", "Search terms"))
                .addTool(tool("reply", "Show a short text answer on screen.", "text", "One or two sentences"))
                .addUserMessage(request)
                .build();

        Message response = client.messages().create(params);
        if (response.stopReason().isPresent() && response.stopReason().get().equals(StopReason.REFUSAL)) {
            return "Claude declined that request.";
        }

        StringBuilder text = new StringBuilder();
        for (ContentBlock block : response.content()) {
            if (block.isToolUse()) {
                return runTool(block.asToolUse());
            }
            block.text().ifPresent(t -> text.append(t.text()));
        }
        return text.length() > 0 ? text.toString() : "Claude didn't pick an action. Try rephrasing.";
    }

    @SuppressWarnings("unchecked")
    private String runTool(ToolUseBlock use) {
        Map<String, Object> input = new ObjectMapper().convertValue(use._input(), Map.class);
        String name = use.name();
        Log.i(TAG, "action " + name + " " + input);
        switch (name) {
            case "open_url": {
                String url = String.valueOf(input.get("url"));
                Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                if (isInstalled(TV_BRO)) view.setPackage(TV_BRO);
                return start(view, "Opening " + url, "No browser can open web pages. Install TV Bro from the Play Store.");
            }
            case "open_app": {
                String pkg = String.valueOf(input.get("package_name"));
                PackageManager pm = getPackageManager();
                Intent launch = pm.getLeanbackLaunchIntentForPackage(pkg);
                if (launch == null) launch = pm.getLaunchIntentForPackage(pkg);
                if (launch == null) return "That app isn't installed (" + pkg + ").";
                return start(launch, "Opening " + pkg, "Couldn't open " + pkg + ".");
            }
            case "youtube_search": {
                String query = String.valueOf(input.get("query"));
                Intent search = new Intent(Intent.ACTION_SEARCH).setPackage(YOUTUBE)
                        .putExtra(SearchManager.QUERY, query);
                return start(search, "Searching YouTube for " + query, "YouTube isn't available.");
            }
            case "reply":
                return String.valueOf(input.get("text"));
            default:
                return "Claude asked for an unknown action: " + name;
        }
    }

    private boolean isInstalled(String pkg) {
        try {
            getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /** Starts an intent from the worker thread via the UI thread; returns the status line. */
    private String start(Intent intent, String success, String failure) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            if (intent.resolveActivity(getPackageManager()) == null) return failure;
            runOnUiThread(() -> startActivity(intent));
            return success;
        } catch (ActivityNotFoundException e) {
            return failure;
        }
    }

    @Override
    protected void onDestroy() {
        if (recognizer != null) recognizer.destroy();
        worker.shutdownNow();
        super.onDestroy();
    }
}
