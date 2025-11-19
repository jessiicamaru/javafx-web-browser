package org.com.webbrowser.model;


import com.google.gson.annotations.SerializedName;
import java.util.List;

public class SimpleGroupRequest {
    @SerializedName("name")  public String name;
    @SerializedName("color") public String color;
    @SerializedName("tabs")  public List<ServerTab> tabs;

    public SimpleGroupRequest(String name, String color, List<ServerTab> tabs) {
        this.name = name;
        this.color = color;
        this.tabs = tabs;
    }
}
