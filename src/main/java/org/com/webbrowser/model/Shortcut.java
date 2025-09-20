package org.com.webbrowser.model;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;

public class Shortcut {
    private String name;
    private String url;
    private String faviconUrl;

    public Shortcut(String name, String url, String faviconUrl) {
        this.name = name;
        this.url = url;
        this.faviconUrl = faviconUrl;
    }

    public Button createButton() {
        Button button = new Button();
        button.setPrefSize(100, 100);
        button.setStyle("-fx-background-color: white; -fx-border-color: #ccc; -fx-border-radius: 8px;");

        VBox content = new VBox(5);
        content.setAlignment(Pos.CENTER);

        try {
            Image favicon = new Image(faviconUrl, 32, 32, true, true);
            ImageView imgView = new ImageView(favicon);
            imgView.setPreserveRatio(true);
            content.getChildren().add(imgView);
        } catch (Exception e) {
            Label fallback = new Label("🔗");
            fallback.setStyle("-fx-font-size: 24px;");
            content.getChildren().add(fallback);
        }

        Label label = new Label(name);
        label.setStyle("-fx-font-size: 12px; -fx-text-alignment: center;");
        content.getChildren().add(label);

        button.setGraphic(content);
        return button;
    }

    public String getUrl() {
        return url;
    }

    public String getName() {
        return name;
    }
}