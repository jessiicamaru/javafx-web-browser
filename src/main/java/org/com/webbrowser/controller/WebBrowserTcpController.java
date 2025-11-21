package org.com.webbrowser.controller;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
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
import javafx.util.Duration;
import org.com.webbrowser.WebBrowserApplication;
import org.com.webbrowser.model.HistoryEntry;
import org.com.webbrowser.model.ServerTabGroup;
import org.com.webbrowser.model.ServerTab;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import org.com.webbrowser.service.BookmarkService;
import org.com.webbrowser.service.HistoryService;
import org.com.webbrowser.service.TabGroupService;
import org.com.webbrowser.session.UserSession;

import static javafx.collections.FXCollections.observableArrayList;
import static org.com.webbrowser.utils.UrlNormalizer.normalizeUrl;

/**
 * Controller chính của trình duyệt – điều khiển toàn bộ giao diện và chức năng của cửa sổ trình duyệt
 * Hỗ trợ:
 * - Nhiều tab với khả năng nhóm tab (Tab Group) đồng bộ server
 * - Lịch sử duyệt web riêng cho từng tab + lịch sử toàn cục
 * - Bookmark bar đồng bộ với server
 * - Tìm kiếm trong trang (Ctrl+F), phím tắt toàn cục
 * - Kéo thả tab để sắp xếp hoặc đưa vào nhóm
 * - Favicon tự động, reload/stop, back/forward...
 */
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

    /** Cache ID bookmark để edit/delete */
    private final Map<String, Long> bookmarkIds = new HashMap<>();
    /** Lịch sử duyệt web riêng của từng tab */
    private final Map<Tab, List<String>> history = new HashMap<>();
    private final Map<Tab, Integer> historyIndex = new HashMap<>();

    /** Lịch sử toàn cục để hiển thị trong cửa sổ History */
    private final ObservableList<HistoryEntry> globalHistory = observableArrayList();

    /** Service xử lý bookmark */
    private final BookmarkService bookmarkService = new BookmarkService();

    /** Cache favicon theo domain để tránh tải lại nhiều lần */
    private final Map<String, Image> faviconCache = new HashMap<>();

    /** Danh sách các nhóm tab hiện tại */
    private final List<GroupHeader> tabGroups = new ArrayList<>();

    /** Các tab không thuộc nhóm nào */
    private final List<Tab> looseTabs = new ArrayList<>();

    /**
     * Ánh xạ header giả → nhóm, tab → nhóm, serverId → nhóm
     * Header là bảng tên của group, luôn nằm bên trái group
     * Tab là các tab trong group đó
     * ServerId là Id của group đó trong database
     */
    private final Map<Tab, GroupHeader> headerToGroup = new HashMap<>();
    private final Map<Tab, GroupHeader> tabToGroup = new HashMap<>();
    private final Map<Long, GroupHeader> serverIdToGroup = new HashMap<>();

    /** Service đồng bộ nhóm tab với server */
    private final TabGroupService tabGroupService = new TabGroupService();

    /** Thứ tự tab hiện tại (chỉ chứa tab thật, không có header) – dùng để debug */
    private final List<Tab> tabOrder = new ArrayList<>();

    /**
     * Class nội bộ đại diện cho một nhóm tab (Tab Group)
     * - Có header giả để hiển thị tên nhóm và trạng thái collapsed
     * - Quản lý danh sách tab con, màu sắc, đồng bộ server
     */
    private class GroupHeader {
        String name; // Tên group, sẽ được hiển thị ở Header group
        Color color; // Màu của group
        boolean collapsed = false; // Trạng thái đóng hoặc mở rộng của group
        Long serverId; // Id của group ở trên server
        final List<Tab> tabs = new ArrayList<>(); // Danh sách các tab của group đó
        Tab headerTab = new Tab(); // Header của tab dùng để hiển thị tên

        GroupHeader(String name, Color color, Long serverId) {
            this.name = name;
            this.color = color == null ? generateRandomColor() : color;
            this.serverId = serverId;

            headerTab = new Tab();
            headerTab.setClosable(false); // Tắt nút X mặc định của tab
            headerToGroup.put(headerTab, this); // Ánh xạ phần tên group với group

            headerTab.graphicProperty().addListener((obs, oldGraphic, newGraphic) -> {
                if (newGraphic instanceof Region region) {
                    region.setOnMouseClicked(e -> {
                        if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 1) {
                            // Thực hiện việc toggle hiển thị và thu gọn tab group
                            collapsed = !collapsed;
                            updateHeaderGraphic(); // Cập nhật lại giao diện
                            rebuildTabOrder(); // Thực hiện hiển thị lại các tab trong tabpane
                            e.consume();
                        }
                    });
                }
            });


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

            // Khởi tạo context menu cho group
            setupGroupContextMenu();
            Platform.runLater(this::updateHeaderGraphic);
        }

        /**
         * Cập nhật giao diện header (tên nhóm + số tab + màu)
         */
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

            // Update trạng thái của header: hiển thị các tab hoặc thu gọn
            badge.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 1) {
                    collapsed = !collapsed;
                    updateHeaderGraphic();
                    rebuildTabOrder();
                    e.consume();
                }
            });


            // Cập nhật lại giao diện cho header
            if (collapsed) {
                // Khi trạng thái là đóng thì chỉ hiển thị badge
                headerTab.setGraphic(badge);
            } else {
                // Khi mở thì thêm một label có khoảng trống làm tab dài ra tạo cảm giác mở rộng
                Label separator = new Label("   ");
                separator.setStyle("-fx-background-color: transparent;");
                HBox fullHeader = new HBox(badge, separator);
                fullHeader.setAlignment(Pos.CENTER_LEFT);
                headerTab.setGraphic(fullHeader);
            }
        }

        /**
         * Thêm một tab thật vào nhóm này
         */
        void addTab(Tab fxTab, ServerTab modelTab) {
            // Kiểm tra trùng lặp tab
            if (tabs.contains(fxTab)) return;

            // Xoá tab đó ở danh sách các tab đơn ở ngoài
            looseTabs.remove(fxTab);

            // Thêm vào danh sách tabs của group
            tabs.add(fxTab);
            // Thêm ánh xạ
            tabToGroup.put(fxTab, this);

            /**
             * Gắn dữ liệu mô hình từ server (ServerTab) vào tab JavaFX
             * dùng để đồng bộ lên server
             */
            fxTab.setUserData(modelTab);

            // Tô màu nền nhẹ cho tab để nhận biết nó thuộc nhóm nào
            int r = (int) (color.getRed() * 255);
            int g = (int) (color.getGreen() * 255);
            int b = (int) (color.getBlue() * 255);
            fxTab.setStyle("-fx-background-color: rgba(" + r + "," + g + "," + b + ", 0.15);");

            // Thiết lập menu chuột phải cho tab (New Group, Add to Group, Remove from Group...)
            setupTabContextMenu(fxTab);
            // Xử lý hành vi khi đóng tab (rất đặc biệt khi tab nằm trong nhóm)
            setupTabCloseHandler(fxTab);
            // Cập nhật lại header nhóm (để hiện số lượng tab mới)
            updateHeaderGraphic();

            // Nếu nhóm này chưa nằm trong danh sách nhóm toàn cục thì thêm vào
            if (!tabGroups.contains(this)) {
                tabGroups.add(this);
            }

            // Gọi API để update dữ liệu với server
            syncToServer();
        }

        /**
         * Đồng bộ thay đổi nhóm lên server (tên, màu, danh sách tab)
         */
        private void syncToServer() {
            // Chỉ đồng bộ nếu group đã có trên server
            if (serverId == null) return;

            ServerTabGroup model = new ServerTabGroup();
            model.setId(serverId);
            model.setName(name);
            model.setColor(String.format("#%02X%02X%02X",
                    (int) (color.getRed() * 255),
                    (int) (color.getGreen() * 255),
                    (int) (color.getBlue() * 255)));

            List<ServerTab> serverTabs = tabs.stream()
                    .map(t -> (ServerTab) t.getUserData()) // Lấy dữ liệu đã gắn trước đó
                    .filter(Objects::nonNull) // Loại bỏ nếu có tab chưa có dữ liệu
                    .collect(Collectors.toList());

            model.setTabs(serverTabs);

            // Gửi lên server
            tabGroupService.updateTabGroup(model, () -> {
                System.out.println("Tab group synced: " + name + " đã được đồng bộ server");
            });
        }

        void removeTab(Tab tab) {
            if (tabs.remove(tab)) {         // Nếu xoá thành công
                tabToGroup.remove(tab);     // Bỏ ánh xạ
                tab.setStyle(null);

                looseTabs.add(tab);         // Add lại tab này vào danh sách các tab đơn

                updateHeaderGraphic();      // Cập nhật lại header của group

                // Nếu nhóm còn 0 tab → tự động xóa nhóm luôn
                if (tabs.isEmpty()) {
                    headerToGroup.remove(headerTab);
                    tabGroups.remove(this);
                }

                if (serverId != null) {
                    syncToServer();
                }
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

                        if (serverId != null) {
                            syncToServer();
                        }
                    }
                });
            });

            MenuItem changeColor = new MenuItem("Change Color");
            changeColor.setOnAction(e -> {
                color = generateRandomColor();

                updateHeaderGraphic();
                for (Tab t : tabs) {
                    int r = (int) (color.getRed() * 255);
                    int g = (int) (color.getGreen() * 255);
                    int b = (int) (color.getBlue() * 255);
                    t.setStyle("-fx-background-color: rgba(" + r + "," + g + "," + b + ", 0.15);");
                }

                if (serverId != null) {
                    syncToServer();
                }
            });

            MenuItem toggle = new MenuItem(collapsed ? "Expand Group" : "Collapse Group");
            toggle.setOnAction(e -> {
                collapsed = !collapsed;
                updateHeaderGraphic(); // Update lại header
                rebuildTabOrder();  // Update lại các tab hiển thị
            });

            MenuItem ungroup = new MenuItem("Ungroup");
            ungroup.setOnAction(e -> {
                looseTabs.addAll(tabs); // Đưa hết tab về trạng thái lẻ
                for (Tab t : tabs) {
                    t.setStyle(null);
                    tabToGroup.remove(t);
                }
                tabs.clear(); // Xoá sạch danh sách tabs

                if (serverId != null) {
                    // Xoá luôn ở trên server
                    tabGroupService.deleteTabGroup(serverId, () -> {
                        System.out.println("Group đã bị xóa hoàn toàn trên server (do ungroup)");
                    });
                }

                headerToGroup.remove(headerTab);
                tabGroups.remove(this);
                rebuildTabOrder(); // Cập nhật lại giao diện của tab
            });

            MenuItem delete = new MenuItem("Delete Group (close all tabs)");
            delete.setOnAction(e -> {
                tabs.forEach(t -> {
                    history.remove(t); // Xóa lịch sử back/forward của từng tab
                    historyIndex.remove(t);
                });

                if (serverId != null) {
                    tabGroupService.deleteTabGroup(serverId, () -> {
                        System.out.println("Group đã bị xóa trên server: " + name);
                    });
                }

                tabs.clear();
                tabPane.getTabs().remove(headerTab); // Xóa luôn header của group
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
        setupUI();
        loadDataFromServer();
        setupEventHandlers();
        setupGlobalShortcuts();
    }

    /**
     * Thiết lập giao diện ban đầu
     */
    private void setupUI() {
        tabPane.setTabDragPolicy(TabPane.TabDragPolicy.REORDER);
        enableTabDragFinal();

        reloadButton.setVisible(true);
        reloadButton.setManaged(true);
        stopButton.setVisible(false);
        stopButton.setManaged(false);

        findBar.setVisible(false);
        findBar.setManaged(false);
    }

    /**
     *  Tải toàn bộ dữ liệu từ server: bookmark + tab groups
     */
    private void loadDataFromServer() {
        loadBookmarksFromServer();
        globalHistory.setAll(HistoryService.loadAllHistory());
        loadTabGroupsFromServer();
    }

    /**
     * Gán sự kiện cho các nút điều hướng, reload, bookmark...
     */
    private void setupEventHandlers() {
        goButton.setOnAction(e -> loadCurrentUrl());
        urlField.setOnAction(e -> loadCurrentUrl());
        addTabButton.setOnAction(e -> addNewTab("newtab"));
        backButton.setOnAction(e -> goBack());
        forwardButton.setOnAction(e -> goForward());
        closeFindButton.setOnAction(e -> closeFindBar());
        findField.textProperty().addListener((obs, old, text) -> findInPage(text, true));
        nextButton.setOnAction(e -> findInPage(findField.getText(), true));
        prevButton.setOnAction(e -> findInPage(findField.getText(), false));
        reloadButton.setOnAction(e -> reloadCurrentTab());
        stopButton.setOnAction(e -> stopCurrentTab());
        bookmarkButton.setOnAction(e -> addBookmark());
    }

    /**
     * Đăng ký các phím tắt toàn cục (Ctrl+T, Ctrl+W, Ctrl+Tab, Ctrl+F, F5, ESC...)
     */
    private void setupGlobalShortcuts() {
        tabPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.addEventFilter(KeyEvent.KEY_PRESSED, this::handleGlobalKey);
            }
        });
    }

    /**
     * Xử lý phím tắt toàn cục
     */
    private void handleGlobalKey(KeyEvent event) {
        if (event.getCode() == KeyCode.W && event.isControlDown()) {
            Tab tab = getCurrentTab();
            if (tab != null && !headerToGroup.containsKey(tab)) {
                closeTab(tab);
            }
            event.consume();

        } else if (event.getCode() == KeyCode.T && event.isControlDown()) {
            addNewTab("newtab");
            event.consume();

        } else if (event.getCode() == KeyCode.TAB && event.isControlDown()) {
            int size = tabPane.getTabs().size();
            if (size <= 1) return;

            int current = tabPane.getSelectionModel().getSelectedIndex();
            int next = event.isShiftDown()
                    ? (current - 1 + size) % size
                    : (current + 1) % size;

            Tab nextTab = tabPane.getTabs().get(next);
            while (headerToGroup.containsKey(nextTab)) {
                next = event.isShiftDown()
                        ? (next - 1 + size) % size
                        : (next + 1) % size;
                nextTab = tabPane.getTabs().get(next);
            }

            tabPane.getSelectionModel().select(nextTab);
            event.consume();

        } else if (event.getCode() == KeyCode.E && event.isControlDown()) {
            Platform.runLater(() -> {
                urlField.requestFocus();
                urlField.selectAll();
            });
            event.consume();

        } else if (event.getCode() == KeyCode.F && event.isControlDown()) {
            openFindBar();
            event.consume();

        } else if (event.getCode() == KeyCode.F5) {
            reloadCurrentTab();
            event.consume();

        } else if (event.getCode() == KeyCode.ESCAPE) {
            stopCurrentTab();
            closeFindBar();
            event.consume();
        } else  if (event.getCode() == KeyCode.H) {
            openHistoryWindow();
            event.consume();
        }
    }

    /**
     * Đồng bộ tab hiện tại lên server nếu nó thuộc một nhóm
     */
    private void syncTabIfInGroup(Tab tab) {
        if (tab == null) return;

        GroupHeader group = tabToGroup.get(tab);
        if (group != null && group.serverId != null) {
            group.syncToServer();
        }
    }

    /**
     * Kiểm tra nếu không còn tab thật nào → thoát ứng dụng
     */
    private void checkAndExitIfNoTabs() {
        long realTabCount = looseTabs.size() + tabGroups.stream().mapToLong(g -> g.tabs.size()).sum();
        if (realTabCount == 0) {
            Platform.runLater(() -> {
                new Timeline(new KeyFrame(Duration.millis(100), e -> Platform.exit())).play();
            });
        }
    }

    /**
     * Tải danh sách nhóm tab từ serveri
     */
    private void loadTabGroupsFromServer() {
        Integer userId = UserSession.getInstance().getUserId();

        if (userId == null) {
            Platform.runLater(() -> addNewTab("newtab"));
            return;
        }

        tabGroupService.getTabGroups(rawList -> {
            Platform.runLater(() -> {
                System.out.println("\nCALLBACK từ server đã về!");
                System.out.println("rawList = " + rawList);
                System.out.println("rawList == null ? " + (rawList == null));
                System.out.println("rawList size = " + (rawList != null ? rawList.size() : "N/A"));

                if (rawList != null) {
                    System.out.println("=== CHI TIẾT TỪNG GROUP TRẢ VỀ ===");
                    for (int i = 0; i < rawList.size(); i++) {
                        ServerTabGroup sg = rawList.get(i);
                        System.out.println("Group " + i + ":");
                        System.out.println("  ID      : " + sg.getId());
                        System.out.println("  Name    : " + sg.getName());
                        System.out.println("  Color   : " + sg.getColor());
                        System.out.println("  Tabs    : " + (sg.getTabs() != null ? sg.getTabs().size() : "null"));
                        if (sg.getTabs() != null) {
                            for (int j = 0; j < sg.getTabs().size(); j++) {
                                ServerTab st = sg.getTabs().get(j);
                                System.out.println("    Tab " + j + ": " +
                                        (st.getTitle() != null ? st.getTitle() : "No title") +
                                        " → " + st.getUrl());
                            }
                        }
                    }
                } else {
                    System.out.println("rawList NULL → server trả về null hoặc lỗi");
                }

                tabGroups.clear();
                looseTabs.clear();
                headerToGroup.clear();
                tabToGroup.clear();
                serverIdToGroup.clear();

                boolean hasAnyTab = false;

                if (rawList != null && !rawList.isEmpty()) {
                    for (ServerTabGroup sg : rawList) {
                        if (sg.getName() == null) {
                            System.out.println("Bỏ qua group có tên null");
                            continue;
                        }

                        Color groupColor = sg.getColor() != null ? Color.web(sg.getColor()) : generateRandomColor();
                        GroupHeader gh = new GroupHeader(sg.getName(), groupColor, sg.getId());
                        serverIdToGroup.put(sg.getId(), gh);
                        tabGroups.add(gh);

                        List<ServerTab> serverTabs = sg.getTabs();
                        if (serverTabs == null) serverTabs = new ArrayList<>();

                        for (ServerTab st : serverTabs) {
                            if (st.getUrl() == null || st.getUrl().trim().isEmpty()) continue;

                            Tab fxTab = new Tab(st.getTitle() != null ? st.getTitle() : "Loading...");
                            fxTab.setUserData(st);
                            createWebViewAndLoad(fxTab, st.getUrl());

                            setupTabContextMenu(fxTab);

                            gh.addTab(fxTab, st);
                            hasAnyTab = true;
                        }
                    }
                } else {
                    System.out.println("Không có dữ liệu group nào từ server");
                }

                System.out.println("Tổng số group sau khi xử lý: " + tabGroups.size());
                rebuildTabOrder();

                if (!hasAnyTab && looseTabs.isEmpty() && tabGroups.isEmpty()) {
                    System.out.println("Không có tab nào → mở New Tab mặc định");
                    addNewTab("newtab");
                }

                System.out.println("=== HOÀN TẤT LOAD TAB GROUPS ===\n");
            });
        });
    }

    /**
     * Tạo WebView + load URL cho một tab
     */
    private void createWebViewAndLoad(Tab tab, String url) {
        WebView webView = new WebView();
        WebEngine engine = webView.getEngine();

        tab.setContent(webView);
        tab.setUserData(tab.getUserData());
        urlField.setText(url);

        engine.getLoadWorker().runningProperty().addListener((obs, old, loading) -> {
            reloadButton.setVisible(!loading);
            reloadButton.setManaged(!loading);
            stopButton.setVisible(loading);
            stopButton.setManaged(loading);
        });

        engine.load(url);

        engine.getLoadWorker().stateProperty().addListener((obs, old, state) -> {
            if (state == Worker.State.SUCCEEDED) {
                String title = engine.getTitle();
                if (title != null && !title.isEmpty()) {
                    tab.setText(title.length() > 50 ? title.substring(0, 47) + "..." : title);
                }
                setTabFavicon(tab, url);
            }
        });
    }

    /**
     * Thêm một nút bookmark vào bookmark bar
     */
    private void addBookmarkButton(String name, String url, Long id) {
        Button bmButton = new Button(name);
        bmButton.setOnAction(_ -> loadUrl(getCurrentTab(), url, true));
        bmButton.setUserData(id);

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

    /**
     * Mở tab mới – có thể là newtab page hoặc URL cụ thể
     */
    private void addNewTab(String url) {
        Tab tab = new Tab("New Tab");

        setupTabContextMenu(tab);
        setupTabCloseHandler(tab);

        history.put(tab, new ArrayList<>());
        historyIndex.put(tab, -1);

        if (url.equals("newtab")) {
            try {
                FXMLLoader loader = new FXMLLoader(WebBrowserApplication.class.getResource("new-tab.fxml"));
                Parent newTabRoot = loader.load();
                NewTabController newTabController = loader.getController();
                newTabController.setOnUrlOpen(requestedUrl -> loadUrl(tab, requestedUrl, true));
                tab.setContent(newTabRoot);

                newTabRoot.setFocusTraversable(false);

                TextField searchField = (TextField) newTabRoot.lookup("#searchField");
                if (searchField != null) {
                    searchField.setFocusTraversable(false);

                    searchField.setOnMouseClicked(e -> {
                        Platform.runLater(() -> {
                            searchField.setFocusTraversable(true);
                            searchField.requestFocus();
                            searchField.selectAll();
                        });
                    });

                    searchField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
                        if (!isNowFocused) {
                            searchField.setFocusTraversable(false);
                        }
                    });

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

    /**
     * Load URL vào tab hiện tại (chuẩn hóa URL trước)
     */
    private void loadUrl(Tab tab, String input, boolean addToHistory) {
        if (tab == null || input == null || input.isEmpty()) return;

        String url = normalizeUrl(input);
        WebView webView = createWebView(tab, url);
        ServerTab serverTab = ensureServerTab(tab, url);

        webView.getEngine().load(url);
        setupLoadWorkerListener(webView.getEngine(), tab, serverTab, addToHistory);
    }

    /**
     * Tạo WebView mới cho tab
     */
    private WebView createWebView(Tab tab, String url) {
        WebView webView = new WebView();
        tab.setContent(webView);
        urlField.setText(url);
        return webView;
    }

    /**
     * Đảm bảo tab có đối tượng ServerTab để đồng bộ server
     */
    private ServerTab ensureServerTab(Tab tab, String url) {
        Object data = tab.getUserData();
        ServerTab serverTab;
        if (data instanceof ServerTab st) {
            serverTab = st;
        } else {
            serverTab = new ServerTab();
            if (data instanceof String oldUrl) serverTab.setUrl(oldUrl);
            tab.setUserData(serverTab);
        }
        serverTab.setUrl(url);
        serverTab.setTitle("Loading...");
        return serverTab;
    }

    /**
     * Thiết lập listener khi trang load xong: cập nhật title, favicon, lịch sử, đồng bộ server
     */
    private void setupLoadWorkerListener(WebEngine engine, Tab tab, ServerTab serverTab, boolean addToHistory) {
        engine.getLoadWorker().stateProperty().addListener((obs, old, state) -> {
            if (state == Worker.State.SUCCEEDED) {
                String title = engine.getTitle();
                if (title != null) {
                    tab.setText(title.length() > 50 ? title.substring(0, 47) + "..." : title);
                    serverTab.setTitle(title);
                }
                setTabFavicon(tab, engine.getLocation());
                syncTabIfInGroup(tab);
                updateHistory(tab, engine.getLocation(), addToHistory);
            }
        });
    }

    /**
     * Load URL từ ô địa chỉ khi nhấn Enter hoặc nút Go
     */
    private void loadCurrentUrl() {
        Tab currentTab = getCurrentTab();
        if (currentTab == null || headerToGroup.containsKey(currentTab)) {
            return;
        }

        String input = urlField.getText().trim();
        if (input.isEmpty()) {
            return;
        }

        loadUrl(currentTab, input, true);
    }

    /**
     * Reload trang hiện tại
     */
    private void reloadCurrentTab() {
        Tab currentTab = getCurrentTab();
        if (currentTab == null || !(currentTab.getContent() instanceof WebView webView)) {
            return;
        }

        webView.getEngine().reload();
    }

    /**
     * Thêm bookmark cho trang hiện tại
     */
    private void addBookmark() {
        String currentUrl = urlField.getText().trim();

        if (currentUrl.isEmpty() || currentUrl.equals("about:blank") || currentUrl.startsWith("newtab")) {
            showAlert(Alert.AlertType.WARNING, "Không thể thêm bookmark", "URL không hợp lệ hoặc trống!");
            return;
        }

        Tab currentTab = getCurrentTab();
        String defaultName = currentTab != null && currentTab.getText() != null
                ? currentTab.getText() : extractTitleFromUrl(currentUrl);

        TextInputDialog dialog = new TextInputDialog(defaultName);
        dialog.setTitle("Thêm Bookmark");
        dialog.setHeaderText("Nhập tên bookmark");
        dialog.setContentText("Tên:");

        dialog.showAndWait().ifPresent(name -> {
            if (name == null || name.trim().isEmpty()) {
                showAlert(Alert.AlertType.WARNING, "Lỗi", "Tên bookmark không được để trống!");
                return;
            }

            bookmarkService.addBookmark(name.trim(), currentUrl, () -> {
                Platform.runLater(this::loadBookmarksFromServer);
                System.out.println("Bookmark đã được thêm: " + name);
            });
        });
    }

    /**
     * Trích xuất tên mặc định từ URL nếu không có title
     */
    private String extractTitleFromUrl(String url) {
        try {
            String host = new java.net.URI(url).getHost();
            if (host != null) {
                host = host.replaceFirst("^www\\.", "");
                return host.substring(0, 1).toUpperCase() + host.substring(1);
            }
        } catch (Exception ignored) {}
        return "Bookmark mới";
    }

    /**
     * Hiển thị thông báo dạng Alert
     */
    private void showAlert(Alert.AlertType type, String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    /**
     * Dừng tải trang hiện tại
     */
    private void stopCurrentTab() {
        Tab currentTab = getCurrentTab();
        if (currentTab == null || !(currentTab.getContent() instanceof WebView webView)) {
            return;
        }

        webView.getEngine().getLoadWorker().cancel();
    }

    /**
     * Cập nhật lịch sử duyệt web của tab
     */
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

    /**
     * Điều hướng lùi lại trong lịch sử của tab
     */
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

    /**
     * Điều hướng tiến lên trong lịch sử của tab
     */
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

    /**
     * Lấy tab hiện đang được chọn
     */
    private Tab getCurrentTab() {
        return tabPane.getSelectionModel().getSelectedItem();
    }

    /**
     * Mở cửa sổ History riêng
     */
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

    /**
     * Tải danh sách bookmark từ server và hiển thị lên bookmark bar
     */
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

    /**
     * Mở thanh tìm kiếm trong trang (Ctrl+F)
     */
    private void openFindBar() {
        findBar.setVisible(true);
        findBar.setManaged(true);
        findField.requestFocus();
    }

    /**
     * Đóng thanh tìm kiếm
     */
    private void closeFindBar() {
        findBar.setVisible(false);
        findBar.setManaged(false);
    }

    /**
     * Tìm kiếm văn bản trong trang hiện tại
     */
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

    /**
     * Tải và hiển thị favicon của trang
     */
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

    /**
     * Cập nhật title và favicon khi duyệt lịch sử back/forward
     */
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

    /**
     * Bật tính năng kéo thả tab để sắp xếp hoặc đưa vào nhóm
     * */
    private void enableTabDragFinal() {
        tabPane.setOnMouseDragged(event -> {
            if (event.getButton() != MouseButton.PRIMARY) return;

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

            int finalDropIndex = dropIndex;
            Platform.runLater(() -> {
                int oldIndex = tabPane.getTabs().indexOf(draggedTab);
                tabPane.getTabs().remove(draggedTab);

                int newIndex = finalDropIndex;
                if (oldIndex < newIndex) newIndex--;

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

                if (targetTab != null && headerToGroup.containsKey(targetTab)) {
                    GroupHeader targetGroup = headerToGroup.get(targetTab);

                    GroupHeader oldGroup = tabToGroup.get(draggedTab);
                    if (oldGroup != null) {
                        oldGroup.removeTab(draggedTab);
                    } else {
                        looseTabs.remove(draggedTab);
                        tabPane.getTabs().remove(draggedTab);
                    }

                    targetGroup.addTab(draggedTab, (ServerTab) draggedTab.getUserData());

                    if (targetGroup.collapsed) {
                        targetGroup.collapsed = false;
                        targetGroup.updateHeaderGraphic();
                    }

                    tabPane.getSelectionModel().select(draggedTab);

                    List<GroupHeader> newGroupOrder = new ArrayList<>();
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
                    return;
                }

                if (headerToGroup.containsKey(draggedTab)) {
                    GroupHeader group = headerToGroup.get(draggedTab);
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

    /**
     * Lấy vùng header của một tab (dùng trong drag & drop)
     */
    private Node getTabHeaderArea(Tab tab) {
        for (Node node : tabPane.lookupAll(".tab")) {
            if (node instanceof StackPane sp && sp.getUserData() == tab) {
                return sp;
            }
        }
        return null;
    }

    /**
     * Tái xây dựng lại thứ tự tab trong TabPane (rất quan trọng khi collapse/expand nhóm)
     */
    private void rebuildTabOrder() {
        System.out.println("rebuildTabOrder() called | tabGroups: " + tabGroups.size() +
                " | looseTabs: " + looseTabs.size());

        Tab selected = getCurrentTab();

        tabPane.setDisable(true);
        tabPane.getTabs().clear();

        for (GroupHeader group : tabGroups) {
            System.out.println("Adding group header: " + group.name +
                    " | collapsed=" + group.collapsed +
                    " | tabs=" + group.tabs.size());
            tabPane.getTabs().add(group.headerTab);
            if (!group.collapsed) {
                tabPane.getTabs().addAll(group.tabs);
            }
        }
        tabPane.getTabs().addAll(looseTabs);

        tabOrder.clear();
        tabOrder.addAll(tabPane.getTabs().stream()
                .filter(t -> !headerToGroup.containsKey(t))
                .toList());

        if (selected != null && tabPane.getTabs().contains(selected)) {
            tabPane.getSelectionModel().select(selected);
        } else if (!tabPane.getTabs().isEmpty()) {
            tabPane.getSelectionModel().select(tabPane.getTabs().size() - 1);
        }

        Platform.runLater(() -> {
            tabPane.setDisable(false);
            System.out.println("rebuildTabOrder() hoàn tất – tabPane có " + tabPane.getTabs().size() + " tab");
        });
    }

    /**
     * Thiết lập menu chuột phải cho tab thường: tạo nhóm mới, thêm vào nhóm, bỏ nhóm...
     */
    private void setupTabContextMenu(Tab tab) {
        ContextMenu cm = new ContextMenu();

        MenuItem newGroup = new MenuItem("New Tab Group");
        newGroup.setOnAction(e -> createTabGroup(tab));

        Menu addToMenu = new Menu("Add to Group");
        cm.setOnShowing(e -> {
            addToMenu.getItems().clear();
            for (GroupHeader g : tabGroups) {
                MenuItem mi = new MenuItem(g.name);
                mi.setOnAction(ev -> {
                    GroupHeader oldGroup = tabToGroup.get(tab);
                    if (oldGroup != null) oldGroup.removeTab(tab);
                    else looseTabs.remove(tab);

                    ServerTab modelTab = null;
                    Object data = tab.getUserData();
                    if (data instanceof ServerTab st) {
                        modelTab = st;
                    } else if (data instanceof String url) {
                        modelTab = new ServerTab();
                        modelTab.setUrl(url);
                        modelTab.setTitle(tab.getText());
                        tab.setUserData(modelTab);
                    }

                    g.addTab(tab, modelTab);
                    rebuildTabOrder();
                });
                addToMenu.getItems().add(mi);
            }
        });

        MenuItem removeFromGroup = new MenuItem("Remove from Group");
        removeFromGroup.setOnAction(e -> {
            GroupHeader g = tabToGroup.get(tab);
            if (g != null) {
                g.removeTab(tab);
            }
        });

        cm.getItems().addAll(newGroup, addToMenu, removeFromGroup);
        tab.setContextMenu(cm);
    }

    /**
     * Tạo nhóm tab mới từ một tab đã chọn
     */
    private void createTabGroup(Tab tab) {
        TextInputDialog dialog = new TextInputDialog("My Group");
        dialog.setTitle("Create Tab Group");
        dialog.setHeaderText("Group name:");
        dialog.showAndWait().ifPresent(name -> {
            if (!name.trim().isEmpty()) {
                ServerTabGroup tempGroup = new ServerTabGroup();
                tempGroup.setName(name.trim());
                tempGroup.setColor("#" + Integer.toHexString(generateRandomColor().hashCode() & 0xFFFFFF));

                ServerTab modelTab;
                Object data = tab.getUserData();
                if (data instanceof ServerTab st) {
                    modelTab = st;
                } else if (data instanceof String url) {
                    modelTab = new ServerTab();
                    modelTab.setUrl(url);
                    modelTab.setTitle(tab.getText());
                    tab.setUserData(modelTab);
                } else {
                    modelTab = null;
                }

                List<ServerTab> tabsList = modelTab != null ? List.of(modelTab) : List.of();
                tempGroup.setTabs(tabsList);

                tabGroupService.addTabGroup(tempGroup, createdGroup -> {
                    Platform.runLater(() -> {
                        GroupHeader group = new GroupHeader(createdGroup.getName(),
                                Color.web(createdGroup.getColor()), createdGroup.getId());

                        GroupHeader old = tabToGroup.get(tab);
                        if (old != null) old.removeTab(tab);
                        else looseTabs.remove(tab);

                        group.addTab(tab, modelTab);
                        serverIdToGroup.put(createdGroup.getId(), group);

                        rebuildTabOrder();
                        tabPane.getSelectionModel().select(tab);
                    });
                });
            }
        });
    }

    /**
     * Xử lý đóng tab (Ctrl+W hoặc nút X) – có tính đến nhóm
     */
    private void setupTabCloseHandler(Tab tab) {
        if (headerToGroup.containsKey(tab)) {
            tab.setClosable(false);
            return;
        }

        tab.setOnCloseRequest(e -> {
            GroupHeader group = tabToGroup.get(tab);
            if (group != null) {
                group.tabs.remove(tab);
                tabToGroup.remove(tab);
                tab.setStyle(null);
                if (group.tabs.isEmpty()) {
                    headerToGroup.remove(group.headerTab);
                    tabGroups.remove(group);
                }
                updateHeaderGraphicIfNeeded(group);
            } else {
                looseTabs.remove(tab);
            }

            history.remove(tab);
            historyIndex.remove(tab);
            historyIndex.remove(tab);
            tabToGroup.remove(tab);

            rebuildTabOrder();
            checkAndExitIfNoTabs();
            e.consume();
        });
    }

    /**
     * Đóng tab một cách an toàn (dùng trong phím tắt)
     */
    private void closeTab(Tab tab) {
        if (tab == null || headerToGroup.containsKey(tab)) return;

        GroupHeader group = tabToGroup.get(tab);
        if (group != null) {
            group.tabs.remove(tab);
            tabToGroup.remove(tab);
            tab.setStyle(null);
            group.updateHeaderGraphic();

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
        checkAndExitIfNoTabs();
    }

    /**
     * Cập nhật lại giao diện header nếu cần sau khi xóa tab khỏi nhóm
     */
    private void updateHeaderGraphicIfNeeded(GroupHeader group) {
        if (group != null && group.headerTab != null) {
            group.updateHeaderGraphic();
        }
    }
}
