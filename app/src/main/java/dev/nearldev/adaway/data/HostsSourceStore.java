package dev.nearldev.adaway.data;

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

    private static volatile HostsSourceStore instance;

    public static class Source {
        public final String url;
        public boolean enabled;
        public int domainCount;

        public Source(String url, boolean enabled, int domainCount) {
            this.url = url;
            this.enabled = enabled;
            this.domainCount = domainCount;
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

    public synchronized boolean add(String url) {
        String key = normalize(url);
        if (key.isEmpty() || this.sources.containsKey(key)) {
            return false;
        }
        this.sources.put(key, new Source(key, true, 0));
        persist();
        return true;
    }

    public synchronized void remove(String url) {
        if (this.sources.remove(normalize(url)) != null) {
            persist();
        }
    }

    public synchronized boolean toggleEnabled(String url) {
        Source source = this.sources.get(normalize(url));
        if (source == null) {
            return false;
        }
        source.enabled = !source.enabled;
        persist();
        return source.enabled;
    }

    public synchronized void updateCount(String url, int count) {
        Source source = this.sources.get(normalize(url));
        if (source != null) {
            source.domainCount = count;
            persist();
        }
    }

    public synchronized String toJson() {
        JSONArray array = new JSONArray();
        try {
            for (Source source : this.sources.values()) {
                JSONObject item = new JSONObject();
                item.put("url", source.url);
                item.put("enabled", source.enabled);
                item.put("domainCount", source.domainCount);
                array.put(item);
            }
        } catch (JSONException ignored) {
        }
        return array.toString();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
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
                String url = item.getString("url");
                boolean enabled = item.optBoolean("enabled", true);
                int count = item.optInt("domainCount", 0);
                this.sources.put(url, new Source(url, enabled, count));
            }
        } catch (JSONException e) {
            this.sources.clear();
        }
    }

    private void persist() {
        this.prefs.edit().putString(KEY_SOURCES, toJson()).apply();
    }
}
