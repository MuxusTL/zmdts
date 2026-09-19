package dev.nearldev.adaway.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import dev.nearldev.adaway.R;
import dev.nearldev.adaway.data.DomainStore;
import dev.nearldev.adaway.data.HostsFetcher;
import dev.nearldev.adaway.data.HostsSourceStore;
import dev.nearldev.adaway.data.SourceScheduler;

public class MainActivity extends AppCompatActivity {

    private DomainStore domainStore;
    private HostsSourceStore sourceStore;
    private WebView webView;
    private boolean vpnRunning = false;
    private String pendingFileSourceMode = HostsSourceStore.MODE_BLOCK;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable statsTicker = new Runnable() {
        @Override
        public void run() {
            if (vpnRunning) {
                runJs("if (typeof updateStats === 'function') updateStats();");
            }
            handler.postDelayed(this, 1000);
        }
    };

    private ActivityResultLauncher<Intent> vpnConsentLauncher;
    private ActivityResultLauncher<String> notificationPermissionLauncher;
    private ActivityResultLauncher<String[]> filePickerLauncher;

    private final BroadcastReceiver vpnStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Object status = intent.getSerializableExtra(dev.nearldev.adaway.vpn.VpnService.VPN_UPDATE_STATUS_EXTRA);
            if (status instanceof dev.nearldev.adaway.vpn.VpnStatus) {
                onVpnStatusChanged((dev.nearldev.adaway.vpn.VpnStatus) status);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        this.domainStore = DomainStore.getInstance(this);
        this.sourceStore = HostsSourceStore.getInstance(this);
        SourceScheduler.ensureStarted(this);

        this.webView = findViewById(R.id.webView);
        this.webView.getSettings().setJavaScriptEnabled(true);
        this.webView.setWebViewClient(new WebViewClient());
        this.webView.addJavascriptInterface(new AndroidBridge(this, this.domainStore), "Android");
        this.webView.loadUrl("file:///android_asset/adaway.html");

        this.vpnConsentLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        startVpn();
                    }
                }
        );
        this.notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> { }
        );
        this.filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                this::onFilePicked
        );

        requestNotificationPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(
                this.vpnStatusReceiver,
                new IntentFilter(dev.nearldev.adaway.vpn.VpnService.VPN_UPDATE_STATUS_INTENT)
        );
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(this.vpnStatusReceiver);
        this.handler.removeCallbacks(this.statsTicker);
    }

    boolean isVpnRunning() {
        return this.vpnRunning;
    }

    void onVpnToggleRequested() {
        if (this.vpnRunning) {
            stopVpn();
        } else {
            requestVpnPermission();
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            this.notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private void requestVpnPermission() {
        Intent prepareIntent = VpnService.prepare(this);
        if (prepareIntent != null) {
            this.vpnConsentLauncher.launch(prepareIntent);
        } else {
            startVpn();
        }
    }

    private void startVpn() {
        Intent intent = new Intent(this, dev.nearldev.adaway.vpn.VpnService.class)
                .setAction(dev.nearldev.adaway.vpn.VpnService.ACTION_START);
        ContextCompat.startForegroundService(this, intent);
    }

    private void stopVpn() {
        Intent intent = new Intent(this, dev.nearldev.adaway.vpn.VpnService.class)
                .setAction(dev.nearldev.adaway.vpn.VpnService.ACTION_STOP);
        startService(intent);
    }

    private void onVpnStatusChanged(dev.nearldev.adaway.vpn.VpnStatus status) {
        this.vpnRunning = status == dev.nearldev.adaway.vpn.VpnStatus.RUNNING;
        if (status == dev.nearldev.adaway.vpn.VpnStatus.RUNNING) {
            this.handler.removeCallbacks(this.statsTicker);
            this.handler.post(this.statsTicker);
        } else if (status == dev.nearldev.adaway.vpn.VpnStatus.STOPPED) {
            this.handler.removeCallbacks(this.statsTicker);
            this.domainStore.resetBlockedCount();
        }
        String label = getString(status.getTextResource());
        runJs("onVpnStatus('" + status.name() + "', '" + escapeJs(label) + "');");
    }

    private void runJs(String script) {
        runOnUiThread(() -> this.webView.evaluateJavascript(script, null));
    }

    void pushSourceUpdated(String id, int count, String error) {
        String errorArg = error == null ? "null" : "'" + escapeJs(error) + "'";
        runJs("onSourceUpdated('" + escapeJs(id) + "', " + count + ", " + errorArg + ");");
    }

    void launchFilePicker(String mode) {
        this.pendingFileSourceMode = mode;
        this.filePickerLauncher.launch(new String[]{"*/*"});
    }

    private void onFilePicked(Uri uri) {
        if (uri == null) {
            return;
        }
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
        }
        String name = queryFileName(uri);
        String id = this.sourceStore.addFile(name, uri.toString(), this.pendingFileSourceMode);
        runJs("refreshSources();");
        if (id == null) {
            return;
        }
        boolean allow = HostsSourceStore.MODE_ALLOW.equals(this.pendingFileSourceMode);
        new Thread(() -> {
            int count = -1;
            String error = null;
            try {
                count = HostsFetcher.fetchFromUri(this, uri, this.domainStore, id, allow);
                this.sourceStore.updateCount(id, count, System.currentTimeMillis());
            } catch (Exception e) {
                error = e.getMessage() == null ? "Lỗi đọc tệp" : e.getMessage();
            }
            pushSourceUpdated(id, count, error);
        }).start();
    }

    private String queryFileName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    return cursor.getString(index);
                }
            }
        } catch (Exception ignored) {
        }
        return "Tệp hosts";
    }

    private String escapeJs(String value) {
        return value.replace("'", "\\'");
    }
}
