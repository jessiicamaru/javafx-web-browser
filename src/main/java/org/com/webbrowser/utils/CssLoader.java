package org.com.webbrowser.utils;

import org.com.webbrowser.mapper.InlineStyleMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class CssLoader {

    public static List<String> extractCssLinksAndStyles(String html, String baseUri) {
        Document doc = Jsoup.parse(html, baseUri);
        List<String> result = new ArrayList<>();

        Elements links = doc.select("link[rel=stylesheet]");
        for (Element link : links) {
            String href = link.attr("abs:href");
            if (href == null || href.isEmpty()) href = link.attr("href");
            if (href != null && !href.isEmpty()) {
                String cssFile = downloadCss(href);
                if (cssFile != null) result.add(cssFile);
            }
        }

        Elements styles = doc.select("style");
        for (Element style : styles) {
            String cssContent = style.data();
            if (!cssContent.isEmpty()) {
                String converted = InlineStyleMapper.convertInlineStyle(cssContent);
                String cssFile = writeTempCss(converted);
                if (cssFile != null) result.add(cssFile);
            }
        }

        List<String> sanitized = new ArrayList<>();
        for (String css : result) {
            sanitized.add(CssSanitizer.clean(css));
        }
        return sanitized;
    }

    private static String downloadCss(String href) {
        try (InputStream in = new URL(href).openStream()) {
            Path tempFile = Files.createTempFile("remote-style-", ".css");
            Files.copy(in, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return tempFile.toUri().toString();
        } catch (IOException e) {
            return null;
        }
    }

    private static String writeTempCss(String cssContent) {
        try {
            Path tempFile = Files.createTempFile("inline-style-", ".css");
            Files.writeString(tempFile, cssContent);
            return tempFile.toUri().toString();
        } catch (IOException e) {
            return null;
        }
    }
}
