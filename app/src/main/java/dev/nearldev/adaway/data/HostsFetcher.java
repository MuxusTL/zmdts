package dev.nearldev.adaway.data;

import android.content.Context;
import android.net.Uri;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class HostsFetcher {

    public static int fetchFromUrl(String sourceUrl, DomainStore domainStore, String sourceId, boolean allowMode) throws IOException {
        URL url = new URL(sourceUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", "NearlBlock/1.0");
        connection.setInstanceFollowRedirects(true);

        try {
            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                throw new IOException("HTTP " + responseCode);
            }
            try (InputStream input = connection.getInputStream()) {
                return applyStream(input, domainStore, sourceId, allowMode);
            }
        } finally {
            connection.disconnect();
        }
    }

    public static int fetchFromUri(Context context, Uri uri, DomainStore domainStore, String sourceId, boolean allowMode) throws IOException {
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) {
                throw new IOException("Không mở được tệp");
            }
            return applyStream(input, domainStore, sourceId, allowMode);
        }
    }

    private static int applyStream(InputStream input, DomainStore domainStore, String sourceId, boolean allowMode) throws IOException {
        int count = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String domain = parseLine(line);
                if (domain != null && domainStore.addFromSource(domain, sourceId, allowMode)) {
                    count++;
                }
            }
        }
        domainStore.persistNow();
        return count;
    }

    private static String parseLine(String rawLine) {
        String line = rawLine.trim();
        if (line.isEmpty() || line.startsWith("#")) {
            return null;
        }
        String[] parts = line.split("\\s+");
        String domain;
        if (parts.length >= 2 && (parts[0].equals("0.0.0.0") || parts[0].equals("127.0.0.1") || parts[0].equals("::1"))) {
            domain = parts[1].toLowerCase(Locale.ROOT);
        } else if (parts.length == 1) {
            domain = parts[0].toLowerCase(Locale.ROOT);
        } else {
            return null;
        }
        if (domain.equals("localhost")
                || domain.equals("localhost.localdomain")
                || domain.equals("local")
                || domain.equals("broadcasthost")
                || domain.startsWith("ip6-")) {
            return null;
        }
        if (!domain.contains(".")) {
            return null;
        }
        return domain;
    }
}
