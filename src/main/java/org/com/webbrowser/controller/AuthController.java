package org.com.webbrowser.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.com.webbrowser.WebBrowserApplication;
import org.com.webbrowser.model.User;
import org.com.webbrowser.model.ApiResponse;
import org.com.webbrowser.service.UserStorageService;
import org.com.webbrowser.session.UserSession;
import org.com.webbrowser.utils.EncryptionUtils;
import org.com.webbrowser.utils.Toast;

import java.io.IOException;
import java.util.List;

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

    @FXML
    private void initialize() {
        loadUsers();
        loginButton.setOnAction(e -> openLoginForm());
        backButton.setOnAction(e -> showUserList());
        submitButton.setOnAction(e -> handleFormSubmit());
    }

    private void loadUsers() {

        userList.getChildren().clear();
        List<User> users = UserStorageService.loadAllUsers();

        for (User user : users) {
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

    private void loginWithLocalUser(User user) {
        try {
            String realPassword = EncryptionUtils.decrypt(user.getPassword());
            ApiResponse<User> response = sendRequest(API_BASE + "/login", user.getUsername(), realPassword);

            if (response != null && response.getCode() == 1000 && response.getResult() != null) {
                UserSession.getInstance().setUser(response.getResult());
                Toast.show((Stage) userList.getScene().getWindow(), "Đăng nhập thành công!", 1500, true);
                openBrowser();
            } else {
                Toast.show((Stage) userList.getScene().getWindow(), "Sai tài khoản hoặc mật khẩu!", 1500, false);
            }
        } catch (Exception e) {
            Toast.show((Stage) userList.getScene().getWindow(), "Lỗi kết nối server!", 1500, false);
        }
    }

    private void openLoginForm() {
        isRegisterMode = false;
        formTitle.setText("Đăng nhập");
        confirmPasswordField.setVisible(false);
        formPane.setVisible(true);
        userListPane.setVisible(false);
        messageLabel.setText("");
    }

    private void openRegisterForm() {
        isRegisterMode = true;
        formTitle.setText("Đăng ký tài khoản");
        confirmPasswordField.setVisible(true);
        formPane.setVisible(true);
        userListPane.setVisible(false);
        messageLabel.setText("");
    }

    private void showUserList() {
        formPane.setVisible(false);
        userListPane.setVisible(true);
        usernameField.clear();
        passwordField.clear();
        confirmPasswordField.clear();
        messageLabel.setText("");
        loadUsers();
    }

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

    private void loginUser(String username, String password) {
        try {
            ApiResponse<User> response = sendRequest(API_BASE + "/login", username, password);
            if (response != null && response.getCode() == 1000 && response.getResult() != null) {
                User user = response.getResult();
                user.setPassword(EncryptionUtils.encrypt(password));
                UserStorageService.saveUser(user);

                UserSession.getInstance().setUser(user);
                openBrowser();
            } else {
                messageLabel.setText("Sai tài khoản hoặc mật khẩu!");
            }
        } catch (Exception e) {
            messageLabel.setText("Không thể kết nối đến máy chủ!");
        }
    }

    private void registerUser(String username, String password) {
        try {
            ApiResponse<User> response = sendRequest(API_BASE + "/register", username, password);
            if (response != null && response.getCode() == 1000 && response.getResult() != null) {
                User user = response.getResult();
                user.setPassword(EncryptionUtils.encrypt(password));
                UserStorageService.saveUser(user);

                Toast.show((Stage) formPane.getScene().getWindow(), "Đăng ký thành công!", 1500, true);
                showUserList();
            } else {
                messageLabel.setText("Tên người dùng đã tồn tại hoặc lỗi khác!");
            }
        } catch (Exception e) {
            messageLabel.setText("Không thể kết nối đến máy chủ!");
        }
    }

    private ApiResponse<User> sendRequest(String apiUrl, String username, String password) throws IOException {
        java.net.URL url = new java.net.URL(apiUrl);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        String json = String.format("{\"username\":\"%s\", \"password\":\"%s\"}", username, password);
        try (java.io.OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes());
        }

        java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(
                        conn.getResponseCode() == 200 ? conn.getInputStream() : conn.getErrorStream()
                )
        );

        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }

        com.google.gson.Gson gson = new com.google.gson.Gson();
        return gson.fromJson(response.toString(),
                com.google.gson.reflect.TypeToken.getParameterized(
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
