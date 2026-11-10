package com.gathin.tvassistant;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

final class ModelFiles {
    static File unpack(Context context) throws IOException {
        File root = new File(context.getFilesDir(), "wake-model-0.15");
        File ready = new File(root, ".ready");
        if (!ready.exists()) {
            copy(context, "wake-model", root);
            if (!ready.createNewFile()) throw new IOException("Cannot mark model ready");
        }
        return root;
    }

    private static void copy(Context context, String asset, File target) throws IOException {
        String[] children = context.getAssets().list(asset);
        if (children == null || children.length == 0) {
            try (var input = context.getAssets().open(asset); var output = new FileOutputStream(target)) {
                byte[] buffer = new byte[16384];
                int n;
                while ((n = input.read(buffer)) != -1) output.write(buffer, 0, n);
            }
        } else {
            if (!target.isDirectory() && !target.mkdirs()) throw new IOException("Cannot create model directory");
            for (String child : children) copy(context, asset + "/" + child, new File(target, child));
        }
    }
}
