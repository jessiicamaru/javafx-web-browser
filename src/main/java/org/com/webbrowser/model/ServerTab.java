package org.com.webbrowser.model;

public class ServerTab {
    private Long id;
    private String url;
    private String title;
    private String favicon;  // Có thể null

    // Constructors
    public ServerTab() {}
    public ServerTab(String url, String title, String favicon) {
        this.url = url;
        this.title = title;
        this.favicon = favicon;
    }

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getFavicon() { return favicon; }
    public void setFavicon(String favicon) { this.favicon = favicon; }
}