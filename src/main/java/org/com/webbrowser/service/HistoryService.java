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

    public static List<HistoryEntry> loadHistory() {
        File file = getHistoryFile();
        if (file == null || !file.exists()) return new ArrayList<>();

        try (Reader reader = new FileReader(file)) {
            Type listType = new TypeToken<List<HistoryRecord>>(){}.getType();
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

        List<HistoryRecord> records = historyEntries.stream()
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

        boolean exists = list.stream()
                .anyMatch(h -> h.getUrl() != null && h.getUrl().equals(entry.getUrl()));

        if (!exists) {
            list.add(entry);
            saveHistory(list);
        }
    }

    public static List<HistoryEntry> loadAllHistory() {
        Integer userId = UserSession.getInstance().getUserId();
        if (userId == null) return new ArrayList<>();

        File dir = new File(BASE_DIR);
        if (!dir.exists() || !dir.isDirectory()) return new ArrayList<>();

        List<HistoryEntry> allEntries = new ArrayList<>();

        File[] files = dir.listFiles((d, name) -> name.startsWith("user_" + userId + "_") && name.endsWith(".json"));
        if (files == null) return allEntries;

        for (File file : files) {
            try (Reader reader = new FileReader(file)) {
                Type listType = new TypeToken<List<HistoryRecord>>(){}.getType();
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
}
