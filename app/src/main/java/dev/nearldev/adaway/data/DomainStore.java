package dev.nearldev.adaway.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class DomainStore {
    private static final String PREFS_NAME = "domain_store";
    private static final String KEY_DOMAINS = "domains";

    private static volatile DomainStore instance;

    public static class Domain {
        public final String name;
        public boolean enabled;

        public Domain(String name, boolean enabled) {
            this.name = name;
            this.enabled = enabled;
        }
    }

    private final SharedPreferences prefs;
    private final Map<String, Domain> domains = new LinkedHashMap<>();
    private final AtomicInteger blockedCount = new AtomicInteger(0);

    private DomainStore(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        load();
        if (this.domains.isEmpty()) {
            seedDefaults();
            persist();
        }
    }

    public static synchronized DomainStore getInstance(Context context) {
        if (instance == null) {
            instance = new DomainStore(context.getApplicationContext());
        }
        return instance;
    }

    private void seedDefaults() {
        this.domains.put("ads.doubleclick.net", new Domain("ads.doubleclick.net", true));
        this.domains.put("graph.facebook.com", new Domain("graph.facebook.com", true));
        this.domains.put("track.adjust.com", new Domain("track.adjust.com", true));
    }

    public synchronized List<Domain> getAll() {
        return new ArrayList<>(this.domains.values());
    }

    public synchronized boolean add(String name) {
        String key = normalize(name);
        if (key.isEmpty() || this.domains.containsKey(key)) {
            return false;
        }
        this.domains.put(key, new Domain(key, true));
        persist();
        return true;
    }

    public synchronized void remove(String name) {
        if (this.domains.remove(normalize(name)) != null) {
            persist();
        }
    }

    public synchronized boolean rename(String oldName, String newName) {
        String oldKey = normalize(oldName);
        String newKey = normalize(newName);
        if (newKey.isEmpty() || this.domains.containsKey(newKey)) {
            return false;
        }
        Domain domain = this.domains.remove(oldKey);
        if (domain == null) {
            return false;
        }
        this.domains.put(newKey, new Domain(newKey, domain.enabled));
        persist();
        return true;
    }

    public synchronized void setEnabled(String name, boolean enabled) {
        Domain domain = this.domains.get(normalize(name));
        if (domain != null) {
            domain.enabled = enabled;
            persist();
        }
    }

    public boolean isBlocked(String host) {
        Domain domain;
        synchronized (this) {
            domain = this.domains.get(normalize(host));
        }
        boolean blocked = domain != null && domain.enabled;
        if (blocked) {
            this.blockedCount.incrementAndGet();
        }
        return blocked;
    }

    public int getBlockedCount() {
        return this.blockedCount.get();
    }

    public void resetBlockedCount() {
        this.blockedCount.set(0);
    }

    public synchronized int getEnabledCount() {
        int count = 0;
        for (Domain domain : this.domains.values()) {
            if (domain.enabled) {
                count++;
            }
        }
        return count;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private void load() {
        String json = this.prefs.getString(KEY_DOMAINS, null);
        if (json == null) {
            return;
        }
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                String name = item.getString("name");
                boolean enabled = item.optBoolean("enabled", true);
                this.domains.put(name, new Domain(name, enabled));
            }
        } catch (JSONException e) {
            this.domains.clear();
        }
    }

    private void persist() {
        JSONArray array = new JSONArray();
        try {
            for (Domain domain : this.domains.values()) {
                JSONObject item = new JSONObject();
                item.put("name", domain.name);
                item.put("enabled", domain.enabled);
                array.put(item);
            }
        } catch (JSONException e) {
            return;
        }
        this.prefs.edit().putString(KEY_DOMAINS, array.toString()).apply();
    }
}
