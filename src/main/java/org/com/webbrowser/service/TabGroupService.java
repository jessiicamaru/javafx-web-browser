package org.com.webbrowser.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import org.com.webbrowser.model.ApiResponse;
import org.com.webbrowser.model.ServerTabGroup;
import org.com.webbrowser.model.SimpleGroupRequest;
import org.com.webbrowser.session.UserSession;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

public class TabGroupService {

    private static final String BASE_URL = "http://localhost:8080/api/tabgroup/";
    private final Gson gson = new Gson();

    public void getTabGroups(Consumer<List<ServerTabGroup>> onSuccess, Runnable onFail) {
        Integer userId = UserSession.getInstance().getUserId();
        if (userId == null) {
            showWarning("Bạn chưa đăng nhập!");
            onFail.run();
            return;
        }

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(BASE_URL + "get-tabgroup?userId=" + userId);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                int code = conn.getResponseCode();
                InputStream stream = code < 400 ? conn.getInputStream() : conn.getErrorStream();

                if (stream == null) {
                    Platform.runLater(onFail);
                    return;
                }

                try (InputStreamReader isr = new InputStreamReader(stream, StandardCharsets.UTF_8);
                     BufferedReader reader = new BufferedReader(isr)) {  // BỌC LẠI ĐÚNG!

                    ApiResponse<List<ServerTabGroup>> response = gson.fromJson(
                            reader,
                            new TypeToken<ApiResponse<List<ServerTabGroup>>>(){}.getType()
                    );

                    Platform.runLater(() -> {
                        if (response != null && response.getCode() == 1000 && response.getResult() != null) {
                            System.out.println("Loaded " + response.getResult().size() + " tab groups from server");
                            onSuccess.accept(response.getResult());
                        } else {
                            onFail.run();
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(onFail);
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    public void addTabGroup(ServerTabGroup group, Consumer<ServerTabGroup> onSuccess, Runnable onFail) {
        Integer userId = UserSession.getInstance().getUserId();
        if (userId == null) {
            showWarning("Bạn chưa đăng nhập!");
            onFail.run();
            return;
        }

        String jsonBody = """
        {
          "userId": %d,
          "tabGroups": [%s]
        }
        """.formatted(userId, gson.toJson(group));

        executeRequestWithResult(
                "POST",
                "add-tabgroup",
                jsonBody,
                new TypeToken<ApiResponse<List<ServerTabGroup>>>(){}, // Server trả về List<ServerTabGroup>
                resultList -> {
                    if (!resultList.isEmpty()) {
                        onSuccess.accept(resultList.get(0)); // Lấy group đầu tiên
                    } else {
                        onFail.run();
                    }
                },
                onFail
        );
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
                Platform.runLater(code >= 200 && code < 300 ? onSuccess : onFail);

            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(onFail);
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private <T> void executeRequestWithResult(
            String method, String endpoint, String jsonBody,
            TypeToken<ApiResponse<T>> typeToken,
            Consumer<T> onResult,
            Runnable onFail) {

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
                InputStream stream = code < 400 ? conn.getInputStream() : conn.getErrorStream();
                if (stream == null) {
                    Platform.runLater(onFail);
                    return;
                }

                // SỬA TẠI ĐÂY: Dùng BufferedReader ĐÚNG CÁCH!
                try (InputStreamReader isr = new InputStreamReader(stream, StandardCharsets.UTF_8);
                     BufferedReader reader = new BufferedReader(isr)) {  // HOÀN HẢO!

                    ApiResponse<T> response = gson.fromJson(reader, typeToken.getType());

                    Platform.runLater(() -> {
                        if (response != null && response.getCode() == 1000 && response.getResult() != null) {
                            onResult.accept(response.getResult());
                        } else {
                            System.out.println("API lỗi: " + (response != null ? response.getMessage() : "No response"));
                            onFail.run();
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(onFail);
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    public void updateTabGroup(ServerTabGroup group, Runnable onSuccess, Runnable onFail) {
        if (group.getId() == null) {
            onFail.run();
            return;
        }

        String json = gson.toJson(new SimpleGroupRequest(group.getName(), group.getColor(), group.getTabs()));
        executeSimpleRequest("PUT", "update-tabgroup/" + group.getId(), json, onSuccess, onFail);
    }

    public void deleteTabGroup(Long groupId, Runnable onSuccess, Runnable onFail) {
        executeSimpleRequest("DELETE", "delete-tabgroup/" + groupId, null, onSuccess, onFail);
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
}