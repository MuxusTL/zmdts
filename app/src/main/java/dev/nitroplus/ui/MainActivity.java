package dev.nitroplus.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import dev.nitroplus.R;
import dev.nitroplus.data.DomainStore;

public class MainActivity extends AppCompatActivity {

    private DomainStore domainStore;
    private WebView webView;
    private boolean vpnRunning = false;

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

    private final BroadcastReceiver vpnStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Object status = intent.getSerializableExtra(dev.nitroplus.vpn.VpnService.VPN_UPDATE_STATUS_EXTRA);
            if (status instanceof dev.nitroplus.vpn.VpnStatus) {
                onVpnStatusChanged((dev.nitroplus.vpn.VpnStatus) status);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        this.domainStore = DomainStore.getInstance(this);
        this.vpnRunning = dev.nitroplus.vpn.VpnService.isRunning;

        this.webView = findViewById(R.id.webView);
        this.webView.getSettings().setJavaScriptEnabled(true);
        this.webView.getSettings().setDomStorageEnabled(true);
        this.webView.setWebViewClient(new WebViewClient());
        this.webView.setWebChromeClient(new WebChromeClient());
        this.webView.addJavascriptInterface(new AndroidBridge(this, this.domainStore), "Android");
        this.webView.loadUrl("file:///android_asset/nitrovpn.html");

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

        requestNotificationPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(
                this.vpnStatusReceiver,
                new IntentFilter(dev.nitroplus.vpn.VpnService.VPN_UPDATE_STATUS_INTENT)
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
        Intent intent = new Intent(this, dev.nitroplus.vpn.VpnService.class)
                .setAction(dev.nitroplus.vpn.VpnService.ACTION_START);
        ContextCompat.startForegroundService(this, intent);
    }

    private void stopVpn() {
        Intent intent = new Intent(this, dev.nitroplus.vpn.VpnService.class)
                .setAction(dev.nitroplus.vpn.VpnService.ACTION_STOP);
        startService(intent);
    }

    private void onVpnStatusChanged(dev.nitroplus.vpn.VpnStatus status) {
        this.vpnRunning = status == dev.nitroplus.vpn.VpnStatus.RUNNING;
        if (status == dev.nitroplus.vpn.VpnStatus.RUNNING) {
            this.handler.removeCallbacks(this.statsTicker);
            this.handler.post(this.statsTicker);
        } else if (status == dev.nitroplus.vpn.VpnStatus.STOPPED) {
            this.handler.removeCallbacks(this.statsTicker);
            this.domainStore.resetBlockedCount();
        }
        String label = getString(status.getTextResource());
        runJs("onVpnStatus('" + status.name() + "', '" + escapeJs(label) + "');");
    }

    private void runJs(String script) {
        runOnUiThread(() -> this.webView.evaluateJavascript(script, null));
    }

    public boolean openApp(String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) {
            return false;
        }
        final String pkg = packageName.trim();
        runOnUiThread(() -> {
            boolean launched = false;
            // 1. Thử qua getLaunchIntentForPackage
            try {
                Intent intent = getPackageManager().getLaunchIntentForPackage(pkg);
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                    startActivity(intent);
                    launched = true;
                }
            } catch (Exception ignored) {
            }

            // 2. Thử tạo Intent trực tiếp
            if (!launched) {
                try {
                    Intent intent = new Intent(Intent.ACTION_MAIN);
                    intent.addCategory(Intent.CATEGORY_LAUNCHER);
                    intent.setPackage(pkg);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                    startActivity(intent);
                    launched = true;
                } catch (Exception ignored) {
                }
            }

            // 3. Nếu chưa cài đặt hoặc không mở được, thử mở Play Store
            if (!launched) {
                try {
                    Intent marketIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg));
                    marketIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(marketIntent);
                    launched = true;
                } catch (Exception e1) {
                    try {
                        Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + pkg));
                        webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(webIntent);
                        launched = true;
                    } catch (Exception ignored) {
                    }
                }
            }

            if (launched) {
                Toast.makeText(MainActivity.this, "Đang mở: " + pkg, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(MainActivity.this, "Không thể mở ứng dụng: " + pkg, Toast.LENGTH_LONG).show();
            }
        });
        return true;
    }

    private String escapeJs(String value) {
        return value.replace("'", "\\'");
    }
}
