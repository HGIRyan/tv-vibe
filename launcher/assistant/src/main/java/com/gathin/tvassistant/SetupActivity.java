package com.gathin.tvassistant;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.*;

public final class SetupActivity extends Activity {
    private TextView status;
    private EditText key;
    private AssistantFeedback feedback;
    private final BroadcastReceiver updates = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { status.setText(intent.getStringExtra("status")); }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        feedback = new AssistantFeedback(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 24);
        TextView title = new TextView(this);
        title.setText("Hey TV — voice controls"); title.setTextSize(28); layout.addView(title);
        TextView description = new TextView(this);
        description.setText("Say Hey TV, pause / resume / open Hulu. Local software wake detection; OpenAI transcribes and chooses an action. "
            + "A four-second RAM buffer includes speech immediately before detection. Each upload includes that pre-roll. "
            + "No audio files are saved. Without a key, only capture duration is reported. "
            + "TCL microphone access and standby listening need testing.");
        layout.addView(description);
        key = new EditText(this);
        key.setHint("OpenAI API key (optional for local buffer test)");
        key.setSingleLine(true);
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setSaveEnabled(false); key.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO);
        layout.addView(key);
        Button save = new Button(this); save.setText("Save key on TV");
        save.setOnClickListener(v -> {
            String value = key.getText().toString().trim();
            if (value.isEmpty() || value.contains("\n") || value.contains("\r")) { status.setText("Enter a single API key"); return; }
            try { new SecretStore(this).save(value); key.setText(""); status.setText("Key encrypted on this TV"); }
            catch (Exception ignored) { status.setText("Key could not be saved"); }
        }); layout.addView(save);
        Button clear = new Button(this); clear.setText("Remove saved key");
        clear.setOnClickListener(v -> { new SecretStore(this).clear(); key.setText(""); status.setText("Saved key removed"); }); layout.addView(clear);
        Switch dry = new Switch(this); dry.setText("Dry run — choose actions without executing them");
        dry.setChecked(getSharedPreferences("config", 0).getBoolean("dry_run", true));
        dry.setOnCheckedChangeListener((button, checked) -> getSharedPreferences("config", 0).edit().putBoolean("dry_run", checked).apply());
        layout.addView(dry);
        Button start = new Button(this); start.setText("Start listening");
        start.setOnClickListener(v -> {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1);
            else startListener();
        }); layout.addView(start);
        Button stop = new Button(this); stop.setText("Stop listening");
        stop.setOnClickListener(v -> { stopService(new Intent(this, AssistantService.class)); status.setText("Listening stopped"); }); layout.addView(stop);
        Button preview = new Button(this); preview.setText("Preview wake feedback");
        preview.setOnClickListener(v -> feedback.preview()); layout.addView(preview);
        status = new TextView(this); status.setText(getSharedPreferences("status", 0).getString("last", "Ready. Listening is off."));
        layout.addView(status);
        ScrollView scroll = new ScrollView(this); scroll.addView(layout); setContentView(scroll);
        start.requestFocus();
    }

    private void startListener() {
        try { startForegroundService(new Intent(this, AssistantService.class)); status.setText("Loading local wake model…"); }
        catch (RuntimeException ignored) { status.setText("Cannot start microphone service. Keep setup visible and retry."); }
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(request, permissions, grants);
        if (request == 1 && grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) startListener();
        else status.setText("Microphone permission is needed");
    }
    @Override protected void onStart() {
        super.onStart();
        registerReceiver(updates, new IntentFilter(AssistantService.STATUS), RECEIVER_NOT_EXPORTED);
    }
    @Override protected void onStop() { unregisterReceiver(updates); super.onStop(); }
    @Override protected void onDestroy() { feedback.close(); super.onDestroy(); }
}
