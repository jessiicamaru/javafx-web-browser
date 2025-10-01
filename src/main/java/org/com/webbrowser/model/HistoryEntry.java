package org.com.webbrowser.model;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class HistoryEntry {
    private final BooleanProperty selected = new SimpleBooleanProperty(false);
    private final StringProperty title = new SimpleStringProperty();
    private final StringProperty url = new SimpleStringProperty();
    private final StringProperty date = new SimpleStringProperty();

    public HistoryEntry(String title, String url, String date) {
        this.title.set(title);
        this.url.set(url);
        this.date.set(date);
    }

    public String getTitle() {
        return title.get();
    }

    public String getUrl() {
        return url.get();
    }

    public String getDate() {
        return date.get();
    }

    public BooleanProperty selectedProperty() {
        return selected;
    }

    public boolean isSelected() {
        return selected.get();
    }

    public void setSelected(boolean v) {
        selected.set(v);
    }
}
