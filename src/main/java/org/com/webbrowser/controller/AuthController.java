package org.com.webbrowser.controller;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.com.webbrowser.WebBrowserApplication;
import org.com.webbrowser.model.User;
import org.com.webbrowser.model.ApiResponse;
import org.com.webbrowser.service.AuthService;
import org.com.webbrowser.service.UserStorageService;
import org.com.webbrowser.session.UserSession;
import org.com.webbrowser.utils.EncryptionUtils;
import org.com.webbrowser.utils.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.List;

/**
 * Controller này dùng để login hoặc register
 */
public class AuthController {

    @FXML
    private VBox userListPane;
    @FXML
    private FlowPane userList;
    @FXML
    private Button loginButton;

    @FXML
    private VBox formPane;
    @FXML
    private Label formTitle;
    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private PasswordField confirmPasswordField;
    @FXML
    private Label messageLabel;
    @FXML
    private Button submitButton;
    @FXML
    private Button backButton;

    private final String API_BASE = "http://localhost:8080/api/users";
    private boolean isRegisterMode = false;

    private final AuthService authService = new AuthService();

    @FXML
    private void initialize() {
        loadUsers();
        loginButton.setOnAction(e -> openLoginForm());
        backButton.setOnAction(e -> showUserList());
        submitButton.setOnAction(e -> handleFormSubmit());
    }

    /**
     * Hàm này load toàn bộ user đang có trong thư mục data/users/<username>.json
     */
    private void loadUsers() {

        userList.getChildren().clear();
        List<User> users = UserStorageService.loadAllUsers();

        /**
         * Render các user ra ngoài dưới dạng thẻ
         */
        for (User user : users) {
            /**
             * Tạo user card để thực hiện khi click vào thì tự động login với data có sẵn
             */
            VBox card = createUserCard(user);
            userList.getChildren().add(card);
        }

        VBox addCard = new VBox();
        addCard.setAlignment(javafx.geometry.Pos.CENTER);
        addCard.setPrefSize(100, 100);
        addCard.setStyle("""
                    -fx-background-color: white;
                    -fx-background-radius: 10;
                    -fx-border-color: #ddd;
                    -fx-border-width: 1;
                    -fx-border-radius: 10;
                    -fx-cursor: hand;
                    -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.05), 3, 0, 0, 1);
                """);

        /**
         * Thẻ dấu + dùng để thêm user nếu chưa có account
         */
        Label plus = new Label("+");
        plus.setStyle("-fx-font-size: 36;");
        addCard.getChildren().add(plus);
        addCard.setOnMouseClicked(e -> openRegisterForm());
        userList.getChildren().add(addCard);
    }

    private VBox createUserCard(User user) {
        VBox card = new VBox();
        card.setAlignment(javafx.geometry.Pos.CENTER);
        card.setSpacing(5);
        card.setPrefSize(100, 100);
        card.setStyle("""
                    -fx-background-color: white;
                    -fx-background-radius: 10;
                    -fx-border-color: #ddd;
                    -fx-border-width: 1;
                    -fx-border-radius: 10;
                    -fx-cursor: hand;
                    -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.05), 3, 0, 0, 1);
                """);

        Label name = new Label(user.getUsername());
        name.setStyle("-fx-font-size: 14;");
        card.getChildren().add(name);

        card.setOnMouseClicked(e -> loginWithLocalUser(user));
        return card;
    }

    /**
     * Hàm này thực hiện giải mã từ file trong data/users/<username>.json
     * Data trong đó được mã hoá 2 lần:
     * - Lần 1 mã hoá mật khẩu
     * - Lần 2 mã hoá cả mật khẩu đã mã hoá với tên đăng nhập
     */
    private void loginWithLocalUser(User user) {
        try {
            // Giải mã
            String realPassword = EncryptionUtils.decrypt(user.getPassword());

            authService.login(
                    user.getUsername(),
                    realPassword,
                    userFromServer -> {
                        // Đăng nhập thành công → lưu session vào trình duyệt
                        UserSession.getInstance().setUser(userFromServer);
                        Toast.show((Stage) userList.getScene().getWindow(), "Đăng nhập thành công!", 1500, true);
                        openBrowser();
                    },
                    () -> Toast.show((Stage) userList.getScene().getWindow(), "Sai mật khẩu hoặc tài khoản không tồn tại!", 1500, false)
            );
        } catch (Exception e) {
            Toast.show((Stage) userList.getScene().getWindow(), "Lỗi kết nối server!", 1500, false);
        }
    }

