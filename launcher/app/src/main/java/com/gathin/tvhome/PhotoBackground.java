package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.json.JSONArray;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Local album exports only. Decode off the UI thread, and stop rotating when Home is hidden. */
final class PhotoBackground {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<File> photos = new ArrayList<>();
    private final FrameLayout layer;
    private final ImageView[] images;
    private final View ambient;
    private final boolean reducedMotion;
    private boolean running;
    private int generation, next, front;
    private boolean shown;
    private final Runnable rotate = this::loadNext;

    PhotoBackground(Activity activity, FrameLayout root, View ambient, boolean reducedMotion) {
        this.ambient = ambient;
        this.reducedMotion = reducedMotion;
        File base = activity.getExternalFilesDir(null);
        try {
            File folder = new File(base, "photos").getCanonicalFile();
            File manifest = new File(folder, "active.json");
            if (base != null && manifest.isFile() && manifest.length() <= 128 * 1024) {
                JSONArray names = new JSONArray(new String(Files.readAllBytes(manifest.toPath()), StandardCharsets.UTF_8));
                for (int i = 0; i < Math.min(names.length(), 1000); i++) {
                    File photo = new File(folder, names.getString(i)).getCanonicalFile();
                    if (photo.getPath().startsWith(folder.getPath() + File.separator)
                            && photo.isFile() && photo.length() <= 8 * 1024 * 1024) photos.add(photo);
                }
            }
        } catch (Exception ignored) { /* Keep the existing background when no usable album exists. */ }
        Collections.shuffle(photos);
        layer = new FrameLayout(activity);
        layer.setVisibility(View.GONE);
        images = new ImageView[]{new ImageView(activity), new ImageView(activity)};
        for (ImageView image : images) {
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            layer.addView(image, new FrameLayout.LayoutParams(-1, -1));
        }
        View shade = new View(activity);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x99000000, 0x55000000, 0xBB000000}));
        layer.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        root.addView(layer, 0, new FrameLayout.LayoutParams(-1, -1));
    }

    void resume() {
        if (running || photos.isEmpty()) return;
        running = true;
        if (shown) main.postDelayed(rotate, 30_000); else loadNext();
    }

    void pause() {
        running = false;
        generation++;
        main.removeCallbacks(rotate);
        for (ImageView image : images) image.animate().cancel();
        if (shown) {
            images[front].setAlpha(1f);
            images[1 - front].setAlpha(0f);
            images[1 - front].setImageDrawable(null);
        }
    }

    void close() { pause(); worker.shutdownNow(); }

    private void loadNext() {
        if (!running) return;
        final int ticket = generation;
        final File file = photos.get(next++ % photos.size());
        worker.execute(() -> {
            Bitmap decoded = null;
            try {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(file.getPath(), opts);
                opts.inSampleSize = 1;
                while (opts.outWidth / opts.inSampleSize > 1920 || opts.outHeight / opts.inSampleSize > 1920)
                    opts.inSampleSize *= 2;
                opts.inJustDecodeBounds = false;
                opts.inPreferredConfig = Bitmap.Config.RGB_565;
                decoded = BitmapFactory.decodeFile(file.getPath(), opts);
            } catch (Exception | OutOfMemoryError ignored) { }
            final Bitmap bitmap = decoded;
            main.post(() -> {
                if (!running || ticket != generation) { if (bitmap != null) bitmap.recycle(); return; }
                if (bitmap != null) {
                    int target = shown ? 1 - front : front;
                    ImageView incoming = images[target];
                    ImageView outgoing = images[1 - target];
                    incoming.setImageBitmap(bitmap);
                    incoming.setAlpha(0f);
                    layer.setVisibility(View.VISIBLE);
                    ambient.setVisibility(View.GONE);
                    incoming.animate().alpha(1f).setDuration(reducedMotion || !shown ? 0 : 1200).start();
                    outgoing.animate().alpha(0f).setDuration(reducedMotion ? 0 : 1200)
                            .withEndAction(() -> outgoing.setImageDrawable(null)).start();
                    front = target;
                    shown = true;
                }
                if (photos.size() > 1 || !shown) main.postDelayed(rotate, 30_000);
            });
        });
    }
}
