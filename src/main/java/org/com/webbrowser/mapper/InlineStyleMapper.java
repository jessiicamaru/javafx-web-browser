package org.com.webbrowser.mapper;

import java.util.HashMap;
import java.util.Map;

public class InlineStyleMapper {

    private static final Map<String, String> PROPERTY_MAP = new HashMap<>();
    static {
        PROPERTY_MAP.put("color", "-fx-text-fill");
        PROPERTY_MAP.put("background-color", "-fx-background-color");
        PROPERTY_MAP.put("font-size", "-fx-font-size");
        PROPERTY_MAP.put("font-weight", "-fx-font-weight");
        PROPERTY_MAP.put("font-style", "-fx-font-style");
        PROPERTY_MAP.put("text-align", "-fx-text-alignment");
        PROPERTY_MAP.put("margin", "-fx-margin");
        PROPERTY_MAP.put("padding", "-fx-padding");
    }

    public static String convertInlineStyle(String inlineStyle) {
        if (inlineStyle == null || inlineStyle.isEmpty()) return "";

        StringBuilder result = new StringBuilder();
        String[] declarations = inlineStyle.split(";");

        for (String declaration : declarations) {
            String[] parts = declaration.split(":");
            if (parts.length != 2) continue;

            String property = parts[0].trim().toLowerCase();
            String value = parts[1].trim();

            String fxProperty = PROPERTY_MAP.get(property);
            if (fxProperty != null) {
                result.append(fxProperty).append(": ").append(value).append("; ");
            }
        }

        return result.toString().trim();
    }
}

