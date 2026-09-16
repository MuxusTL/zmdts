package dev.nearldev.adaway.ui;

import android.webkit.JavascriptInterface;

import dev.nearldev.adaway.data.DomainStore;

public class AndroidBridge {

    private final MainActivity activity;
    private final DomainStore domainStore;

    AndroidBridge(MainActivity activity, DomainStore domainStore) {
        this.activity = activity;
        this.domainStore = domainStore;
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
}
