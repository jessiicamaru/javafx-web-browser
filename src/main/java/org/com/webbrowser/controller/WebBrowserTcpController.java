package org.com.webbrowser.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import org.com.webbrowser.WebBrowserApplication;
import org.com.webbrowser.model.HistoryEntry;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;

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
    private final ObservableList<HistoryEntry> globalHistory = FXCollections.observableArrayList();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        addNewTab("newtab");

        goButton.setOnAction(_ -> loadUrl(getCurrentTab(), urlField.getText(), true));
        urlField.setOnAction(_ -> loadUrl(getCurrentTab(), urlField.getText(), true));
        addTabButton.setOnAction(_ -> addNewTab("newtab"));
        backButton.setOnAction(_ -> goBack());
        forwardButton.setOnAction(_ -> goForward());

        tabPane.getTabs().addListener((javafx.collections.ListChangeListener<Tab>) _ -> {
            if (tabPane.getTabs().isEmpty()) Platform.exit();
        });

        tabPane.getSelectionModel().selectedItemProperty().addListener((_, _, newTab) -> {
            if (newTab == null) urlField.clear();
            else urlField.setText((String) newTab.getUserData());
        });

        tabPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
                    if (event.isControlDown() && event.getCode() == javafx.scene.input.KeyCode.H) {
                        openHistoryWindow();
                        event.consume();
                    }
                });
            }
        });

        bookmarkButton.setOnAction(_ -> {
            String currentUrl = urlField.getText();
            if (currentUrl == null || currentUrl.isEmpty()) return;

            TextInputDialog dialog = new TextInputDialog("Bookmark name");
            dialog.setTitle("Add Bookmark");
            dialog.setHeaderText("Add new bookmark");
            dialog.setContentText("Name:");

            dialog.showAndWait().ifPresent(name -> {
                bookmarks.put(name, currentUrl);
                addBookmarkButton(name, currentUrl);
            });
        });
    }

    private void addBookmarkButton(String name, String url) {
        Button bmButton = new Button(name);
        bmButton.setOnAction(_ -> loadUrl(getCurrentTab(), url, true));
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
                searchField.setOnAction(_ -> loadUrl(tab, searchField.getText(), true));
            } catch (IOException ex) {
                tab.setContent(new Label("Error loading new tab page"));
            }
        } else {
            loadUrl(tab, url, true);
        }
    }

    private void loadUrl(Tab tab, String input, boolean addToHistory) {
        if (tab == null || input == null || input.isEmpty()) return;

        Platform.runLater(() -> {
            try {
                String url = normalizeUrl(input);

                WebView webView = new WebView();
                WebEngine engine = webView.getEngine();

                tab.setContent(webView);
                tab.setUserData(url);
                urlField.setText(url);
                tab.setText(url.replaceFirst("https://", ""));

                engine.load(url);

                engine.documentProperty().addListener((obs, oldDoc, newDoc) -> {
                    if (newDoc != null) {
                        String title = engine.getTitle() != null ? engine.getTitle() : url;
                        String visitedAt = LocalDateTime.now().toString();
                        globalHistory.add(new HistoryEntry(title, url, visitedAt));
                        updateHistory(tab, url, addToHistory);
                    }
                });
            } catch (Exception e) {
                tab.setContent(new Label("Error loading page: " + e.getMessage()));
            }
        });
    }

    private void updateHistory(Tab tab, String url, boolean addToHistory) {
        if (!addToHistory || tab == null) return;

        history.putIfAbsent(tab, new ArrayList<>());
        historyIndex.putIfAbsent(tab, -1);

        List<String> urls = history.get(tab);
        int idx = historyIndex.get(tab);

        if (idx < urls.size() - 1) urls = new ArrayList<>(urls.subList(0, idx + 1));
        urls.add(url);
        history.put(tab, urls);
        historyIndex.put(tab, urls.size() - 1);
    }

    private void goBack() {
        Tab tab = getCurrentTab();
        if (tab == null) return;

        int idx = historyIndex.get(tab);
        if (idx > 0) {
            historyIndex.put(tab, idx - 1);
            String prevUrl = history.get(tab).get(idx - 1);
            ((WebView) tab.getContent()).getEngine().load(prevUrl);
        }
    }

    private void goForward() {
        Tab tab = getCurrentTab();
        if (tab == null) return;

        int idx = historyIndex.get(tab);
        List<String> urls = history.get(tab);
        if (idx < urls.size() - 1) {
            historyIndex.put(tab, idx + 1);
            String nextUrl = urls.get(idx + 1);
            ((WebView) tab.getContent()).getEngine().load(nextUrl);
        }
    }

    private Tab getCurrentTab() {
        return tabPane.getSelectionModel().getSelectedItem();
    }

    private String normalizeUrl(String input) {
        if (input == null || input.isEmpty()) return "";

        String lower = input.toLowerCase();
        if (!lower.startsWith("http://") && !lower.startsWith("https://"))
            return "https://" + input;
        return input;
    }

    private void openHistoryWindow() {
        Tab historyTab = new Tab("History");

        history.putIfAbsent(historyTab, new ArrayList<>());
        historyIndex.putIfAbsent(historyTab, -1);

        TableView<HistoryEntry> table = new TableView<>();
        table.setEditable(true);

        TableColumn<HistoryEntry, Boolean> selectCol = new TableColumn<>("Select");
        selectCol.setCellValueFactory(cellData -> cellData.getValue().selectedProperty());
        selectCol.setCellFactory(CheckBoxTableCell.forTableColumn(selectCol));
        selectCol.setEditable(true);
        selectCol.setPrefWidth(80);

        TableColumn<HistoryEntry, String> timeCol = new TableColumn<>("Visited At");
        timeCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getDate()));
        timeCol.setPrefWidth(220);

        TableColumn<HistoryEntry, String> titleCol = new TableColumn<>("Title");
        titleCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getTitle()));
        titleCol.setPrefWidth(360);

        TableColumn<HistoryEntry, String> urlCol = new TableColumn<>("URL");
        urlCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getUrl()));
        urlCol.setPrefWidth(360);

        table.getColumns().addAll(selectCol, timeCol, titleCol, urlCol);

        table.setItems(globalHistory);

        table.setRowFactory(tv -> {
            TableRow<HistoryEntry> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    HistoryEntry entry = row.getItem();
                    addNewTab(entry.getUrl());   // mở ở tab mới
                }
            });
            return row;
        });

        Button bookmarkBtn = new Button("Add to Bookmarks");
        bookmarkBtn.setOnAction(_ev -> {
            List<HistoryEntry> checked = globalHistory.stream()
                    .filter(h -> h.selectedProperty().get())
                    .collect(Collectors.toList());
            for (HistoryEntry entry : checked) {
                String name = (entry.getTitle() != null && !entry.getTitle().isEmpty()) ? entry.getTitle() : entry.getUrl();
                addBookmarkButton(name, entry.getUrl());
                entry.selectedProperty().set(false);
            }
        });

        Button deleteBtn = new Button("Delete Selected");
        deleteBtn.setOnAction(_ev -> {
            List<HistoryEntry> toDelete = globalHistory.stream()
                    .filter(h -> h.selectedProperty().get())
                    .collect(Collectors.toList());
            globalHistory.removeAll(toDelete);
        });

        HBox actionBar = new HBox(10, bookmarkBtn, deleteBtn);
        actionBar.setPadding(new Insets(10));

        VBox layout = new VBox(10, table, actionBar);
        layout.setPadding(new Insets(10));

        historyTab.setContent(layout);
        tabPane.getTabs().add(historyTab);
        tabPane.getSelectionModel().select(historyTab);
    }

}
