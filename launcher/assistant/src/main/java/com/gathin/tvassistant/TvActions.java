package com.gathin.tvassistant;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.SystemClock;
import android.view.KeyEvent;

final class TvActions {
    private final Context context;
    TvActions(Context context) { this.context = context; }

    String execute(CommandPolicy.Selection selection) {
        CommandPolicy.Action action = selection.action;
        AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        switch (action) {
            case PAUSE: return playback(false, audio);
            case PLAY: return playback(true, audio);
            case OPEN_APP: return selection.app == null ? "No action" : open(selection.app.packageName);
            case GO_HOME:
                context.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return "Home requested";
            case MUTE: case UNMUTE:
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                    action == CommandPolicy.Action.MUTE ? AudioManager.ADJUST_MUTE : AudioManager.ADJUST_UNMUTE,
                    AudioManager.FLAG_SHOW_UI);
                return "Volume requested: " + action;
            case VOLUME_UP: case VOLUME_DOWN:
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                    action == CommandPolicy.Action.VOLUME_UP ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER,
                    AudioManager.FLAG_SHOW_UI);
                return "Volume requested: " + action;
            default: return "No action";
        }
    }

    private String open(String packageName) {
        Intent intent = context.getPackageManager().getLeanbackLaunchIntentForPackage(packageName);
        if (intent == null) intent = context.getPackageManager().getLaunchIntentForPackage(packageName);
        if (intent == null) return "App no longer launchable";
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        return "App launch requested";
    }

    private String playback(boolean play, AudioManager audio) {
        // Android routes these to its selected media session; do not request system-only permissions.
        int code = play ? KeyEvent.KEYCODE_MEDIA_PLAY : KeyEvent.KEYCODE_MEDIA_PAUSE;
        long now = SystemClock.uptimeMillis();
        audio.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0));
        audio.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0));
        return "Media key sent: " + (play ? "PLAY" : "PAUSE");
    }
}
