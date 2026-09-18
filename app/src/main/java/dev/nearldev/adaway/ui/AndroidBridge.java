package dev.nearldev.adaway.ui;

import android.webkit.JavascriptInterface;

import dev.nearldev.adaway.data.DomainStore;
import dev.nearldev.adaway.data.DnsLogStore;
import dev.nearldev.adaway.data.HostsSourceStore;
import dev.nearldev.adaway.data.HostsFetcher;

public class AndroidBridge {

    private final MainActivity activity;
    private final DomainStore domainStore;
    private final DnsLogStore logStore;
    private final HostsSourceStore sourceStore;

    AndroidBridge(MainActivity activity, DomainStore domainStore) {
        this.activity = activity;
        this.domainStore = domainStore;
        this.logStore = DnsLogStore.getInstance(activity);
        this.sourceStore = HostsSourceStore.getInstance(activity);
    }

    @JavascriptInterface
    public String getDomains() {
        return this.domainStore.toJson();
    }

    @JavascriptInterface
    public String addDomain(String name) {
        this.domainStore.add(name);
        return this.domainStore.toJson();
    }

    @JavascriptInterface
    public String deleteDomain(String name) {
        this.domainStore.remove(name);
        return this.domainStore.toJson();
    }

    @JavascriptInterface
    public String toggleDomain(String name) {
        this.domainStore.toggleEnabled(name);
        return this.domainStore.toJson();
    }

    @JavascriptInterface
    public String renameDomain(String oldName, String newName) {
        this.domainStore.rename(oldName, newName);
        return this.domainStore.toJson();
    }

    @JavascriptInterface
    public int getBlockedCount() {
        return this.domainStore.getBlockedCount();
    }

    @JavascriptInterface
    public boolean isVpnRunning() {
        return this.activity.isVpnRunning();
    }

    @JavascriptInterface
    public void requestToggleVpn() {
        this.activity.runOnUiThread(this.activity::onVpnToggleRequested);
    }

    @JavascriptInterface
    public boolean isLogEnabled() {
        return this.logStore.isEnabled();
    }

    @JavascriptInterface
    public void setLogEnabled(boolean enabled) {
        this.logStore.setEnabled(enabled);
    }

    @JavascriptInterface
    public String getLogs() {
        return this.logStore.toJson();
    }

    @JavascriptInterface
    public void clearLogs() {
        this.logStore.clear();
    }

    @JavascriptInterface
    public String getSources() {
        return this.sourceStore.toJson();
    }

    @JavascriptInterface
    public String addSource(String url) {
        this.sourceStore.add(url);
        return this.sourceStore.toJson();
    }

    @JavascriptInterface
    public String removeSource(String url) {
        this.domainStore.removeAllFromSource(url);
        this.sourceStore.remove(url);
        return this.sourceStore.toJson();
    }

    @JavascriptInterface
    public String toggleSource(String url) {
        boolean enabled = this.sourceStore.toggleEnabled(url);
        this.domainStore.setSourceEnabled(url, enabled);
        return this.sourceStore.toJson();
    }

    @JavascriptInterface
    public void updateSource(String url) {
        new Thread(() -> {
            int count = -1;
            String error = null;
            try {
                count = HostsFetcher.fetchAndApply(url, this.domainStore);
                this.sourceStore.updateCount(url, count);
            } catch (Exception e) {
                error = e.getMessage() == null ? "Lỗi tải nguồn" : e.getMessage();
            }
            this.activity.pushSourceUpdated(url, count, error);
        }).start();
    }
}
