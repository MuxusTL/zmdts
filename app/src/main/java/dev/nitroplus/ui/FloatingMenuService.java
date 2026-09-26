package dev.nitroplus.ui;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import dev.nitroplus.R;
import dev.nitroplus.data.DnsLogStore;

public class FloatingMenuService extends Service {

    private WindowManager windowManager;
    private View floatingView;
    private View menuView;
    private ImageView logoImage;
    private boolean isMenuOpen = false;
    private WindowManager.LayoutParams params;

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

        floatingView = createFloatingView();
        windowManager.addView(floatingView, params);
    }

    private View createFloatingView() {
        FrameLayout container = new FrameLayout(this);
        
        logoImage = new ImageView(this);
        logoImage.setImageResource(R.mipmap.ic_launcher);
        int size = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48, getResources().getDisplayMetrics());
        FrameLayout.LayoutParams logoParams = new FrameLayout.LayoutParams(size, size);
        logoImage.setLayoutParams(logoParams);
        
        menuView = createMenuView();
        menuView.setVisibility(View.GONE);
        
        container.addView(menuView);
        container.addView(logoImage);

        setupTouchListener();
        
        return container;
    }

    private View createMenuView() {
        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setBackgroundColor(Color.parseColor("#121116")); // surface-2
        menu.setPadding(32, 32, 32, 32);
        
        int width = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 220, getResources().getDisplayMetrics());
        FrameLayout.LayoutParams menuParams = new FrameLayout.LayoutParams(width, FrameLayout.LayoutParams.WRAP_CONTENT);
        menuParams.setMargins(60, 0, 0, 0); // Offset to show near logo
        menu.setLayoutParams(menuParams);

        TextView title = new TextView(this);
        title.setText("NitroVPN");
        title.setTextColor(Color.WHITE);
        title.setTextSize(16);
        title.setPadding(0, 0, 0, 16);
        
        Switch vpnSwitch = new Switch(this);
        vpnSwitch.setText("Trạng thái VPN");
        vpnSwitch.setTextColor(Color.WHITE);
        vpnSwitch.setChecked(dev.nitroplus.vpn.VpnService.isRunning);
        vpnSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Intent intent = new Intent(this, dev.nitroplus.vpn.VpnService.class);
            intent.setAction(isChecked ? dev.nitroplus.vpn.VpnService.ACTION_START : dev.nitroplus.vpn.VpnService.ACTION_STOP);
            if (isChecked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        });

        Switch logSwitch = new Switch(this);
        logSwitch.setText("Ghi Nhật ký DNS");
        logSwitch.setTextColor(Color.WHITE);
        logSwitch.setChecked(DnsLogStore.getInstance(this).isEnabled());
        logSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            DnsLogStore.getInstance(this).setEnabled(isChecked);
        });

        TextView closeBtn = new TextView(this);
        closeBtn.setText("Đóng Menu");
        closeBtn.setTextColor(Color.parseColor("#ec4899"));
        closeBtn.setPadding(0, 32, 0, 0);
        closeBtn.setOnClickListener(v -> stopSelf());

        menu.addView(title);
        menu.addView(vpnSwitch);
        menu.addView(logSwitch);
        menu.addView(closeBtn);

        return menu;
    }

    private void setupTouchListener() {
        logoImage.setOnTouchListener(new View.OnTouchListener() {
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
                        if (!isMoved) {
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
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingView != null) windowManager.removeView(floatingView);
    }
}
