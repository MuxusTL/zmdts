package dev.nitroplus.data;

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
    private static final String KEY_ALLOW_DOMAINS = "allow_domains";

    private static volatile DomainStore instance;

    public static class Domain {
        public final String name;
        public boolean enabled;
        public final String source;

        public Domain(String name, boolean enabled, String source) {
            this.name = name;
            this.enabled = enabled;
            this.source = source;
        }
    }

    private final SharedPreferences prefs;
    private final Map<String, Domain> domains = new LinkedHashMap<>();
    private final Map<String, Domain> allowDomains = new LinkedHashMap<>();
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
        this.domains.put("verxdev.app", new Domain("verxdev.app", true, null));
    }

    public synchronized List<Domain> getAll() {
        return new ArrayList<>(this.domains.values());
    }

    public synchronized boolean add(String name) {
        String key = normalize(name);
        if (key.isEmpty() || this.domains.containsKey(key)) {
            return false;
        }
        this.domains.put(key, new Domain(key, true, null));
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
        this.domains.put(newKey, new Domain(newKey, domain.enabled, domain.source));
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

    public synchronized void toggleEnabled(String name) {
        Domain domain = this.domains.get(normalize(name));
        if (domain != null) {
            domain.enabled = !domain.enabled;
            persist();
        }
    }

    public synchronized boolean addFromSource(String name, String sourceId, boolean allowMode) {
        String key = normalize(name);
        if (key.isEmpty()) {
            return false;
        }
        Map<String, Domain> target = allowMode ? this.allowDomains : this.domains;
        if (target.containsKey(key)) {
            return false;
        }
        target.put(key, new Domain(key, true, sourceId));
        return true;
    }

    public synchronized void persistNow() {
        persist();
    }

    public synchronized void removeAllFromSource(String sourceId, boolean allowMode) {
        Map<String, Domain> target = allowMode ? this.allowDomains : this.domains;
        target.values().removeIf(d -> sourceId.equals(d.source));
        persist();
    }

    public synchronized void setSourceEnabled(String sourceId, boolean enabled, boolean allowMode) {
        Map<String, Domain> target = allowMode ? this.allowDomains : this.domains;
        for (Domain domain : target.values()) {
            if (sourceId.equals(domain.source)) {
                domain.enabled = enabled;
            }
        }
        persist();
    }

    public synchronized String getDomainsForSourceJson(String sourceId, boolean allowMode) {
        Map<String, Domain> target = allowMode ? this.allowDomains : this.domains;
        JSONArray array = new JSONArray();
        try {
            for (Domain domain : target.values()) {
                if (sourceId.equals(domain.source)) {
                    JSONObject item = new JSONObject();
                    item.put("name", domain.name);
                    item.put("enabled", domain.enabled);
                    array.put(item);
                }
            }
        } catch (JSONException ignored) {
        }
        return array.toString();
    }

    public synchronized String toJson() {
        return domainsToJson(this.domains, false);
    }

    public synchronized String toJsonManual() {
        return domainsToJson(this.domains, true);
    }

    private String domainsToJson(Map<String, Domain> map, boolean manualOnly) {
        JSONArray array = new JSONArray();
        try {
            for (Domain domain : map.values()) {
                if (manualOnly && domain.source != null) {
                    continue;
                }
                JSONObject item = new JSONObject();
                item.put("name", domain.name);
                item.put("enabled", domain.enabled);
                item.put("source", domain.source == null ? JSONObject.NULL : domain.source);
                array.put(item);
            }
        } catch (JSONException ignored) {
        }
        return array.toString();
    }

    public boolean isBlocked(String host) {
        String key = normalize(host);
        Domain allowEntry;
        Domain blockEntry;
        synchronized (this) {
            allowEntry = this.allowDomains.get(key);
            blockEntry = this.domains.get(key);
        }
        if (allowEntry != null && allowEntry.enabled) {
            return false;
        }
        boolean blocked = blockEntry != null && blockEntry.enabled;
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
        loadMap(KEY_DOMAINS, this.domains);
        loadMap(KEY_ALLOW_DOMAINS, this.allowDomains);
    }

    private void loadMap(String key, Map<String, Domain> target) {
        String json = this.prefs.getString(key, null);
        if (json == null) {
            return;
        }
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                String name = item.getString("name");
                boolean enabled = item.optBoolean("enabled", true);
                String source = item.isNull("source") ? null : item.optString("source", null);
                target.put(name, new Domain(name, enabled, source));
            }
        } catch (JSONException e) {
            target.clear();
        }
    }

    private void persist() {
        persistMap(KEY_DOMAINS, this.domains);
        persistMap(KEY_ALLOW_DOMAINS, this.allowDomains);
    }

    private void persistMap(String key, Map<String, Domain> source) {
        JSONArray array = new JSONArray();
        try {
            for (Domain domain : source.values()) {
                JSONObject item = new JSONObject();
                item.put("name", domain.name);
                item.put("enabled", domain.enabled);
                item.put("source", domain.source == null ? JSONObject.NULL : domain.source);
                array.put(item);
            }
        } catch (JSONException e) {
            return;
        }
        this.prefs.edit().putString(key, array.toString()).apply();
    }
}
