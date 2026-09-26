package dev.nitroplus.vpn;

import static android.Manifest.permission.POST_NOTIFICATIONS;
import static android.app.NotificationManager.IMPORTANCE_LOW;
import static android.app.PendingIntent.FLAG_IMMUTABLE;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;
import static dev.nitroplus.vpn.VpnService.NetworkType.CELLULAR;
import static dev.nitroplus.vpn.VpnService.NetworkType.WIFI;
import static dev.nitroplus.vpn.VpnStatus.RECONNECTING;
import static dev.nitroplus.vpn.VpnStatus.RUNNING;
import static dev.nitroplus.vpn.VpnStatus.STARTING;
import static dev.nitroplus.vpn.VpnStatus.STOPPED;
import static dev.nitroplus.vpn.VpnStatus.WAITING_FOR_NETWORK;
import static java.util.Objects.requireNonNull;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import dev.nitroplus.R;
import dev.nitroplus.ui.MainActivity;
import dev.nitroplus.vpn.worker.VpnWorker;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Set;

import timber.log.Timber;

public class VpnService extends android.net.VpnService implements Handler.Callback {
    public static final String ACTION_START = "dev.nitroplus.vpn.START";
    public static volatile boolean isRunning = false;
    public static final String ACTION_STOP = "dev.nitroplus.vpn.STOP";
    public static final String VPN_UPDATE_STATUS_INTENT = "dev.nitroplus.VPN_UPDATE_STATUS";
    public static final String VPN_UPDATE_STATUS_EXTRA = "VPN_STATUS";

    private static final String NOTIFICATION_CHANNEL_ID = "vpn_service";
    private static final int NOTIFICATION_ID_RUNNING = 1001;
    private static final int NOTIFICATION_ID_RESUME = 1002;
    private static final int REQUEST_CODE_START = 43;
    private static final int REQUEST_CODE_PAUSE = 42;
    private static final int VPN_STATUS_UPDATE_MESSAGE_TYPE = 0;

    private final MyHandler handler;
    private final NetworkTypeCallback wifiNetworkCallback;
    private final NetworkTypeCallback cellularNetworkCallback;
    private final Set<NetworkType> availableNetworkTypes;
    private final VpnWorker vpnWorker;

    public VpnService() {
        this.handler = new MyHandler(this);
        this.wifiNetworkCallback = new NetworkTypeCallback(WIFI);
        this.cellularNetworkCallback = new NetworkTypeCallback(CELLULAR);
        this.availableNetworkTypes = new HashSet<>();
        this.vpnWorker = new VpnWorker(this);
    }

