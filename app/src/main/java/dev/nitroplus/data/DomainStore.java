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
        this.domains.put("garena.vn", new Domain("garena.vn", true, null));
        this.domains.put("garena.com", new Domain("garena.com", true, null));
        this.domains.put("garena.co.id", new Domain("garena.co.id", true, null));
        this.domains.put("ff.garena.com", new Domain("ff.garena.com", true, null));
        this.domains.put("ffshoppy.com", new Domain("ffshoppy.com", true, null));
        this.domains.put("garenanow.com", new Domain("garenanow.com", true, null));
        this.domains.put("ggpolarbear.com", new Domain("ggpolarbear.com", true, null));
        this.domains.put("ggblueshark.com", new Domain("ggblueshark.com", true, null));
        this.domains.put("freefiremobile.com", new Domain("freefiremobile.com", true, null));
        this.domains.put("vnevent.ggblueshark.com", new Domain("vnevent.ggblueshark.com", true, null));
        this.domains.put("gin.freefireind.in", new Domain("gin.freefireind.in", true, null));
        this.domains.put("gin.freefiremobile.com", new Domain("gin.freefiremobile.com", true, null));
        this.domains.put("na-gin.freefiremobile.com", new Domain("na-gin.freefiremobile.com", true, null));
        this.domains.put("gamesecurity.us.freefiremobile.com", new Domain("gamesecurity.us.freefiremobile.com", true, null));
        this.domains.put("clientbp.ggblueshark.com", new Domain("clientbp.ggblueshark.com", true, null));
        this.domains.put("dl.bs.freefiremobile.com", new Domain("dl.bs.freefiremobile.com", true, null));
        this.domains.put("version.ggwhitehawk.com", new Domain("version.ggwhitehawk.com", true, null));
        this.domains.put("dl.gmc.freefiremobile.com", new Domain("dl.gmc.freefiremobile.com", true, null));
        this.domains.put("vnnetwork.ggblueshark.com", new Domain("vnnetwork.ggblueshark.com", true, null));
        this.domains.put("app-measurement.com", new Domain("app-measurement.com", true, null));
        this.domains.put("dl.dir.freefiremobile.com", new Domain("dl.dir.freefiremobile.com", true, null));
        this.domains.put("dl-sg-production.freefiremobile.com", new Domain("dl-sg-production.freefiremobile.com", true, null));
        this.domains.put("idevent.ggblueshark.com", new Domain("idevent.ggblueshark.com", true, null));
        this.domains.put("rslw0r.launches.appsflyersdk.com", new Domain("rslw0r.launches.appsflyersdk.com", true, null));
        this.domains.put("ff.sdk.grtc.garenanow.com", new Domain("ff.sdk.grtc.garenanow.com", true, null));
        this.domains.put("dl.aw.freefiremobile.com", new Domain("dl.aw.freefiremobile.com", true, null));
        this.domains.put("dl-sg-production.wildflamestudio.com", new Domain("dl-sg-production.wildflamestudio.com", true, null));
        this.domains.put("clientbp.ppmainecoonghj.com", new Domain("clientbp.ppmainecoonghj.com", true, null));
        this.domains.put("version.common.redflamenco.com", new Domain("version.common.redflamenco.com", true, null));
        this.domains.put("loginbp.ppmainecoonghj.com", new Domain("loginbp.ppmainecoonghj.com", true, null));
        this.domains.put("rslw0r.inapps.appsflyersdk.com", new Domain("rslw0r.inapps.appsflyersdk.com", true, null));
        this.domains.put("api-sdk.datadome.co", new Domain("api-sdk.datadome.co", true, null));
        this.domains.put("dl.castle.freefiremobile.com", new Domain("dl.castle.freefiremobile.com", true, null));
        this.domains.put("dl.listdl.com", new Domain("dl.listdl.com", true, null));
        this.domains.put("core-gmc.freefiremobile.com", new Domain("core-gmc.freefiremobile.com", true, null));
        this.domains.put("firebaselogging-pa.googleapis.com", new Domain("firebaselogging-pa.googleapis.com", true, null));
        this.domains.put("firebaselogging.googleapis.com", new Domain("firebaselogging.googleapis.com", true, null));
        this.domains.put("graph.facebook.com", new Domain("graph.facebook.com", true, null));
        this.domains.put("api.vk.ru", new Domain("api.vk.ru", true, null));
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
