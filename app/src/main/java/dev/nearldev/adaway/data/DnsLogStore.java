package dev.nearldev.adaway.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

public class DnsLogStore {
    private static final String PREFS_NAME = "dns_log_store";
    private static final String KEY_ENABLED = "log_enabled";
    private static final int MAX_ENTRIES = 300;

    private static volatile DnsLogStore instance;

    public static class Entry {
        public final long time;
        public final String domain;
        public final boolean blocked;
        public final String appLabel;
        public final String packageName;

        public Entry(long time, String domain, boolean blocked, String appLabel, String packageName) {
            this.time = time;
            this.domain = domain;
            this.blocked = blocked;
            this.appLabel = appLabel;
            this.packageName = packageName;
        }
    }

    private final SharedPreferences prefs;
    private final Deque<Entry> entries = new ArrayDeque<>();
    private volatile boolean enabled;

    private DnsLogStore(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.enabled = this.prefs.getBoolean(KEY_ENABLED, false);
    }

    public static synchronized DnsLogStore getInstance(Context context) {
        if (instance == null) {
            instance = new DnsLogStore(context.getApplicationContext());
        }
        return instance;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.prefs.edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public synchronized void add(String domain, boolean blocked, String appLabel, String packageName) {
        this.entries.addFirst(new Entry(System.currentTimeMillis(), domain, blocked, appLabel, packageName));
        while (this.entries.size() > MAX_ENTRIES) {
            this.entries.removeLast();
        }
    }

    public synchronized void clear() {
        this.entries.clear();
    }

    public synchronized String toJson() {
        SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        JSONArray array = new JSONArray();
        try {
            for (Entry entry : this.entries) {
                JSONObject item = new JSONObject();
                item.put("time", format.format(entry.time));
                item.put("domain", entry.domain);
                item.put("blocked", entry.blocked);
                item.put("appLabel", entry.appLabel == null ? "Không xác định" : entry.appLabel);
                item.put("packageName", entry.packageName == null ? "" : entry.packageName);
                array.put(item);
            }
        } catch (JSONException ignored) {
        }
        return array.toString();
    }
}
