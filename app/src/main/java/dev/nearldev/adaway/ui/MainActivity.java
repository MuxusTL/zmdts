package dev.nearldev.adaway.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;
import java.util.List;

import dev.nearldev.adaway.R;
import dev.nearldev.adaway.data.DomainStore;

public class MainActivity extends AppCompatActivity {

    private static final String FILTER_ALL = "all";
    private static final String FILTER_ON = "on";
    private static final String FILTER_OFF = "off";

    private DomainStore domainStore;
    private String currentFilter = FILTER_ALL;
    private boolean vpnRunning = false;

    private TextView vpnStatusText;
    private SwitchMaterial vpnSwitch;
    private View vpnCard;
    private TextView blockedCountText;
    private TextView activeDomainCountText;
    private EditText addInput;
    private LinearLayout domainList;
    private View emptyState;
    private Button filterAllBtn;
    private Button filterOnBtn;
    private Button filterOffBtn;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable statsTicker = new Runnable() {
        @Override
        public void run() {
            refreshStats();
            handler.postDelayed(this, 1000);
        }
    };

    private ActivityResultLauncher<Intent> vpnConsentLauncher;
    private ActivityResultLauncher<String> notificationPermissionLauncher;

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

        this.vpnStatusText = findViewById(R.id.vpnStatusText);
        this.vpnSwitch = findViewById(R.id.vpnSwitch);
        this.vpnCard = findViewById(R.id.vpnCard);
        this.blockedCountText = findViewById(R.id.blockedCount);
        this.activeDomainCountText = findViewById(R.id.activeDomainCount);
        this.addInput = findViewById(R.id.addInput);
        this.domainList = findViewById(R.id.domainList);
        this.emptyState = findViewById(R.id.emptyState);
        this.filterAllBtn = findViewById(R.id.filterAllBtn);
        this.filterOnBtn = findViewById(R.id.filterOnBtn);
        this.filterOffBtn = findViewById(R.id.filterOffBtn);

