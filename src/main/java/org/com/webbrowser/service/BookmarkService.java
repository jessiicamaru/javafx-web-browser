package org.com.webbrowser.service;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import org.com.webbrowser.session.UserSession;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class BookmarkService {

    private final String BASE_URL = "http://localhost:8080/api/bookmark/";

    public void addBookmark(String title, String url, Runnable onSuccess) {
        Integer userId = UserSession.getInstance().getUserId();
        if (userId == null) {
            showWarning("User chưa đăng nhập — không thể thêm bookmark");
            return;
        }

        new Thread(() -> {
            try {
                URL apiUrl = new URL(BASE_URL + "add-bookmark");
                HttpURLConnection conn = (HttpURLConnection) apiUrl.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                String jsonInput = String.format(
                        "{\"userId\": %d, \"bookmarks\": [{\"title\": \"%s\", \"url\": \"%s\"}]}",
                        userId, escapeJson(title), escapeJson(url)
                );

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(jsonInput.getBytes("utf-8"));
                }

                int code = conn.getResponseCode();
                if (code == 200 || code == 201) {
                    Platform.runLater(onSuccess);
                    System.out.println("✅ Bookmark added successfully");
                } else {
                    System.out.println("⚠️ Lỗi khi thêm bookmark: HTTP " + code);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void updateBookmark(Long id, String newTitle, String newUrl, Runnable onSuccess) {
        new Thread(() -> {
            try {
                URL apiUrl = new URL(BASE_URL + "update-bookmark/" + id);
                HttpURLConnection conn = (HttpURLConnection) apiUrl.openConnection();
                conn.setRequestMethod("PUT");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                String jsonInput = String.format(
                        "{\"title\": \"%s\", \"url\": \"%s\"}",
                        escapeJson(newTitle), escapeJson(newUrl)
                );

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(jsonInput.getBytes("utf-8"));
                }

                int code = conn.getResponseCode();
                if (code == 200) {
                    Platform.runLater(onSuccess);
                    System.out.println("✅ Bookmark updated successfully (ID: " + id + ")");
                } else {
                    System.out.println("⚠️ Lỗi khi cập nhật bookmark: HTTP " + code);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void deleteBookmark(Long id, Runnable onSuccess) {
        new Thread(() -> {
            try {
                URL apiUrl = new URL(BASE_URL + "delete-bookmark/" + id);
                HttpURLConnection conn = (HttpURLConnection) apiUrl.openConnection();
                conn.setRequestMethod("DELETE");

                int code = conn.getResponseCode();
                if (code == 200) {
                    Platform.runLater(onSuccess);
                    System.out.println("🗑️ Bookmark deleted successfully (ID: " + id + ")");
                } else {
                    System.out.println("⚠️ Lỗi khi xóa bookmark: HTTP " + code);
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
