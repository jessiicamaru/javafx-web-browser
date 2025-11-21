package org.com.webbrowser.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import javafx.application.Platform;
import org.com.webbrowser.model.ApiResponse;
import org.com.webbrowser.model.User;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.function.Consumer;

/**
 * Service xử lý đăng nhập và đăng ký người dùng
 */
public class AuthService {
    private static final String BASE_URL = "http://localhost:8080/api/users";
    private final Gson gson = new Gson();

    /**
     * Đăng nhập người dùng
     *
     * @param username  Tên đăng nhập
     * @param onSuccess Callback khi đăng nhập thành công → trả về đối tượng User
     * @param onFail    Callback khi thất bại (sai mật khẩu, lỗi mạng, server lỗi...)
     */
    public void login(String username, String password, Consumer<User> onSuccess, Runnable onFail) {
        sendRequest(BASE_URL + "/login", username, password, onSuccess, onFail);
    }

    /**
     * Đăng ký tài khoản mới
     *
     * @param username  Tên đăng nhập muốn tạo
     * @param password  Mật khẩu
     * @param onSuccess    Thành công → trả về User vừa tạo
     * @param onFail    Thất bại (username đã tồn tại, lỗi server...)
     */
    public void register(String username, String password, Consumer<User> onSuccess, Runnable onFail) {
        sendRequest(BASE_URL + "/register", username, password, onSuccess, onFail);
    }

    /**
     * Hàm chung gửi request POST tới backend
     * Chạy trong Thread riêng → không block JavaFX Application Thread
     *
     * @param url           Đường dẫn endpoint (login hoặc register)
     * @param username      Username
     * @param password      Password
     * @param onSuccess     Callback khi nhận được User hợp lệ
     * @param onFail        Callback khi có bất kỳ lỗi nào
     */
    private void sendRequest(String url, String username, String password,
                             Consumer<User> onSuccess, Runnable onFail) {
        // Tạo một Thread mới để thực hiện mạng
        new Thread(() -> {
            try {
                // Mở kết nối HTTP
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                // Tạo JSON body: {"username":"abc","password":"123"}
                String json = String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password);

                // Ghi dữ liệu vào request body
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.getBytes());
                }

                // Đọc toàn bộ phản hồi thành String
                try (BufferedReader br = new BufferedReader(new InputStreamReader(
                        conn.getResponseCode() == 200 ? conn.getInputStream() : conn.getErrorStream()))) {

                    // 4. Parse JSON phản hồi theo format:
                    // { "code": 1000, "message": "...", "result": { User } }
                    ApiResponse<User> resp = gson.fromJson(br, TypeToken.getParameterized(
                            ApiResponse.class, User.class).getType());

                    // 5. Kiểm tra mã thành công (code = 1000 là thành công theo chuẩn backend)
                    if (resp.getCode() == 1000 && resp.getResult() != null) {
                        // 5. Kiểm tra mã thành công (code = 1000 là thành công theo chuẩn backend)
                        Platform.runLater(() -> onSuccess.accept(resp.getResult()));
                    } else {
                        // Thành công → trả về User trên JavaFX thread (an toàn cho UI)
                        Platform.runLater(onFail);
                    }
                }
            } catch (Exception e) {
                // Lỗi mạng, timeout, server sập, JSON sai định dạng...
                Platform.runLater(onFail);
            }
        }).start();
    }
}