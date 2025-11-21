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

/**
 * Service đồng bộ Nhóm Tab (Tab Group) với server
 */
public class TabGroupService {

    private static final String BASE_URL = "http://localhost:8080/api/tabgroup/";

    private final Gson gson = new Gson();

    /**
     * Lấy toàn bộ nhóm tab của người dùng hiện tại từ server
     *
     * @param onSuccess Trả về List<ServerTabGroup> khi thành công
     * @param onFail    Gọi khi lỗi (chưa đăng nhập, mạng, server lỗi...)
     */
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

                // Đọc JSON phản hồi đúng chuẩn try-with-resources
                try (InputStreamReader isr = new InputStreamReader(stream, StandardCharsets.UTF_8);
                     BufferedReader reader = new BufferedReader(isr)) {

                    ApiResponse<List<ServerTabGroup>> response = gson.fromJson(
                            reader,
                            new TypeToken<ApiResponse<List<ServerTabGroup>>>() {}.getType()
                    );

                    Platform.runLater(() -> {
                        if (response != null && response.getCode() == 1000 && response.getResult() != null) {
                            System.out.println("Đã tải thành công " + response.getResult().size() + " nhóm tab từ server");
                            onSuccess.accept(response.getResult());
                        } else {
                            System.err.println("Lỗi tải nhóm tab: " + (response != null ? response.getMessage() : "Không có phản hồi"));
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

    /**
     * Thêm một nhóm tab mới lên server
     * Server sẽ trả về danh sách nhóm (thường là 1 phần tử mới)
     *
     * @param group     Nhóm tạm (chưa có ID)
     * @param onSuccess Trả về nhóm thật có ID từ server
     * @param onFail    Lỗi khi thêm
     */
    public void addTabGroup(ServerTabGroup group, Consumer<ServerTabGroup> onSuccess, Runnable onFail) {
        Integer userId = UserSession.getInstance().getUserId();
        if (userId == null) {
            showWarning("Bạn chưa đăng nhập!");
            onFail.run();
            return;
        }

        // Backend yêu cầu format: { "userId": 1, "tabGroups": [ { ... } ] }
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
                new TypeToken<ApiResponse<List<ServerTabGroup>>>() {},
                resultList -> {
                    if (resultList != null && !resultList.isEmpty()) {
                        onSuccess.accept(resultList.get(0)); // Lấy nhóm đầu tiên (mới tạo)
                    } else {
                        onFail.run();
                    }
                },
                onFail
        );
    }

    /**
     * Cập nhật tên, màu, danh sách tab của một nhóm đã tồn tại
     */
    public void updateTabGroup(ServerTabGroup group, Runnable onSuccess, Runnable onFail) {
        if (group.getId() == null) {
            System.err.println("Không thể cập nhật nhóm tab: thiếu ID!");
            onFail.run();
            return;
        }

        // Chỉ gửi những field cần thiết (tối ưu payload)
        SimpleGroupRequest request = new SimpleGroupRequest(
                group.getName(),
                group.getColor(),
                group.getTabs()
        );

        String json = gson.toJson(request);
        executeSimpleRequest("PUT", "update-tabgroup/" + group.getId(), json, onSuccess, onFail);
    }

    /**
     * Xóa hoàn toàn một nhóm tab khỏi server
     */
    public void deleteTabGroup(Long groupId, Runnable onSuccess, Runnable onFail) {
        if (groupId == null) {
            onFail.run();
            return;
        }
        executeSimpleRequest("DELETE", "delete-tabgroup/" + groupId, null, onSuccess, onFail);
    }

    // ===================================================================
    // HÀM HỖ TRỢ (PRIVATE) – SIÊU SẠCH, KHÔNG LẶP CODE
    // ===================================================================

    /**
     * Gửi request đơn giản (PUT, DELETE) chỉ cần biết thành công/thất bại
     */
    private void executeSimpleRequest(String method, String endpoint, String jsonBody,
                                      Runnable onSuccess, Runnable onFail) {
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
                        os.flush();
                    }
                }

                int code = conn.getResponseCode();
                Platform.runLater((code >= 200 && code < 300) ? onSuccess : onFail);

            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(onFail);
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    /**
     * Gửi request và nhận kết quả phức tạp (có result trả về)
     * Dùng cho addTabGroup (server trả về List<ServerTabGroup>)
     */
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
                        os.flush();
                    }
                }

                int code = conn.getResponseCode();
                InputStream stream = code < 400 ? conn.getInputStream() : conn.getErrorStream();
                if (stream == null) {
                    Platform.runLater(onFail);
                    return;
                }

                try (InputStreamReader isr = new InputStreamReader(stream, StandardCharsets.UTF_8);
                     BufferedReader reader = new BufferedReader(isr)) {

                    ApiResponse<T> response = gson.fromJson(reader, typeToken.getType());

                    Platform.runLater(() -> {
                        if (response != null && response.getCode() == 1000 && response.getResult() != null) {
                            onResult.accept(response.getResult());
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