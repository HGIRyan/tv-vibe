package com.gathin.tvassistant;

import android.app.*;
import android.Manifest;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.os.*;
import android.util.Log;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns one microphone stream for both wake detection and buffered cloud transcription. */
public final class AssistantService extends Service {
    static final String STATUS = "com.gathin.tvassistant.STATUS";
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile AudioRecord recorder;
    private Thread worker;
    private AssistantFeedback feedback;

    @Override public void onCreate() {
        super.onCreate();
        feedback = new AssistantFeedback(this);
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("listening", "Hey TV microphone", NotificationManager.IMPORTANCE_LOW));
        PendingIntent stop = PendingIntent.getService(this, 1,
            new Intent(this, AssistantService.class).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent setup = PendingIntent.getActivity(this, 2,
            new Intent(this, SetupActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, "listening")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("Hey TV is listening")
            .setContentText("Local wake detection • tap to configure").setOngoing(true).setContentIntent(setup)
            .addAction(new Notification.Action.Builder(null, "Stop listening", stop).build()).build();
        startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        if (running.compareAndSet(false, true)) {
            worker = new Thread(this::listen, "hey-tv-audio");
            worker.start();
        }
        // Do not silently restart mic capture after a user stop or process kill.
        return START_NOT_STICKY;
    }

    private void listen() {
        AudioBuffer buffer = new AudioBuffer(4000);
        AudioRecord audio = null;
        AcousticEchoCanceler echo = null;
        try (Model model = new Model(ModelFiles.unpack(this).getAbsolutePath());
             Recognizer wake = new Recognizer(model, AudioBuffer.RATE)) {
            if (!running.get()) return;
            int minimum = AudioRecord.getMinBufferSize(AudioBuffer.RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (minimum <= 0) throw new IllegalStateException("Unsupported microphone format");
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                throw new SecurityException("Microphone permission required");
            audio = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, AudioBuffer.RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(minimum * 2, 6400));
            recorder = audio;
            if (audio.getState() != AudioRecord.STATE_INITIALIZED) throw new IllegalStateException("Microphone unavailable");
            if (AcousticEchoCanceler.isAvailable()) {
                echo = AcousticEchoCanceler.create(audio.getAudioSessionId());
                if (echo != null) echo.setEnabled(true);
            }
            audio.startRecording();
            if (audio.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) throw new IllegalStateException("Microphone did not start");
            listeningStatus();
            byte[] frame = new byte[640]; // 20 ms of 16 kHz, signed 16-bit mono PCM.
            AudioBuffer.Capture capture = null;
            double noise = 100;
            long silentBytes = 0;
            while (running.get()) {
                int length = audio.read(frame, 0, frame.length);
                if (!running.get()) break;
                if (length <= 0) throw new IllegalStateException("Microphone read failed");
                buffer.append(frame, length);
                double rms = AudioBuffer.rms(frame, length);
                silentBytes = rms < 1 ? silentBytes + length : 0;
                if (silentBytes > AudioBuffer.RATE * 2 * 10) {
                    status("Microphone is returning silence; check TV mic switch, input routing, and permissions");
                    silentBytes = 0;
                }
                if (capture == null) {
                    boolean end = wake.acceptWaveForm(frame, length);
                    JSONObject result = new JSONObject(end ? wake.getResult() : wake.getPartialResult());
                    if (CommandPolicy.hasWake(result.optString(end ? "text" : "partial"))) {
                        capture = new AudioBuffer.Capture(buffer.snapshot(), noise);
                        feedback.wake();
                        status("Wake detected; capturing command");
                    } else {
                        // Conservative background estimate: don't let loud speech raise the gate indefinitely.
                        noise = 0.995 * noise + 0.005 * Math.min(rms, 600);
                    }
                } else if (capture.append(frame, length)) {
                    byte[] pcm = capture.finish();
                    capture = null;
                    // Capture is bounded; process one request at a time, never queue stale TV commands.
                    audio.stop();
                    if (running.get()) process(pcm);
                    Arrays.fill(pcm, (byte) 0);
                    buffer.clear();
                    wake.reset();
                    if (running.get()) {
                        audio.startRecording();
                        listeningStatus();
                    }
                }
            }
            Arrays.fill(frame, (byte) 0);
        } catch (Exception | LinkageError failure) {
            if (running.get()) status("Listener stopped: " + failure.getClass().getSimpleName()
                + ". Check microphone permission and bundled model.");
        } finally {
            running.set(false);
            buffer.clear();
            recorder = null;
            if (echo != null) echo.release();
            if (audio != null) audio.release();
            stopSelf();
        }
    }

    private void process(byte[] pcm) {
        byte[] wav = null;
        try {
            String key = new SecretStore(this).load();
            if (key.isEmpty()) {
                feedback.result("Add an API key");
                result("Captured " + pcm.length / 32 + " ms. No API key; nothing uploaded."); return;
            }
            long started = SystemClock.elapsedRealtime();
            OpenAiClient client = new OpenAiClient(key, InstalledApps.discover(this));
            status("Transcribing buffered utterance");
            feedback.processing();
            wav = AudioBuffer.wav(pcm);
            String command = CommandPolicy.commandAfterWake(client.transcribe(wav));
            if (!running.get() || command.isEmpty()) {
                feedback.result("No command detected");
                result("No confirmed wake phrase and command; no action"); return;
            }
            status("Choosing TV action");
            CommandPolicy.Selection action = client.decide(command);
            if (!running.get() || SystemClock.elapsedRealtime() - started > 15000) {
                feedback.result("Command timed out");
                result("Request canceled or expired; no action"); return;
            }
            if (getSharedPreferences("config", 0).getBoolean("dry_run", true)) {
                feedback.result("Test: " + AssistantFeedback.actionLabel(action));
                result("Dry run: " + action); return;
            }
            String outcome = new TvActions(this).execute(action);
            feedback.result(outcome.equals("App no longer launchable") ? "App unavailable" : AssistantFeedback.actionLabel(action));
            result(outcome);
        } catch (Exception failure) {
            feedback.result("Command failed");
            // Exception messages from network bodies or secret storage are never printed.
            result("Request failed (" + failure.getClass().getSimpleName() + "); no action");
        } finally { if (wav != null) Arrays.fill(wav, (byte) 0); }
    }

    private void result(String text) {
        getSharedPreferences("status", 0).edit().putString("result", text).apply();
        status(text);
    }

    private void listeningStatus() {
        String last = getSharedPreferences("status", 0).getString("result", "");
        status("Listening for Hey TV (local)" + (last.isEmpty() ? "" : "\nLast result: " + last));
    }

    private void status(String text) {
        Log.i("HeyTV", text);
        getSharedPreferences("status", 0).edit().putString("last", text).apply();
        sendBroadcast(new Intent(STATUS).setPackage(getPackageName()).putExtra("status", text));
    }

    @Override public void onDestroy() {
        running.set(false);
        if (feedback != null) feedback.close();
        AudioRecord audio = recorder;
        if (audio != null) try { audio.stop(); } catch (IllegalStateException ignored) { }
        if (worker != null) worker.interrupt();
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
