package org.com.webbrowser.utils;

public class UrlNormalizer {
    public static String normalizeUrl(String input) {
        if (input == null || input.isEmpty()) return "";

        String lower = input.toLowerCase();

        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return input;
        }

        if (lower.contains(".") && !lower.contains(" ")) {
            return "https://" + input;
        }

        try {
            String query = input.trim().replaceAll("\\s+", " ");

            query = query.replace(" ", "+");

            query = java.net.URLEncoder.encode(query, "UTF-8")
                    .replace("%2B", "+"); // giữ nguyên dấu '+'

            return "https://www.google.com/search?q=" + query;

        } catch (Exception e) {
            return "https://www.google.com/search?q=" + input;
        }
    }
}
