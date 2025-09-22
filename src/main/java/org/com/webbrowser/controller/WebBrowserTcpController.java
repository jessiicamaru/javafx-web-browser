package org.com.webbrowser.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.web.WebView;
import javafx.scene.web.WebEngine;

import java.net.URL;
import java.util.*;

public class WebBrowserTcpController implements Initializable {
    @FXML
    private Button backButton;
    @FXML
    private Button forwardButton;
    @FXML
    private TextField urlField;
    @FXML
    private Button goButton;
    @FXML
    private Button addTabButton;
    @FXML
    private TabPane tabPane;

    private final Map<Tab, List<String>> history = new HashMap<>();
    private final Map<Tab, Integer> historyIndex = new HashMap<>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        addNewTab("http://example.com");

        goButton.setOnAction(e -> loadUrl(getCurrentTab(), urlField.getText(), true));
        urlField.setOnAction(e -> loadUrl(getCurrentTab(), urlField.getText(), true));
        addTabButton.setOnAction(e -> addNewTab("http://example.com"));
        backButton.setOnAction(e -> goBack());
        forwardButton.setOnAction(e -> goForward());
    }

    private void addNewTab(String url) {
        WebView webView = new WebView();
        webView.setContextMenuEnabled(true);

        Tab tab = new Tab("New Tab", webView);
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);

        history.put(tab, new ArrayList<>());
        historyIndex.put(tab, -1);

        loadUrl(tab, url, true);
    }

    private void loadUrl(Tab tab, String input, boolean addToHistory) {
        if (tab == null || input == null || input.isEmpty()) return;

        try {
            URL u = new URL(input.startsWith("http") ? input : "http://" + input);

            Platform.runLater(() -> {
                WebView webView = new WebView();
                WebEngine webEngine = webView.getEngine();
                webEngine.load(input);

                tab.setContent(webView);
                tab.setText("Loading...");

                webEngine.documentProperty().addListener((obs, oldDoc, newDoc) -> {
                    if (newDoc != null) {
                        String title = (String) webEngine.executeScript("document.title");
                        tab.setText(title);
                        urlField.setText(input);
                    }
                });

                updateHistory(tab, input, addToHistory);
            });
        } catch (Exception ex) {
            TextArea errorArea = new TextArea("Invalid URL: " + ex.getMessage());
            tab.setContent(errorArea);
        }
    }

    private void updateHistory(Tab tab, String url, boolean addToHistory) {
        if (!addToHistory) return;
        List<String> urls = history.get(tab);
        int idx = historyIndex.get(tab);

        if (idx < urls.size() - 1) {
            urls = urls.subList(0, idx + 1);
            history.put(tab, new ArrayList<>(urls));
        }

        urls.add(url);
        historyIndex.put(tab, urls.size() - 1);
    }


    private Tab getCurrentTab() {
        return tabPane.getSelectionModel().getSelectedItem();
    }

    private void goBack() {
        Tab tab = getCurrentTab();
        if (tab == null) return;

        int idx = historyIndex.get(tab);
        if (idx > 0) {
            historyIndex.put(tab, idx - 1);
            loadUrl(tab, history.get(tab).get(idx - 1), false);
        }
    }

    private void goForward() {
        Tab tab = getCurrentTab();
        if (tab == null) return;

        int idx = historyIndex.get(tab);
        List<String> urls = history.get(tab);
        if (idx < urls.size() - 1) {
            historyIndex.put(tab, idx + 1);
            loadUrl(tab, urls.get(idx + 1), false);
        }
    }
}
