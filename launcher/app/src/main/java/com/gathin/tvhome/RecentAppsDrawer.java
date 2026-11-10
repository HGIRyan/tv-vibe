package com.gathin.tvhome;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Process;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bottom app switcher: OK opens an app; a separate close mode retains the cleanup controls.
 * Android hides the real process list from normal apps, so "recent" comes from usage history
 * (needs: appops set com.gathin.tvhome GET_USAGE_STATS allow). Opened by DoubleHomeService.
 */
public class RecentAppsDrawer extends Activity {

    private static final long WINDOW_MS = 12L * 60 * 60 * 1000;
    private static final int MAX_APPS = 14;

    private boolean closeMode;
    private TextView hint;
    private LinearLayout buttons;

    private final Set<String> selected = new HashSet<>();
    private final List<String> packages = new ArrayList<>();
    private final List<View> cards = new ArrayList<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xB0000000);
        root.setOnClickListener(v -> finish());

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(48), dp(28), dp(48), dp(32));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF2161618);
        bg.setCornerRadii(new float[]{dp(28), dp(28), dp(28), dp(28), 0, 0, 0, 0});
        panel.setBackground(bg);
        root.addView(panel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));

        TextView title = text("Recent apps", 26, Color.WHITE);
        panel.addView(title);
        hint = text("OK to switch apps  ·  Back to dismiss", 15, 0x99FFFFFF);
        hint.setPadding(0, dp(4), 0, dp(16));
        panel.addView(hint);

        Map<String, Long> recent = loadRecent();
        PackageManager pm = getPackageManager();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (String pkg : recent.keySet()) {
            try {
                CharSequence label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
                android.graphics.drawable.Drawable banner = pm.getApplicationInfo(pkg, 0).loadBanner(pm);
                View card = makeCard(pkg, label.toString(), banner != null ? banner : pm.getApplicationIcon(pkg));
                packages.add(pkg);
                cards.add(card);
                row.addView(card);
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        if (cards.isEmpty()) {
            String msg = hasUsageAccess() ? "No recently used apps." : "Usage access is off. Run: appops set "
                    + getPackageName() + " GET_USAGE_STATS allow";
            panel.addView(text(msg, 18, Color.WHITE));
        } else {
            HorizontalScrollView scroll = new HorizontalScrollView(this);
            scroll.setHorizontalScrollBarEnabled(false);
            scroll.setClipToPadding(false);
            scroll.addView(row);
            panel.addView(scroll);

            buttons = new LinearLayout(this);
            buttons.setPadding(0, dp(20), 0, 0);
            updateControls();
            panel.addView(buttons);
        }
        setContentView(root);
        if (!cards.isEmpty()) cards.get(0).requestFocus();
    }

    private Map<String, Long> loadRecent() {
        Map<String, Long> last = new LinkedHashMap<>();
        if (!hasUsageAccess()) return last;
        UsageStatsManager usm = (UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
        long now = System.currentTimeMillis();
        UsageEvents events = usm.queryEvents(now - WINDOW_MS, now);
        UsageEvents.Event e = new UsageEvents.Event();
        Map<String, Long> times = new LinkedHashMap<>();
        while (events.hasNextEvent()) {
            events.getNextEvent(e);
            if (e.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) times.put(e.getPackageName(), e.getTimeStamp());
        }
        PackageManager pm = getPackageManager();
        List<Map.Entry<String, Long>> sorted = new ArrayList<>(times.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        for (Map.Entry<String, Long> en : sorted) {
            String p = en.getKey();
            if (p.equals(getPackageName()) || p.startsWith("com.android.") || p.equals("android")) continue;
            if (pm.getLeanbackLaunchIntentForPackage(p) == null && pm.getLaunchIntentForPackage(p) == null) continue;
            last.put(p, en.getValue());
            if (last.size() >= MAX_APPS) break;
        }
        return last;
    }

    private boolean hasUsageAccess() {
        AppOpsManager ops = (AppOpsManager) getSystemService(APP_OPS_SERVICE);
        return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), getPackageName())
                == AppOpsManager.MODE_ALLOWED;
    }

    private View makeCard(String pkg, String label, android.graphics.drawable.Drawable icon) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(12));
        card.setFocusable(true);
        card.setClickable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(184), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(16), 0);
        card.setLayoutParams(lp);
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(icon);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        card.addView(iv, new LinearLayout.LayoutParams(dp(156), dp(88)));
        TextView name = text(label, 15, Color.WHITE);
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setPadding(0, dp(8), 0, 0);
        card.addView(name);
        TextView mark = text("", 14, 0xFF7CFC9A);
        mark.setGravity(Gravity.CENTER);
        card.addView(mark);
        card.setTag(mark);
        card.setContentDescription(label);
        style(card, pkg);
        card.setOnClickListener(v -> {
            if (closeMode) {
                if (!selected.remove(pkg)) selected.add(pkg);
                style(card, pkg);
            } else {
                openApp(pkg);
            }
        });
        card.setOnFocusChangeListener((v, f) -> style(card, pkg));
        return card;
    }

    private void style(View card, String pkg) {
        boolean sel = selected.contains(pkg);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(18));
        g.setColor(card.hasFocus() ? 0x40FFFFFF : 0x14FFFFFF);
        if (sel) g.setStroke(dp(3), 0xFF7CFC9A);
        else if (card.hasFocus()) g.setStroke(dp(2), 0xAAFFFFFF);
        card.setBackground(g);
        ((TextView) card.getTag()).setText(closeMode
                ? (sel ? "✓ Will close" : "Select to close")
                : (card.hasFocus() ? "Open app" : " "));
    }

    private void openApp(String pkg) {
        PackageManager pm = getPackageManager();
        Intent intent = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (intent == null) intent = pm.getLaunchIntentForPackage(pkg);
        if (intent != null) {
            try {
                startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                finish();
                return;
            } catch (ActivityNotFoundException | SecurityException ignored) { }
        }
        Toast.makeText(this, "This app is no longer available", Toast.LENGTH_SHORT).show();
    }

    private void setCloseMode(boolean enabled) {
        closeMode = enabled;
        selected.clear();
        hint.setText(enabled ? "OK to select apps to close  ·  Back to switch apps"
                : "OK to switch apps  ·  Back to dismiss");
        for (int i = 0; i < cards.size(); i++) style(cards.get(i), packages.get(i));
        updateControls();
        if (!cards.isEmpty()) cards.get(0).requestFocus();
    }

    private void updateControls() {
        buttons.removeAllViews();
        buttons.addView(button(closeMode ? "Done" : "Close apps…", () -> setCloseMode(!closeMode)));
        if (closeMode) {
            buttons.addView(button("Close selected", () -> closeApps(false)));
            buttons.addView(button("Close all", () -> closeApps(true)));
        }
    }

    private View button(String label, Runnable action) {
        TextView t = text(label, 18, Color.WHITE);
        t.setPadding(dp(28), dp(12), dp(28), dp(12));
        t.setFocusable(true);
        t.setClickable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(16), 0);
        t.setLayoutParams(lp);
        View.OnFocusChangeListener paint = (v, f) -> {
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(40));
            g.setColor(f ? 0x55FFFFFF : 0x22FFFFFF);
            v.setBackground(g);
        };
        t.setOnFocusChangeListener(paint);
        paint.onFocusChange(t, false);
        t.setOnClickListener(v -> action.run());
        return t;
    }

    private void closeApps(boolean all) {
        Set<String> targets = all ? new HashSet<>(packages) : selected;
        if (targets.isEmpty()) {
            Toast.makeText(this, "Select apps first", Toast.LENGTH_SHORT).show();
            return;
        }
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        final List<String> list = new ArrayList<>(targets);
        if (DoubleHomeService.instance != null) {
            DoubleHomeService.instance.forceStop(list);
        } else {
            for (String p : list) am.killBackgroundProcesses(p);
            Toast.makeText(this, "Accessibility service is off; stopped background processes only", Toast.LENGTH_LONG).show();
        }
        finish();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_HOME) {
            if (keyCode == KeyEvent.KEYCODE_BACK && closeMode) setCloseMode(false);
            else finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
