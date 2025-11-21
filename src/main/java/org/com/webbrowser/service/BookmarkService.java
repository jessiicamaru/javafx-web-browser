package org.com.webbrowser.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import org.com.webbrowser.model.ApiResponse;
import org.com.webbrowser.model.Bookmark;
import org.com.webbrowser.session.UserSession;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

public class BookmarkService {

    private static final String BASE_URL = "http://localhost:8080/api/bookmark/";
    private final Gson gson = new Gson();

    public void getBookmarks(Consumer<List<Bookmark>> onSuccess, Runnable onFail) {
        Integer userId = UserSession.getInstance().getUserId();
        if (userId == null) {
            showWarning("Bạn chưa đăng nhập!");
            onFail.run();
            return;
        }

        new Thread(() -> {
            try {
                URL url = new URL(BASE_URL + "get-bookmark?userId=" + userId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                int code = conn.getResponseCode();
                InputStream stream = code < 400 ? conn.getInputStream() : conn.getErrorStream();

                if (stream == null) {
                    Platform.runLater(onFail);
                    return;
                }

                try (BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    ApiResponse<List<Bookmark>> response = gson.fromJson(
                            br,
                            new TypeToken<ApiResponse<List<Bookmark>>>(){}.getType()
                    );

                    Platform.runLater(() -> {
                        if (response != null && response.getCode() == 1000 && response.getResult() != null) {
                            onSuccess.accept(response.getResult());
                        } else {
                            onFail.run();
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(onFail);
            }
        }).start();
    }

    private void executeSimpleRequest(String method, String endpoint, String jsonBody, Runnable onSuccess, Runnable onFail) {
        Integer userId = UserSession.getInstance().getUserId();
        if (userId == null) {
            showWarning("Bạn chưa đăng nhập!");
            onFail.run();
            return;
        }

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(BASE_URL + endpoint);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod(method);
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                if (jsonBody != null) {
                    conn.setDoOutput(true);
                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
                    }
                }

                int code = conn.getResponseCode();
                boolean success = code >= 200 && code < 300;

                Platform.runLater(success ? onSuccess : onFail);

            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(onFail);
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    public void addBookmark(String title, String url, Runnable onSuccess, Runnable onFail) {
        String json = String.format(
                "{\"userId\": %d, \"bookmarks\": [{\"title\": \"%s\", \"url\": \"%s\"}]}",
                getUserId(), escapeJson(title), escapeJson(url)
        );
        executeSimpleRequest("POST", "add-bookmark", json, onSuccess, onFail);
    }

    public void updateBookmark(Long id, String newTitle, String newUrl, Runnable onSuccess, Runnable onFail) {
        String json = String.format("{\"title\": \"%s\", \"url\": \"%s\"}",
                escapeJson(newTitle), escapeJson(newUrl));

        executeSimpleRequest("PUT", "update-bookmark/" + id, json, onSuccess, onFail);
    }

    public void deleteBookmark(Long id, Runnable onSuccess, Runnable onFail) {
        executeSimpleRequest("DELETE", "delete-bookmark/" + id, null, onSuccess, onFail);
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

    private String escapeJson(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private Integer getUserId() {
        return UserSession.getInstance().getUserId();
    }
}