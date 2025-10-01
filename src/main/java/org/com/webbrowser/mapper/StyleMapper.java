package org.com.webbrowser.mapper;

import java.util.Map;

public class StyleMapper {
    public static final Map<String, String> STYLES = Map.ofEntries(
            Map.entry("body", "-fx-background-color: #ffffff; -fx-padding: 20; -fx-spacing: 10;"),
            Map.entry("h1", "-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: #222; -fx-padding: 0 0 10 0;"),
            Map.entry("h2", "-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #333; -fx-padding: 0 0 8 0;"),
            Map.entry("h3", "-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #444; -fx-padding: 0 0 6 0;"),
            Map.entry("p", "-fx-font-size: 16px; -fx-text-fill: #333; -fx-line-spacing: 4; -fx-padding: 4 0;"),
            Map.entry("a", "-fx-font-size: 16px; -fx-text-fill: #1a73e8; -fx-underline: true;"),
            Map.entry("div", "-fx-spacing: 6; -fx-padding: 4 0;"),
            Map.entry("img", "-fx-padding: 10 0;"),
            Map.entry("ul", "-fx-spacing: 4; -fx-padding: 4 0;"),
            Map.entry("li", "-fx-font-size: 16px; -fx-text-fill: #333; -fx-padding: 2 0;")
    );
}