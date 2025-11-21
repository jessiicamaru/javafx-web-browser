package org.com.webbrowser.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.com.webbrowser.model.HistoryEntry;
import org.com.webbrowser.model.HistoryRecord;
import org.com.webbrowser.session.UserSession;

import java.io.*;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service quản lý LỊCH SỬ DUYỆT WEB cục bộ (offline-first)
 * - Lưu theo tuần (1 file JSON/tuần) → tối ưu dung lượng & tốc độ
 * - Tự động dọn dẹp file cũ (>4 tuần)
 * - Hỗ trợ thêm, xóa, đọc toàn bộ lịch sử
 * - Hoạt động hoàn hảo ngay cả khi không có mạng
 */
public class HistoryService {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final String BASE_DIR = "data/history/";

    /**
     * Tạo đường dẫn file history theo tuần hiện tại
     * Ví dụ: data/history/user_1_2025-week47.json
     */
    private static File getHistoryFile() {
        Integer userId = UserSession.getInstance().getUserId();
        if (userId == null) return null;

        LocalDate now = LocalDate.now();
        int week = now.get(WeekFields.ISO.weekOfWeekBasedYear());
        String filename = String.format("user_%d_%d-week%d.json", userId, now.getYear(), week);

        File file = new File(BASE_DIR + filename);
        file.getParentFile().mkdirs(); // Tạo thư mục nếu chưa có
        return file;
    }

