package org.com.webbrowser.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.com.webbrowser.WebBrowserApplication;
import org.com.webbrowser.tcp.HttpClient;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import javafx.scene.Node;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.io.IOException;
import java.net.URL;
import java.util.*;

public class WebBrowserTcpController implements Initializable {
    @FXML
    private Button backButton;
    @FXML
    private Button forwardButton;
    @FXML
    private TextField urlField;
    @FXML
    private Button goButton;
    @FXML
    private Button addTabButton;
    @FXML
    private TabPane tabPane;
    @FXML
    private ToolBar bookmarkBar;
    @FXML
    private Button bookmarkButton;

    private final Map<String, String> bookmarks = new HashMap<>();
    private final Map<Tab, List<String>> history = new HashMap<>();
    private final Map<Tab, Integer> historyIndex = new HashMap<>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        addNewTab("newtab");

        goButton.setOnAction(e -> loadUrl(getCurrentTab(), urlField.getText(), true));
        urlField.setOnAction(e -> loadUrl(getCurrentTab(), urlField.getText(), true));
        addTabButton.setOnAction(e -> addNewTab("newtab"));
        backButton.setOnAction(e -> goBack());
        forwardButton.setOnAction(e -> goForward());

        tabPane.getTabs().addListener((javafx.collections.ListChangeListener<Tab>) change -> {
            if (tabPane.getTabs().isEmpty()) {
                Platform.exit();
            }
        });

        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab == null) {
                urlField.clear();
            } else {
                String url = (String) newTab.getUserData();
                if (url != null) {
                    urlField.setText(url);
                } else {
                    urlField.clear();
                }
            }
        });


        bookmarkButton.setOnAction(e -> {
            String currentUrl = urlField.getText();
            if (currentUrl == null || currentUrl.isEmpty()) return;

            TextInputDialog dialog = new TextInputDialog("Bookmark name");
            dialog.setTitle("Add Bookmark");
            dialog.setHeaderText("Add new bookmark");
            dialog.setContentText("Name:");

            Optional<String> result = dialog.showAndWait();
            result.ifPresent(name -> {
                bookmarks.put(name, currentUrl);
                addBookmarkButton(name, currentUrl);

                Alert alert = new Alert(AlertType.INFORMATION,
                        "Saved bookmark: " + name, ButtonType.OK);
                alert.showAndWait();
            });
        });
    }

    private void addBookmarkButton(String name, String url) {
        Button bmButton = new Button(name);
        bmButton.setOnAction(ev -> loadUrl(getCurrentTab(), url, true));

        ContextMenu menu = new ContextMenu();

        MenuItem editItem = new MenuItem("Edit");
        editItem.setOnAction(e -> {
            TextInputDialog nameDialog = new TextInputDialog(name);
            nameDialog.setTitle("Edit Bookmark");
            nameDialog.setHeaderText("Edit bookmark name");
            nameDialog.setContentText("Name:");

            Optional<String> newName = nameDialog.showAndWait();
            newName.ifPresent(updatedName -> {
                TextInputDialog urlDialog = new TextInputDialog(url);
                urlDialog.setTitle("Edit Bookmark");
                urlDialog.setHeaderText("Edit bookmark link");
                urlDialog.setContentText("URL:");

                Optional<String> newUrl = urlDialog.showAndWait();
                newUrl.ifPresent(updatedUrl -> {
                    bookmarks.remove(name);
                    bookmarks.put(updatedName, updatedUrl);

                    bmButton.setText(updatedName);
                    bmButton.setOnAction(ev -> loadUrl(getCurrentTab(), updatedUrl, true));
                });
            });
        });

        MenuItem deleteItem = new MenuItem("Delete");
        deleteItem.setOnAction(e -> {
            bookmarks.remove(name);
            bookmarkBar.getItems().remove(bmButton);
        });

        menu.getItems().addAll(editItem, deleteItem);
        bmButton.setContextMenu(menu);

        bookmarkBar.getItems().add(bmButton);
    }

    private void addNewTab(String url) {
        Tab tab = new Tab("New Tab");
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);

        history.put(tab, new ArrayList<>());
        historyIndex.put(tab, -1);

        if (url.equals("newtab")) {
            try {
                FXMLLoader loader = new FXMLLoader(WebBrowserApplication.class.getResource("new-tab.fxml"));
                Parent newTabRoot = loader.load();
                tab.setContent(newTabRoot);

                TextField searchField = (TextField) newTabRoot.lookup("#searchField");
                searchField.setOnAction(e -> {
                    String input = searchField.getText();
                    loadUrl(tab, input, true);
                });

            } catch (IOException ex) {
                tab.setContent(new Label("Error loading new tab page"));
            }
        } else {
            loadUrl(tab, url, true);
        }
    }

    private void loadUrl(Tab tab, String input, boolean addToHistory) {
        if (tab == null || input == null || input.isEmpty()) return;

        new Thread(() -> {
            try {
                String url = normalizeUrl(input);
                String withoutProtocol = url.replaceFirst("https://", "");
                String host;
                String path = "/";
                int slashIdx = withoutProtocol.indexOf("/");
                if (slashIdx >= 0) {
                    host = withoutProtocol.substring(0, slashIdx);
                    path = withoutProtocol.substring(slashIdx);
                } else {
                    host = withoutProtocol;
                }

                var response = HttpClient.fetch(host, path);
                String html = response.getBody();

                Platform.runLater(() -> {
                    Node rendered = renderHtml(html);
                    ScrollPane scroll = new ScrollPane(rendered);
                    tab.setContent(scroll);
                    tab.setText(host);
                    urlField.setText(url);
                    updateHistory(tab, url, addToHistory);
                });

                tab.setUserData(url);

            } catch (Exception ex) {
                Platform.runLater(() -> {
                    TextArea errorArea = new TextArea("Error: " + ex.getMessage());
                    tab.setContent(errorArea);
                });
            }
        }).start();
    }


    private Node renderHtml(String html) {
        Document doc = Jsoup.parse(html);
        Element body = doc.body();
        return renderElement(body);
    }

    private Node renderElement(Element el) {
        switch (el.tagName()) {
            case "div":
                VBox vbox = new VBox();
                for (Element child : el.children()) {
                    vbox.getChildren().add(renderElement(child));
                }
                return vbox;
            case "p":
                return new Label(el.text());
            case "h1":
                Label h1 = new Label(el.text());
                h1.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");
                return h1;
            case "a":
                Hyperlink link = new Hyperlink(el.text());
                link.setOnAction(e -> loadUrl(getCurrentTab(), el.attr("href"), true));
                return link;
            case "img":
                try {
                    Image img = new Image(el.attr("src"), true);
                    return new ImageView(img);
                } catch (Exception ex) {
                    return new Label("[Image failed]");
                }
            default:
                VBox container = new VBox();
                for (Element child : el.children()) {
                    container.getChildren().add(renderElement(child));
                }
                if (!el.ownText().isEmpty()) {
                    container.getChildren().add(new Label(el.ownText()));
                }
                return container;
        }
    }

    private void updateHistory(Tab tab, String url, boolean addToHistory) {
        if (!addToHistory) return;
        List<String> urls = history.get(tab);
        int idx = historyIndex.get(tab);

        if (idx < urls.size() - 1) {
            urls = urls.subList(0, idx + 1);
            history.put(tab, new ArrayList<>(urls));
        }

        urls.add(url);
        historyIndex.put(tab, urls.size() - 1);
    }

    private Tab getCurrentTab() {
        return tabPane.getSelectionModel().getSelectedItem();
    }

    private void goBack() {
        Tab tab = getCurrentTab();
        if (tab == null) return;

        int idx = historyIndex.get(tab);
        if (idx > 0) {
            historyIndex.put(tab, idx - 1);
            loadUrl(tab, history.get(tab).get(idx - 1), false);
        }
    }

    private void goForward() {
        Tab tab = getCurrentTab();
        if (tab == null) return;

        int idx = historyIndex.get(tab);
        List<String> urls = history.get(tab);
        if (idx < urls.size() - 1) {
            historyIndex.put(tab, idx + 1);
            loadUrl(tab, urls.get(idx + 1), false);
        }
    }

    private String normalizeUrl(String input) {
        if (input == null || input.isEmpty()) return "";

        String lower = input.toLowerCase();

        if (lower.startsWith("http://")) {
            return "https://" + input.substring(7);
        } else if (lower.startsWith("https://")) {
            return input;
        } else {
            return "https://" + input;
        }
    }

}
