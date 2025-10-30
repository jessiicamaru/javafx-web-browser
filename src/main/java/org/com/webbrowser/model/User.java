package org.com.webbrowser.model;

import java.util.List;

public class User {
    private int id;
    private String username;
    private String password;
    private String theme;
    private List<Bookmark> bookmarks;

    public User() {
    }

    public User(int id, String username, String password, String theme, List<Bookmark> bookmarks, List<String> histories) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.theme = theme;
        this.bookmarks = bookmarks;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public void setBookmarks(List<Bookmark> bookmarks) {
        this.bookmarks = bookmarks;
    }

    public int getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getTheme() {
        return theme;
    }

    public List<Bookmark> getBookmarks() {
        return bookmarks;
    }
}
