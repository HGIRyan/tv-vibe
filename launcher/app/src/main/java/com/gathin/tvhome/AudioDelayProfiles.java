package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-app audio delay. While an app listed in config.xml (audio_delay_profiles) is in front, the TV's own
 * delay setting is changed; leaving the app puts the original value back.
 *
 * Each config item is "package|slot=value,slot=value" or "package|*=value" (every slot). Values are written to
 * the secure setting tcl_app_setting_audio_delay, a list of numbers such as "0;0;0;...;". The original list is
 * remembered on disk, so a crash or a service restart still restores it. Needs WRITE_SECURE_SETTINGS
 * (pm grant com.gathin.tvhome android.permission.WRITE_SECURE_SETTINGS).
 *
 * Live override, no rebuild: settings put secure tvhome_audio_delay_override 'pkg|*=-150#pkg2|3=-80'
 * (items separated by #; an override wins over config.xml for the same package; delete the key to go back).
 * Changing it re-applies at once for the app in front.
 */
final class AudioDelayProfiles {

    private static final String TAG = "TvHomeAudio";
    private static final String KEY = "tcl_app_setting_audio_delay";
    private static final String PREFS = "audio_delay";
    private static final String SAVED = "original";

    private final Context ctx;
    private final SharedPreferences prefs;
    private static final String OVERRIDE_KEY = "tvhome_audio_delay_override";

    private final Map<String, String> specs = new HashMap<>();
    private final ContentObserver observer;
    private String lastPkg;

    /** @param onOverrideChanged run when the live override changes, so the caller can re-check the app in front */
    AudioDelayProfiles(Context ctx, Runnable onOverrideChanged) {
        this.ctx = ctx;
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        load();
        observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                load();
                if (onOverrideChanged != null) onOverrideChanged.run();
            }
        };
        ctx.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(OVERRIDE_KEY), false, observer);
    }

    void close() {
        ctx.getContentResolver().unregisterContentObserver(observer);
    }

    private void load() {
        specs.clear();
        for (String item : ctx.getResources().getStringArray(R.array.audio_delay_profiles)) add(item);
        String live = Settings.Secure.getString(ctx.getContentResolver(), OVERRIDE_KEY);
        if (live != null) for (String item : live.split("#")) add(item);
    }

    private void add(String item) {
        int bar = item.indexOf('|');
        if (bar > 0) specs.put(item.substring(0, bar).trim(), item.substring(bar + 1).trim());
    }

    /** Call with the package now in front. Applies its profile, or restores the original if it has none. */
    void onForeground(String pkg) {
        lastPkg = pkg;
        String spec = pkg == null ? null : specs.get(pkg);
        if (spec == null) restore();
        else apply(pkg, spec);
    }

    void restore() {
        String original = prefs.getString(SAVED, null);
        if (original == null) return;
        if (put(original)) {
            prefs.edit().remove(SAVED).apply();
            Log.i(TAG, "restored original delay");
        }
    }

    private void apply(String pkg, String spec) {
        String original = prefs.getString(SAVED, null);
        if (original == null) {
            original = Settings.Secure.getString(ctx.getContentResolver(), KEY);
            if (original == null) return;   // this TV has no such setting
            prefs.edit().putString(SAVED, original).apply();
        }
        String[] slots = original.split(";", -1);
        for (String part : spec.split(",")) {
            String[] kv = part.split("=");
            if (kv.length != 2) continue;
            String value = kv[1].trim();
            if (kv[0].trim().equals("*")) {
                for (int i = 0; i < slots.length; i++) if (!slots[i].isEmpty()) slots[i] = value;
            } else {
                try {
                    int i = Integer.parseInt(kv[0].trim());
                    if (i >= 0 && i < slots.length) slots[i] = value;
                } catch (NumberFormatException ignored) {
                }
            }
        }
        String result = String.join(";", slots);
        if (result.equals(Settings.Secure.getString(ctx.getContentResolver(), KEY))) return;
        if (put(result)) Log.i(TAG, "applied " + pkg + " -> " + result);
    }

    private boolean put(String value) {
        try {
            return Settings.Secure.putString(ctx.getContentResolver(), KEY, value);
        } catch (SecurityException e) {
            Log.w(TAG, "WRITE_SECURE_SETTINGS not granted", e);
            return false;
        }
    }
}
