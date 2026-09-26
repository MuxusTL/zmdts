package dev.nitroplus.ui;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
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
    private View floatingView;
    private View menuView;
    private ImageView logoImage;
    private Switch vpnSwitch;
    private TextView tvVpnStatus;
    private boolean isMenuOpen = false;
    private WindowManager.LayoutParams params;

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

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 200;

        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_widget, null);
        menuView = floatingView.findViewById(R.id.menuView);
        logoImage = floatingView.findViewById(R.id.logoImage);

        setupMenu();
        setupTouchListener();
        
        LocalBroadcastManager.getInstance(this).registerReceiver(
                vpnStatusReceiver,
                new IntentFilter(dev.nitroplus.vpn.VpnService.VPN_UPDATE_STATUS_INTENT)
        );
        
        windowManager.addView(floatingView, params);
    }

    private void setupMenu() {
        vpnSwitch = floatingView.findViewById(R.id.switchVpn);
        Switch logSwitch = floatingView.findViewById(R.id.switchLog);
        tvVpnStatus = floatingView.findViewById(R.id.tvVpnStatus);
        ImageView btnClose = floatingView.findViewById(R.id.btnClose);
        
        updateVpnUI(dev.nitroplus.vpn.VpnService.isRunning);

        vpnSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked == dev.nitroplus.vpn.VpnService.isRunning) return;
            
            Intent intent = new Intent(this, dev.nitroplus.vpn.VpnService.class);
            intent.setAction(isChecked ? dev.nitroplus.vpn.VpnService.ACTION_START : dev.nitroplus.vpn.VpnService.ACTION_STOP);
            if (isChecked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        });

        logSwitch.setChecked(DnsLogStore.getInstance(this).isEnabled());
        logSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            DnsLogStore.getInstance(this).setEnabled(isChecked);
        });

        btnClose.setOnClickListener(v -> stopSelf());
    }

    private void updateVpnUI(boolean isRunning) {
        if (vpnSwitch != null) {
            vpnSwitch.setChecked(isRunning);
        }
        if (tvVpnStatus != null) {
            tvVpnStatus.setText(isRunning ? "Đã bật" : "Đang tắt");
            tvVpnStatus.setTextColor(Color.parseColor(isRunning ? "#34D399" : "#A1A1AA"));
        }
    }

    private void setupTouchListener() {
        View.OnTouchListener touchListener = new View.OnTouchListener() {
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
                        if (!isMoved && v == logoImage) {
                            isMenuOpen = !isMenuOpen;
                            menuView.setVisibility(isMenuOpen ? View.VISIBLE : View.GONE);
                        }
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (event.getRawX() - initialTouchX);
                        int dy = (int) (event.getRawY() - initialTouchY);
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            isMoved = true;
                            params.x = initialX + dx;
                            params.y = initialY + dy;
                            windowManager.updateViewLayout(floatingView, params);
                        }
                        return true;
                }
                return false;
            }
        };
        
        logoImage.setOnTouchListener(touchListener);
        menuView.setOnTouchListener(touchListener); // Allow dragging by touching the menu background too
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(vpnStatusReceiver);
        if (floatingView != null) windowManager.removeView(floatingView);
    }
}
