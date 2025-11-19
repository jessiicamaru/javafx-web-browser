package org.com.webbrowser.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Side;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import org.com.webbrowser.model.HistoryEntry;
import org.com.webbrowser.service.BookmarkService;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.com.webbrowser.utils.UrlNormalizer.normalizeUrl;

public class UrlAutoComplete {

    private final TextField textField;
    private final ObservableList<HistoryEntry> history;
    private final ContextMenu suggestionsPopup = new ContextMenu();
    private final Consumer<String> onSelect;
    private final BookmarkService bookmarkService;
    private final Map<String, Image> iconCache = new HashMap<>();

    private List<String> bookmarkCache = new ArrayList<>();
    private List<HistoryEntry> cachedHistory = new ArrayList<>();
    private List<CustomMenuItem> currentItems = new ArrayList<>();
    private int currentIndex = -1;
    private ChangeListener<String> textChangeListener;

    public UrlAutoComplete(TextField textField,
                           ObservableList<HistoryEntry> history,
                           BookmarkService bookmarkService,
                           Consumer<String> onSelect) {
        this.textField = textField;
        this.history = history;
        this.onSelect = onSelect;
        this.bookmarkService = bookmarkService;

        loadCache();
        setupListeners();
    }

    private void loadCache() {
        // cache history
        cachedHistory = new ArrayList<>(history);
        Collections.reverse(cachedHistory);

        // cache bookmark
        CompletableFuture.runAsync(() -> {
            try {
                Integer userId = org.com.webbrowser.session.UserSession.getInstance().getUserId();
                if (userId == null) return;

                String apiUrl = "http://localhost:8080/api/bookmark/get-bookmark?userId=" + userId;
                HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Content-Type", "application/json");
                if (conn.getResponseCode() == 200) {
                    String json = new BufferedReader(new InputStreamReader(conn.getInputStream()))
                            .lines().collect(Collectors.joining());
                    JsonArray result = JsonParser.parseString(json)
                            .getAsJsonObject().getAsJsonArray("result");
                    bookmarkCache = new ArrayList<>();
                    for (var e : result) {
                        String url = e.getAsJsonObject().get("url").getAsString();
                        bookmarkCache.add(url);
                    }
                }
            } catch (Exception ignored) {
            }
        });
    }

