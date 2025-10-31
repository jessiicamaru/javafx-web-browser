package org.com.webbrowser.model;

public class HistoryRecord {
    private String title;
    private String url;
    private String date;

    public HistoryRecord() {}

    public HistoryRecord(String title, String url, String date) {
        this.title = title;
        this.url = url;
        this.date = date;
    }

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }

    public String getDate() {
        return date;
    }
}
