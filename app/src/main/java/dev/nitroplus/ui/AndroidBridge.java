package dev.nitroplus.ui;

import android.webkit.JavascriptInterface;

import dev.nitroplus.data.DnsLogStore;
import dev.nitroplus.data.DomainStore;
import dev.nitroplus.data.HostsFetcher;
import dev.nitroplus.data.HostsSourceStore;

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
        return this.domainStore.toJsonManual();
    }

    @JavascriptInterface
    public String addDomain(String name) {
        this.domainStore.add(name);
        return this.domainStore.toJsonManual();
    }

    @JavascriptInterface
    public String deleteDomain(String name) {
        this.domainStore.remove(name);
        return this.domainStore.toJsonManual();
    }

    @JavascriptInterface
    public String toggleDomain(String name) {
        this.domainStore.toggleEnabled(name);
        return this.domainStore.toJsonManual();
    }

    @JavascriptInterface
    public String renameDomain(String oldName, String newName) {
        this.domainStore.rename(oldName, newName);
        return this.domainStore.toJsonManual();
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
    public String getSource(String id) {
        return this.sourceStore.toJson(id);
    }

    @JavascriptInterface
    public String addUrlSource(String name, String url, String mode) {
        this.sourceStore.addUrl(name, url, mode);
        return this.sourceStore.toJson();
    }

    @JavascriptInterface
    public void pickFileSource(String mode) {
        this.activity.runOnUiThread(() -> this.activity.launchFilePicker(mode));
    }

    @JavascriptInterface
    public String removeSource(String id) {
        HostsSourceStore.Source source = this.sourceStore.get(id);
        if (source != null) {
            this.domainStore.removeAllFromSource(id, source.isAllow());
        }
        this.sourceStore.remove(id);
        return this.sourceStore.toJson();
    }

    @JavascriptInterface
    public String toggleSource(String id) {
        HostsSourceStore.Source source = this.sourceStore.get(id);
        boolean allowBefore = source != null && source.isAllow();
        boolean enabled = this.sourceStore.toggleEnabled(id);
        this.domainStore.setSourceEnabled(id, enabled, allowBefore);
        return this.sourceStore.toJson();
    }

    @JavascriptInterface
    public String setAllSourcesEnabled(boolean enabled) {
        for (HostsSourceStore.Source source : this.sourceStore.getAll()) {
            if (source.enabled != enabled) {
                this.sourceStore.toggleEnabled(source.id);
                this.domainStore.setSourceEnabled(source.id, enabled, source.isAllow());
            }
        }
        return this.sourceStore.toJson();
    }

    @JavascriptInterface
    public String renameSource(String id, String name) {
        this.sourceStore.setName(id, name);
        return this.sourceStore.toJson();
    }

    @JavascriptInterface
    public String setSourceMode(String id, String mode) {
        HostsSourceStore.Source source = this.sourceStore.get(id);
        if (source != null && !source.mode.equals(mode)) {
            boolean wasAllow = source.isAllow();
            this.domainStore.removeAllFromSource(id, wasAllow);
            this.sourceStore.setMode(id, mode);
        }
        return this.sourceStore.toJson();
    }

    @JavascriptInterface
    public String getSourceDomains(String id) {
        HostsSourceStore.Source source = this.sourceStore.get(id);
        if (source == null) {
            return "[]";
        }
        return this.domainStore.getDomainsForSourceJson(id, source.isAllow());
    }

    @JavascriptInterface
    public void updateSource(String id) {
        new Thread(() -> {
            HostsSourceStore.Source source = this.sourceStore.get(id);
            if (source == null) {
                return;
            }
            int count = -1;
            String error = null;
            try {
                boolean allow = source.isAllow();
                if (HostsSourceStore.TYPE_FILE.equals(source.type)) {
                    count = HostsFetcher.fetchFromUri(this.activity, android.net.Uri.parse(source.location), this.domainStore, id, allow);
                } else {
                    count = HostsFetcher.fetchFromUrl(source.location, this.domainStore, id, allow);
                }
                this.sourceStore.updateCount(id, count, System.currentTimeMillis());
            } catch (Exception e) {
                error = e.getMessage() == null ? "Lỗi tải nguồn" : e.getMessage();
            }
            this.activity.pushSourceUpdated(id, count, error);
        }).start();
    }
}
