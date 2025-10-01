package org.com.webbrowser.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CssSanitizer {

    public static String clean(String rawCss) {
        if (rawCss == null || rawCss.isEmpty()) return "";

        String css = rawCss;

        css = css.replaceAll("@[a-zA-Z0-9\\-]+[^{;]*\\{[\\s\\S]*?\\}", "");
        css = css.replaceAll("@[a-zA-Z0-9\\-]+[\\s\\S]*?;", "");

        css = css.replaceAll("/\\*.*?\\*/", "");

        css = css.replaceAll("(box-shadow|margin|padding|border-[^:]+|outline|cursor|z-index|position|top|left|right|bottom)[^;]+;", "");

        css = mapCommonProperties(css);

        css = css.replaceAll("\\s+", " ").trim();

        return css;
    }

    private static String mapCommonProperties(String css) {
        css = css.replaceAll("(?i)color\\s*:\\s*([^;]+);", "-fx-text-fill: $1;");
        css = css.replaceAll("(?i)background-color\\s*:\\s*([^;]+);", "-fx-background-color: $1;");
        css = css.replaceAll("(?i)font-size\\s*:\\s*([^;]+);", "-fx-font-size: $1;");
        css = css.replaceAll("(?i)font-weight\\s*:\\s*([^;]+);", "-fx-font-weight: $1;");
        css = css.replaceAll("(?i)border\\s*:\\s*[^;]*\\s*([^;]+);", "-fx-border-color: $1;");

        Pattern textAlignPattern = Pattern.compile("(?i)text-align\\s*:\\s*([^;]+);");
        Matcher matcher = textAlignPattern.matcher(css);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String val = matcher.group(1).trim().toLowerCase();
            String javafxVal = mapTextAlignment(val);
            matcher.appendReplacement(sb, "-fx-alignment: " + javafxVal + ";");
        }
        matcher.appendTail(sb);
        css = sb.toString();

        return css;
    }

    private static String mapTextAlignment(String val) {
        switch (val) {
            case "center": return "CENTER";
            case "right": return "CENTER_RIGHT";
            case "left": return "CENTER_LEFT";
            case "justify": return "CENTER";
            default: return "CENTER_LEFT";
        }
    }
}