    /** Chuyển chuỗi ISO → LocalDateTime (an toàn với null) */
    private static LocalDateTime parseDate(String isoDate) {
        if (isoDate == null) return null;
        try {
            return LocalDateTime.parse(isoDate);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Kiểm tra một entry có nằm trong tuần hiện tại không */
    private static boolean isEntryInCurrentWeek(HistoryEntry entry) {
        LocalDateTime dt = parseDate(entry.getDate());
        if (dt == null) return false;

        LocalDate entryDate = dt.toLocalDate();
        LocalDate now = LocalDate.now();
        WeekFields wf = WeekFields.ISO;

        return entryDate.getYear() == now.getYear() &&
                entryDate.get(wf.weekOfWeekBasedYear()) == now.get(wf.weekOfWeekBasedYear());
    }

    /**
     * Đọc lịch sử của TUẦN HIỆN TẠI (file hiện tại)
     */
    public static List<HistoryEntry> loadHistory() {
        File file = getHistoryFile();
        if (file == null || !file.exists()) return new ArrayList<>();

        try (Reader reader = new FileReader(file)) {
            Type listType = new TypeToken<List<HistoryRecord>>() {}.getType();
            List<HistoryRecord> records = gson.fromJson(reader, listType);

            if (records == null) return new ArrayList<>();

            return records.stream()
                    .map(HistoryEntry::new)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            System.err.println("Lỗi đọc history tuần hiện tại: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * Lưu danh sách entry vào file tuần hiện tại
     * Chỉ lưu những entry thuộc tuần hiện tại → tiết kiệm dung lượng
     */
    public static void saveHistory(List<HistoryEntry> historyEntries) {
        File file = getHistoryFile();
        if (file == null) return;

        // Lọc chỉ giữ lại entry của tuần hiện tại
        List<HistoryEntry> currentWeekEntries = historyEntries.stream()
                .filter(Objects::nonNull)
                .filter(HistoryService::isEntryInCurrentWeek)
                .map(e -> new HistoryEntry(e.getTitle(), e.getUrl(), e.getDate())) // copy an toàn
                .collect(Collectors.toList());

        List<HistoryRecord> records = currentWeekEntries.stream()
                .map(HistoryEntry::toRecord)
                .collect(Collectors.toList());

        try (Writer writer = new FileWriter(file)) {
            gson.toJson(records, writer);
            System.out.println("Đã lưu " + records.size() + " lịch sử vào " + file.getName());
        } catch (IOException e) {
            System.err.println("Lỗi lưu history: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Thêm một trang mới vào lịch sử (tự động xóa trùng URL)
     * Luôn thêm vào đầu danh sách → mới nhất ở trên cùng
     */
    public static void addHistoryEntry(HistoryEntry entry) {
        if (entry == null || entry.getUrl() == null) return;

        List<HistoryEntry> list = new ArrayList<>(loadHistory());

        // Xóa trang cũ nếu đã tồn tại (tránh trùng)
        list.removeIf(e -> e.getUrl() != null && e.getUrl().equalsIgnoreCase(entry.getUrl()));

        // Thêm mới vào đầu
        list.add(0, entry);

        saveHistory(list);
    }

    /**
     * Đọc TOÀN BỘ lịch sử của người dùng (tất cả các tuần)
     * Dùng để hiển thị trong cửa sổ History
     */
    public static List<HistoryEntry> loadAllHistory() {
        cleanupOldHistoryFiles(); // Dọn dẹp file cũ trước

        Integer userId = UserSession.getInstance().getUserId();
        if (userId == null) return new ArrayList<>();

        File dir = new File(BASE_DIR);
        if (!dir.exists() || !dir.isDirectory()) return new ArrayList<>();

        List<HistoryEntry> allEntries = new ArrayList<>();

        File[] files = dir.listFiles((d, name) ->
                name.startsWith("user_" + userId + "_") && name.endsWith(".json"));

        if (files == null) return allEntries;

        for (File file : files) {
            try (Reader reader = new FileReader(file)) {
                Type listType = new TypeToken<List<HistoryRecord>>() {}.getType();
                List<HistoryRecord> records = gson.fromJson(reader, listType);

                if (records != null) {
                    allEntries.addAll(records.stream()
                            .map(HistoryEntry::new)
                            .collect(Collectors.toList()));
                }
            } catch (Exception e) {
                System.err.println("Lỗi đọc file history: " + file.getName());
                e.printStackTrace();
            }
        }

        // Sắp xếp giảm dần theo thời gian (mới nhất lên đầu)
        allEntries.sort(Comparator.comparing(HistoryEntry::getDate).reversed());

        return allEntries;
    }

    /**
     * Xóa nhiều entry khỏi tất cả các file history
     */
    public static void deleteHistoryEntries(List<HistoryEntry> entriesToDelete) {
        Integer userId = UserSession.getInstance().getUserId();
        if (userId == null) return;

        File dir = new File(BASE_DIR);
        if (!dir.exists()) return;

        File[] files = dir.listFiles((d, name) -> name.startsWith("user_" + userId + "_") && name.endsWith(".json"));
        if (files == null) return;

        for (File file : files) {
            try (Reader reader = new FileReader(file)) {
                Type listType = new TypeToken<List<HistoryRecord>>() {}.getType();
                List<HistoryRecord> records = gson.fromJson(reader, listType);
                if (records == null) continue;

                Set<String> urlsToDelete = entriesToDelete.stream()
                        .map(HistoryEntry::getUrl)
                        .collect(Collectors.toSet());

                records.removeIf(r -> urlsToDelete.contains(r.getUrl()));

                try (Writer writer = new FileWriter(file)) {
                    gson.toJson(records, writer);
                }

            } catch (Exception e) {
                System.err.println("Lỗi xóa history từ file: " + file.getName());
                e.printStackTrace();
            }
        }
    }

    /**
     * TỰ ĐỘNG DỌN DẸP file history cũ (>4 tuần)
     * Giữ hệ thống luôn sạch sẽ, nhẹ nhàng
     */
    private static void cleanupOldHistoryFiles() {
        Integer userId = UserSession.getInstance().getUserId();
        if (userId == null) return;

        File dir = new File(BASE_DIR);
        if (!dir.exists() || !dir.isDirectory()) return;

        LocalDate now = LocalDate.now();
        WeekFields wf = WeekFields.ISO;
        int currentWeek = now.get(wf.weekOfWeekBasedYear());
        int currentYear = now.getYear();

        File[] files = dir.listFiles((d, name) ->
                name.startsWith("user_" + userId + "_") && name.endsWith(".json"));

        if (files == null) return;

        for (File file : files) {
            String name = file.getName();
            try {
                // Parse tên file: user_1_2025-week47.json
                String[] parts = name.split("[_\\-]"); // tách theo _ và -
                int fileYear = Integer.parseInt(parts[2]);
                int fileWeek = Integer.parseInt(parts[3].replace("week", "").replace(".json", ""));

                boolean isOld = fileYear < currentYear ||
                        (fileYear == currentYear && currentWeek - fileWeek > 4);

                if (isOld && file.delete()) {
                    System.out.println("Đã dọn dẹp file cũ: " + name);
                }

            } catch (Exception e) {
                System.err.println("Không thể xóa file cũ: " + name);
            }
        }
    }
}