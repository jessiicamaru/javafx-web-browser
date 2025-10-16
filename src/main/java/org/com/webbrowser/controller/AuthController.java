package org.com.webbrowser.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import org.com.webbrowser.WebBrowserApplication;
import org.com.webbrowser.utils.Toast;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.OutputStream;

public class AuthController {

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Button loginButton;

    @FXML
    private Button registerButton;

    @FXML
    private AnchorPane container;

    private final String API_BASE = "http://localhost:8080/api/users";

    @FXML
    private void initialize() {
        loginButton.setOnAction(this::handleLogin);
        registerButton.setOnAction(this::handleRegister);
    }

    private void handleLogin(ActionEvent event) {
        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();
        if (username.isEmpty() || password.isEmpty()) {
            Toast.show((Stage) container.getScene().getWindow(), "Please fill in all fields!", 2000, false);
            return;
        }

        try {
            if (sendRequest(API_BASE + "/login", username, password)) {
                openBrowser();
            } else {
                Toast.show((Stage) container.getScene().getWindow(), "Invalid username or password", 2000, false);
            }
        } catch (Exception e) {
            Toast.show((Stage) container.getScene().getWindow(), "Connection error: " + e.getMessage(), 2000, false);
        }
    }

    private void handleRegister(ActionEvent event) {
        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();
        if (username.isEmpty() || password.isEmpty()) {
            Toast.show((Stage) container.getScene().getWindow(), "Please fill in all fields!", 2000, false);
            return;
        }

        try {
            if (sendRequest(API_BASE + "/register", username, password)) {
                openBrowser();
            } else {
                Toast.show((Stage) container.getScene().getWindow(), "Username already exists or registration failed", 2000, false);
            }
        } catch (Exception e) {
            Toast.show((Stage) container.getScene().getWindow(), "Connection error: " + e.getMessage(), 2000, false);
        }
    }

    private boolean sendRequest(String apiUrl, String username, String password) throws IOException {
        URL url = new URL(apiUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        String json = String.format("{\"username\":\"%s\", \"password\":\"%s\"}", username, password);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes());
        }

        return conn.getResponseCode() == 200;
    }

    private void openBrowser() throws IOException {
        Stage stage = (Stage) container.getScene().getWindow();
        Toast.show(stage, "Login successful!", 2000, true);
        FXMLLoader loader = new FXMLLoader(WebBrowserApplication.class.getResource("web-browser.fxml"));
        Scene scene = new Scene(loader.load(), 1200, 800);
        stage.setScene(scene);
        stage.setTitle("Modular JavaFX Web Browser");
        stage.centerOnScreen();
        stage.show();
    }
}
