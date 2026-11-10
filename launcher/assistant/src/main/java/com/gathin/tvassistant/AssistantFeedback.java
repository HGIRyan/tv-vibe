package com.gathin.tvassistant;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/** App-owned feedback exists only during a wake/command cycle; never takes remote focus. */
final class AssistantFeedback {
    private final Context context;
    private final WindowManager windows;
    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout bubble;
    private TextView label;
    private ObjectAnimator pulse;
    private boolean closed, active;
    private final Runnable hide = this::remove;

    AssistantFeedback(Context context) {
        this.context = context;
        windows = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    void wake() {
        main.post(this::beginWake);
    }

    private void beginWake() {
        if (closed) return;
        main.removeCallbacksAndMessages(null);
        active = true;
        show("Listening…", true);
        main.postDelayed(hide, 20000); // Never leave a bubble up after an interrupted request.
    }

    void processing() {
        main.post(() -> { if (!closed && active) show("Processing…", true); });
    }

    void result(String text) {
        main.post(() -> {
            if (closed || !active) return;
            main.removeCallbacksAndMessages(null);
            show(text, false);
            main.postDelayed(hide, 3500);
        });
    }

    void preview() {
        main.post(() -> {
            beginWake();
            main.postDelayed(() -> result("Pause · preview"), 1000);
        });
    }

    private void show(String text, boolean listening) {
        if (!Settings.canDrawOverlays(context)) return;
        try {
            if (bubble == null) {
                bubble = new LinearLayout(context);
                bubble.setOrientation(LinearLayout.HORIZONTAL);
                bubble.setGravity(Gravity.CENTER_VERTICAL);
                bubble.setPadding(dp(14), dp(12), dp(20), dp(12));
                GradientDrawable background = new GradientDrawable();
                background.setColor(Color.rgb(18, 35, 29));
                background.setCornerRadius(dp(28));
                bubble.setBackground(background);
                View dot = new View(context);
                GradientDrawable circle = new GradientDrawable();
                circle.setShape(GradientDrawable.OVAL);
                circle.setColor(Color.rgb(153, 232, 179));
                dot.setBackground(circle);
                bubble.addView(dot, new LinearLayout.LayoutParams(dp(24), dp(24)));
                label = new TextView(context);
                label.setTextColor(Color.WHITE);
                label.setTextSize(20);
                label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                label.setMaxLines(2);
                label.setMaxWidth(dp(480));
                LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(-2, -2);
                textParams.setMarginStart(dp(14));
                bubble.addView(label, textParams);
                pulse = ObjectAnimator.ofFloat(dot, View.ALPHA, 1f, 0.35f, 1f);
                pulse.setDuration(1100);
                pulse.setRepeatCount(ValueAnimator.INFINITE);
                WindowManager.LayoutParams params = new WindowManager.LayoutParams(-2, -2,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT);
                params.gravity = Gravity.TOP | Gravity.RIGHT;
                params.x = dp(48);
                params.y = dp(64);
                params.alpha = 0.8f;
                params.setTitle("HeyTVFeedback");
                windows.addView(bubble, params);
            }
            label.setText(text);
            bubble.setContentDescription("Hey TV: " + text);
            if (listening && !pulse.isRunning()) pulse.start();
            else if (!listening) { pulse.cancel(); bubble.getChildAt(0).setAlpha(1f); }
        } catch (RuntimeException ignored) {
            Log.w("HeyTV", "Could not show wake feedback overlay");
            remove();
        }
    }

    private int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }

    private void remove() {
        active = false;
        if (pulse != null) pulse.cancel();
        if (bubble != null) {
            try { windows.removeViewImmediate(bubble); } catch (RuntimeException ignored) { }
        }
        bubble = null; label = null; pulse = null;
    }

    void close() {
        main.post(() -> { closed = true; main.removeCallbacksAndMessages(null); remove(); });
    }

    static String actionLabel(CommandPolicy.Selection selection) {
        switch (selection.action) {
            case PAUSE: return "Pause";
            case PLAY: return "Resume";
            case OPEN_APP: return selection.app == null ? "No action" : "Open " + selection.app.name;
            case GO_HOME: return "Go home";
            case MUTE: return "Mute";
            case UNMUTE: return "Unmute";
            case VOLUME_UP: return "Volume up";
            case VOLUME_DOWN: return "Volume down";
            default: return "No action detected";
        }
    }
}
