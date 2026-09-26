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
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import dev.nitroplus.R;
import dev.nitroplus.data.DomainStore;

public class FloatingMenuService extends Service {

    private WindowManager windowManager;
    private View logoView;
    private View menuView;
    private androidx.appcompat.widget.SwitchCompat vpnSwitch;
    private TextView tvVpnStatus;
    private boolean isMenuOpen = false;
    private WindowManager.LayoutParams logoParams;
    private WindowManager.LayoutParams menuParams;

    private final android.widget.CompoundButton.OnCheckedChangeListener vpnSwitchListener = (buttonView, isChecked) -> {
        if (isChecked) {
            Intent prepareIntent = android.net.VpnService.prepare(this);
            if (prepareIntent != null) {
                // Permission not granted. Launch MainActivity.
                Intent activityIntent = new Intent(this, MainActivity.class);
                activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(activityIntent);
                Toast.makeText(this, "Vui lòng cấp quyền VPN trong ứng dụng", Toast.LENGTH_LONG).show();
                updateVpnUI(false); // Revert switch visually
                return;
            }
        }
        
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
        updateStats();
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
        try {
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

        Context ctx = new androidx.appcompat.view.ContextThemeWrapper(this, R.style.Theme_NitroVPN);
        
        logoView = LayoutInflater.from(ctx).inflate(R.layout.layout_floating_widget, null);
        menuView = LayoutInflater.from(ctx).inflate(R.layout.floating_menu, null);
        
        menuView.setVisibility(View.GONE);

        // Bo góc cho logo
        ImageView logoImage = logoView.findViewById(R.id.logoImage);
        logoImage.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), view.getWidth() * 0.25f);
            }
        });
        logoImage.setClipToOutline(true);

        setupMenu();
        setupTouchListeners();
        
        LocalBroadcastManager.getInstance(this).registerReceiver(
                vpnStatusReceiver,
                new IntentFilter(dev.nitroplus.vpn.VpnService.VPN_UPDATE_STATUS_INTENT)
        );
        
        windowManager.addView(logoView, logoParams);
        windowManager.addView(menuView, menuParams);
        } catch (Exception e) {
            android.util.Log.e("NitroVPN", "Menu Error: " + android.util.Log.getStackTraceString(e));
            Toast.makeText(this, "Lỗi hiển thị Menu: " + e.getMessage(), Toast.LENGTH_LONG).show();
            stopSelf();
        }
    }

    private void setupMenu() {
        vpnSwitch = menuView.findViewById(R.id.switchVpn);
        tvVpnStatus = menuView.findViewById(R.id.tvVpnStatus);
        ImageView btnClose = menuView.findViewById(R.id.btnClose);
        
        updateVpnUI(dev.nitroplus.vpn.VpnService.isRunning);
        updateStats();
        
        vpnSwitch.setOnCheckedChangeListener(vpnSwitchListener);

        btnClose.setOnClickListener(v -> stopSelf());
        // Setup fonts
        android.graphics.Typeface spaceGrotesk = android.graphics.Typeface.createFromAsset(getAssets(), "fonts/space_grotesk.ttf");
        android.graphics.Typeface inter = android.graphics.Typeface.createFromAsset(getAssets(), "fonts/inter.ttf");
        android.graphics.Typeface jbMono = android.graphics.Typeface.createFromAsset(getAssets(), "fonts/jb_mono.ttf");
        
        TextView textview1 = menuView.findViewById(R.id.textview1);
        if(textview1 != null) textview1.setTypeface(spaceGrotesk, android.graphics.Typeface.BOLD);
        
        TextView textview2 = menuView.findViewById(R.id.textview2);
        if(textview2 != null) textview2.setTypeface(inter, android.graphics.Typeface.NORMAL);
        
        TextView textview3 = menuView.findViewById(R.id.textview3);
        if(textview3 != null) textview3.setTypeface(inter, android.graphics.Typeface.NORMAL);
        
        TextView textview4 = menuView.findViewById(R.id.textview4);
        if(textview4 != null) textview4.setTypeface(inter, android.graphics.Typeface.NORMAL);
        
        TextView button1 = menuView.findViewById(R.id.button1);
        if(button1 != null) button1.setTypeface(inter, android.graphics.Typeface.BOLD);
        
        TextView blockedcount = menuView.findViewById(R.id.blockedcount);
        if(blockedcount != null) blockedcount.setTypeface(jbMono, android.graphics.Typeface.NORMAL);
        
        TextView activedomaincount = menuView.findViewById(R.id.activedomaincount);
        if(activedomaincount != null) activedomaincount.setTypeface(jbMono, android.graphics.Typeface.NORMAL);
        
        if(tvVpnStatus != null) tvVpnStatus.setTypeface(spaceGrotesk, android.graphics.Typeface.BOLD);

        TextView btnCloseMenu = menuView.findViewById(R.id.button1);
        if (btnCloseMenu != null) btnCloseMenu.setOnClickListener(v -> { isMenuOpen = false; menuView.setVisibility(View.GONE); });
    }

    
    private void updateStats() {
        DomainStore domainStore = DomainStore.getInstance(this);
        if (domainStore != null) {
            TextView blockedcount = menuView.findViewById(R.id.blockedcount);
            TextView activedomaincount = menuView.findViewById(R.id.activedomaincount);
            if (blockedcount != null) {
                blockedcount.setText(String.valueOf(domainStore.getBlockedCount()));
            }
            if (activedomaincount != null) {
                activedomaincount.setText(String.valueOf(domainStore.getEnabledCount()));
            }
        }
    }

    private void updateVpnUI(boolean isRunning) {
        if (vpnSwitch != null) {
            vpnSwitch.setOnCheckedChangeListener(null);
            vpnSwitch.setChecked(isRunning);
            vpnSwitch.setOnCheckedChangeListener(vpnSwitchListener);
        }
        if (tvVpnStatus != null) {
            tvVpnStatus.setText(isRunning ? "Đã kết nối" : "Đang tắt");
            tvVpnStatus.setTextColor(Color.parseColor(isRunning ? "#4ade80" : "#848386"));
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
                            if (isMenuOpen) updateStats();
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
