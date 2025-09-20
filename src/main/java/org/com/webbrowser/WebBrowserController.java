package org.com.webbrowser;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import org.com.webbrowser.model.Shortcut;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class WebBrowserController implements Initializable {
    @FXML
    private HBox tabContainer;
    @FXML
    private VBox contentArea;
    @FXML
    private GridPane shortcutsGrid;
    @FXML
    private HBox navigationBar;
    @FXML
    private Button backButton;
    @FXML
    private Button forwardButton;
    @FXML
    private Button refreshButton;
    @FXML
    private TextField urlField;
    @FXML
    private Button goButton;
    @FXML
    private Label newTabTitle;

    private List<BrowserTab> tabs;
    private int currentTabIndex;
    private VBox newTabPage;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        tabs = new ArrayList<>();
        currentTabIndex = -1;

        setupNavigationBar();

        setupNewTabPage();

        createNewTab();
    }

    private void setupNavigationBar() {
        backButton.setOnAction(e -> {
            BrowserTab currentTab = getCurrentTab();
            if (currentTab != null && currentTab.isWebTab()) {
                WebEngine engine = currentTab.getWebView().getEngine();
                if (engine.getHistory().getCurrentIndex() > 0) {
                    engine.getHistory().go(-1);
                }
            }
        });

        forwardButton.setOnAction(e -> {
            BrowserTab currentTab = getCurrentTab();
            if (currentTab != null && currentTab.isWebTab()) {
                WebEngine engine = currentTab.getWebView().getEngine();
                if (engine.getHistory().getCurrentIndex() < engine.getHistory().getEntries().size() - 1) {
                    engine.getHistory().go(1);
                }
            }
        });

        refreshButton.setOnAction(e -> {
            BrowserTab currentTab = getCurrentTab();
            if (currentTab != null && currentTab.isWebTab()) {
                currentTab.getWebView().getEngine().reload();
            }
        });

        goButton.setOnAction(e -> loadUrl());
        urlField.setOnAction(e -> loadUrl());
    }

    private void setupNewTabPage() {
        newTabPage = new VBox(20);
        newTabPage.setAlignment(Pos.CENTER);
        newTabPage.setPadding(new Insets(50));
        newTabPage.getChildren().addAll(newTabTitle, shortcutsGrid);

        List<Shortcut> shortcuts = List.of(
                new Shortcut("Google", "https://www.google.com", "https://www.google.com/s2/favicons?domain=google.com"),
                new Shortcut("Facebook", "https://www.facebook.com", "https://www.facebook.com/favicon.ico"),
                new Shortcut("YouTube", "https://www.youtube.com", "https://www.youtube.com/s/desktop/0a2f2c6a/img/favicon_32x32.png"),
                new Shortcut("Gmail", "https://mail.google.com", "https://mail.google.com/mail/u/0/x/3x1/favicon.ico")
        );

        int col = 0, row = 0;
        for (Shortcut shortcut : shortcuts) {
            Button shortcutButton = shortcut.createButton();
            shortcutButton.setOnAction(e -> {
                createWebTab(shortcut.getUrl(), shortcut.getName());
                switchToTab(tabs.size() - 1);
            });
            shortcutsGrid.add(shortcutButton, col, row);
            col++;
            if (col == 4) {
                col = 0;
                row++;
            }
        }
    }

    private void createNewTab() {
        BrowserTab newTab = new BrowserTab("New Tab", true);
        tabs.add(newTab);

        Button tabButton = createTabButton("New Tab", tabs.size() - 1);
        tabButton.setOnAction(e -> switchToTab(tabs.size() - 1));
        tabContainer.getChildren().add(tabButton);

        switchToTab(0);
    }

    private void createWebTab(String url, String title) {
        BrowserTab webTab = new BrowserTab(title, false);
        webTab.getWebView().getEngine().load(url);

        webTab.getWebView().getEngine().titleProperty().addListener((obs, oldTitle, newTitle) -> {
            if (tabs.indexOf(webTab) == currentTabIndex) {
                updateCurrentTabTitle(newTitle);
            }
        });

        webTab.getWebView().getEngine().locationProperty().addListener((obs, oldUrl, newUrl) -> {
            if (tabs.indexOf(webTab) == currentTabIndex) {
                urlField.setText(newUrl);
            }
        });

        tabs.add(webTab);

        Button tabButton = createTabButton(title, tabs.size() - 1);
        tabButton.setOnAction(e -> switchToTab(tabs.size() - 1));
        tabContainer.getChildren().add(tabButton);
    }

    private Button createTabButton(String title, int index) {
        Button tabButton = new Button(title);
        tabButton.setPrefSize(150, 30);
        tabButton.setStyle("-fx-background-color: #d0d0d0;");
        return tabButton;
    }

    private void switchToTab(int index) {
        if (index < 0 || index >= tabs.size()) return;

        BrowserTab oldTab = getCurrentTab();
        if (oldTab != null && oldTab.isWebTab()) {
            urlField.setText(oldTab.getWebView().getEngine().getLocation());
        }

        currentTabIndex = index;
        BrowserTab newTab = tabs.get(index);

        if (newTab.isNewTab()) {
            contentArea.getChildren().setAll(newTabPage);
            urlField.clear();
        } else {
            contentArea.getChildren().setAll(newTab.getWebView());
            urlField.setText(newTab.getWebView().getEngine().getLocation());
        }

        for (int i = 0; i < tabContainer.getChildren().size(); i++) {
            Button btn = (Button) tabContainer.getChildren().get(i);
            btn.setStyle(i == index ? "-fx-background-color: #c0c0c0;" : "-fx-background-color: #d0d0d0;");
        }
    }

    private void loadUrl() {
        String url = urlField.getText();
        if (url.isEmpty()) return;

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }

        BrowserTab currentTab = getCurrentTab();
        if (currentTab != null && !currentTab.isNewTab()) {
            currentTab.getWebView().getEngine().load(url);
        } else {
            createWebTab(url, "New Tab");
            switchToTab(tabs.size() - 1);
        }
    }

    private BrowserTab getCurrentTab() {
        return currentTabIndex >= 0 && currentTabIndex < tabs.size() ? tabs.get(currentTabIndex) : null;
    }

    private void updateCurrentTabTitle(String title) {
        if (currentTabIndex >= 0 && currentTabIndex < tabs.size()) {
            tabs.get(currentTabIndex).setTitle(title);
            Button tabBtn = (Button) tabContainer.getChildren().get(currentTabIndex);
            if (tabBtn.getText().length() > 15) {
                tabBtn.setText(title.substring(0, 12) + "...");
            } else {
                tabBtn.setText(title);
            }
        }
    }

    private static class BrowserTab {
        private String title;
        private WebView webView;
        private final boolean isNewTab;

        public BrowserTab(String title, boolean isNewTab) {
            this.title = title;
            this.isNewTab = isNewTab;
            if (!isNewTab) {
                this.webView = new WebView();
            }
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public WebView getWebView() {
            return webView;
        }

        public boolean isWebTab() {
            return !isNewTab;
        }

        public boolean isNewTab() {
            return isNewTab;
        }
    }
}