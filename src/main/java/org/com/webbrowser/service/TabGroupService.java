package org.com.webbrowser.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import org.com.webbrowser.model.ServerTabGroup;
import org.com.webbrowser.model.SimpleGroupRequest;
import org.com.webbrowser.session.UserSession;

import java.io.*;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class TabGroupService {
    private final String BASE_URL = "http://localhost:8080/api/tabgroup/";
    private final Gson gson = new Gson();

    // Lấy tất cả tab group của user
    public void getTabGroups(Consumer<List<ServerTabGroup>> callback) {
        Integer userId = UserSession.getInstance().getUserId();
        if (userId == null) {
            Platform.runLater(() -> callback.accept(new ArrayList<>()));
            return;
        }

        new Thread(() -> {
            try {
                URL url = new URL(BASE_URL + "get-tabgroup?userId=" + userId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                if (conn.getResponseCode() == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    JsonObject root = gson.fromJson(br, JsonObject.class);
                    JsonArray result = root.getAsJsonArray("result");

                    Type listType = new TypeToken<List<ServerTabGroup>>(){}.getType();
                    List<ServerTabGroup> groups = gson.fromJson(result, listType);
                    System.out.println("Loaded " + groups.size() + " tab groups from server");
                    Platform.runLater(() -> callback.accept(groups));
                } else {
                    Platform.runLater(() -> callback.accept(new ArrayList<>()));
                }
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> callback.accept(new ArrayList<>()));
            }
        }).start();
    }

    // SỬA CHÍNH TẠI ĐÂY: TRẢ VỀ ServerTabGroup CÓ ID MỚI!
    public void addTabGroup(ServerTabGroup group, Consumer<ServerTabGroup> onSuccess) {
        Integer userId = UserSession.getInstance().getUserId();
        if (userId == null) {
            Platform.runLater(() -> onSuccess.accept(null));
            return;
        }

        new Thread(() -> {
            try {
                URL url = new URL(BASE_URL + "add-tabgroup");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                String json = """
                {
                  "userId": %d,
                  "tabGroups": [%s]
                }
                """.formatted(userId, gson.toJson(group));

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                }

                if (conn.getResponseCode() == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    JsonObject root = gson.fromJson(br, JsonObject.class);
                    JsonArray resultArray = root.getAsJsonArray("result"); // ← ĐÚNG: là mảng!

                    if (resultArray != null && resultArray.size() > 0) {
                        JsonObject firstGroup = resultArray.get(0).getAsJsonObject();
                        ServerTabGroup createdGroup = gson.fromJson(firstGroup, ServerTabGroup.class);
                        System.out.println("Group created successfully! ID: " + createdGroup.getId());
                        Platform.runLater(() -> onSuccess.accept(createdGroup));
                    } else {
                        System.err.println("Server trả về result rỗng!");
                        Platform.runLater(() -> onSuccess.accept(null));
                    }
                } else {
                    System.err.println("Add group failed: HTTP " + conn.getResponseCode());
                    Platform.runLater(() -> onSuccess.accept(null));
                }
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> onSuccess.accept(null));
            }
        }).start();
    }

    // Cập nhật group (name, color, toàn bộ tabs)
    public void updateTabGroup(ServerTabGroup group, Runnable onSuccess) {
        if (group.getId() == null) return;

        new Thread(() -> {
            try {
                URL url = new URL(BASE_URL + "update-tabgroup/" + group.getId());
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("PUT");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                String json = gson.toJson(new SimpleGroupRequest(group.getName(), group.getColor(), group.getTabs()));

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                }

                if (conn.getResponseCode() == 200) {
                    Platform.runLater(onSuccess);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    // Xóa group
    public void deleteTabGroup(Long groupId, Runnable onSuccess) {
        new Thread(() -> {
            try {
                URL url = new URL(BASE_URL + "delete-tabgroup/" + groupId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("DELETE");

                if (conn.getResponseCode() == 200) {
                    Platform.runLater(onSuccess);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void showWarning(String msg) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.WARNING);
            a.setContentText(msg);
            a.show();
        });
    }
}