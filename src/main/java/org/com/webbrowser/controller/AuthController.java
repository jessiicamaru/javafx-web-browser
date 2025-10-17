package org.com.webbrowser.controller;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import org.com.webbrowser.WebBrowserApplication;
import org.com.webbrowser.model.ApiResponse;
import org.com.webbrowser.model.User;
import org.com.webbrowser.session.UserSession;
import org.com.webbrowser.utils.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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
            ApiResponse<User> response = sendRequest(API_BASE + "/login", username, password);

            if (response != null && response.getCode() == 1000 && response.getResult() != null) {
                UserSession.getInstance().setUser(response.getResult());

                Toast.show((Stage) container.getScene().getWindow(), "Login successful!", 1500, true);
                openBrowser();
            } else {
                String msg = (response != null && response.getMessage() != null)
                        ? response.getMessage()
                        : "Invalid username or password";
                Toast.show((Stage) container.getScene().getWindow(), msg, 2000, false);
            }
        } catch (IOException e) {
            Toast.show((Stage) container.getScene().getWindow(),
                    "Connection error: " + e.getMessage(), 2000, false);
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
            ApiResponse<User> response = sendRequest(API_BASE + "/register", username, password);

            if (response != null && response.getCode() == 1000) {
                Toast.show((Stage) container.getScene().getWindow(), "Registration successful!", 1500, true);
                openBrowser();
            } else {
                String msg = (response != null && response.getMessage() != null)
                        ? response.getMessage()
                        : "Username already exists or registration failed";
                Toast.show((Stage) container.getScene().getWindow(), msg, 2000, false);
            }
        } catch (IOException e) {
            Toast.show((Stage) container.getScene().getWindow(),
                    "Connection error: " + e.getMessage(), 2000, false);
        }
    }

    private ApiResponse<User> sendRequest(String apiUrl, String username, String password) throws IOException {
        URL url = new URL(apiUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        String json = String.format("{\"username\":\"%s\", \"password\":\"%s\"}", username, password);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes());
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(
                conn.getResponseCode() == 200 ? conn.getInputStream() : conn.getErrorStream()
        ));

        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }

        Gson gson = new Gson();
        return gson.fromJson(response.toString(),
                TypeToken.getParameterized(ApiResponse.class, User.class).getType());
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
