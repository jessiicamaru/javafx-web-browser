package org.com.webbrowser.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import org.com.webbrowser.model.ApiResponse;
import org.com.webbrowser.model.Shortcut;
import org.com.webbrowser.session.UserSession;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

/**
 * Service quản lý các Shortcut (phím tắt trang web yêu thích)
 * Hỗ trợ: Lấy danh sách, Thêm, Sửa, Xóa shortcut
 */
public class ShortcutService {

    private static final String BASE_URL = "http://localhost:8080/api/shortcut/";
    private final Gson gson = new Gson();

    /**
     * Lấy danh sách tất cả shortcut của người dùng hiện tại
     *
     * @param onSuccess Callback khi thành công → trả về List<Shortcut>
     * @param onFail    Callback khi thất bại (chưa đăng nhập, lỗi mạng, server lỗi...)
     */
    public void getShortcuts(Long userId, Consumer<List<Shortcut>> onSuccess, Runnable onFail) {
        if (userId == null) {
            showWarning("Bạn chưa đăng nhập!");
            onFail.run();
            return;
        }

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(BASE_URL + "get-shortcut?userId=" + userId);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json"); // Yêu cầu server trả JSON
                conn.setConnectTimeout(10000); // Timeout kết nối 10 giây
                conn.setReadTimeout(10000);    // Timeout đọc dữ liệu 10 giây

                int code = conn.getResponseCode();
                // Chọn stream phù hợp: thành công → InputStream, lỗi → ErrorStream
                InputStream stream = code < 400 ? conn.getInputStream() : conn.getErrorStream();

                if (stream == null) {
                    Platform.runLater(onFail);
                    return;
                }

                // Đọc và parse JSON phản hồi từ server
                try (BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    ApiResponse<List<Shortcut>> response = gson.fromJson(
                            br,
                            new TypeToken<ApiResponse<List<Shortcut>>>(){}.getType()
                    );

                    // Chạy callback trên JavaFX thread (an toàn cho UI)
                    Platform.runLater(() -> {
                        if (response != null && response.getCode() == 1000 && response.getResult() != null) {
                            onSuccess.accept(response.getResult()); // Thành công → trả danh sách shortcut
                        } else {
                            System.out.println("Lỗi API get-shortcut: " + (response != null ? response.getMessage() : "No response"));
                            onFail.run(); // Lỗi nghiệp vụ từ server
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(onFail); // Lỗi mạng, timeout, JSON sai định dạng...
            } finally {
                if (conn != null) conn.disconnect(); // Luôn ngắt kết nối khi xong
            }
        }).start();
    }

    /**
     * Hàm chung thực hiện các request đơn giản (POST, PUT, DELETE)
     * Dùng để thêm, sửa, xóa shortcut → tránh lặp code
     */
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

                // Nếu có dữ liệu cần gửi (POST hoặc PUT)
                if (jsonBody != null) {
                    conn.setDoOutput(true);
                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
                        os.flush();
                    }
                }

                int code = conn.getResponseCode();
                boolean success = code >= 200 && code < 300;

                // Chạy callback tương ứng trên JavaFX thread
                Platform.runLater(success ? onSuccess : onFail);

            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(onFail);
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    /**
     * Thêm một shortcut mới (tên + URL + màu)
     * Format gửi lên: { "userId": 1, "shortcuts": [{"name": "...", "url": "...", "color": "#FF5733"}] }
     */
    public void addShortcut(String name, String url, String color, Runnable onSuccess, Runnable onFail) {
        String json = String.format(
                "{\"userId\": %d, \"shortcuts\": [{\"name\": \"%s\", \"url\": \"%s\", \"color\": \"%s\"}]}",
                getUserId(), escapeJson(name), escapeJson(url), escapeJson(color)
        );
        executeSimpleRequest("POST", "add-shortcut", json, onSuccess, onFail);
    }

    /**
     * Cập nhật tên và URL của một shortcut đã tồn tại
     */
    public void updateShortcut(Long id, String newName, String newUrl, Runnable onSuccess, Runnable onFail) {
        String json = String.format("{\"name\": \"%s\", \"url\": \"%s\"}", escapeJson(newName), escapeJson(newUrl));
        executeSimpleRequest("PUT", "update-shortcut/" + id, json, onSuccess, onFail);
    }

    /**
     * Xóa một shortcut theo ID
     */
    public void deleteShortcut(Long id, Runnable onSuccess, Runnable onFail) {
        executeSimpleRequest("DELETE", "delete-shortcut/" + id, null, onSuccess, onFail);
    }

    /** Hiển thị cảnh báo nếu người dùng chưa đăng nhập */
    private void showWarning(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Cảnh báo");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    /** Thoát các ký tự đặc biệt trong JSON để tránh lỗi parse */
    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    /** Lấy userId hiện tại từ Session (rút gọn) */
    private Integer getUserId() {
        return UserSession.getInstance().getUserId();
    }
}