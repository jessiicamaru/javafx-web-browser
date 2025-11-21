package org.com.webbrowser.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.com.webbrowser.model.ApiResponse;
import org.com.webbrowser.model.User;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class AuthService {
    private static final String BASE_URL = "http://localhost:8080/api/users";
    private final Gson gson = new Gson();

    public void login(String username, String password, java.util.function.Consumer<User> onSuccess, Runnable onFail) {
        sendRequest(BASE_URL + "/login", username, password, onSuccess, onFail);
    }

    public void register(String username, String password, java.util.function.Consumer<User> onSuccess, Runnable onFail) {
        sendRequest(BASE_URL + "/register", username, password, onSuccess, onFail);
    }

    private void sendRequest(String url, String username, String password,
                             java.util.function.Consumer<User> onSuccess, Runnable onFail) {
        new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                String json = String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.getBytes());
                }

                try (BufferedReader br = new BufferedReader(new InputStreamReader(
                        conn.getResponseCode() == 200 ? conn.getInputStream() : conn.getErrorStream()))) {

                    ApiResponse<User> resp = gson.fromJson(br, TypeToken.getParameterized(
                            ApiResponse.class, User.class).getType());

                    if (resp.getCode() == 1000 && resp.getResult() != null) {
                        javafx.application.Platform.runLater(() -> onSuccess.accept(resp.getResult()));
                    } else {
                        javafx.application.Platform.runLater(onFail);
                    }
                }
            } catch (Exception e) {
                javafx.application.Platform.runLater(onFail);
            }
        }).start();
    }
}