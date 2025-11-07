package org.com.webbrowser.controller;

import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import org.com.webbrowser.WebBrowserApplication;
import org.com.webbrowser.model.HistoryEntry;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.*;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import org.com.webbrowser.service.BookmarkService;
import org.com.webbrowser.service.HistoryService;

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
    @FXML
    private HBox findBar;
    @FXML
    private TextField findField;
    @FXML
    private Button nextButton;
    @FXML
    private Button prevButton;
    @FXML
    private Button closeFindButton;

    private final Map<String, Long> bookmarkIds = new HashMap<>();
    private final Map<Tab, List<String>> history = new HashMap<>();
    private final Map<Tab, Integer> historyIndex = new HashMap<>();
    private final ObservableList<HistoryEntry> globalHistory = FXCollections.observableArrayList();
    private final BookmarkService bookmarkService = new BookmarkService();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        addNewTab("newtab");

        loadBookmarksFromServer();
        globalHistory.setAll(HistoryService.loadAllHistory());

        findBar.setVisible(false);
        findBar.setManaged(false);

        goButton.setOnAction(_ -> loadUrl(getCurrentTab(), urlField.getText(), true));
        urlField.setOnAction(_ -> loadUrl(getCurrentTab(), urlField.getText(), true));
        addTabButton.setOnAction(_ -> addNewTab("newtab"));
        backButton.setOnAction(_ -> goBack());
        forwardButton.setOnAction(_ -> goForward());

        closeFindButton.setOnAction(e -> closeFindBar());
        findField.textProperty().addListener((obs, oldText, newText) -> findInPage(newText, true));
        nextButton.setOnAction(e -> findInPage(findField.getText(), true));
        prevButton.setOnAction(e -> findInPage(findField.getText(), false));

        tabPane.getTabs().addListener((javafx.collections.ListChangeListener<Tab>) _ -> {
            if (tabPane.getTabs().isEmpty()) Platform.exit();
        });

        tabPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
                    // ✅ Xử lý Ctrl + Tab chuyển tab
                    if (event.isControlDown() && event.getCode() == KeyCode.TAB) {
                        int totalTabs = tabPane.getTabs().size();
                        if (totalTabs > 1) {
                            int currentIndex = tabPane.getSelectionModel().getSelectedIndex();

                            Platform.runLater(() -> {
                                int nextIndex;
                                if (event.isShiftDown()) {
                                    nextIndex = (currentIndex - 1 + totalTabs) % totalTabs;
                                } else {
                                    nextIndex = (currentIndex + 1) % totalTabs;
                                }
                                tabPane.getSelectionModel().select(nextIndex);
                            });
                        }
                        event.consume();
                    }
                });
            }
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

                    if (event.isControlDown() && event.getCode() == javafx.scene.input.KeyCode.T) {
                        addNewTab("newtab");
                        event.consume();
                    }

                    if (event.isControlDown() && event.getCode() == javafx.scene.input.KeyCode.W) {
                        Tab currentTab = getCurrentTab();
                        if (currentTab != null) {
                            tabPane.getTabs().remove(currentTab);
                            event.consume();
                        }
                    }

                    if (event.isControlDown() && event.getCode() == javafx.scene.input.KeyCode.E) {
                        if (urlField != null) {
                            Platform.runLater(() -> {
                                urlField.requestFocus();
                                urlField.selectAll();
                            });
                        }
                        event.consume();
                    }

                    if (event.isControlDown() && event.getCode() == javafx.scene.input.KeyCode.D) {
                        if (bookmarkButton != null) {
                            Platform.runLater(() -> bookmarkButton.fire());
                        }
                        event.consume();
                    }

                    if (event.isControlDown() && event.getCode() == KeyCode.F) {
                        openFindBar();
                        event.consume();
                    }
                });
            }
        });

        bookmarkButton.setOnAction(_ -> {
            String currentUrl = urlField.getText();

            if (currentUrl == null || currentUrl.isEmpty() || currentUrl.equals("about:blank")) {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Invalid URL");
                alert.setHeaderText(null);
                alert.setContentText("Không thể thêm bookmark vì URL trống hoặc không hợp lệ!");
                alert.showAndWait();
                return;
            }

            TextInputDialog dialog = new TextInputDialog("Bookmark name");
            dialog.setTitle("Add Bookmark");
            dialog.setHeaderText("Add new bookmark");
            dialog.setContentText("Name:");

            dialog.showAndWait().ifPresent(name -> {
                if (name == null || name.trim().isEmpty()) {
                    Alert alert = new Alert(Alert.AlertType.WARNING);
                    alert.setTitle("Invalid Name");
                    alert.setHeaderText(null);
                    alert.setContentText("Tên bookmark không được để trống!");
                    alert.showAndWait();
                    return;
                }

                bookmarkService.addBookmark(name.trim(), currentUrl.trim(), this::loadBookmarksFromServer);
            });
        });
    }

    private void addBookmarkButton(String name, String url, Long id) {
        Button bmButton = new Button(name);
        bmButton.setOnAction(_ -> loadUrl(getCurrentTab(), url, true));
        bmButton.setUserData(id); // ✅ Lưu id vào button

        ContextMenu menu = new ContextMenu();

        MenuItem editItem = new MenuItem("Edit");
        editItem.setOnAction(e -> {
            TextInputDialog nameDialog = new TextInputDialog(name);
            nameDialog.setTitle("Edit Bookmark");
            nameDialog.setHeaderText("Chỉnh sửa tên bookmark");
            nameDialog.setContentText("Tên:");

            nameDialog.showAndWait().ifPresent(updatedName -> {
                TextInputDialog urlDialog = new TextInputDialog(url);
                urlDialog.setTitle("Edit Bookmark");
                urlDialog.setHeaderText("Chỉnh sửa link bookmark");
                urlDialog.setContentText("URL:");

                urlDialog.showAndWait().ifPresent(updatedUrl -> {
                    bmButton.setText(updatedName);
                    bmButton.setOnAction(ev -> loadUrl(getCurrentTab(), updatedUrl, true));

                    Long buttonId = (Long) bmButton.getUserData();
                    if (buttonId != null) {
                        bookmarkService.updateBookmark(buttonId, updatedName, updatedUrl, this::loadBookmarksFromServer);
                    } else {
                        System.out.println("⚠️ Bookmark chưa có ID — bỏ qua cập nhật server");
                    }
                });
            });
        });

        MenuItem deleteItem = new MenuItem("Delete");
        deleteItem.setOnAction(e -> {
            bookmarkBar.getItems().remove(bmButton);
            Long buttonId = (Long) bmButton.getUserData();
            if (buttonId != null) {
                bookmarkService.deleteBookmark(buttonId, this::loadBookmarksFromServer);
            } else {
                System.out.println("⚠️ Bookmark chưa có ID — bỏ qua xóa server");
            }
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

                if (url.contains("https://www.google.com/search?q=")) {
                    try {
                        String query = url.substring(url.indexOf("q=") + 2);
                        if (query.contains("&")) {
                            query = query.substring(0, query.indexOf("&"));
                        }
                        query = java.net.URLDecoder.decode(query, "UTF-8");

                        tab.setText(query + " - Tìm kiếm trên Google");
                    } catch (Exception e) {
                        tab.setText("Tìm kiếm trên Google");
                    }
                } else {
                    tab.setText(url.replaceFirst("https://", ""));
                }

                engine.load(url);

                engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                    if (newState == Worker.State.SUCCEEDED) {
                        String title = engine.getTitle() != null ? engine.getTitle() : url;
                        String visitedAt = LocalDateTime.now().toString();

                        if (globalHistory.isEmpty() || !globalHistory.get(globalHistory.size() - 1).getUrl().equals(url)) {
                            globalHistory.add(new HistoryEntry(title, url, visitedAt));
                            HistoryService.addHistoryEntry(new HistoryEntry(title, url, visitedAt));
                            updateHistory(tab, url, addToHistory);
                        }
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

        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return input;
        }
        if (lower.contains(".") && !lower.contains(" ")) {
            return "https://" + input;
        }

        try {
            String query = java.net.URLEncoder.encode(input, "UTF-8");
            return "https://www.google.com/search?q=" + query;
        } catch (Exception e) {
            return "https://www.google.com/search?q=" + input;
        }
    }


    private void openHistoryWindow() {
        try {
            FXMLLoader loader = new FXMLLoader(WebBrowserApplication.class.getResource("history-view.fxml"));
            Parent root = loader.load();

            HistoryController controller = loader.getController();
            controller.setData(globalHistory, bookmarkService, url -> addNewTab(url));

            Tab historyTab = new Tab("History");
            historyTab.setContent(root);

            tabPane.getTabs().add(historyTab);
            tabPane.getSelectionModel().select(historyTab);

        } catch (IOException e) {
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR, "Không thể mở History view: " + e.getMessage());
            alert.showAndWait();
        }
    }


    private void loadBookmarksFromServer() {
        Integer userId = org.com.webbrowser.session.UserSession.getInstance().getUserId();
        if (userId == null) {
            System.out.println("⚠️ User chưa đăng nhập — bỏ qua tải bookmark");
            return;
        }

        new Thread(() -> {
            try {
                String apiUrl = "http://localhost:8080/api/bookmark/get-bookmark?userId=" + userId;
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(apiUrl).openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Content-Type", "application/json");

                if (conn.getResponseCode() == 200) {
                    java.io.InputStream is = conn.getInputStream();
                    java.util.Scanner sc = new java.util.Scanner(is).useDelimiter("\\A");
                    String json = sc.hasNext() ? sc.next() : "";

                    com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
                    com.google.gson.JsonArray result = root.getAsJsonArray("result");

                    Platform.runLater(() -> {
                        bookmarkBar.getItems().clear();
                        bookmarkIds.clear();
                    });

                    for (com.google.gson.JsonElement e : result) {
                        com.google.gson.JsonObject obj = e.getAsJsonObject();
                        Long id = obj.get("id").getAsLong();
                        String title = obj.get("title").getAsString();
                        String url = obj.get("url").getAsString();

                        System.out.println(id + " " + title + " " + url);

                        Platform.runLater(() -> addBookmarkButton(title, url, id));
                    }
                } else {
                    System.out.println("⚠️ Không thể lấy bookmark, mã lỗi: " + conn.getResponseCode());
                }

            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }).start();
    }

    private void openFindBar() {
        findBar.setVisible(true);
        findBar.setManaged(true);
        findField.requestFocus();
    }

    private void closeFindBar() {
        findBar.setVisible(false);
        findBar.setManaged(false);
    }

    private void findInPage(String query, boolean forward) {
        if (query == null || query.isEmpty()) return;
        Tab currentTab = getCurrentTab();
        if (currentTab == null) return;

        if (!(currentTab.getContent() instanceof WebView webView)) return;

        WebEngine webEngine = webView.getEngine();

        String js = """
        if (window.find) {
            window.find('%s', false, %b, true, false, false, false);
        }
    """.formatted(query.replace("'", "\\'"), !forward);

        Platform.runLater(() -> {
            try {
                webEngine.executeScript(js);
            } catch (Exception e) {
                System.err.println("❌ Lỗi khi tìm trong trang: " + e.getMessage());
            }
        });
    }

}
