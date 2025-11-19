package org.com.webbrowser.model;

import java.util.ArrayList;
import java.util.List;

public class ServerTabGroup {
    private Long id;
    private String name;
    private String color;
    private List<ServerTab> tabs = new ArrayList<>();

    // Constructors
    public ServerTabGroup() {}
    public ServerTabGroup(String name, String color, List<ServerTab> tabs) {
        this.name = name;
        this.color = color;
        this.tabs = tabs != null ? tabs : new ArrayList<>();
    }

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public List<ServerTab> getTabs() { return tabs; }
    public void setTabs(List<ServerTab> tabs) { this.tabs = tabs; }
}