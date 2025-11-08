package org.com.webbrowser.utils;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Side;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.com.webbrowser.model.HistoryEntry;
import org.com.webbrowser.service.HistoryService;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class UrlAutoComplete {

    private final TextField textField;
    private final ObservableList<HistoryEntry> history;
    private final ContextMenu suggestionsPopup = new ContextMenu();
    private int currentIndex = -1;
    private List<CustomMenuItem> currentItems = new ArrayList<>();
    private final Consumer<String> onSelect;
    private List<HistoryEntry> cachedList = new ArrayList<>();

    public UrlAutoComplete(TextField textField, ObservableList<HistoryEntry> history, Consumer<String> onSelect) {
        this.textField = textField;
        this.history = history;
        this.onSelect = onSelect;

        rebuildCache(); // load ban đầu
        setupListeners();

        // 🔄 Lắng nghe thay đổi danh sách history để tự refresh
        history.addListener((ListChangeListener<HistoryEntry>) change -> rebuildCache());
    }

    private void setupListeners() {
        textField.textProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue == null || newValue.isEmpty()) {
                suggestionsPopup.hide();
            } else {
                showSuggestions(newValue);
            }
        });

        textField.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case DOWN -> navigate(1);
                case UP -> navigate(-1);
                case ENTER -> applySelection();
                case ESCAPE -> suggestionsPopup.hide();
                default -> {}
            }
        });

        // 🔧 Đảm bảo dropdown luôn bằng width của URL field
        textField.widthProperty().addListener((obs, oldVal, newVal) -> {
            if (suggestionsPopup.isShowing()) {
                suggestionsPopup.setMinWidth(newVal.doubleValue());
                suggestionsPopup.setMaxWidth(newVal.doubleValue());
            }
        });
    }

    /** 🔄 Cập nhật lại cache khi history thay đổi */
    private void rebuildCache() {
        // Đảo ngược danh sách: mới nhất nằm trên
        List<HistoryEntry> reversed = new ArrayList<>(history);
        Collections.reverse(reversed);

        // Loại bỏ trùng theo URL
        Map<String, HistoryEntry> uniqueMap = new LinkedHashMap<>();
        for (HistoryEntry h : reversed) {
            if (h != null && h.getUrl() != null && !uniqueMap.containsKey(h.getUrl())) {
                uniqueMap.put(h.getUrl(), h);
            }
        }
        cachedList = new ArrayList<>(uniqueMap.values());
    }

    private void showSuggestions(String input) {
        String lower = input.toLowerCase();

        List<HistoryEntry> matches = cachedList.stream()
                .filter(h -> h.getUrl().toLowerCase().contains(lower)
                        || (h.getTitle() != null && h.getTitle().toLowerCase().contains(lower)))
                .limit(8)
                .collect(Collectors.toList());

        if (matches.isEmpty()) {
            suggestionsPopup.hide();
            return;
        }

        List<CustomMenuItem> items = matches.stream().map(entry -> {
            String display;
            if (entry.getUrl().contains("google.com/search?q=")) {
                try {
                    String query = entry.getUrl().substring(entry.getUrl().indexOf("q=") + 2);
                    if (query.contains("&")) query = query.substring(0, query.indexOf("&"));
                    query = URLDecoder.decode(query, StandardCharsets.UTF_8);
                    display = "Tìm kiếm trên Google - " + query;
                } catch (Exception e) {
                    display = "Tìm kiếm trên Google";
                }
            } else {
                String cleanUrl = entry.getUrl().replaceFirst("^https?://", "");
                display = (entry.getTitle() != null ? entry.getTitle() : cleanUrl) + " - " + cleanUrl;
            }

            Label label = new Label(display);
            label.setStyle("-fx-padding: 6 10; -fx-font-size: 13px;");
            CustomMenuItem item = new CustomMenuItem(label, true);
            item.setOnAction(e -> applyItem(entry));
            return item;
        }).toList();

        currentItems = items;
        currentIndex = -1;

        suggestionsPopup.getItems().setAll(items);
        suggestionsPopup.setMinWidth(textField.getWidth());
        suggestionsPopup.setMaxWidth(textField.getWidth());

        if (!suggestionsPopup.isShowing()) {
            suggestionsPopup.show(textField, Side.BOTTOM, 0, 0);
        }
    }

    private void applyItem(HistoryEntry entry) {
        Platform.runLater(() -> {
            String url = entry.getUrl();
            textField.setText(url);
            textField.positionCaret(url.length());
            suggestionsPopup.hide();

            onSelect.accept(url);
        });
    }

    private void navigate(int delta) {
        if (currentItems == null || currentItems.isEmpty()) return;

        currentIndex = (currentIndex + delta + currentItems.size()) % currentItems.size();

        for (int i = 0; i < currentItems.size(); i++) {
            Label lbl = (Label) currentItems.get(i).getContent();
            lbl.setStyle(i == currentIndex
                    ? "-fx-background-color: -fx-accent; -fx-text-fill: white; -fx-padding: 6 10; -fx-font-size: 13px;"
                    : "-fx-padding: 6 10; -fx-font-size: 13px;");
        }

        Label currentLabel = (Label) currentItems.get(currentIndex).getContent();
        String currentText = currentLabel.getText();

        HistoryEntry entry = cachedList.stream()
                .filter(h -> currentText.contains(h.getUrl()) || (h.getTitle() != null && currentText.contains(h.getTitle())))
                .findFirst()
                .orElse(null);

        if (entry != null) {
            Platform.runLater(() -> {
                textField.setText(entry.getUrl());
                textField.positionCaret(entry.getUrl().length());
            });
        }
    }

    private void applySelection() {
        if (currentItems != null && currentIndex >= 0 && currentIndex < currentItems.size()) {
            currentItems.get(currentIndex).fire();
        } else {
            suggestionsPopup.hide();
        }
    }
}