    /**
     * Form đăng nhập tài khoản
     */
    private void openLoginForm() {
        isRegisterMode = false;
        formTitle.setText("Đăng nhập");
        confirmPasswordField.setVisible(false);
        formPane.setVisible(true);
        userListPane.setVisible(false);
        messageLabel.setText("");
    }

    /**
     * Form đăng kí tài khoản
     */
    private void openRegisterForm() {
        isRegisterMode = true;
        formTitle.setText("Đăng ký tài khoản");
        confirmPasswordField.setVisible(true);
        formPane.setVisible(true);
        userListPane.setVisible(false);
        messageLabel.setText("");
    }

    /**
     * Thực hiện việc thay đổi UI
     */
    private void showUserList() {
        formPane.setVisible(false);
        userListPane.setVisible(true);
        usernameField.clear();
        passwordField.clear();
        confirmPasswordField.clear();
        messageLabel.setText("");
        loadUsers();
    }

    /**
     * Xử lí form kể cả đăng nhập và đăng kí:
     * - Nhận biết đăng nhập hay đăng kí dựa vào isRegisterMode
     * - Nếu là đăng kí thì kiểm tra thêm trường confirmPassword
     */
    private void handleFormSubmit() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            messageLabel.setText("Vui lòng nhập đầy đủ thông tin!");
            return;
        }

        if (isRegisterMode) {
            String confirm = confirmPasswordField.getText().trim();
            if (!password.equals(confirm)) {
                messageLabel.setText("Mật khẩu xác nhận không khớp!");
                return;
            }
            registerUser(username, password);
        } else {
            loginUser(username, password);
        }
    }

    /**
     * Hàm gọi API đăng nhập với data được nhập từ form, không phải data được load ra từ file
     */
    private void loginUser(String username, String password) {
        authService.login(
                username,
                password,
                user -> {
                    // Lưu local để lần sau đăng nhập nhanh
                    user.setPassword(EncryptionUtils.encrypt(password));
                    UserStorageService.saveUser(user); // Lưu user vào file local để sử dụng chức năng đăng nhập nhanh

                    UserSession.getInstance().setUser(user); // Lưu user vào session
                    Toast.show((Stage) formPane.getScene().getWindow(), "Đăng nhập thành công!", 1500, true);
                    openBrowser();
                },
                () -> messageLabel.setText("Sai tài khoản hoặc mật khẩu!")
        );
    }

    /**
     * Hàm gọi API đăng kí với data được nhập từ form
     */
    private void registerUser(String username, String password) {
        authService.register(
                username,
                password,
                user -> {
                    // Lưu local luôn
                    user.setPassword(EncryptionUtils.encrypt(password));
                    UserStorageService.saveUser(user);

                    Toast.show((Stage) formPane.getScene().getWindow(), "Đăng ký thành công!", 1500, true);
                    showUserList();
                },
                () -> messageLabel.setText("Tên đăng nhập đã tồn tại!")
        );
    }

    /**
     * Hàm helper sendRequest nhằm tiên xử lí và hậu xử lí payload và response trả về từ server
     * Mapping các trường cần lấy để phục vụ hàm login và register ở trên
     */
    private ApiResponse<User> sendRequest(String apiUrl, String username, String password) throws IOException {
        java.net.URL url = new java.net.URL(apiUrl);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        // Chuẩn bị payload để gửi
        String json = String.format("{\"username\":\"%s\", \"password\":\"%s\"}", username, password);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes());
        }

        // Đọc response code và xử lí response rồi mapping qua gson
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        conn.getResponseCode() == 200 ? conn.getInputStream() : conn.getErrorStream()
                )
        );

        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }

        Gson gson = new Gson();
        return gson.fromJson(response.toString(),
                TypeToken.getParameterized(
                        org.com.webbrowser.model.ApiResponse.class,
                        org.com.webbrowser.model.User.class
                ).getType());
    }

    private void openBrowser() {
        try {
            Stage stage = (Stage) userList.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(WebBrowserApplication.class.getResource("web-browser.fxml"));
            Scene scene = new Scene(loader.load(), 1200, 800);
            stage.setScene(scene);
            stage.setTitle("Modular JavaFX Web Browser");
            stage.centerOnScreen();
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
