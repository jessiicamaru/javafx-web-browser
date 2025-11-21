package org.com.webbrowser.utils;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.application.Platform;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FaviconHelper {
    private static final Map<String, Image> cache = new ConcurrentHashMap<>();

    public static void loadFavicon(String url, javafx.scene.control.Tab tab) {
        try {
            java.net.URI uri = new java.net.URI(url);
            String domain = uri.getScheme() + "://" + uri.getHost();

            if (cache.containsKey(domain)) {
                setIcon(tab, cache.get(domain));
                return;
            }

            String faviconUrl = "https://www.google.com/s2/favicons?domain=" + uri.getHost() + "&sz=32";
            Image image = new Image(faviconUrl, true);

            image.progressProperty().addListener((obs, old, progress) -> {
                if (progress.doubleValue() >= 1.0) {
                    cache.put(domain, image);
                    setIcon(tab, image);
                }
            });

            image.errorProperty().addListener((obs, old, error) -> {
                if ((Boolean) error) System.out.println("Không tải favicon: " + domain);
            });

        } catch (Exception ignored) {}
    }

    private static void setIcon(javafx.scene.control.Tab tab, Image image) {
        Platform.runLater(() -> {
            ImageView iv = new ImageView(image);
            iv.setFitWidth(16);
            iv.setFitHeight(16);
            tab.setGraphic(iv);
        });
    }
}