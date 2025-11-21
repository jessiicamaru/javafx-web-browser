package org.com.webbrowser.controller;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
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
import java.net.URLDecoder;
import java.util.*;
import java.util.stream.Collectors;

import javafx.collections.ObservableList;

import org.com.webbrowser.service.BookmarkService;
import org.com.webbrowser.service.HistoryService;
import org.com.webbrowser.service.TabGroupService;
import org.com.webbrowser.session.UserSession;
import org.com.webbrowser.utils.ColorUtils;
import org.com.webbrowser.utils.FaviconHelper;

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

    /**
     * Cache ID bookmark để edit/delete
     */
    private final Map<String, Long> bookmarkIds = new HashMap<>();
    /**
     * Lịch sử duyệt web riêng của từng tab
     */
    private final Map<Tab, List<String>> history = new HashMap<>();
    private final Map<Tab, Integer> historyIndex = new HashMap<>();

    /**
     * Lịch sử toàn cục để hiển thị trong cửa sổ History
     */
    private final ObservableList<HistoryEntry> globalHistory = observableArrayList();

    /**
     * Service xử lý bookmark
     */
    private final BookmarkService bookmarkService = new BookmarkService();

    /**
     * Cache favicon theo domain để tránh tải lại nhiều lần
     */
    private final Map<String, Image> faviconCache = new HashMap<>();

    /**
     * Danh sách các nhóm tab hiện tại
     */
    private final List<GroupHeader> tabGroups = new ArrayList<>();

    /**
     * Các tab không thuộc nhóm nào
     */
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

    /**
     * Service đồng bộ nhóm tab với server
     */
    private final TabGroupService tabGroupService = new TabGroupService();

    /**
     * Thứ tự tab hiện tại (chỉ chứa tab thật, không có header) – dùng để debug
     */
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
            this.color = color == null ? ColorUtils.randomPastelColor() : color;
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
            tabGroupService.updateTabGroup(model,
                    () -> System.out.println("Tab group synced: " + name + " đã được đồng bộ server"),
                    () -> System.err.println("Lỗi đồng bộ nhóm tab: " + name)
            );
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
                color = ColorUtils.randomPastelColor();

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
                    tabGroupService.deleteTabGroup(serverId,
                            () -> System.out.println("Group đã bị xóa hoàn toàn trên server (do ungroup)"),
                            () -> System.err.println("Lỗi xóa nhóm tab trên server")
                    );
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
                    tabGroupService.deleteTabGroup(serverId,
                            () -> System.out.println("Group đã bị xóa hoàn toàn trên server (do ungroup)"),
                            () -> System.err.println("Lỗi xóa nhóm tab trên server")
                    );
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

        reloadButton.setVisible(true);
        reloadButton.setManaged(true);
        stopButton.setVisible(false);
        stopButton.setManaged(false);

        findBar.setVisible(false);
        findBar.setManaged(false);
    }

    /**
     * Tải toàn bộ dữ liệu từ server: bookmark + tab groups
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
            // Ctrl + W để tắt tab hiện tại
            Tab tab = getCurrentTab();
            if (tab != null && !headerToGroup.containsKey(tab)) {
                closeTab(tab);
            }
            event.consume();

        } else if (event.getCode() == KeyCode.T && event.isControlDown()) {
            // Ctrl + T để mở tab mới
            addNewTab("newtab");
            event.consume();

        } else if (event.getCode() == KeyCode.TAB && event.isControlDown()) {
            // Ctrl + Tab để switch tab
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
            // Ctrl + E để focus vào url field
            Platform.runLater(() -> {
                urlField.requestFocus();
                urlField.selectAll();
            });
            event.consume();

        } else if (event.getCode() == KeyCode.F && event.isControlDown()) {
            // Ctrl + F để tìm kiếm trong web
            openFindBar();
            event.consume();

        } else if (event.getCode() == KeyCode.F5) {
            // F5 để reload trang
            reloadCurrentTab();
            event.consume();

        } else if (event.getCode() == KeyCode.ESCAPE) {
            // Esc để tắt tìm kiếm (Ctrl + F)
            stopCurrentTab();
            closeFindBar();
            event.consume();
        } else if (event.getCode() == KeyCode.H) {
            // Ctrl + H để mở lịch sử web
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

        // Nếu chưa đăng nhập → chỉ mở tab "New Tab" đơn giản
        if (userId == null) {
            Platform.runLater(() -> addNewTab("newtab"));
            return;
        }

        tabGroupService.getTabGroups(
                groups -> Platform.runLater(() -> {
                    tabGroups.clear();
                    looseTabs.clear();
                    headerToGroup.clear();
                    tabToGroup.clear();
                    serverIdToGroup.clear();

                    boolean hasAnyTab = false;

                    if (groups != null && !groups.isEmpty()) {
                        for (ServerTabGroup sg : groups) {
                            if (sg.getName() == null) continue;

                            Color groupColor = sg.getColor() != null ? Color.web(sg.getColor()) : ColorUtils.randomPastelColor();
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
                    }

                    rebuildTabOrder();

                    if (!hasAnyTab && looseTabs.isEmpty() && tabGroups.isEmpty()) {
                        addNewTab("newtab");
                    }
                }),
                () -> Platform.runLater(() -> addNewTab("newtab"))
        );
    }

    /**
     * Tạo WebView + load URL cho một tab
     */
    private void createWebViewAndLoad(Tab tab, String url) {
        // Tạo WebView
        WebView webView = new WebView();
        WebEngine engine = webView.getEngine();

        // Gắn WebView vào nội dung của Tab
        tab.setContent(webView);

        // Cập nhật ô địa chỉ ngay lập tức (người dùng thấy URL đang mở)
        urlField.setText(url);

        // Listener: Khi trang đang tải → ẩn nút Reload, hiện nút Stop (X)
        engine.getLoadWorker().runningProperty().addListener((obs, old, loading) -> {
            reloadButton.setVisible(!loading);
            reloadButton.setManaged(!loading);
            stopButton.setVisible(loading);
            stopButton.setManaged(loading);
        });

        // Bắt đầu tải trang
        engine.load(url);

        // Listener: Khi trang tải xong (SUCCEEDED)
        engine.getLoadWorker().stateProperty().addListener((obs, old, state) -> {
            if (state == Worker.State.SUCCEEDED) {

                // Cập nhật tiêu đề tab
                String title = engine.getTitle();
                if (title != null && !title.isEmpty()) {
                    tab.setText(title.length() > 50 ? title.substring(0, 47) + "..." : title);
                }

                // Cập nhật favicon
                FaviconHelper.loadFavicon(url, tab);;
            }
        });
    }

    /**
     * Thêm một nút bookmark vào bookmark bar
     */
    private void addBookmarkButton(String name, String url, Long id) {
        Button bmButton = new Button(name);

        // Click vào nút → mở URL trong tab hiện tại
        bmButton.setOnAction(_ -> loadUrl(getCurrentTab(), url, true));

        // Gắn ID từ server vào nút
        bmButton.setUserData(id);

        ContextMenu menu = new ContextMenu();

        MenuItem editItem = new MenuItem("Edit");
        editItem.setOnAction(e -> {
            // Dialog 1: Sửa tên
            TextInputDialog nameDialog = new TextInputDialog(name);
            nameDialog.setTitle("Edit Bookmark");
            nameDialog.setHeaderText("Chỉnh sửa tên bookmark");
            nameDialog.setContentText("Tên:");

            nameDialog.showAndWait().ifPresent(updatedName -> {
                // Dialog 2: Sửa URL
                TextInputDialog urlDialog = new TextInputDialog(url);
                urlDialog.setTitle("Edit Bookmark");
                urlDialog.setHeaderText("Chỉnh sửa link bookmark");
                urlDialog.setContentText("URL:");

                urlDialog.showAndWait().ifPresent(updatedUrl -> {
                    // Cập nhật giao diện nút ngay lập tức
                    bmButton.setText(updatedName);
                    bmButton.setOnAction(ev -> loadUrl(getCurrentTab(), updatedUrl, true));

                    // Cập nhật trên server
                    Long buttonId = (Long) bmButton.getUserData();
                    if (buttonId != null) {
                        bookmarkService.updateBookmark(id, updatedName, updatedUrl,
                                this::loadBookmarksFromServer,
                                () -> showAlert(Alert.AlertType.WARNING, "Lỗi", "Không thể cập nhật bookmark!")
                        );
                    } else {
                        System.out.println("⚠️ Bookmark chưa có ID — bỏ qua cập nhật server");
                    }
                });
            });
        });

        MenuItem deleteItem = new MenuItem("Delete");
        deleteItem.setOnAction(e -> {
            // Xóa nút khỏi giao diện
            bookmarkBar.getItems().remove(bmButton);

            // Xóa trên server
            Long buttonId = (Long) bmButton.getUserData();
            if (buttonId != null) {
                bookmarkService.deleteBookmark(id, this::loadBookmarksFromServer,
                        () -> showAlert(Alert.AlertType.WARNING, "Lỗi", "Không thể xóa bookmark!")
                );
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
        // Tạo tab mới với tiêu đề tạm thời
        Tab tab = new Tab("New Tab");

        // Gắn menu chuột phải
        setupTabContextMenu(tab);

        // Khởi tạo hàm xử lí tắt cho tab
        setupTabCloseHandler(tab);

        // Khởi tạo lịch sử duyệt web riêng cho tab này
        history.put(tab, new ArrayList<>());
        historyIndex.put(tab, -1);  // -1 = chưa có trang nào

        // Kiểm tra: mở trang "New Tab" hay mở URL thật?
        if (url.equals("newtab")) {
            try {
                // Load file FXML của trang New Tab
                FXMLLoader loader = new FXMLLoader(WebBrowserApplication.class.getResource("new-tab.fxml"));
                Parent newTabRoot = loader.load();

                // Gắn controller để khi người dùng gõ URL
                NewTabController newTabController = loader.getController();
                newTabController.setOnUrlOpen(requestedUrl -> loadUrl(tab, requestedUrl, true));

                tab.setContent(newTabRoot);

                newTabRoot.setFocusTraversable(false);

                TextField searchField = (TextField) newTabRoot.lookup("#searchField");
                if (searchField != null) {
                    searchField.setFocusTraversable(false);  // Tắt focus lúc ban đầu

                    // Khi click vào thanh tìm kiếm → bật focus + chọn hết
                    searchField.setOnMouseClicked(e -> {
                        Platform.runLater(() -> {
                            searchField.setFocusTraversable(true);
                            searchField.requestFocus();
                            searchField.selectAll();
                        });
                    });

                    // Khi rời khỏi thanh tìm kiếm → tắt focus
                    searchField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
                        if (!isNowFocused) {
                            searchField.setFocusTraversable(false);
                        }
                    });

                    // Nhấn Enter → mở URL
                    searchField.setOnAction(_ -> loadUrl(tab, searchField.getText(), true));
                }
            } catch (IOException ex) {
                tab.setContent(new Label("Error loading new tab page"));
                ex.printStackTrace();
            }
        } else {
            loadUrl(tab, url, true);
        }

        // Thêm tab vào danh sách tab đơn
        looseTabs.add(tab);

        // Cập nhật giao diện
        rebuildTabOrder();

        // Chọn tab vừa tạo
        tabPane.getSelectionModel().select(tab);
    }

    /**
     * Load URL vào tab hiện tại (chuẩn hóa URL trước)
     */
    private void loadUrl(Tab tab, String input, boolean addToHistory) {
        if (tab == null || input == null || input.isEmpty()) return;

        // Chuẩn hoá lại url trước khi load
        String url = normalizeUrl(input);

        // Tạo WebView mới
        WebView webView = createWebView(tab, url);

        // Đảm bảo tab có ServerTab để đồng bộ server
        ServerTab serverTab = ensureServerTab(tab, url);

        // Tải trang
        webView.getEngine().load(url);

        // Gắn listener: khi tải xong → cập nhật title, favicon, lịch sử, đồng bộ server
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

        // Kiểm tra data của tab đã là ServerTab hay chưa
        if (data instanceof ServerTab st) {
            serverTab = st; // Nếu có rồi thì tái sử dụng
        } else {
            // Chưa có → tạo mới
            serverTab = new ServerTab();

            // Nếu trước đó chỉ lưu url (string)
            if (data instanceof String oldUrl) serverTab.setUrl(oldUrl); // set url mới vào ServerTab vừa tạo

            // Set lại data cho tab
            tab.setUserData(serverTab);
        }

        // Cập nhật thông tin mới nhất
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
                // Cập nhật tiêu đề tab
                String title = engine.getTitle();
                if (title != null) {
                    tab.setText(title.length() > 50 ? title.substring(0, 47) + "..." : title);
                    serverTab.setTitle(title);  // Để đồng bộ server sau này
                }

                // Cập nhật favicon
                FaviconHelper.loadFavicon(engine.getLocation(), tab);

                // Đồng bộ tab lên server nếu nó nằm trong một nhóm nào
                syncTabIfInGroup(tab);

                // Lưu vào lịch sử duyệt web riêng của tab
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

        // Kiểm tra URL hợp lệ – không cho thêm newtab, about:blank
        if (currentUrl.isEmpty() || currentUrl.equals("about:blank") || currentUrl.startsWith("newtab")) {
            showAlert(Alert.AlertType.WARNING, "Không thể thêm bookmark", "URL không hợp lệ hoặc trống!");
            return;
        }

        Tab currentTab = getCurrentTab();

        // Lấy tên mặc định: ưu tiên tiêu đề tab → nếu không có thì trích từ URL
        String defaultName = currentTab != null && currentTab.getText() != null
                ? currentTab.getText() : extractTitleFromUrl(currentUrl);

        // Hiện dialog để người dùng đặt tên bookmark
        TextInputDialog dialog = new TextInputDialog(defaultName);
        dialog.setTitle("Thêm Bookmark");
        dialog.setHeaderText("Nhập tên bookmark");
        dialog.setContentText("Tên:");

        dialog.showAndWait().ifPresent(name -> {
            if (name == null || name.trim().isEmpty()) {
                showAlert(Alert.AlertType.WARNING, "Lỗi", "Tên bookmark không được để trống!");
                return;
            }

            // Gửi lên server → thêm bookmark
            bookmarkService.addBookmark(
                    name.trim(),
                    currentUrl,
                    () -> Platform.runLater(this::loadBookmarksFromServer),
                    () -> showAlert(Alert.AlertType.WARNING, "Lỗi", "Không thể thêm bookmark!")
            );

        });
    }

    /**
     * Trích xuất tên mặc định từ URL nếu không có title
     */
    private String extractTitleFromUrl(String url) {
        try {
            String host = new java.net.URI(url).getHost();
            if (host != null) {
                host = host.replaceFirst("^www\\.", ""); // Bỏ www.
                return host.substring(0, 1).toUpperCase() + host.substring(1); // Viết hoa chữ cái đầu
            }
        } catch (Exception ignored) {
        }
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

        // Đảm bảo mỗi tab có lịch sử riêng (nếu chưa có)
        history.putIfAbsent(tab, new ArrayList<>());
        historyIndex.putIfAbsent(tab, -1);

        List<String> urls = history.get(tab);
        int idx = historyIndex.get(tab);

        // Nếu đang ở giữa lịch sử (đã nhấn Back) → xóa các trang phía sau
        if (idx < urls.size() - 1) {
            urls = new ArrayList<>(urls.subList(0, idx + 1));
        }

        // Thêm URL mới vào cuối danh sách
        urls.add(url);

        // Cập nhật lại danh sách và con trỏ
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
        bookmarkService.getBookmarks(
                bookmarks -> Platform.runLater(() -> {
                    bookmarkBar.getItems().clear();
                    bookmarkIds.clear();
                    bookmarks.forEach(b ->
                            addBookmarkButton(b.getTitle(), b.getUrl(), b.getId())
                    );
                }),
                () -> System.out.println("Không tải được bookmark (có thể offline)")
        );
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

        // Kiểm tra tab hiện tại có phải là WebView không (tránh lỗi khi ở New Tab page)
        if (!(currentTab.getContent() instanceof WebView webView)) return;

        WebEngine webEngine = webView.getEngine();

        // JavaScript gọi hàm window.find() – hàm có sẵn trong mọi trình duyệt
        String js = """
                if (window.find) {
                    window.find('%s', false, %b, true, false, false, false);
                }
                """.formatted(query.replace("'", "\\'"), !forward);

        Platform.runLater(() -> {
            try {
                webEngine.executeScript(js);
            } catch (Exception e) {
                System.err.println("Lỗi khi tìm trong trang: " + e.getMessage());
            }
        });
    }

    /**
     * Cập nhật title và favicon khi duyệt lịch sử back/forward
     */
    private void updateTabTitleAndIcon(Tab tab, WebEngine engine, String url) {
        engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                String title = engine.getTitle() != null ? engine.getTitle() : url;

                // Xử lí khi người dùng tìm kiếm trên google
                if (url.contains("https://www.google.com/search?q=")) {
                    try {
                        String query = url.substring(url.indexOf("q=") + 2);
                        if (query.contains("&")) {
                            query = query.substring(0, query.indexOf("&"));
                        }
                        query = URLDecoder.decode(query, "UTF-8");

                        tab.setText(query + " - Tìm kiếm trên Google");
                    } catch (Exception e) {
                        tab.setText("Tìm kiếm trên Google");
                    }
                } else {
                    tab.setText(title);
                }

                // Cập nhật favicon
                FaviconHelper.loadFavicon(url, tab);
            }
        });
    }

    /**
     * Tái xây dựng lại thứ tự tab trong TabPane (rất quan trọng khi collapse/expand nhóm)
     */
    private void rebuildTabOrder() {
        Tab selected = getCurrentTab();

        // Tạm khóa TabPane để tránh lỗi khi đang thao tác
        tabPane.setDisable(true);
        tabPane.getTabs().clear();

        // Duyệt từng nhóm → thêm header + tab con (nếu đang mở rộng)
        for (GroupHeader group : tabGroups) {
            tabPane.getTabs().add(group.headerTab);
            if (!group.collapsed) {
                tabPane.getTabs().addAll(group.tabs);  // Chỉ thêm tab con nếu nhóm đang mở rộng
            }
        }

        // Thêm các tab lẻ ở cuối
        tabPane.getTabs().addAll(looseTabs);

        // Cập nhật danh sách tabOrder
        tabOrder.clear();
        tabOrder.addAll(tabPane.getTabs().stream()
                .filter(t -> !headerToGroup.containsKey(t))  // Loại bỏ header giả
                .toList());

        // Khôi phục tab đang chọn
        if (selected != null && tabPane.getTabs().contains(selected)) {
            tabPane.getSelectionModel().select(selected);
        } else if (!tabPane.getTabs().isEmpty()) {
            tabPane.getSelectionModel().select(tabPane.getTabs().size() - 1); // Chọn tab cuối
        }

        // Mở khóa lại TabPane
        Platform.runLater(() -> {
            tabPane.setDisable(false);
        });
    }

    /**
     * Thiết lập menu chuột phải cho tab thường: tạo nhóm mới, thêm vào nhóm, bỏ nhóm...
     */
    private void setupTabContextMenu(Tab tab) {
        ContextMenu cm = new ContextMenu();

        // Tạo nhóm mới từ tab này
        MenuItem newGroup = new MenuItem("New Tab Group");
        newGroup.setOnAction(e -> createTabGroup(tab));

        // Menu "Add to Group" – động theo danh sách nhóm hiện có!
        Menu addToMenu = new Menu("Add to Group");
        cm.setOnShowing(e -> {
            addToMenu.getItems().clear();
            for (GroupHeader g : tabGroups) {
                MenuItem mi = new MenuItem(g.name);
                mi.setOnAction(ev -> {
                    // Xóa tab khỏi nhóm cũ (nếu có) hoặc khỏi looseTabs
                    GroupHeader oldGroup = tabToGroup.get(tab);
                    if (oldGroup != null) oldGroup.removeTab(tab);
                    else looseTabs.remove(tab);

                    // Đảm bảo có ServerTab để đồng bộ server
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

                    // Thêm vào nhóm mới
                    g.addTab(tab, modelTab);
                    rebuildTabOrder();
                });
                addToMenu.getItems().add(mi);
            }
        });

        // Bỏ tab ra khỏi nhóm hiện tại
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
                // Tạo nhóm tạm trên client trước
                ServerTabGroup tempGroup = new ServerTabGroup();
                tempGroup.setName(name.trim());
                tempGroup.setColor("#" + Integer.toHexString(ColorUtils.randomPastelColor().hashCode() & 0xFFFFFF));

                // Đảm bảo tab có ServerTab để đồng bộ
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

                // Gửi lên server → nhận về nhóm thật có ID
                tabGroupService.addTabGroup(tempGroup,
                        createdGroup -> Platform.runLater(() -> {
                            // Tạo GroupHeader thật trên giao diện
                            GroupHeader group = new GroupHeader(createdGroup.getName(),
                                    Color.web(createdGroup.getColor()), createdGroup.getId());

                            // Xóa tab khỏi vị trí cũ (nhóm cũ hoặc looseTabs)
                            GroupHeader old = tabToGroup.get(tab);
                            if (old != null) old.removeTab(tab);
                            else looseTabs.remove(tab);

                            // Thêm tab vào nhóm mới + lưu ánh xạ server
                            group.addTab(tab, modelTab);
                            serverIdToGroup.put(createdGroup.getId(), group);

                            // Cập nhật giao diện + giữ focus
                            rebuildTabOrder();
                            tabPane.getSelectionModel().select(tab);
                        }), () -> showAlert(Alert.AlertType.ERROR, "Lỗi", "Không thể tạo nhóm tab mới!")
                );
            }
        });
    }

    /**
     * Xử lý đóng tab (Ctrl+W hoặc nút X) – có tính đến nhóm
     */
    private void setupTabCloseHandler(Tab tab) {
        // Header nhóm không được đóng → tắt nút X
        if (headerToGroup.containsKey(tab)) {
            tab.setClosable(false);
            return;
        }

        // Xử lý sự kiện khi người dùng nhấn X hoặc Ctrl+W
        tab.setOnCloseRequest(e -> {
            GroupHeader group = tabToGroup.get(tab);

            // TRƯỜNG HỢP 1: Tab đang nằm trong nhóm
            if (group != null) {
                group.tabs.remove(tab);           // Xóa khỏi danh sách tab con
                tabToGroup.remove(tab);           // Xóa ánh xạ
                tab.setStyle(null);               // Bỏ màu nhóm

                // Nếu nhóm còn 0 tab → Xoá nhóm
                if (group.tabs.isEmpty()) {
                    headerToGroup.remove(group.headerTab);
                    tabGroups.remove(group);
                }

                // Cập nhật lại header
                updateHeaderGraphicIfNeeded(group);
            }
            // TRƯỜNG HỢP 2: Tab lẻ
            else {
                looseTabs.remove(tab);
            }

            // Dọn dẹp lịch sử duyệt web của tab này
            history.remove(tab);
            historyIndex.remove(tab);

            // Dọn dẹp ánh xạ nhóm
            tabToGroup.remove(tab);

            // Tái xây dựng giao diện + kiểm tra thoát ứng dụng
            rebuildTabOrder();
            checkAndExitIfNoTabs();

            // NGĂN hành vi đóng mặc định của JavaFX → tự xử lý hoàn toàn
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
            group.updateHeaderGraphic(); // Cập nhật số lượng tab trên header

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
