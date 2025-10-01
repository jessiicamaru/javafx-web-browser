package org.com.webbrowser.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.collections.*;
import org.com.webbrowser.model.HistoryEntry;

public class HistoryController {

    @FXML private ListView<HistoryEntry> historyList;
    @FXML private HBox selectionBar;
    @FXML private Label selectedCount;
    @FXML private Button openSelectedBtn;
    @FXML private Button deleteSelectedBtn;

    private final ObservableList<HistoryEntry> globalHistory = FXCollections.observableArrayList();
    private final ObservableList<HistoryEntry> selectedItems = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        historyList.setItems(globalHistory);

        historyList.setCellFactory(listView -> new HistoryListCell(this));
    }

    public void updateSelection(HistoryEntry entry, boolean selected) {
        if (selected) selectedItems.add(entry);
        else selectedItems.remove(entry);

        selectionBar.setVisible(!selectedItems.isEmpty());
        selectedCount.setText(selectedItems.size() + " selected");
    }

    @FXML
    private void onDeleteSelected() {
        globalHistory.removeAll(selectedItems);
        selectedItems.clear();
        selectionBar.setVisible(false);
    }

}
