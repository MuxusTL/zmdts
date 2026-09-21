package dev.nitroplus.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HostsSourceStore {
    private static final String PREFS_NAME = "hosts_source_store";
    private static final String KEY_SOURCES = "sources";

    public static final String MODE_BLOCK = "block";
    public static final String MODE_ALLOW = "allow";
    public static final String TYPE_URL = "url";
    public static final String TYPE_FILE = "file";

    private static volatile HostsSourceStore instance;

    public static class Source {
        public final String id;
        public String name;
        public boolean enabled;
        public String mode;
        public final String type;
        public final String location;
        public int domainCount;
        public long lastUpdated;

        public Source(String id, String name, boolean enabled, String mode, String type, String location, int domainCount, long lastUpdated) {
            this.id = id;
            this.name = name;
            this.enabled = enabled;
            this.mode = mode;
            this.type = type;
            this.location = location;
            this.domainCount = domainCount;
            this.lastUpdated = lastUpdated;
        }

        public boolean isAllow() {
            return MODE_ALLOW.equals(this.mode);
        }
    }

    private final SharedPreferences prefs;
    private final Map<String, Source> sources = new LinkedHashMap<>();

    private HostsSourceStore(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        load();
    }

    public static synchronized HostsSourceStore getInstance(Context context) {
        if (instance == null) {
            instance = new HostsSourceStore(context.getApplicationContext());
        }
        return instance;
    }

    public synchronized List<Source> getAll() {
        return new ArrayList<>(this.sources.values());
    }

    public synchronized Source get(String id) {
        return this.sources.get(id);
    }

    public synchronized String addUrl(String name, String url, String mode) {
        String id = "url:" + url.trim();
        if (this.sources.containsKey(id)) {
            return null;
        }
        String finalName = (name == null || name.trim().isEmpty()) ? url.trim() : name.trim();
        this.sources.put(id, new Source(id, finalName, true, mode, TYPE_URL, url.trim(), 0, 0));
        persist();
        return id;
    }

    public synchronized String addFile(String name, String uriString, String mode) {
        String id = "file:" + uriString;
        String finalName = (name == null || name.trim().isEmpty()) ? "Tệp hosts" : name.trim();
        this.sources.put(id, new Source(id, finalName, true, mode, TYPE_FILE, uriString, 0, 0));
        persist();
        return id;
    }

    public synchronized void remove(String id) {
        if (this.sources.remove(id) != null) {
            persist();
        }
    }

    public synchronized boolean toggleEnabled(String id) {
        Source source = this.sources.get(id);
        if (source == null) {
            return false;
        }
        source.enabled = !source.enabled;
        persist();
        return source.enabled;
    }

    public synchronized void setName(String id, String name) {
        Source source = this.sources.get(id);
        if (source != null && name != null && !name.trim().isEmpty()) {
            source.name = name.trim();
            persist();
        }
    }

    public synchronized void setMode(String id, String mode) {
        Source source = this.sources.get(id);
        if (source != null) {
            source.mode = mode;
            persist();
        }
    }

    public synchronized void updateCount(String id, int count, long timestamp) {
        Source source = this.sources.get(id);
        if (source != null) {
            source.domainCount = count;
            source.lastUpdated = timestamp;
            persist();
        }
    }

    public synchronized String toJson() {
        JSONArray array = new JSONArray();
        try {
            for (Source source : this.sources.values()) {
                array.put(toJsonObject(source));
            }
        } catch (JSONException ignored) {
        }
        return array.toString();
    }

    public synchronized String toJson(String id) {
        Source source = this.sources.get(id);
        if (source == null) {
            return "null";
        }
        try {
            return toJsonObject(source).toString();
        } catch (JSONException e) {
            return "null";
        }
    }

    private JSONObject toJsonObject(Source source) throws JSONException {
        JSONObject item = new JSONObject();
        item.put("id", source.id);
        item.put("name", source.name);
        item.put("enabled", source.enabled);
        item.put("mode", source.mode);
        item.put("type", source.type);
        item.put("location", source.location);
        item.put("domainCount", source.domainCount);
        item.put("lastUpdated", source.lastUpdated);
        return item;
    }

    private void load() {
        String json = this.prefs.getString(KEY_SOURCES, null);
        if (json == null) {
            return;
        }
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                String id = item.optString("id", null);
                if (id == null) {
                    continue;
                }
                String name = item.optString("name", id);
                boolean enabled = item.optBoolean("enabled", true);
                String mode = item.optString("mode", MODE_BLOCK);
                String type = item.optString("type", TYPE_URL);
                String location = item.optString("location", "");
                int count = item.optInt("domainCount", 0);
                long lastUpdated = item.optLong("lastUpdated", 0);
                this.sources.put(id, new Source(id, name, enabled, mode, type, location, count, lastUpdated));
            }
        } catch (JSONException e) {
            this.sources.clear();
        }
    }

    private void persist() {
        this.prefs.edit().putString(KEY_SOURCES, toJson()).apply();
    }
}
