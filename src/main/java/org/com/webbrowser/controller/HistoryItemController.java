package org.com.webbrowser.controller;


import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.com.webbrowser.model.HistoryEntry;


public class HistoryItemController {
    @FXML private CheckBox selectBox;
    @FXML private Label titleLabel;
    @FXML private Hyperlink urlLink;
    @FXML private Label dateLabel;
    @FXML private Button bookmarkBtn;

    private HistoryEntry entry;
    private HistoryController parent;

    public void setData(HistoryEntry entry, HistoryController parent) {
        this.entry = entry;
        this.parent = parent;

        titleLabel.setText(entry.getTitle());
        urlLink.setText(entry.getUrl());
        dateLabel.setText(entry.getDate());

        selectBox.selectedProperty().bindBidirectional(entry.selectedProperty());

        selectBox.setOnAction(e ->
                parent.updateSelection(entry, selectBox.isSelected())
        );

        urlLink.setOnAction(e ->
                System.out.println("Navigate to: " + entry.getUrl())
        );
    }
}

