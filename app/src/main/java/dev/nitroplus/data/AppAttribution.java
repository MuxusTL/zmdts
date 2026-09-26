package dev.nitroplus.data;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.os.Build;
import android.system.OsConstants;

import java.net.InetAddress;
import java.net.InetSocketAddress;

public class AppAttribution {

    public static class Result {
        public final String packageName;
        public final String label;

        Result(String packageName, String label) {
            this.packageName = packageName;
            this.label = label;
        }
    }

    private static final Result UNKNOWN = new Result(null, null);

    public static Result resolve(Context context, InetAddress localAddr, int localPort, InetAddress remoteAddr, int remotePort) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return UNKNOWN;
        }
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                return UNKNOWN;
            }
            int uid = cm.getConnectionOwnerUid(
                    OsConstants.IPPROTO_UDP,
                    new InetSocketAddress(localAddr, localPort),
                    new InetSocketAddress(remoteAddr, remotePort)
            );
            if (uid < 0) {
                return UNKNOWN;
            }
            PackageManager pm = context.getPackageManager();
            String[] packages = pm.getPackagesForUid(uid);
            if (packages == null || packages.length == 0) {
                return UNKNOWN;
            }
            String packageName = packages[0];
            ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            String label = pm.getApplicationLabel(appInfo).toString();
            if (packages.length > 1) {
                label += " (+ " + (packages.length - 1) + " apps)";
            }
            return new Result(packageName, label);
        } catch (Exception e) {
            return UNKNOWN;
        }
    }
}
