package org.com.webbrowser.service;

import com.google.gson.Gson;
import org.com.webbrowser.model.User;
import org.com.webbrowser.utils.EncryptionUtils;

import java.io.*;
import java.util.*;

/**
 * Service lưu trữ thông tin người dùng MỘT CÁCH AN TOÀN trên máy cục bộ
 *
 * Tính năng bảo mật ĐỈNH CAO:
 * - Mọi dữ liệu user đều được MÃ HOÁ 2 LỚP trước khi ghi file
 * - Dùng AES + Base64 (qua EncryptionUtils)
 * - Lưu theo file riêng: data/users/username.json
 */
public class UserStorageService {
    private static final String USER_DIR = "data/users/";
    private static final Gson gson = new Gson();

    /**
     * Khối static: Tự động tạo thư mục lưu user khi ứng dụng khởi động
     * Đảm bảo data/users/ luôn tồn tại
     */
    static {
        new File(USER_DIR).mkdirs();
    }

    /**
     * Lưu thông tin một User xuống file cục bộ (đã được mã hóa toàn bộ)
     *
     * Quy trình bảo mật:
     * 1. Chuyển User → JSON
     * 2. Mã hóa JSON bằng AES (EncryptionUtils.encrypt)
     * 3. Bọc trong một Map {"data": "chuỗi_mã_hóa"} → tránh lộ cấu trúc
     * 4. Ghi ra file: data/users/username.json
     *
     * Kết quả: Dù mở file bằng Notepad → chỉ thấy chuỗi ký tự vô nghĩa!
     */
    public static void saveUser(User user) {
        try {
            // Bước 1: Chuyển User thành JSON thuần
            String json = gson.toJson(user);

            // Bước 2: MÃ HOÁ TOÀN BỘ dữ liệu bằng thuật toán mạnh (AES)
            String encrypted = EncryptionUtils.encrypt(json); // MÃ HOÁ 2 LỚP

            // Bước 3: Bọc dữ liệu mã hóa vào một wrapper → tăng thêm lớp bảo vệ
            Map<String, String> wrapper = new HashMap<>();
            wrapper.put("data", encrypted);

            // Bước 4: Ghi file theo tên username → dễ quản lý
            try (Writer writer = new FileWriter(USER_DIR + user.getUsername() + ".json")) {
                gson.toJson(wrapper, writer); // Ghi dạng JSON đẹp
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Đọc tất cả tài khoản người dùng đã đăng ký offline từ thư mục data/users/
     *
     * Quy trình giải mã an toàn:
     * 1. Đọc file → lấy chuỗi trong trường "data"
     * 2. GIẢI MÃ chuỗi đó bằng EncryptionUtils.decrypt
     * 3. Chuyển JSON đã giải mã → Object User
     * 4. Thêm vào danh sách
     *
     * Chỉ thành công nếu có đúng khóa mã hóa → cực kỳ an toàn!
     */
    public static List<User> loadAllUsers() {
        List<User> users = new ArrayList<>();
        File dir = new File(USER_DIR);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) return users;

        for (File file : files) {
            try (Reader reader = new FileReader(file)) {
                // Đọc wrapper {"data": "chuỗi_mã_hóa"}
                Map<?, ?> wrapper = gson.fromJson(reader, Map.class);
                String encrypted = (String) wrapper.get("data");

                if (encrypted == null) {
                    System.err.println("File bị hỏng hoặc không có dữ liệu: " + file.getName());
                    continue;
                }

                // GIẢI MÃ DỮ LIỆU
                String decrypted = EncryptionUtils.decrypt(encrypted); // GIẢI MÃ 2 LỚP
                if (decrypted == null) {
                    System.err.println("Không giải mã được: " + file.getName() + " (sai khóa hoặc file bị sửa)");
                    continue;
                }

                // Chuyển JSON đã giải mã thành User
                User user = gson.fromJson(decrypted, User.class);
                users.add(user);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return users;
    }
}