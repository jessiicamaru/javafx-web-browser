package org.com.webbrowser.model;

import javafx.scene.image.Image;

public class SuggestionEntry {
    private final String displayText;
    private final String url;
    private final String type; // "history", "bookmark", "google"
    private final Image icon;

    public SuggestionEntry(String displayText, String url, String type, Image icon) {
        this.displayText = displayText;
        this.url = url;
        this.type = type;
        this.icon = icon;
    }

    public String getDisplayText() {
        return displayText;
    }

    public String getUrl() {
        return url;
    }

    public String getType() {
        return type;
    }

    public Image getIcon() {
        return icon;
    }

}

