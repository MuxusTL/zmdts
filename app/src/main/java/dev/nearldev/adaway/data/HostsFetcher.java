package dev.nearldev.adaway.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class HostsFetcher {

    public static int fetchAndApply(String sourceUrl, DomainStore domainStore) throws IOException {
        URL url = new URL(sourceUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", "NearlBlock/1.0");
        connection.setInstanceFollowRedirects(true);

        int count = 0;
        try {
            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                throw new IOException("HTTP " + responseCode);
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String domain = parseLine(line);
                    if (domain != null && domainStore.addFromSource(domain, sourceUrl)) {
                        count++;
                    }
                }
            }
        } finally {
            connection.disconnect();
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
        if (parts.length < 2) {
            return null;
        }
        String ip = parts[0];
        if (!(ip.equals("0.0.0.0") || ip.equals("127.0.0.1") || ip.equals("::1"))) {
            return null;
        }
        String domain = parts[1].toLowerCase(Locale.ROOT);
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
