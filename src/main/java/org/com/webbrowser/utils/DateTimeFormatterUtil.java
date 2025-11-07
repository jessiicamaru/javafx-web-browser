package org.com.webbrowser.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DateTimeFormatterUtil {

    public static String formatVisitedAt(String visitedAt) {
        try {
            LocalDateTime time = LocalDateTime.parse(visitedAt);
            LocalDateTime now = LocalDateTime.now();

            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
            DateTimeFormatter fullFormatter = DateTimeFormatter.ofPattern("HH:mm dd-MM-yyyy");

            if (time.toLocalDate().equals(now.toLocalDate())) {
                // cùng ngày => chỉ hiển thị giờ phút
                return time.format(timeFormatter);
            } else {
                // khác ngày => hiển thị giờ phút và ngày tháng năm
                return time.format(fullFormatter);
            }
        } catch (Exception e) {
            return visitedAt; // fallback nếu parse lỗi
        }
    }
}
