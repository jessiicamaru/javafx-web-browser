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

public class HistoryService {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final String BASE_DIR = "data/history/";

    private static File getHistoryFile() {
        Integer userId = UserSession.getInstance().getUserId();
        if (userId == null) return null;

        LocalDate now = LocalDate.now();
        int week = now.get(WeekFields.ISO.weekOfWeekBasedYear());
        String filename = String.format("user_%d_%d-week%d.json", userId, now.getYear(), week);

        File file = new File(BASE_DIR + filename);
        file.getParentFile().mkdirs();
        return file;
    }

    private static LocalDateTime parseDate(String isoDate) {
        if (isoDate == null) return null;
        try {
            return LocalDateTime.parse(isoDate);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static boolean isEntryInCurrentWeek(HistoryEntry entry) {
        LocalDateTime dt = parseDate(entry.getDate());
        if (dt == null) return false;
        LocalDate now = LocalDate.now();
        WeekFields wf = WeekFields.ISO;
        int entryWeek = dt.toLocalDate().get(wf.weekOfWeekBasedYear());
        int entryYear = dt.getYear();
        int nowWeek = now.get(wf.weekOfWeekBasedYear());
        int nowYear = now.getYear();
        return entryYear == nowYear && entryWeek == nowWeek;
    }

    public static List<HistoryEntry> loadHistory() {
        File file = getHistoryFile();
        if (file == null || !file.exists()) return new ArrayList<>();

        try (Reader reader = new FileReader(file)) {
            Type listType = new TypeToken<List<HistoryRecord>>() {
            }.getType();
            List<HistoryRecord> records = gson.fromJson(reader, listType);
            if (records == null) return new ArrayList<>();

            return records.stream()
                    .map(HistoryEntry::new)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public static void saveHistory(List<HistoryEntry> historyEntries) {
        File file = getHistoryFile();
        if (file == null) return;

        List<HistoryEntry> entriesForCurrentWeek = historyEntries.stream()
                .filter(Objects::nonNull)
                .filter(HistoryService::isEntryInCurrentWeek)
                .map(h -> new HistoryEntry(h.getTitle(), h.getUrl(), h.getDate())) // make safe copy
                .collect(Collectors.toList());

        List<HistoryRecord> records = entriesForCurrentWeek.stream()
                .map(HistoryEntry::toRecord)
                .collect(Collectors.toList());

        try (Writer writer = new FileWriter(file)) {
            gson.toJson(records, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void addHistoryEntry(HistoryEntry entry) {
        List<HistoryEntry> list = loadHistory();
        list.removeIf(e -> e.getUrl().equalsIgnoreCase(entry.getUrl()));

        list.add(0, entry);

        saveHistory(list);
    }


    public static List<HistoryEntry> loadAllHistory() {
        cleanupOldHistoryFiles();

        Integer userId = UserSession.getInstance().getUserId();
        if (userId == null) return new ArrayList<>();

        File dir = new File(BASE_DIR);
        if (!dir.exists() || !dir.isDirectory()) return new ArrayList<>();

        List<HistoryEntry> allEntries = new ArrayList<>();

        File[] files = dir.listFiles((d, name) -> name.startsWith("user_" + userId + "_") && name.endsWith(".json"));
        if (files == null) return allEntries;

        for (File file : files) {
            try (Reader reader = new FileReader(file)) {
                Type listType = new TypeToken<List<HistoryRecord>>() {
                }.getType();
                List<HistoryRecord> records = gson.fromJson(reader, listType);
                if (records != null) {
                    allEntries.addAll(records.stream()
                            .map(HistoryEntry::new)
                            .collect(Collectors.toList()));
                }
            } catch (Exception e) {
                System.err.println("⚠️ Lỗi khi đọc file history: " + file.getName());
                e.printStackTrace();
            }
        }

        allEntries.sort(Comparator.comparing(HistoryEntry::getDate).reversed());

        return allEntries;
    }

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
                System.err.println("⚠️ Lỗi khi xóa khỏi file: " + file.getName());
                e.printStackTrace();
            }
        }
    }

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
                // ví dụ: user_1_2025-week43.json
                String[] parts = name.split("_");
                String yearPart = parts[2]; // "2025-week43.json"
                String[] ySplit = yearPart.split("-week");
                int year = Integer.parseInt(ySplit[0]);
                int week = Integer.parseInt(ySplit[1].replace(".json", ""));

                boolean isOld = false;
                if (year < currentYear) {
                    isOld = true; // năm cũ hơn
                } else if (year == currentYear && currentWeek - week > 4) {
                    isOld = true; // cùng năm nhưng cách quá 4 tuần
                }

                if (isOld && file.delete()) {
                    System.out.println("🧹 Đã xóa file history cũ: " + file.getName());
                }

            } catch (Exception e) {
                System.err.println("⚠️ Không thể phân tích hoặc xóa file: " + name);
            }
        }
    }
}
