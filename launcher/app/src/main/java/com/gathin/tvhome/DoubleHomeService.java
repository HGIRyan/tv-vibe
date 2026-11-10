package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Toast;

import java.util.ArrayDeque;
import java.util.List;

/**
 * Pressing the remote's settings (cog) button, which sends KEYCODE_NOTIFICATION on this TV, opens the recent-apps
 * drawer (RecentAppsDrawer). The press is consumed, so Settings does not open; every other key passes through.
 * The same service force-stops the apps picked in the drawer by driving Settings' own "Force stop" button
 * (app info page, then OK), since only Settings may force-stop another app. The class name is kept from an
 * earlier double-press design. Opt-in: enable com.gathin.tvhome/.DoubleHomeService.
 */
public class DoubleHomeService extends AccessibilityService {

    static DoubleHomeService instance;

    /** TCL remote app buttons. TCL's own handler for these lives in com.tcl.ttvs (tracking), which is disabled. */
    private static final android.util.SparseArray<String> APP_KEYS = new android.util.SparseArray<>();
    static {
        APP_KEYS.put(4062, "com.netflix.ninja");
        APP_KEYS.put(4099, "com.amazon.amazonvideo.livingroom");
        APP_KEYS.put(4160, "com.apple.atve.androidtv.appletv");
        APP_KEYS.put(4184, "com.hulu.livingroomplus");
        APP_KEYS.put(KeyEvent.KEYCODE_BUTTON_3, "com.google.android.youtube.tv");
    }

    private static final String SETTINGS_PKG = "com.android.tv.settings";
    private static final int POLL_MS = 250;
    private static final int MAX_POLLS = 24;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayDeque<String> queue = new ArrayDeque<>();
    private boolean busy;
    private int closed;
    private int polls;

    private AudioDelayProfiles audioDelay;

    @Override
    protected void onServiceConnected() {
        instance = this;
        audioDelay = new AudioDelayProfiles(this, this::checkForeground);
        audioDelay.restore();   // a crash or restart may have left a profile applied
        checkForeground();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        if (audioDelay != null) {
            audioDelay.restore();
            audioDelay.close();
        }
        return super.onUnbind(intent);
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        String app = APP_KEYS.get(event.getKeyCode());
        if (app != null) {
            if (event.getAction() == KeyEvent.ACTION_UP) launch(app);
            return true;
        }
        if (event.getKeyCode() != KeyEvent.KEYCODE_NOTIFICATION) return false;
        if (event.getAction() == KeyEvent.ACTION_UP) {
            startActivity(new Intent(this, RecentAppsDrawer.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
        }
        return true;
    }

    private void launch(String pkg) {
        Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = getPackageManager().getLaunchIntentForPackage(pkg);
        if (i != null) startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    /** Queues packages to force-stop through Settings; runs one at a time. */
    void forceStop(List<String> pkgs) {
        queue.addAll(pkgs);
        if (!busy) {
            closed = 0;
            next();
        }
    }

    private void next() {
        String pkg = queue.poll();
        if (pkg == null) {
            busy = false;
            performGlobalAction(GLOBAL_ACTION_HOME);
            Toast.makeText(this, "Closed " + closed + (closed == 1 ? " app" : " apps"), Toast.LENGTH_LONG).show();
            return;
        }
        busy = true;
        polls = 0;
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
        handler.postDelayed(this::poll, 400);
    }

    private void poll() {
        if (++polls > MAX_POLLS) {
            next();
            return;
        }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null && SETTINGS_PKG.contentEquals(root.getPackageName())) {
            if (tapExact(root, "OK")) {
                closed++;
                handler.postDelayed(this::next, 600);
                return;
            }
            // Re-tap every few polls in case the first tap landed before the page was ready.
            if (polls % 5 == 1) tapExact(root, "Force stop");
        }
        handler.postDelayed(this::poll, POLL_MS);
    }

    /** Focuses then clicks the row holding this exact text; logs the row's state for diagnosis. */
    private boolean tapExact(AccessibilityNodeInfo root, String text) {
        for (AccessibilityNodeInfo n : root.findAccessibilityNodeInfosByText(text)) {
            if (!text.contentEquals(n.getText())) continue;
            AccessibilityNodeInfo c = n;
            while (c != null && !c.isClickable()) c = c.getParent();
            if (c == null) continue;
            boolean f = c.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
            boolean ok = c.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            Log.i("TvHomeClose", "row '" + text + "' enabled=" + c.isEnabled() + " focusable=" + c.isFocusable()
                    + " focused=" + c.isFocused() + " focus=" + f + " click=" + ok + " actions=" + c.getActionList());
            return text.equals("OK");
        }
        return false;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) checkForeground();
    }

    /** Tells the audio delay profiles which app is in front. Only real app windows count, so volume bars and overlays are ignored. */
    private void checkForeground() {
        if (audioDelay == null) return;
        for (AccessibilityWindowInfo w : getWindows()) {
            if (w.getType() != AccessibilityWindowInfo.TYPE_APPLICATION || !w.isActive()) continue;
            AccessibilityNodeInfo root = w.getRoot();
            if (root != null && root.getPackageName() != null) audioDelay.onForeground(root.getPackageName().toString());
            return;
        }
    }

    @Override
    public void onInterrupt() {
    }
}
