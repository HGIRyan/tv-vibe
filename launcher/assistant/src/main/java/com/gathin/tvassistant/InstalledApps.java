package com.gathin.tvassistant;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class InstalledApps {
    static AppCatalog discover(Context context) throws Exception {
        AppCatalog known;
        try (var input = context.getAssets().open("apps.json")) {
            known = AppCatalog.fromJson(new JSONObject(new String(input.readAllBytes(), StandardCharsets.UTF_8)));
        }
        PackageManager manager = context.getPackageManager();
        Map<String, AppCatalog.App> installed = new TreeMap<>();
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            List<ResolveInfo> activities = manager.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0);
            for (ResolveInfo activity : activities) {
                if (activity.activityInfo == null || !activity.activityInfo.enabled || !activity.activityInfo.applicationInfo.enabled) continue;
                String pkg = activity.activityInfo.packageName;
                if (installed.containsKey(pkg)) continue;
                AppCatalog.App seed = known.byPackage(pkg);
                String label = activity.loadLabel(manager).toString();
                List<String> aliases = new ArrayList<>();
                if (seed != null) { aliases.addAll(seed.aliases); if (!seed.name.equals(label)) aliases.add(label); }
                installed.put(pkg, new AppCatalog.App(seed == null ? "OPEN_APP:" + pkg : seed.id,
                    seed == null ? label : seed.name, pkg, aliases));
            }
        }
        return new AppCatalog(new ArrayList<>(installed.values()));
    }
}
