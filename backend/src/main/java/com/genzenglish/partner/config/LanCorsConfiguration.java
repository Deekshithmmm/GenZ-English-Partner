package com.genzenglish.partner.config;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.web.cors.CorsConfiguration;

/**
 * Also lets in pages served from a private network address (10.x.x.x, 172.16-31.x.x or 192.168.x.x, any port), so a
 * phone on the laptop's Wi-Fi or hotspot can open the web app at the laptop's address, whatever address the hotspot
 * hands out that day. Only an IP address counts: a name like {@code 192.168.1.5.example.com} does not.
 * For development only ({@code app.cors.allow-lan}).
 */
class LanCorsConfiguration extends CorsConfiguration {

    private static final Pattern IPV4_ORIGIN =
            Pattern.compile("https?://(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})(?::\\d{1,5})?");

    @Override
    public String checkOrigin(String origin) {
        String allowed = super.checkOrigin(origin);
        if (allowed != null || origin == null) {
            return allowed;
        }
        return isPrivateNetwork(origin) ? origin : null;
    }

    static boolean isPrivateNetwork(String origin) {
        Matcher address = IPV4_ORIGIN.matcher(origin);
        if (!address.matches()) {
            return false;
        }
        int[] parts = new int[4];
        for (int i = 0; i < 4; i++) {
            parts[i] = Integer.parseInt(address.group(i + 1));
            if (parts[i] > 255) {
                return false;
            }
        }
        return parts[0] == 10
                || (parts[0] == 172 && parts[1] >= 16 && parts[1] <= 31)
                || (parts[0] == 192 && parts[1] == 168);
    }
}
