package org.com.webbrowser.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.web.WebView;
import javafx.scene.web.WebEngine;
import org.com.webbrowser.WebBrowserApplication;

import java.io.IOException;
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
    @FXML
    private ToolBar bookmarkBar;
    @FXML
    private Button bookmarkButton;

    private final Map<String, String> bookmarks = new HashMap<>();

    private final Map<Tab, List<String>> history = new HashMap<>();
    private final Map<Tab, Integer> historyIndex = new HashMap<>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        addNewTab("newtab");

        goButton.setOnAction(e -> loadUrl(getCurrentTab(), urlField.getText(), true));
        urlField.setOnAction(e -> loadUrl(getCurrentTab(), urlField.getText(), true));
        addTabButton.setOnAction(e -> addNewTab("newtab"));
        backButton.setOnAction(e -> goBack());
        forwardButton.setOnAction(e -> goForward());

        bookmarkButton.setOnAction(e -> {
            String currentUrl = urlField.getText();
            if (currentUrl == null || currentUrl.isEmpty()) return;

            TextInputDialog dialog = new TextInputDialog("Bookmark name");
            dialog.setTitle("Add Bookmark");
            dialog.setHeaderText("Lưu trang web vào bookmark");
            dialog.setContentText("Tên:");

            Optional<String> result = dialog.showAndWait();
            result.ifPresent(name -> {
                bookmarks.put(name, currentUrl);
                addBookmarkButton(name, currentUrl);

                Alert alert = new Alert(Alert.AlertType.INFORMATION,
                        "Đã lưu bookmark: " + name, ButtonType.OK);
                alert.showAndWait();
            });
        });
    }

    private void addBookmarkButton(String name, String url) {
        Button bmButton = new Button(name);
        bmButton.setOnAction(ev -> loadUrl(getCurrentTab(), url, true));

        ContextMenu menu = new ContextMenu();

        MenuItem editItem = new MenuItem("Edit");
        editItem.setOnAction(e -> {
            TextInputDialog nameDialog = new TextInputDialog(name);
            nameDialog.setTitle("Edit Bookmark");
            nameDialog.setHeaderText("Chỉnh sửa tên bookmark");
            nameDialog.setContentText("Tên:");

            Optional<String> newName = nameDialog.showAndWait();
            newName.ifPresent(updatedName -> {
                TextInputDialog urlDialog = new TextInputDialog(url);
                urlDialog.setTitle("Edit Bookmark");
                urlDialog.setHeaderText("Chỉnh sửa link bookmark");
                urlDialog.setContentText("URL:");

                Optional<String> newUrl = urlDialog.showAndWait();
                newUrl.ifPresent(updatedUrl -> {
                    bookmarks.remove(name);
                    bookmarks.put(updatedName, updatedUrl);

                    bmButton.setText(updatedName);
                    bmButton.setOnAction(ev -> loadUrl(getCurrentTab(), updatedUrl, true));
                });
            });
        });

        MenuItem deleteItem = new MenuItem("Delete");
        deleteItem.setOnAction(e -> {
            bookmarks.remove(name);
            bookmarkBar.getItems().remove(bmButton);
        });

        menu.getItems().addAll(editItem, deleteItem);
        bmButton.setContextMenu(menu);

        bookmarkBar.getItems().add(bmButton);
    }

    private void addNewTab(String url) {
        Tab tab = new Tab("New Tab");
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);

        history.put(tab, new ArrayList<>());
        historyIndex.put(tab, -1);

        if (url.equals("newtab")) {
            try {
                FXMLLoader loader = new FXMLLoader(WebBrowserApplication.class.getResource("new-tab.fxml"));
                Parent newTabRoot = loader.load();
                tab.setContent(newTabRoot);

                TextField searchField = (TextField) newTabRoot.lookup("#searchField");
                searchField.setOnAction(e -> {
                    String input = searchField.getText();
                    loadUrl(tab, input, true);
                });

            } catch (IOException ex) {
                tab.setContent(new Label("Error loading new tab page"));
            }
        } else {
            loadUrl(tab, url, true);
        }
    }

    private void loadUrl(Tab tab, String input, boolean addToHistory) {
        if (tab == null || input == null || input.isEmpty()) return;

        try {
            URL u = new URL(input.startsWith("http") ? input : "http://" + input);

            Platform.runLater(() -> {
                WebView webView = new WebView();
                WebEngine webEngine = webView.getEngine();
                webEngine.load(String.valueOf(u));

                tab.setContent(webView);
                tab.setText("Loading...");

                webEngine.documentProperty().addListener((obs, oldDoc, newDoc) -> {
                    if (newDoc != null) {
                        String title = (String) webEngine.executeScript("document.title");
                        tab.setText(title);
                        urlField.setText(input);
                    }
                });

                updateHistory(tab, String.valueOf(u), addToHistory);
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
