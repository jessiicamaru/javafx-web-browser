package org.com.webbrowser.controller;

import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.com.webbrowser.model.HistoryEntry;
import org.com.webbrowser.service.BookmarkService;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public class HistoryController implements Initializable {

    @FXML
    private TableView<HistoryEntry> historyTable;
    @FXML
    private TableColumn<HistoryEntry, Boolean> selectCol;
    @FXML
    private TableColumn<HistoryEntry, String> timeCol;
    @FXML
    private TableColumn<HistoryEntry, String> titleCol;
    @FXML
    private TableColumn<HistoryEntry, String> urlCol;
    @FXML
    private Button bookmarkBtn;
    @FXML
    private Button deleteBtn;
    @FXML
    private VBox rootLayout;
    @FXML
    private HBox actionBar;

    private ObservableList<HistoryEntry> globalHistory;
    private BookmarkService bookmarkService;
    private java.util.function.Consumer<String> openUrlCallback;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        selectCol.setCellValueFactory(cellData -> cellData.getValue().selectedProperty());
        selectCol.setCellFactory(CheckBoxTableCell.forTableColumn(selectCol));
        selectCol.setEditable(true);
        selectCol.setPrefWidth(80);

        timeCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getDate()));
        titleCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getTitle()));
        urlCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getUrl()));

        historyTable.setRowFactory(tv -> {
            TableRow<HistoryEntry> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty() && openUrlCallback != null) {
                    openUrlCallback.accept(row.getItem().getUrl());
                }
            });
            return row;
        });

        bookmarkBtn.setOnAction(_ -> addSelectedToBookmarks());
        deleteBtn.setOnAction(_ -> deleteSelected());
    }

    public void setData(ObservableList<HistoryEntry> globalHistory,
                        BookmarkService bookmarkService,
                        java.util.function.Consumer<String> openUrlCallback) {
        this.globalHistory = globalHistory;
        this.bookmarkService = bookmarkService;
        this.openUrlCallback = openUrlCallback;
        historyTable.setItems(globalHistory);
    }

    private void addSelectedToBookmarks() {
        List<HistoryEntry> checked = globalHistory.stream()
                .filter(h -> h.selectedProperty().get())
                .collect(Collectors.toList());
        for (HistoryEntry entry : checked) {
            String name = (entry.getTitle() != null && !entry.getTitle().isEmpty()) ? entry.getTitle() : entry.getUrl();
            bookmarkService.addBookmark(name, entry.getUrl(), null);
            entry.selectedProperty().set(false);
        }
    }

    private void deleteSelected() {
        List<HistoryEntry> toDelete = globalHistory.stream()
                .filter(h -> h.selectedProperty().get())
                .collect(Collectors.toList());
        globalHistory.removeAll(toDelete);
    }
}
