package org.com.webbrowser.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import org.com.webbrowser.model.Shortcut;
import org.com.webbrowser.session.UserSession;

import java.io.*;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class ShortcutService {
    private final String BASE_URL = "http://localhost:8080/api/shortcut/";

    private final Gson gson = new Gson();

    public void getShortcuts(Long userId, java.util.function.Consumer<List<Shortcut>> callback) {
        new Thread(() -> {
            try {
                URL url = new URL(BASE_URL + "get-shortcut?userId=" + userId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");

                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {

                    com.google.gson.JsonObject jsonObject = gson.fromJson(br, com.google.gson.JsonObject.class);
                    com.google.gson.JsonElement result = jsonObject.get("result");

                    Type listType = new TypeToken<List<Shortcut>>(){}.getType();
                    List<Shortcut> list = gson.fromJson(result, listType);

                    Platform.runLater(() -> callback.accept(list));
                }
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> callback.accept(java.util.Collections.emptyList()));
            }
        }).start();
    }

    public void addShortcut(String name, String url, String color, Runnable onSuccess) {
        Integer userId = UserSession.getInstance().getUserId();
        if (userId == null) {
            showWarning("User chưa đăng nhập — không thể thêm shortcut");
            return;
        }

        new Thread(() -> {
            try {
                URL apiUrl = new URL(BASE_URL + "add-shortcut");
                HttpURLConnection conn = (HttpURLConnection) apiUrl.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                String jsonInput = String.format(
                        "{\"userId\": %d, \"shortcuts\": [{\"name\": \"%s\", \"url\": \"%s\", \"color\": \"%s\"}]}",
                        userId, escapeJson(name), escapeJson(url), escapeJson(color)
                );

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(jsonInput.getBytes(StandardCharsets.UTF_8));
                }

                if (conn.getResponseCode() == 200 || conn.getResponseCode() == 201) {
                    Platform.runLater(onSuccess);
                } else {
                    System.out.println("⚠️ Lỗi khi thêm shortcut: HTTP " + conn.getResponseCode());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void updateShortcut(Long id, String newName, String newUrl, Runnable onSuccess) {
        new Thread(() -> {
            try {
                URL apiUrl = new URL(BASE_URL + "update-shortcut/" + id);
                HttpURLConnection conn = (HttpURLConnection) apiUrl.openConnection();
                conn.setRequestMethod("PUT");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                String jsonInput = String.format(
                        "{\"name\": \"%s\", \"url\": \"%s\"}",
                        escapeJson(newName), escapeJson(newUrl)
                );

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(jsonInput.getBytes("utf-8"));
                }

                int code = conn.getResponseCode();
                if (code == 200) {
                    Platform.runLater(onSuccess);
                    System.out.println("✅ Shortcut updated successfully (ID: " + id + ")");
                } else {
                    System.out.println("⚠️ Lỗi khi cập nhật shortcut: HTTP " + code);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void deleteShortcut(Long id, Runnable onSuccess) {
        new Thread(() -> {
            try {
                URL apiUrl = new URL(BASE_URL + "delete-shortcut/" + id);
                HttpURLConnection conn = (HttpURLConnection) apiUrl.openConnection();
                conn.setRequestMethod("DELETE");

                if (conn.getResponseCode() == 200) {
                    Platform.runLater(onSuccess);
                } else {
                    System.out.println("⚠️ Lỗi khi xóa shortcut: HTTP " + conn.getResponseCode());
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void showWarning(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Cảnh báo");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\"", "\\\"");
    }
}
