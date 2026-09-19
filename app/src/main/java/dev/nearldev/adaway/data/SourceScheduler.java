package dev.nearldev.adaway.data;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

public class SourceScheduler {
    private static final long INTERVAL_MS = 30 * 60 * 1000L;
    private static volatile boolean started = false;
    private static final Handler handler = new Handler(Looper.getMainLooper());

    public static synchronized void ensureStarted(Context context) {
        if (started) {
            return;
        }
        started = true;
        Context appContext = context.getApplicationContext();
        Runnable task = new Runnable() {
            @Override
            public void run() {
                updateAllEnabled(appContext);
                handler.postDelayed(this, INTERVAL_MS);
            }
        };
        handler.postDelayed(task, INTERVAL_MS);
    }

    public static void updateAllEnabled(Context context) {
        new Thread(() -> {
            HostsSourceStore sourceStore = HostsSourceStore.getInstance(context);
            DomainStore domainStore = DomainStore.getInstance(context);
            for (HostsSourceStore.Source source : sourceStore.getAll()) {
                if (!source.enabled) {
                    continue;
                }
                try {
                    int count;
                    boolean allow = source.isAllow();
                    if (HostsSourceStore.TYPE_FILE.equals(source.type)) {
                        count = HostsFetcher.fetchFromUri(context, Uri.parse(source.location), domainStore, source.id, allow);
                    } else {
                        count = HostsFetcher.fetchFromUrl(source.location, domainStore, source.id, allow);
                    }
                    sourceStore.updateCount(source.id, count, System.currentTimeMillis());
                } catch (Exception ignored) {
                }
            }
        }).start();
    }
}