    @Override
    public void onCreate() {
        Timber.d("Creating VPN service…");
        createNotificationChannel();
        registerNetworkCallback();
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        Timber.d("onStartCommand %s", intent == null ? "null intent" : intent);
        if (intent == null) {
            return START_NOT_STICKY;
        }
        String action = intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopVpn();
            return START_NOT_STICKY;
        }
        startVpn();
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        Timber.d("Destroying VPN service…");
        isRunning = false;
        unregisterNetworkCallback();
        Timber.d("Destroyed VPN service.");
    }

    @Override
    public boolean handleMessage(@NonNull Message message) {
        if (message.what == VPN_STATUS_UPDATE_MESSAGE_TYPE) {
            updateVpnStatus(VpnStatus.fromCode(message.arg1));
        }
        return true;
    }

    public void notifyVpnStatus(VpnStatus status) {
        Message statusMessage = this.handler.obtainMessage(VPN_STATUS_UPDATE_MESSAGE_TYPE, status.toCode(), 0);
        this.handler.sendMessage(statusMessage);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.vpn_notification_channel),
                IMPORTANCE_LOW
        );
        manager.createNotificationChannel(channel);
    }

    private void startVpn() {
        Timber.d("Starting VPN service…");
        updateVpnStatus(STARTING);
        this.vpnWorker.start();
        Timber.i("VPN service started.");
    }

    private void stopVpn() {
        Timber.d("Stopping VPN service…");
        this.vpnWorker.stop();
        stopForeground(true);
        stopSelf();
        updateVpnStatus(STOPPED);
        Timber.i("VPN service stopped.");
    }

    private void waitForNetVpn() {
        this.vpnWorker.stop();
        updateVpnStatus(WAITING_FOR_NETWORK);
    }

    private void reconnect() {
        updateVpnStatus(RECONNECTING);
        this.vpnWorker.start();
    }

    private void updateVpnStatus(VpnStatus status) {
        isRunning = status == VpnStatus.RUNNING;
        Notification notification = getNotification(status);
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        switch (status) {
            case STARTING:
            case RUNNING:
                notificationManager.cancel(NOTIFICATION_ID_RESUME);
                startForeground(NOTIFICATION_ID_RUNNING, notification);
                break;
            default:
                if (checkSelfPermission(POST_NOTIFICATIONS) == PERMISSION_GRANTED) {
                    notificationManager.notify(NOTIFICATION_ID_RESUME, notification);
                }
        }

        Intent intent = new Intent(VPN_UPDATE_STATUS_INTENT);
        intent.putExtra(VPN_UPDATE_STATUS_EXTRA, status);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private Notification getNotification(VpnStatus status) {
        String title = getString(R.string.vpn_notification_title, getString(status.getTextResource()));

        Intent contentIntentTarget = new Intent(getApplicationContext(), MainActivity.class);
        contentIntentTarget.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent contentIntent = PendingIntent.getActivity(getApplicationContext(), 0, contentIntentTarget, FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setPriority(IMPORTANCE_LOW)
                .setContentIntent(contentIntent)
                .setSmallIcon(R.drawable.ic_notification)
                .setColorized(true)
                .setColor(getColor(R.color.notification))
                .setContentTitle(title);
        switch (status) {
            case RUNNING:
                Intent stopIntent = new Intent(this, VpnService.class).setAction(ACTION_STOP);
                PendingIntent stopActionIntent = PendingIntent.getService(this, REQUEST_CODE_PAUSE, stopIntent, FLAG_IMMUTABLE);
                builder.addAction(
                        R.drawable.ic_pause_24dp,
                        getString(R.string.vpn_notification_action_pause),
                        stopActionIntent
                ).setOngoing(true);
                break;
            case STOPPED:
                Intent startIntent = new Intent(this, VpnService.class).setAction(ACTION_START);
                PendingIntent startActionIntent = PendingIntent.getService(this, REQUEST_CODE_START, startIntent, FLAG_IMMUTABLE);
                builder.addAction(
                        0,
                        getString(R.string.vpn_notification_action_resume),
                        startActionIntent
                );
                break;
        }
        return builder.build();
    }

    private void registerNetworkCallback() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkRequest wifiNetworkRequest = new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_WIFI)
                .build();
        NetworkRequest cellularNetworkRequest = new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_CELLULAR)
                .build();
        initializeNetworkTypes(connectivityManager);
        connectivityManager.registerNetworkCallback(wifiNetworkRequest, this.wifiNetworkCallback, this.handler);
        connectivityManager.registerNetworkCallback(cellularNetworkRequest, this.cellularNetworkCallback, this.handler);
    }

    private void unregisterNetworkCallback() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        connectivityManager.unregisterNetworkCallback(this.wifiNetworkCallback);
        connectivityManager.unregisterNetworkCallback(this.cellularNetworkCallback);
    }

    private void initializeNetworkTypes(ConnectivityManager connectivityManager) {
        this.availableNetworkTypes.clear();
        Network activeNetwork = connectivityManager.getActiveNetwork();
        if (activeNetwork != null) {
            NetworkCapabilities networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
            if (networkCapabilities != null) {
                if (networkCapabilities.hasTransport(TRANSPORT_WIFI)) {
                    this.availableNetworkTypes.add(WIFI);
                }
                if (networkCapabilities.hasTransport(TRANSPORT_CELLULAR)) {
                    this.availableNetworkTypes.add(CELLULAR);
                }
            }
        }
        Timber.d("Initial network types: %s ", this.availableNetworkTypes);
    }

    private void addNetworkType(NetworkType type) {
        boolean noNetwork = this.availableNetworkTypes.isEmpty();
        this.availableNetworkTypes.add(type);
        if (noNetwork) {
            Timber.d("Reconnecting VPN on network %s.", type);
            reconnect();
        }
    }

    private void removeNetworkType(NetworkType type) {
        this.availableNetworkTypes.remove(type);
        if (this.availableNetworkTypes.isEmpty()) {
            Timber.d("Waiting for network…");
            waitForNetVpn();
        } else {
            reconnect();
        }
    }

    private class NetworkTypeCallback extends NetworkCallback {
        private final NetworkType monitoredType;

        NetworkTypeCallback(NetworkType monitoredType) {
            this.monitoredType = monitoredType;
        }

        @Override
        public void onAvailable(@NonNull Network network) {
            if (!isRunning) return;
            Timber.d("On available %s", this.monitoredType);
            addNetworkType(this.monitoredType);
        }

        @Override
        public void onLost(@NonNull Network network) {
            if (!isRunning) return;
            Timber.d("On lost %s", this.monitoredType);
            removeNetworkType(this.monitoredType);
        }
    }

    enum NetworkType {
        CELLULAR,
        WIFI,
    }

    private static class MyHandler extends Handler {

        private final WeakReference<Callback> callback;

        MyHandler(Callback callback) {
            super(requireNonNull(Looper.myLooper()));
            this.callback = new WeakReference<>(callback);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            Callback callback = this.callback.get();
            if (callback != null) {
                callback.handleMessage(msg);
            }
            super.handleMessage(msg);
        }
    }
}
