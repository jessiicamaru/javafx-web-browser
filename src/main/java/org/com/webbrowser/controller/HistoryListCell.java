package org.com.webbrowser.controller;

import javafx.fxml.FXMLLoader;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import org.com.webbrowser.model.HistoryEntry;

import java.io.IOException;

public class HistoryListCell extends ListCell<HistoryEntry> {
    private final HistoryController parent;

    public HistoryListCell(HistoryController parent) {
        this.parent = parent;
    }

    @Override
    protected void updateItem(HistoryEntry item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
            setGraphic(null);
        } else {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/HistoryItem.fxml"));
                HBox box = loader.load();

                HistoryItemController controller = loader.getController();
                controller.setData(item, parent);

                setGraphic(box);
            } catch (IOException e) {
                e.printStackTrace();
                setGraphic(null);
            }
        }
    }
}

