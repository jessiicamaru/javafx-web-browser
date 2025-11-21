package org.com.webbrowser.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import org.com.webbrowser.model.Shortcut;
import org.com.webbrowser.service.ShortcutService;
import org.com.webbrowser.session.UserSession;
import org.com.webbrowser.utils.UrlNormalizer;

import java.util.List;
import java.util.function.Consumer;

/**
 * Controller này xử lí toàn bộ giao diện và tương tác trên trang New Tab
 * - Hiển thị ô tìm kiếm (có thể tìm bằng Google hoặc nhập URL trực tiếp)
 * - Hiển thị danh sách các Shortcut (lối tắt) của người dùng hiện tại
 * - Cho phép thêm, sửa, xóa shortcut thông qua menu chuột phải hoặc nút "+"
 */
public class NewTabController {
    @FXML
    private TextField searchField;
    @FXML
    private FlowPane shortcutContainer;

    /**
     * Callback để thông báo cho WebBrowserController mở một URL mới
     */
    private Consumer<String> onUrlOpen;

    /**
     * Service xử lý gọi API từ server
     */
    private final ShortcutService shortcutService = new ShortcutService();

    /**
     * Đăng ký callback khi người dùng muốn mở một URL (từ ô tìm kiếm hoặc click shortcut)
     *
     * Giải thích bản chất:
     * - Bản chất việc phải đăng kí callback để thông báo cho webbrowser rằng đang mở một url mới
     * bởi vì ta load url theo nhiều cách: history, bookmark, shortcut,... vậy nên phải có một callback
     * làm trung gian thực hiện việc nhận diện
     */
    public void setOnUrlOpen(Consumer<String> callback) {
        this.onUrlOpen = callback;
    }

    /**
     * Khởi tạo giao diện New Tab:
     * - Load danh sách shortcut của user hiện tại
     * - Xử lý sự kiện Enter trên ô tìm kiếm
     * - Đảm bảo ô tìm kiếm có thể nhận focus khi click vào
     */
    @FXML
    public void initialize() {
        Long userId = Long.valueOf(UserSession.getInstance().getUserId());
        shortcutService.getShortcuts(userId, this::renderShortcuts);

        searchField.setOnAction(e -> handleSearch());

        searchField.setFocusTraversable(false);

        searchField.setOnMouseClicked(e -> {
            Platform.runLater(() -> searchField.requestFocus());
        });
    }

