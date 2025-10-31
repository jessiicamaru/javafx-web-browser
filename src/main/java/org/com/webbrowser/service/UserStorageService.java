package org.com.webbrowser.service;

import com.google.gson.Gson;
import org.com.webbrowser.model.User;
import org.com.webbrowser.utils.EncryptionUtils;

import java.io.*;
import java.util.*;

public class UserStorageService {
    private static final String USER_DIR = "data/users/";
    private static final Gson gson = new Gson();

    static {
        new File(USER_DIR).mkdirs();
    }

    public static void saveUser(User user) {
        try {
            String json = gson.toJson(user);
            String encrypted = EncryptionUtils.encrypt(json); // 🔒 mã hoá 2 lớp
            Map<String, String> wrapper = new HashMap<>();
            wrapper.put("data", encrypted);

            try (Writer writer = new FileWriter(USER_DIR + user.getUsername() + ".json")) {
                gson.toJson(wrapper, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static List<User> loadAllUsers() {
        List<User> users = new ArrayList<>();
        File dir = new File(USER_DIR);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) return users;

        for (File file : files) {
            try (Reader reader = new FileReader(file)) {
                Map<?, ?> wrapper = gson.fromJson(reader, Map.class);
                String encrypted = (String) wrapper.get("data");
                String decrypted = EncryptionUtils.decrypt(encrypted); // 🔓 giải mã 2 lớp
                User user = gson.fromJson(decrypted, User.class);
                users.add(user);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return users;
    }
}