    private void setupListeners() {
        // CHỈ bật autocomplete KHI người dùng THỰC SỰ focus vào thanh URL
        textField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (isNowFocused) {
                // Người dùng click/focus vào → bật listener
                textField.textProperty().addListener(textChangeListener);
                // Nếu có nội dung → show gợi ý ngay
                if (!textField.getText().isEmpty()) {
                    showSuggestions(textField.getText());
                }
            } else {
                // Mất focus → tắt listener + ẩn popup (tiết kiệm CPU & mạng)
                textField.textProperty().removeListener(textChangeListener);
                suggestionsPopup.hide();
            }
        });

        // Listener riêng để có thể remove khi cần
        textChangeListener = (obs, oldVal, newVal) -> {
            if (newVal == null || newVal.isEmpty()) {
                suggestionsPopup.hide();
            } else {
                showSuggestions(newVal);
            }
        };

        // Bật listener ngay nếu đang focus (trường hợp khởi động)
        if (textField.isFocused()) {
            textField.textProperty().addListener(textChangeListener);
        }

        textField.setOnKeyPressed(event -> {
            if (!textField.isFocused()) return; // đảm bảo chắc chắn

            switch (event.getCode()) {
                case DOWN -> navigate(1);
                case UP -> navigate(-1);
                case ENTER -> applySelection();
                case ESCAPE -> suggestionsPopup.hide();
                default -> {}
            }
            if (event.getCode().isArrowKey() || event.getCode() == KeyCode.ENTER) {
                event.consume(); // ngăn di chuyển caret khi dùng phím mũi tên
            }
        });
    }

    private void showSuggestions(String input) {
        String lower = input.toLowerCase();

        List<CustomMenuItem> items = new ArrayList<>();

        // HISTORY
        cachedHistory.stream()
                .filter(h -> h.getUrl().toLowerCase().contains(lower))
                .limit(5)
                .forEach(h -> items.add(createItem(h.getTitle(), h.getUrl(), "history")));

        // BOOKMARK
        bookmarkCache.stream()
                .filter(u -> u.toLowerCase().contains(lower))
                .limit(3)
                .forEach(u -> items.add(createItem("⭐ " + u, u, "bookmark")));

        // GOOGLE SEARCH
        CompletableFuture.supplyAsync(() -> fetchGoogleSuggestions(input))
                .thenAccept(suggestions -> Platform.runLater(() -> {
                    for (String s : suggestions.stream().limit(5).toList()) {
                        items.add(createItem("🔍 " + s, "https://www.google.com/search?q=" + s, "google"));
                    }
                    displaySuggestions(items);
                }));
    }

    private void displaySuggestions(List<CustomMenuItem> items) {
        if (items.isEmpty()) {
            suggestionsPopup.hide();
            return;
        }
        currentItems = items;
        currentIndex = -1;

        suggestionsPopup.getItems().setAll(items);
        suggestionsPopup.setMinWidth(textField.getWidth());
        suggestionsPopup.setMaxWidth(textField.getWidth());
        if (!suggestionsPopup.isShowing()) {
            suggestionsPopup.show(textField, Side.BOTTOM, 0, 0);
        }
    }

    private CustomMenuItem createItem(String text, String url, String type) {
        Image icon = iconCache.computeIfAbsent(type, t -> switch (t) {
            case "bookmark" -> new Image("https://cdn-icons-png.flaticon.com/512/1828/1828884.png", 16, 16, true, true);
            case "google" -> new Image("https://www.google.com/favicon.ico", 16, 16, true, true);
            default -> new Image("https://www.google.com/s2/favicons?domain=" + extractDomain(url), 16, 16, true, true);
        });

        Label label = new Label(text, new ImageView(icon));
        label.setStyle("-fx-padding: 6 10; -fx-font-size: 13px;");
        CustomMenuItem item = new CustomMenuItem(label, true);

        item.setOnAction(_ -> {
            suggestionsPopup.hide();
            String selected = text;

            String finalUrl;
            if (type.equals("google")) {
                // Đây là gợi ý Google → chuyển thành URL tìm kiếm chuẩn
                String query = selected.replace("🔍 ", "").trim();
                finalUrl = "https://www.google.com/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
            } else {
                finalUrl = normalizeUrl(url);
            }

            textField.setText(finalUrl);
            textField.positionCaret(finalUrl.length());
            onSelect.accept(finalUrl);
        });

        return item;
    }

    private String extractDomain(String url) {
        try {
            return new URL(url.startsWith("http") ? url : "http://" + url).getHost();
        } catch (Exception e) {
            return "google.com";
        }
    }

    private List<String> fetchGoogleSuggestions(String query) {
        try {
            String apiUrl = "https://suggestqueries.google.com/complete/search?client=firefox&q="
                    + URLEncoder.encode(query, StandardCharsets.UTF_8);
            HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                String json = reader.lines().collect(Collectors.joining());
                JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
                JsonArray suggestArr = arr.get(1).getAsJsonArray();

                List<String> suggestions = new ArrayList<>();
                for (int i = 0; i < suggestArr.size(); i++) {
                    suggestions.add(suggestArr.get(i).getAsString());
                }
                return suggestions;
            }
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private void navigate(int delta) {
        if (currentItems.isEmpty()) return;
        currentIndex = (currentIndex + delta + currentItems.size()) % currentItems.size();

        for (int i = 0; i < currentItems.size(); i++) {
            Label lbl = (Label) currentItems.get(i).getContent();
            String displayText = lbl.getText();
            String cleanText = displayText.replace("⭐ ", "").replace("🔍 ", "").trim();

            lbl.setStyle(i == currentIndex
                    ? "-fx-background-color: -fx-accent; -fx-text-fill: white; -fx-padding: 6 10;"
                    : "-fx-padding: 6 10;");

            if (i == currentIndex) {
                if (displayText.startsWith("🔍 ")) {
                    textField.setText(cleanText);
                } else {
                    textField.setText(cleanText);
                }
                textField.positionCaret(textField.getText().length());
            }
        }
    }

    private void applySelection() {
        if (currentIndex >= 0 && currentIndex < currentItems.size()) {
            currentItems.get(currentIndex).fire();
        } else {
            suggestionsPopup.hide();
            String input = textField.getText().trim();
            if (input.isEmpty()) return;

            String normalized = normalizeUrl(input);
            onSelect.accept(normalized);
        }
    }

}