    /**
     * Xử lý nội dung nhập trong ô tìm kiếm:
     * - Nếu là URL hợp lệ → mở trực tiếp
     * - Nếu không → tìm kiếm trên Google
     */
    private void handleSearch() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) return;

        // Chuẩn hóa khoảng trắng và normalize URL
        query = query.replaceAll("\\s+", " ").trim().replace(" ", "+");
        query = UrlNormalizer.normalizeUrl(query);
        String searchUrl = "https://www.google.com/search?q=" + query;
        onUrlOpen.accept(searchUrl);
    }

    /**
     * Render lại toàn bộ danh sách shortcut ra giao diện
     * Được gọi lại mỗi khi có thay đổi (thêm/sửa/xóa)
     */
    private void renderShortcuts(List<Shortcut> shortcuts) {
        shortcutContainer.getChildren().clear();

        for (Shortcut sc : shortcuts) {
            VBox box = createShortcutButton(sc);
            shortcutContainer.getChildren().add(box);
        }

        // Nút thêm mới shortcut
        Button addBtn = new Button("+");
        addBtn.getStyleClass().add("shortcut-button");
        addBtn.setOnAction(e -> openAddShortcutDialog());
        shortcutContainer.getChildren().add(addBtn);
    }

    /**
     * Tạo một nút shortcut (với avatar, tên, menu chuột phải)
     */
    private VBox createShortcutButton(Shortcut sc) {
        VBox container = new VBox(8);
        container.setAlignment(Pos.CENTER);

        StackPane avatar = createAvatar(sc.getName(), sc.getColor());
        Label nameLabel = new Label(sc.getName());
        nameLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #333;");

        VBox clickable = new VBox(avatar, nameLabel);
        clickable.setAlignment(Pos.CENTER);
        // Click trái để mở URL
        clickable.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                onUrlOpen.accept(sc.getUrl());
            }
        });

        // Menu chuột phải: Chỉnh sửa / Xóa
        ContextMenu menu = new ContextMenu();

        MenuItem editItem = new MenuItem("Chỉnh sửa");
        editItem.setOnAction(ev -> openEditShortcutDialog(sc));

        MenuItem deleteItem = new MenuItem("Xóa");
        deleteItem.setOnAction(ev -> {
            shortcutService.deleteShortcut(sc.getId(), () ->
                    shortcutService.getShortcuts(Long.valueOf(UserSession.getInstance().getUserId()), this::renderShortcuts)
            );
        });

        menu.getItems().addAll(editItem, deleteItem);
        clickable.setOnContextMenuRequested(ev -> menu.show(clickable, ev.getScreenX(), ev.getScreenY()));

        container.getChildren().add(clickable);
        return container;
    }

    /**
     * Mở dialog để thêm shortcut mới
     */
    private void openAddShortcutDialog() {
        Dialog<Shortcut> dialog = new Dialog<>();
        dialog.setTitle("Thêm Shortcut");

        Label nameLabel = new Label("Tên:");
        Label urlLabel = new Label("URL:");
        TextField nameField = new TextField();
        TextField urlField = new TextField("https://");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.addRow(0, nameLabel, nameField);
        grid.addRow(1, urlLabel, urlField);
        dialog.getDialogPane().setContent(grid);

        ButtonType addButtonType = new ButtonType("Thêm", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == addButtonType) {
                Shortcut sc = new Shortcut();
                sc.setName(nameField.getText().trim());
                sc.setUrl(urlField.getText().trim());
                sc.setColor(randomPastelHex());
                return sc;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(sc -> {
            shortcutService.addShortcut(sc.getName(), sc.getUrl(), sc.getColor(), () ->
                    shortcutService.getShortcuts(Long.valueOf(UserSession.getInstance().getUserId()), this::renderShortcuts)
            );
        });
    }

    /**
     * Tạo avatar tròn với ký tự đầu tiên của tên và màu nền pastel
     */
    private StackPane createAvatar(String name, String colorHex) {
        char firstChar = name != null && !name.isEmpty() ? name.toUpperCase().charAt(0) : '?';
        Color color = (colorHex != null && !colorHex.isEmpty()) ? Color.web(colorHex) : randomPastelColor();
        Circle circle = new Circle(25);
        circle.setFill(color);
        Text text = new Text(String.valueOf(firstChar));
        text.setFill(Color.WHITE);
        text.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        return new StackPane(circle, text);
    }

    /**
     * Sinh màu pastel ngẫu nhiên và trả về dưới dạng hex string (#RRGGBB)
     */
    private String randomPastelHex() {
        Color c = randomPastelColor();
        return String.format("#%02X%02X%02X",
                (int) (c.getRed() * 255),
                (int) (c.getGreen() * 255),
                (int) (c.getBlue() * 255));
    }

    /**
     * Sinh một màu pastel ngẫu nhiên (độ sáng cao, nhẹ nhàng)
     */
    private Color randomPastelColor() {
        java.util.Random rand = new java.util.Random();
        double r = (rand.nextDouble() + 1) / 2;
        double g = (rand.nextDouble() + 1) / 2;
        double b = (rand.nextDouble() + 1) / 2;
        return Color.color(r, g, b);
    }

    /**
     * Mở dialog chỉnh sửa shortcut hiện có
     */
    private void openEditShortcutDialog(Shortcut sc) {
        Dialog<Shortcut> dialog = new Dialog<>();
        dialog.setTitle("Chỉnh sửa Shortcut");

        Label nameLabel = new Label("Tên:");
        Label urlLabel = new Label("URL:");
        TextField nameField = new TextField(sc.getName());
        TextField urlField = new TextField(sc.getUrl());
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.addRow(0, nameLabel, nameField);
        grid.addRow(1, urlLabel, urlField);

        dialog.getDialogPane().setContent(grid);

        ButtonType saveButtonType = new ButtonType("Lưu", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                sc.setName(nameField.getText().trim());
                sc.setUrl(urlField.getText().trim());
                return sc;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(updated -> {
            shortcutService.updateShortcut(sc.getId(), sc.getName(), sc.getUrl(), new Thread(() -> {
                Long userId = Long.valueOf(UserSession.getInstance().getUserId());
                shortcutService.getShortcuts(userId, this::renderShortcuts);
            }));
        });
    }

}