        this.vpnConsentLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        startVpn();
                    } else {
                        this.vpnSwitch.setChecked(false);
                    }
                }
        );
        this.notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> { }
        );

        this.vpnSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                requestVpnPermission();
            } else {
                stopVpn();
            }
        });

        findViewById(R.id.addBtn).setOnClickListener(v -> addDomain());
        this.filterAllBtn.setOnClickListener(v -> setFilter(FILTER_ALL));
        this.filterOnBtn.setOnClickListener(v -> setFilter(FILTER_ON));
        this.filterOffBtn.setOnClickListener(v -> setFilter(FILTER_OFF));

        requestNotificationPermission();
        renderDomains();
    }

    @Override
    protected void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(
                this.vpnStatusReceiver,
                new IntentFilter(dev.nearldev.adaway.vpn.VpnService.VPN_UPDATE_STATUS_INTENT)
        );
        this.handler.post(this.statsTicker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(this.vpnStatusReceiver);
        this.handler.removeCallbacks(this.statsTicker);
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
        this.vpnStatusText.setText(status.getTextResource());
        switch (status) {
            case RUNNING:
                this.vpnRunning = true;
                this.vpnSwitch.setChecked(true);
                this.vpnStatusText.setTextColor(getColor(R.color.green));
                this.vpnCard.setBackgroundResource(R.drawable.bg_card);
                break;
            case STARTING:
            case RECONNECTING:
            case RECONNECTING_NETWORK_ERROR:
                this.vpnStatusText.setTextColor(getColor(R.color.accent));
                break;
            case STOPPED:
                this.vpnRunning = false;
                this.vpnSwitch.setChecked(false);
                this.vpnStatusText.setTextColor(getColor(R.color.text_muted));
                this.domainStore.resetBlockedCount();
                break;
            default:
                this.vpnStatusText.setTextColor(getColor(R.color.text_muted));
        }
        refreshStats();
    }

    private void refreshStats() {
        this.blockedCountText.setText(String.valueOf(this.domainStore.getBlockedCount()));
        this.activeDomainCountText.setText(String.valueOf(this.domainStore.getEnabledCount()));
    }

    private void addDomain() {
        String value = this.addInput.getText().toString().trim();
        if (value.isEmpty()) {
            return;
        }
        if (this.domainStore.add(value)) {
            this.addInput.setText("");
            renderDomains();
        } else {
            Toast.makeText(this, "Domain đã tồn tại", Toast.LENGTH_SHORT).show();
        }
    }

    private void setFilter(String filter) {
        this.currentFilter = filter;
        this.filterAllBtn.setBackgroundResource(FILTER_ALL.equals(filter) ? R.drawable.bg_filter_active : R.drawable.bg_filter_inactive);
        this.filterAllBtn.setTextColor(getColor(FILTER_ALL.equals(filter) ? R.color.text : R.color.text_muted));
        this.filterOnBtn.setBackgroundResource(FILTER_ON.equals(filter) ? R.drawable.bg_filter_active : R.drawable.bg_filter_inactive);
        this.filterOnBtn.setTextColor(getColor(FILTER_ON.equals(filter) ? R.color.text : R.color.text_muted));
        this.filterOffBtn.setBackgroundResource(FILTER_OFF.equals(filter) ? R.drawable.bg_filter_active : R.drawable.bg_filter_inactive);
        this.filterOffBtn.setTextColor(getColor(FILTER_OFF.equals(filter) ? R.color.text : R.color.text_muted));
        renderDomains();
    }

    private void renderDomains() {
        this.domainList.removeAllViews();
        List<DomainStore.Domain> all = this.domainStore.getAll();
        List<DomainStore.Domain> filtered = new ArrayList<>();
        for (DomainStore.Domain domain : all) {
            if (FILTER_ON.equals(this.currentFilter) && !domain.enabled) continue;
            if (FILTER_OFF.equals(this.currentFilter) && domain.enabled) continue;
            filtered.add(domain);
        }

        this.activeDomainCountText.setText(String.valueOf(this.domainStore.getEnabledCount()));

        if (filtered.isEmpty()) {
            this.emptyState.setVisibility(View.VISIBLE);
            return;
        }
        this.emptyState.setVisibility(View.GONE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (DomainStore.Domain domain : filtered) {
            View row = inflater.inflate(R.layout.item_domain, this.domainList, false);
            TextView nameView = row.findViewById(R.id.domainName);
            SwitchMaterial switchView = row.findViewById(R.id.domainSwitch);
            ImageButton editBtn = row.findViewById(R.id.editBtn);
            ImageButton deleteBtn = row.findViewById(R.id.deleteBtn);

            nameView.setText(domain.name);
            nameView.setTextColor(getColor(domain.enabled ? R.color.text : R.color.text_muted));
            switchView.setChecked(domain.enabled);
            switchView.setOnCheckedChangeListener((buttonView, isChecked) -> {
                this.domainStore.setEnabled(domain.name, isChecked);
                renderDomains();
            });
            deleteBtn.setOnClickListener(v -> {
                this.domainStore.remove(domain.name);
                renderDomains();
            });
            editBtn.setOnClickListener(v -> showEditRow(row, domain));

            this.domainList.addView(row);
        }
    }

    private void showEditRow(View row, DomainStore.Domain domain) {
        EditText editText = new EditText(this);
        editText.setText(domain.name);
        editText.setTextColor(getColor(R.color.text));
        editText.setSingleLine(true);
        editText.setBackgroundResource(R.drawable.bg_input);
        editText.setPadding(24, 16, 24, 16);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Sửa domain")
                .setView(editText)
                .setPositiveButton("Lưu", (dialog, which) -> {
                    String newName = editText.getText().toString().trim();
                    if (!newName.isEmpty() && this.domainStore.rename(domain.name, newName)) {
                        renderDomains();
                    } else if (!newName.equals(domain.name)) {
                        Toast.makeText(this, "Không thể đổi tên", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Huỷ", null)
                .show();
    }
}
