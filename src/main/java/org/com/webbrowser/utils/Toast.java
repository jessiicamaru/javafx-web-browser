package org.com.webbrowser.utils;

import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.util.Duration;

public class Toast {

    public static void show(Stage stage, String message, int durationMillis, boolean success) {
        Platform.runLater(() -> {
            Popup popup = new Popup();
            popup.setAutoFix(true);
            popup.setAutoHide(true);
            popup.setHideOnEscape(true);

            Label label = new Label(message);
            label.setStyle("-fx-background-color:" + (success ? "#4CAF50" : "#F44336") + ";"
                    + "-fx-text-fill: white;"
                    + "-fx-padding: 10 20 10 20;"
                    + "-fx-background-radius: 10;"
                    + "-fx-font-size: 14;");

            StackPane root = new StackPane(label);
            root.setStyle("-fx-padding: 10;");
            Scene scene = stage.getScene();
            popup.getContent().add(root);

            double centerX = stage.getX() + scene.getWidth() / 2 - 100;
            double topY = stage.getY() * 1.2;

            popup.show(stage, centerX, topY);

            FadeTransition fade = new FadeTransition(Duration.millis(500), root);
            fade.setFromValue(1.0);
            fade.setToValue(0.0);
            fade.setDelay(Duration.millis(durationMillis));
            fade.setOnFinished(e -> popup.hide());
            fade.play();
        });
    }
}

