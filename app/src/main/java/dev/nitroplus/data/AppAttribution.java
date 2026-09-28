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

    private static final Result UNKNOWN = new Result("", "Không xác định (Đã đóng kết nối)");

    public static Result resolve(Context context, InetAddress localAddr, int localPort, InetAddress remoteAddr, int remotePort) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return new Result("", "Không hỗ trợ trên Android cũ");
        }
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return UNKNOWN;

            int uid = cm.getConnectionOwnerUid(
                    OsConstants.IPPROTO_UDP,
                    new InetSocketAddress(localAddr, localPort),
                    new InetSocketAddress(remoteAddr, remotePort)
            );
            
            if (uid == 0) return new Result("root", "Hệ thống (Root / Kernel)");
            if (uid == 1000) return new Result("android", "Hệ thống Android (Core)");
            if (uid == 1056) return new Result("netd", "Dịch vụ mạng (DNS Resolver)");
            if (uid == 1073) return new Result("network_stack", "Ngăn xếp mạng (Network Stack)");
            
            if (uid < 0) {
                // Try TCP if UDP fails (e.g. DoH/DoT) although DNS is mostly UDP
                uid = cm.getConnectionOwnerUid(
                        OsConstants.IPPROTO_TCP,
                        new InetSocketAddress(localAddr, localPort),
                        new InetSocketAddress(remoteAddr, remotePort)
                );
            }
            
            if (uid < 0) {
                return UNKNOWN; // Still not found
            }

            PackageManager pm = context.getPackageManager();
            String[] packages = pm.getPackagesForUid(uid);
            
            if (packages == null || packages.length == 0) {
                String nameForUid = pm.getNameForUid(uid);
                if (nameForUid != null) {
                    return new Result(nameForUid, "Tiến trình UID: " + uid);
                }
                return new Result("", "UID: " + uid);
            }
            
            // Determine primary package
            String primaryPkg = packages[0];
            int primaryIndex = 0;
            if (packages.length > 1) {
                for (int i = 0; i < packages.length; i++) {
                    if (packages[i].contains("android.gms")) {
                        primaryPkg = packages[i];
                        primaryIndex = i;
                        break;
                    }
                }
            }

            // Get primary label
            String primaryLabel;
            try {
                primaryLabel = pm.getApplicationLabel(pm.getApplicationInfo(primaryPkg, 0)).toString();
            } catch (Exception e) {
                primaryLabel = primaryPkg;
            }

            if (packages.length == 1) {
                return new Result(primaryPkg, primaryLabel);
            }

            // Get secondary labels/packages
            StringBuilder sbSecondaryLabel = new StringBuilder();
            StringBuilder sbSecondaryPkg = new StringBuilder();
            boolean firstSecondary = true;

            for (int i = 0; i < packages.length; i++) {
                if (i == primaryIndex) continue;

                String pkg = packages[i];
                String label;
                try {
                    label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
                } catch (Exception e) {
                    label = pkg;
                }

                if (!firstSecondary) {
                    sbSecondaryLabel.append(", ");
                    sbSecondaryPkg.append(", ");
                }
                sbSecondaryLabel.append(label);
                sbSecondaryPkg.append(pkg);
                firstSecondary = false;
            }

            String finalLabel = primaryLabel + " (" + sbSecondaryLabel.toString() + ")";
            String finalPkg = primaryPkg + " (" + sbSecondaryPkg.toString() + ")";

            return new Result(finalPkg, finalLabel);
        } catch (Exception e) {
            return new Result("", "Lỗi phân tích: " + e.getMessage());
        }
    }
}
