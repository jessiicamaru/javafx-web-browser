package org.com.webbrowser.controller;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
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
import org.com.webbrowser.utils.UrlAutoComplete;

import static org.com.webbrowser.utils.UrlNormalizer.normalizeUrl;

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
    @FXML
    private Button reloadButton;
    @FXML
    private Button stopButton;

    private final Map<String, Long> bookmarkIds = new HashMap<>();
    private final Map<Tab, List<String>> history = new HashMap<>();
    private final Map<Tab, Integer> historyIndex = new HashMap<>();
    private final ObservableList<HistoryEntry> globalHistory = FXCollections.observableArrayList();
    private final BookmarkService bookmarkService = new BookmarkService();
    private final Map<String, Image> faviconCache = new HashMap<>();

    private final List<TabGroup> tabGroups = new ArrayList<>();
    private final List<Tab> looseTabs = new ArrayList<>();
    private final Map<Tab, TabGroup> headerToGroup = new HashMap<>();
    private final Map<Tab, TabGroup> tabToGroup = new HashMap<>();

    private final List<Tab> tabOrder = new ArrayList<>();

    private class TabGroup {
        String name;
        Color color;
        boolean collapsed = false;
        final List<Tab> tabs = new ArrayList<>();
        Tab headerTab = new Tab();

        TabGroup(String name, Color color) {
            this.name = name;
            this.color = color == null ? generateRandomColor() : color;

            headerTab = new Tab();
            headerTab.setClosable(false);
            updateHeaderGraphic();
            headerToGroup.put(headerTab, this);

            // DÙNG CÁCH NÀY ĐỂ BẮT CLICK VÀO HEADER TAB GROUP
            headerTab.graphicProperty().addListener((obs, oldGraphic, newGraphic) -> {
                if (newGraphic instanceof Region region) {
                    region.setOnMouseClicked(e -> {
                        if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 1) {
                            collapsed = !collapsed;
                            updateHeaderGraphic();
                            rebuildTabOrder();
                            e.consume();
                        }
                    });
                }
            });

            // Đảm bảo lần đầu cũng có listener
            if (headerTab.getGraphic() instanceof Region region) {
                region.setOnMouseClicked(e -> {
                    if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 1) {
                        collapsed = !collapsed;
                        updateHeaderGraphic();
                        rebuildTabOrder();
                        e.consume();
                    }
                });
            }

            setupGroupContextMenu();
        }

        void updateHeaderGraphic() {
            int r = (int) (color.getRed() * 255);
            int g = (int) (color.getGreen() * 255);
            int b = (int) (color.getBlue() * 255);

            Circle circle = new Circle(8, color);
            Label label = new Label(collapsed ? name : name + " (" + tabs.size() + ")");
            label.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11;");

            HBox badge = new HBox(6, circle, label);
            badge.setAlignment(Pos.CENTER);
            badge.setPadding(new Insets(6, 12, 6, 12));
            badge.setStyle("-fx-background-color: rgb(" + r + "," + g + "," + b + "); " +
                    "-fx-background-radius: 16; " +
                    "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 3, 0, 0, 1);");

            // Bắt click vào badge để toggle collapse
            badge.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 1) {
                    collapsed = !collapsed;
                    updateHeaderGraphic();
                    rebuildTabOrder();
                    e.consume();
                }
            });

            if (collapsed) {
                headerTab.setGraphic(badge);
            } else {
                Label separator = new Label("   ");
                separator.setStyle("-fx-background-color: transparent;");
                HBox fullHeader = new HBox(badge, separator);
                fullHeader.setAlignment(Pos.CENTER_LEFT);
                headerTab.setGraphic(fullHeader);
            }
        }

        void addTab(Tab tab) {
            if (!tabs.contains(tab)) {
                tabs.add(tab);
                tabToGroup.put(tab, this);
                int r = (int) (color.getRed() * 255);
                int g = (int) (color.getGreen() * 255);
                int b = (int) (color.getBlue() * 255);
                tab.setStyle("-fx-background-color: rgba(" + r + "," + g + "," + b + ", 0.15);");
                setupTabCloseHandler(tab);
                rebuildTabOrder();

                updateHeaderGraphic();
            }
        }

        void removeTab(Tab tab) {
            if (tabs.remove(tab)) {
                tabToGroup.remove(tab);
                tab.setStyle(null);  // reset màu

                // === QUAN TRỌNG NHẤT: KHÔNG ĐƯỢC XÓA KHỎI tabPane.getTabs() Ở ĐÂY NỮA ===
                // Vì rebuildTabOrder() sẽ tự xử lý việc render lại toàn bộ → nếu xóa trước thì sẽ duplicate!

                looseTabs.add(tab);  // chỉ add vào looseTabs là đủ

                updateHeaderGraphic();

                if (tabs.isEmpty()) {
                    headerToGroup.remove(headerTab);
                    tabGroups.remove(this);
                    // headerTab sẽ tự bị xóa trong rebuildTabOrder()
                }

                rebuildTabOrder(); // ← ĐÂY MỚI LÀ NƠI DUY NHẤT ĐƯỢC PHÉP THAY ĐỔI tabPane.getTabs()
            }
        }

        void setupGroupContextMenu() {
            ContextMenu menu = new ContextMenu();

            MenuItem rename = new MenuItem("Rename Group");
            rename.setOnAction(e -> {
                TextInputDialog d = new TextInputDialog(name);
                d.setTitle("Rename Group");
                d.setHeaderText("New name:");
                d.showAndWait().ifPresent(n -> {
                    if (!n.trim().isEmpty()) {
                        name = n.trim();
                        updateHeaderGraphic();
                    }
                });
            });

            MenuItem changeColor = new MenuItem("Change Color");
            changeColor.setOnAction(e -> {
                color = generateRandomColor();
                updateHeaderGraphic();
                for (Tab t : tabs) {
                    int rr = (int)(color.getRed()*255);
                    int gg = (int)(color.getGreen()*255);
                    int bb = (int)(color.getBlue()*255);
                    t.setStyle("-fx-background-color: rgba(" + rr + "," + gg + "," + bb + ", 0.15);");
                }
            });

            MenuItem toggle = new MenuItem(collapsed ? "Expand Group" : "Collapse Group");
            toggle.setOnAction(e -> {
                collapsed = !collapsed;
                updateHeaderGraphic();
                rebuildTabOrder();
            });

            MenuItem ungroup = new MenuItem("Ungroup");
            ungroup.setOnAction(e -> {
                looseTabs.addAll(tabs);
                for (Tab t : tabs) {
                    t.setStyle(null);
                    tabToGroup.remove(t);
                }
                tabs.clear();
                headerToGroup.remove(headerTab);
                tabGroups.remove(this);
                rebuildTabOrder();
            });

            MenuItem delete = new MenuItem("Delete Group (close all tabs)");
            delete.setOnAction(e -> {
                tabs.forEach(t -> {
                    history.remove(t);
                    historyIndex.remove(t);
                });
                tabs.clear();
                tabPane.getTabs().remove(headerTab);
                headerToGroup.remove(headerTab);
                tabGroups.remove(this);
                rebuildTabOrder();
            });

            menu.getItems().addAll(rename, changeColor, toggle, ungroup, delete);
            headerTab.setContextMenu(menu);
        }
    }

    private Color generateRandomColor() {
        Random r = new Random();
        return Color.hsb(r.nextDouble() * 360, 0.6, 0.95);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        tabPane.setTabDragPolicy(TabPane.TabDragPolicy.REORDER);
        enableTabDragFinal();

        looseTabs.clear();
        tabGroups.clear();
        rebuildTabOrder();

        reloadButton.setVisible(true);
        reloadButton.setManaged(true);

        stopButton.setVisible(false);
        stopButton.setManaged(false);

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

        reloadButton.setOnAction(_ -> {
            Tab tab = getCurrentTab();
            if (tab != null && tab.getContent() instanceof WebView webView) {
                webView.getEngine().reload();
            }
        });

        stopButton.setOnAction(_ -> {
            Tab tab = getCurrentTab();
            if (tab != null && tab.getContent() instanceof WebView webView) {
                webView.getEngine().getLoadWorker().cancel();
            }
        });

        new UrlAutoComplete(urlField, globalHistory, bookmarkService, url -> loadUrl(getCurrentTab(), url, true));

        tabPane.getTabs().addListener((ListChangeListener<Tab>) c -> {
            long realTabCount = looseTabs.size() + tabGroups.stream().mapToLong(g -> g.tabs.size()).sum();
            if (realTabCount == 0) {
                Platform.exit();
            }
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

                double[] zoomValue = {1.0};

                newScene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {

                    Tab currentTab = getCurrentTab();
                    if (currentTab != null && currentTab.getContent() instanceof WebView webView) {

                        if (event.isControlDown() && event.getCode() == KeyCode.EQUALS) {
                            zoomValue[0] += 0.1;
                            webView.setZoom(zoomValue[0]);
                            event.consume();
                        }

                        if (event.isControlDown() && event.getCode() == KeyCode.MINUS) {
                            zoomValue[0] -= 0.1;
                            if (zoomValue[0] < 0.3) zoomValue[0] = 0.3;
                            webView.setZoom(zoomValue[0]);
                            event.consume();
                        }

                        if (event.isControlDown() && event.getCode() == KeyCode.DIGIT0) {
                            zoomValue[0] = 1.0;
                            webView.setZoom(zoomValue[0]);
                            event.consume();
                        }
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
                            closeTab(currentTab);
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

        setupTabContextMenu(tab);
        setupTabCloseHandler(tab);

        history.put(tab, new ArrayList<>());
        historyIndex.put(tab, -1);

        // Load nội dung (new tab page hoặc URL)
        if (url.equals("newtab")) {
            try {
                FXMLLoader loader = new FXMLLoader(WebBrowserApplication.class.getResource("new-tab.fxml"));
                Parent newTabRoot = loader.load();
                NewTabController newTabController = loader.getController();
                newTabController.setOnUrlOpen(requestedUrl -> loadUrl(tab, requestedUrl, true));
                tab.setContent(newTabRoot);

                TextField searchField = (TextField) newTabRoot.lookup("#searchField");
                if (searchField != null) {
                    searchField.setOnAction(_ -> loadUrl(tab, searchField.getText(), true));
                }
            } catch (IOException ex) {
                tab.setContent(new Label("Error loading new tab page"));
                ex.printStackTrace();
            }
        } else {
            loadUrl(tab, url, true);
        }

        looseTabs.add(tab);
        rebuildTabOrder();

        tabPane.getSelectionModel().select(tab);
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

                engine.getLoadWorker().runningProperty().addListener((obs, oldVal, isLoading) -> {
                    if (isLoading) {
                        reloadButton.setVisible(false);
                        reloadButton.setManaged(false);

                        stopButton.setVisible(true);
                        stopButton.setManaged(true);
                    } else {
                        reloadButton.setVisible(true);
                        reloadButton.setManaged(true);

                        stopButton.setVisible(false);
                        stopButton.setManaged(false);
                    }
                });

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

                        setTabFavicon(tab, url);

                        if (globalHistory.isEmpty() || !globalHistory.get(0).getUrl().equals(url)) {
                            globalHistory.add(0, new HistoryEntry(title, url, visitedAt));
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

            WebView webView = (WebView) tab.getContent();
            WebEngine engine = webView.getEngine();

            updateTabTitleAndIcon(tab, engine, prevUrl);
            engine.load(prevUrl);
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

            WebView webView = (WebView) tab.getContent();
            WebEngine engine = webView.getEngine();

            updateTabTitleAndIcon(tab, engine, nextUrl);
            engine.load(nextUrl);
        }
    }

    private Tab getCurrentTab() {
        return tabPane.getSelectionModel().getSelectedItem();
    }

    private void openHistoryWindow() {
        try {
            FXMLLoader loader = new FXMLLoader(WebBrowserApplication.class.getResource("history-view.fxml"));
            Parent root = loader.load();

            HistoryController controller = loader.getController();
            controller.setData(globalHistory, bookmarkService, url -> addNewTab(url));

            Tab historyTab = new Tab("History");
            historyTab.setContent(root);
            setupTabContextMenu(historyTab);
            setupTabCloseHandler(historyTab);

            looseTabs.add(historyTab);
            rebuildTabOrder();
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


    private void setTabFavicon(Tab tab, String url) {
        try {
            java.net.URI uri = new java.net.URI(url);
            String domain = uri.getScheme() + "://" + uri.getHost();

            if (faviconCache.containsKey(domain)) {
                ImageView icon = new ImageView(faviconCache.get(domain));
                icon.setFitWidth(16);
                icon.setFitHeight(16);
                Platform.runLater(() -> tab.setGraphic(icon));
                return;
            }

            String faviconUrl = "https://www.google.com/s2/favicons?domain=" + uri.getHost() + "&sz=32";

            Image favicon = new Image(faviconUrl, true);
            favicon.errorProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal) {
                    System.out.println("⚠️ Không tải được favicon cho " + domain);
                }
            });

            favicon.progressProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal.doubleValue() >= 1.0) {
                    faviconCache.put(domain, favicon);
                    ImageView iconView = new ImageView(favicon);
                    iconView.setFitWidth(16);
                    iconView.setFitHeight(16);
                    Platform.runLater(() -> tab.setGraphic(iconView));
                }
            });

        } catch (Exception e) {
            System.err.println("❌ Lỗi favicon: " + e.getMessage());
        }
    }

    private void updateTabTitleAndIcon(Tab tab, WebEngine engine, String url) {
        engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                String title = engine.getTitle() != null ? engine.getTitle() : url;

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
                    tab.setText(title);
                }

                setTabFavicon(tab, url);
            }
        });
    }

    private void enableTabDragFinal() {
        tabPane.setOnMouseDragged(event -> {
            if (event.getButton() != javafx.scene.input.MouseButton.PRIMARY) return;

            Tab draggedTab = null;
            double mouseX = event.getSceneX();
            double mouseY = event.getSceneY();

            for (Tab tab : tabPane.getTabs()) {
                Node header = getTabHeaderArea(tab);
                if (header != null) {
                    Bounds bounds = header.localToScene(header.getBoundsInLocal());
                    if (bounds.contains(mouseX, mouseY)) {
                        draggedTab = tab;
                        break;
                    }
                }
            }

            if (draggedTab == null) return;

            Dragboard db = tabPane.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString("TAB_" + System.identityHashCode(draggedTab));
            db.setContent(content);

            if (draggedTab.getGraphic() != null) {
                db.setDragView(((ImageView) draggedTab.getGraphic()).snapshot(null, null));
            }

            event.consume();
        });

        tabPane.setOnDragOver(event -> {
            if (event.getDragboard().hasString() && event.getDragboard().getString().startsWith("TAB_")) {
                event.acceptTransferModes(TransferMode.MOVE);
                event.consume();
            }
        });

        tabPane.setOnDragDropped(event -> {
            if (!event.getDragboard().hasString()) return;
            String data = event.getDragboard().getString();
            if (!data.startsWith("TAB_")) return;

            int hash = Integer.parseInt(data.substring("TAB_".length()));
            Tab draggedTab = tabPane.getTabs().stream().filter(t -> System.identityHashCode(t) == hash).findFirst().orElse(null);

            if (draggedTab == null) return;

            // Tính vị trí thả theo chuột
            double x = event.getSceneX();
            double y = event.getSceneY();
            int dropIndex = 0;

            for (Tab tab : tabPane.getTabs()) {
                if (tab == draggedTab) continue;
                Node header = getTabHeaderArea(tab);
                if (header != null) {
                    Bounds b = header.localToScene(header.getBoundsInLocal());
                    if (x < b.getMinX() + b.getWidth() / 2) {
                        break;
                    }
                }
                dropIndex++;
            }

            // Di chuyển tab
            int finalDropIndex = dropIndex;
            Platform.runLater(() -> {
                int oldIndex = tabPane.getTabs().indexOf(draggedTab);
                tabPane.getTabs().remove(draggedTab);

                int newIndex = finalDropIndex;
                if (oldIndex < newIndex) newIndex--;

                // === TÍNH TOÁN NẾU KÉO VÀO TRONG GROUP ===
                Tab targetTab = null;
                for (Tab t : tabPane.getTabs()) {
                    if (t == draggedTab) continue;
                    Node header = getTabHeaderArea(t);
                    if (header != null) {
                        Bounds b = header.localToScene(header.getBoundsInLocal());
                        if (x >= b.getMinX() && x <= b.getMaxX() &&
                                y >= b.getMinY() && y <= b.getMaxY()) {
                            targetTab = t;
                            break;
                        }
                    }
                }

// Nếu thả vào header của group → tự động add vào group đó
                if (targetTab != null && headerToGroup.containsKey(targetTab)) {
                    TabGroup targetGroup = headerToGroup.get(targetTab);

                    // Xóa tab khỏi vị trí cũ (group cũ hoặc loose)
                    TabGroup oldGroup = tabToGroup.get(draggedTab);
                    if (oldGroup != null) {
                        oldGroup.removeTab(draggedTab);
                    } else {
                        looseTabs.remove(draggedTab);
                        tabPane.getTabs().remove(draggedTab);
                    }

                    // Thêm vào group mới
                    targetGroup.addTab(draggedTab);

                    // Expand group nếu đang collapse (giống Chrome)
                    if (targetGroup.collapsed) {
                        targetGroup.collapsed = false;
                        targetGroup.updateHeaderGraphic();
                    }

                    tabPane.getSelectionModel().select(draggedTab);

                    List<TabGroup> newGroupOrder = new ArrayList<>();
                    for (Tab t : tabPane.getTabs()) {
                        if (headerToGroup.containsKey(t)) {
                            newGroupOrder.add(headerToGroup.get(t));
                        }
                    }
                    tabGroups.clear();
                    tabGroups.addAll(newGroupOrder);
                    rebuildTabOrder();

                    event.setDropCompleted(true);
                    event.consume();
                    return; // ← QUAN TRỌNG: thoát luôn, không xử lý reorder bình thường
                }

                if (headerToGroup.containsKey(draggedTab)) {
                    TabGroup group = headerToGroup.get(draggedTab);
                    List<Tab> block = new ArrayList<>();
                    block.add(group.headerTab);
                    if (!group.collapsed) block.addAll(group.tabs);

                    tabPane.getTabs().removeAll(block);
                    if (oldIndex < finalDropIndex) newIndex -= block.size();

                    tabPane.getTabs().addAll(newIndex, block);
                } else {
                    tabPane.getTabs().add(newIndex, draggedTab);
                }

                tabPane.getSelectionModel().select(draggedTab);
            });
            event.setDropCompleted(true);
            event.consume();
        });
    }

    private Node getTabHeaderArea(Tab tab) {
        for (Node node : tabPane.lookupAll(".tab")) {
            if (node instanceof StackPane sp && sp.getUserData() == tab) {
                return sp;
            }
        }
        return null;
    }

    private void rebuildTabOrder() {
        Tab selected = getCurrentTab();

        // Tắt tạm để không chớp
        tabPane.setDisable(true);

        tabPane.getTabs().clear();

        // Xây dựng lại đúng thứ tự
        for (TabGroup group : tabGroups) {
            tabPane.getTabs().add(group.headerTab);
            if (!group.collapsed) {
                tabPane.getTabs().addAll(group.tabs);
            }
        }
        tabPane.getTabs().addAll(looseTabs);

        // === QUAN TRỌNG NHẤT: CẬP NHẬT tabOrder MỖI LẦN REBUILD ===
        tabOrder.clear();
        tabOrder.addAll(
                tabPane.getTabs().stream()
                        .filter(t -> !headerToGroup.containsKey(t))  // chỉ lưu tab thật, không lưu header
                        .toList()
        );

        // Khôi phục tab đang chọn
        if (selected != null && tabPane.getTabs().contains(selected)) {
            tabPane.getSelectionModel().select(selected);
        } else if (!tabPane.getTabs().isEmpty()) {
            tabPane.getSelectionModel().select(tabPane.getTabs().size() - 1); // tab mới nhất nếu mất selected
        }

        // Bật lại – mượt như Chrome
        Platform.runLater(() -> tabPane.setDisable(false));
    }

    private void setupTabContextMenu(Tab tab) {
        ContextMenu cm = new ContextMenu();

        MenuItem newGroup = new MenuItem("New Tab Group");
        newGroup.setOnAction(e -> createTabGroup(tab));

        Menu addToMenu = new Menu("Add to Group");
        cm.setOnShowing(e -> {
            addToMenu.getItems().clear();
            for (TabGroup g : tabGroups) {
                MenuItem mi = new MenuItem(g.name);
                mi.setOnAction(ev -> {
                    TabGroup oldGroup = tabToGroup.get(tab);
                    if (oldGroup != null) oldGroup.removeTab(tab);
                    else looseTabs.remove(tab);
                    g.addTab(tab);
                    rebuildTabOrder();
                });
                addToMenu.getItems().add(mi);
            }
        });

        MenuItem removeFromGroup = new MenuItem("Remove from Group");
        removeFromGroup.setOnAction(e -> {
            TabGroup g = tabToGroup.get(tab);
            if (g != null) {
                g.removeTab(tab);
            }
        });

        cm.getItems().addAll(newGroup, addToMenu, removeFromGroup);
        tab.setContextMenu(cm);
    }

    private void createTabGroup(Tab tab) {
        TextInputDialog dialog = new TextInputDialog("My Group");
        dialog.setTitle("Create Tab Group");
        dialog.setHeaderText("Group name:");
        dialog.showAndWait().ifPresent(name -> {
            if (!name.trim().isEmpty()) {
                TabGroup group = new TabGroup(name.trim(), null); // random color
                // xóa khỏi vị trí cũ
                TabGroup old = tabToGroup.get(tab);
                if (old != null) old.removeTab(tab);
                else looseTabs.remove(tab);

                group.addTab(tab);
                tabGroups.add(group);
                rebuildTabOrder();
                tabPane.getSelectionModel().select(tab);
            }
        });
    }

    private void setupTabCloseHandler(Tab tab) {
        if (headerToGroup.containsKey(tab)) {
            tab.setClosable(false);
            return;
        }

        tab.setOnCloseRequest(e -> {
            // === ĐÓNG THẬT – KILL TAB HOÀN TOÀN ===
            TabGroup group = tabToGroup.get(tab);
            if (group != null) {
                group.tabs.remove(tab);                    // xóa khỏi group
                tabToGroup.remove(tab);
                tab.setStyle(null);                         // reset màu
                if (group.tabs.isEmpty()) {
                    headerToGroup.remove(group.headerTab);
                    tabGroups.remove(group);
                }
                updateHeaderGraphicIfNeeded(group);         // helper nhỏ dưới đây
            } else {
                looseTabs.remove(tab);                      // xóa khỏi loose
            }

            // Xóa toàn bộ dữ liệu liên quan
            history.remove(tab);
            historyIndex.remove(tab);
            historyIndex.remove(tab);
            tabToGroup.remove(tab);

            rebuildTabOrder();
            e.consume(); // ngăn JavaFX tự remove
        });
    }

    private void closeTab(Tab tab) {
        if (tab == null || headerToGroup.containsKey(tab)) return;

        TabGroup group = tabToGroup.get(tab);
        if (group != null) {
            group.tabs.remove(tab);
            tabToGroup.remove(tab);
            tab.setStyle(null);
            group.updateHeaderGraphic(); // giữ nguyên tên + số lượng

            if (group.tabs.isEmpty()) {
                headerToGroup.remove(group.headerTab);
                tabGroups.remove(group);
            }
        } else {
            looseTabs.remove(tab);
        }

        history.remove(tab);
        historyIndex.remove(tab);
        tabToGroup.remove(tab);

        rebuildTabOrder();
    }

    private void updateHeaderGraphicIfNeeded(TabGroup group) {
        if (group != null && group.headerTab != null) {
            group.updateHeaderGraphic();
        }
    }
}
