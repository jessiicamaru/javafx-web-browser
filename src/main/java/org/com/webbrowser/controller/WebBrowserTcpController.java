package org.com.webbrowser.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import org.com.webbrowser.tcp.HttpClient;
import org.com.webbrowser.tcp.HttpResponse;

import java.net.URL;
import java.util.*;

public class WebBrowserTcpController implements Initializable {

    @FXML private Button backButton;
    @FXML private Button forwardButton;
    @FXML private TextField urlField;
    @FXML private Button goButton;
    @FXML private Button addTabButton;
    @FXML private TabPane tabPane;

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
        TextArea displayArea = new TextArea();
        displayArea.setWrapText(true);

        Tab tab = new Tab("New Tab", displayArea);
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);

        history.put(tab, new ArrayList<>());
        historyIndex.put(tab, -1);

        loadUrl(tab, url, true);
    }

    private void loadUrl(Tab tab, String input, boolean updateHistory) {
        if (tab == null || input == null || input.isEmpty()) return;

        try {
            URL u = new URL(input.startsWith("http") ? input : "http://" + input);
            String host = u.getHost();
            String path = u.getPath().isEmpty() ? "/" : u.getPath();

            TextArea displayArea = (TextArea) tab.getContent();

            new Thread(() -> {
                try {
                    HttpResponse resp = HttpClient.fetch(host, path);
                    String body = resp.getBody();

                    Platform.runLater(() -> {
                        displayArea.setText(body);
                        tab.setText(host);

                        if (updateHistory) {
                            List<String> urls = history.get(tab);
                            int idx = historyIndex.get(tab);
                            if (idx < urls.size() - 1) {
                                urls.subList(idx + 1, urls.size()).clear();
                            }
                            urls.add(input);
                            historyIndex.put(tab, idx + 1);
                        }

                        urlField.setText(input);
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> displayArea.setText("Error: " + ex.getMessage()));
                }
            }).start();

        } catch (Exception ex) {
            TextArea displayArea = (TextArea) tab.getContent();
            displayArea.setText("Invalid URL: " + ex.getMessage());
        }
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
