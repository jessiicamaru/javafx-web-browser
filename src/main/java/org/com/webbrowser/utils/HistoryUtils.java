package org.com.webbrowser.utils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;

public class HistoryUtils {

    // Danh sách các tham số Google thường thêm vào (tracking)
    private static final Set<String> GOOGLE_TRACKING_PARAMS = Set.of(
            "sei", "ved", "oq", "gs_lcp", "source", "sourceid", "rlz",
            "ie", "oe", "gl", "hl", "uule", "gws_rd", "ei", "client"
    );

    /**
     * Chuẩn hóa URL Google Search để loại bỏ tham số tracking
     */
    public static String normalizeGoogleSearchUrl(String url) {
        if (url == null || !url.contains("google.com/search")) {
            return url;
        }

        try {
            URI uri = new URI(url);
            String query = uri.getQuery();
            if (query == null) return url;

            Map<String, String> params = new LinkedHashMap<>();
            for (String param : query.split("&")) {
                String[] pair = param.split("=", 2);
                if (pair.length == 2) {
                    String key = pair[0];
                    String value = pair[1];

                    // Chỉ giữ lại tham số "q" và một vài tham số quan trọng (nếu cần)
                    if ("q".equals(key) || "tbm".equals(key) || "tbs".equals(key)) {
                        params.put(key, value);
                    }
                    // Bỏ hết các tham số tracking
                }
            }

            if (params.isEmpty()) return url;

            String newQuery = params.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining("&"));

            return new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), newQuery, uri.getFragment()).toString();

        } catch (URISyntaxException e) {
            return url; // Nếu lỗi → trả nguyên bản
        }
    }

    /**
     * Chuẩn hóa URL chung: loại bỏ fragment (#...) và một số tham số không cần thiết
     */
    public static String normalizeUrlForHistory(String url) {
        if (url == null) return null;

        // 1. Loại bỏ captcha Google
        if (url.contains("/sorry/index")) {
            return null;
        }

        // 2. Chuẩn hóa Google Search
        String normalized = normalizeGoogleSearchUrl(url);

        try {
            URI uri = new URI(normalized);
            // Loại bỏ fragment (#...)
            return new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), uri.getQuery(), null).toString();
        } catch (Exception e) {
            return normalized;
        }
    }
}