package com.gathin.tvassistant;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

/** Exact allowlist: the model may select an ID, never invent a package to launch. */
final class AppCatalog {
    static final class App {
        final String id, name, packageName;
        final List<String> aliases;
        App(String id, String name, String packageName, List<String> aliases) {
            if (!id.startsWith("OPEN_") || !id.matches("[A-Za-z0-9_.:]+") || name.isBlank()
                || !packageName.matches("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+"))
                throw new IllegalArgumentException("Invalid app catalog entry");
            this.id = id; this.name = name; this.packageName = packageName; this.aliases = List.copyOf(aliases);
        }
        String description() {
            return "Open the " + name + " app" + (aliases.isEmpty() ? "" : "; also called " + String.join(", ", aliases));
        }
        JSONObject json() throws Exception {
            return new JSONObject().put("id", id).put("name", name).put("package", packageName).put("aliases", new JSONArray(aliases));
        }
    }
    final List<App> apps;
    AppCatalog(List<App> apps) {
        Set<String> ids = new HashSet<>(), packages = new HashSet<>();
        for (App app : apps) if (!ids.add(app.id) || !packages.add(app.packageName))
            throw new IllegalArgumentException("Duplicate app ID or package");
        this.apps = List.copyOf(apps);
    }
    static AppCatalog fromJson(JSONObject object) throws Exception {
        List<App> apps = new ArrayList<>();
        JSONArray entries = object.getJSONArray("apps");
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.getJSONObject(i);
            List<String> aliases = new ArrayList<>();
            JSONArray names = entry.optJSONArray("aliases");
            if (names != null) for (int j = 0; j < names.length(); j++) aliases.add(names.getString(j));
            apps.add(new App(entry.getString("id"), entry.getString("name"), entry.getString("package"), aliases));
        }
        return new AppCatalog(apps);
    }
    App byId(String id) { for (App app : apps) if (app.id.equals(id)) return app; return null; }
    App byPackage(String name) { for (App app : apps) if (app.packageName.equals(name)) return app; return null; }
    JSONArray toJson() throws Exception {
        JSONArray result = new JSONArray();
        for (App app : apps) result.put(app.json());
        return result;
    }
    String names() {
        List<String> names = new ArrayList<>();
        for (App app : apps) { names.add(app.name); names.addAll(app.aliases); }
        return String.join(", ", names);
    }
}
