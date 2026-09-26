import re

content = """package dev.nitroplus.ui;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import dev.nitroplus.R;
import dev.nitroplus.data.DnsLogStore;

public class FloatingMenuService extends Service {

    private WindowManager windowManager;
    private View logoView;
    private View menuView;
    private Switch vpnSwitch;
    private TextView tvVpnStatus;
    private boolean isMenuOpen = false;
    private WindowManager.LayoutParams logoParams;
    private WindowManager.LayoutParams menuParams;

    private final android.widget.CompoundButton.OnCheckedChangeListener vpnSwitchListener = (buttonView, isChecked) -> {
        Intent intent = new Intent(this, dev.nitroplus.vpn.VpnService.class);
        intent.setAction(isChecked ? dev.nitroplus.vpn.VpnService.ACTION_START : dev.nitroplus.vpn.VpnService.ACTION_STOP);
        if (isChecked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    };

    private final BroadcastReceiver vpnStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Object status = intent.getSerializableExtra(dev.nitroplus.vpn.VpnService.VPN_UPDATE_STATUS_EXTRA);
            if (status instanceof dev.nitroplus.vpn.VpnStatus) {
                updateVpnUI(dev.nitroplus.vpn.VpnService.isRunning);
            }
        }
    };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;

        logoParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        logoParams.gravity = Gravity.TOP | Gravity.START;
        logoParams.x = 0;
        logoParams.y = 200;

        menuParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        menuParams.gravity = Gravity.TOP | Gravity.START;
        menuParams.x = 100;
        menuParams.y = 200;

        Context ctx = new android.view.ContextThemeWrapper(this, R.style.Theme_NitroVPN);
        
        logoView = LayoutInflater.from(ctx).inflate(R.layout.layout_floating_widget, null);
        menuView = LayoutInflater.from(ctx).inflate(R.layout.floating_menu, null);
        
        menuView.setVisibility(View.GONE);

        setupMenu();
        setupTouchListeners();
        
        LocalBroadcastManager.getInstance(this).registerReceiver(
                vpnStatusReceiver,
                new IntentFilter(dev.nitroplus.vpn.VpnService.VPN_UPDATE_STATUS_INTENT)
        );
        
        windowManager.addView(logoView, logoParams);
        windowManager.addView(menuView, menuParams);
    }

    private void setupMenu() {
        vpnSwitch = menuView.findViewById(R.id.switchVpn);
        Switch logSwitch = menuView.findViewById(R.id.switchLog);
        tvVpnStatus = menuView.findViewById(R.id.tvVpnStatus);
        ImageView btnClose = menuView.findViewById(R.id.btnClose);
        
        updateVpnUI(dev.nitroplus.vpn.VpnService.isRunning);
        
        vpnSwitch.setOnCheckedChangeListener(vpnSwitchListener);

        logSwitch.setChecked(DnsLogStore.getInstance(this).isEnabled());
        logSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            DnsLogStore.getInstance(this).setEnabled(isChecked);
        });

        btnClose.setOnClickListener(v -> stopSelf());
    }

    private void updateVpnUI(boolean isRunning) {
        if (vpnSwitch != null) {
            vpnSwitch.setOnCheckedChangeListener(null);
            vpnSwitch.setChecked(isRunning);
            vpnSwitch.setOnCheckedChangeListener(vpnSwitchListener);
        }
        if (tvVpnStatus != null) {
            tvVpnStatus.setText(isRunning ? "Đã bật" : "Đang tắt");
            tvVpnStatus.setTextColor(Color.parseColor(isRunning ? "#34D399" : "#A1A1AA"));
        }
    }

    private void setupTouchListeners() {
        logoView.setOnTouchListener(createTouchListener(logoView, logoParams, true));
        menuView.setOnTouchListener(createTouchListener(menuView, menuParams, false));
    }

    private View.OnTouchListener createTouchListener(final View view, final WindowManager.LayoutParams params, final boolean isLogo) {
        return new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            private boolean isMoved = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        isMoved = false;
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!isMoved && isLogo) {
                            isMenuOpen = !isMenuOpen;
                            menuView.setVisibility(isMenuOpen ? View.VISIBLE : View.GONE);
                            windowManager.updateViewLayout(menuView, menuParams);
                        }
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (event.getRawX() - initialTouchX);
                        int dy = (int) (event.getRawY() - initialTouchY);
                        if (Math.abs(dx) > 25 || Math.abs(dy) > 25) {
                            isMoved = true;
                            params.x = initialX + dx;
                            params.y = initialY + dy;
                            windowManager.updateViewLayout(view, params);
                        }
                        return true;
                }
                return false;
            }
        };
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(vpnStatusReceiver);
        if (logoView != null) windowManager.removeView(logoView);
        if (menuView != null) windowManager.removeView(menuView);
    }
}
"""

with open("app/src/main/java/dev/nitroplus/ui/FloatingMenuService.java", "w") as f:
    f.write(content)
