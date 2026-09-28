package dev.nitroplus.ui;

import android.webkit.JavascriptInterface;

import dev.nitroplus.data.DnsLogStore;
import dev.nitroplus.data.DomainStore;

public class AndroidBridge {

    private final MainActivity activity;
    private final DomainStore domainStore;
    private final DnsLogStore logStore;

    AndroidBridge(MainActivity activity, DomainStore domainStore) {
        this.activity = activity;
        this.domainStore = domainStore;
        this.logStore = DnsLogStore.getInstance(activity);
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
    public boolean openApp(String packageName) {
        return this.activity.openApp(packageName);
    }

    @JavascriptInterface
    public void requestFloatingMenu() {
        this.activity.runOnUiThread(this.activity::requestFloatingMenu);
    }

    @JavascriptInterface
    public boolean isDevModeStrictBlocking() {
        return this.domainStore.isDevModeStrictBlocking();
    }

    @JavascriptInterface
    public void setDevModeStrictBlocking(boolean strict) {
        this.domainStore.setDevModeStrictBlocking(strict);
    }

    @JavascriptInterface
    public String getAppVersion() {
        try {
            android.content.pm.PackageInfo pInfo = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            return pInfo.versionName + " (" + pInfo.versionCode + ")";
        } catch (Exception e) {
            return "Unknown";
        }
    }

    @JavascriptInterface
    public String getDeviceInfo() {
        return android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL + " (Android " + android.os.Build.VERSION.RELEASE + ", API " + android.os.Build.VERSION.SDK_INT + ")";
    }

    @JavascriptInterface
    public String getDebugLogs() {
        try {
            java.lang.Process process = Runtime.getRuntime().exec("logcat -d -t 500");
            java.io.BufferedReader bufferedReader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()));
            StringBuilder log = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                if (line.contains("nitroplus") || line.contains("VpnWorker") || line.contains("FloatingMenu")) {
                    log.append(line).append("\n");
                }
            }
            return log.toString();
        } catch (Exception e) {
            return "Failed to get logs: " + e.getMessage();
        }
    }
}

