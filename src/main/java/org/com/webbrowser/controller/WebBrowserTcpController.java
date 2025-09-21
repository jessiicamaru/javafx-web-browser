package org.com.webbrowser.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import org.com.webbrowser.tcp.HttpClient;
import org.com.webbrowser.tcp.HttpResponse;

import java.net.URL;
import java.util.ResourceBundle;

public class WebBrowserTcpController implements Initializable {
    @FXML private TextField urlField;
    @FXML private Button goButton;
    @FXML private TextArea displayArea;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        goButton.setOnAction(e -> loadUrl());
        urlField.setOnAction(e -> loadUrl());
    }

    private void loadUrl() {
        String input = urlField.getText();
        if (input == null || input.isEmpty()) return;

        try {
            URL u = new URL(input.startsWith("http") ? input : "http://" + input);
            String host = u.getHost();
            String path = u.getPath();
            if (path == null || path.isEmpty()) path = "/";

            final String finalHost = host;
            final String finalPath = path;

            new Thread(() -> {
                try {
                    HttpResponse resp = HttpClient.fetch(finalHost, finalPath);
                    String body = resp.getBody();
                    Platform.runLater(() -> displayArea.setText(body));
                } catch (Exception ex) {
                    Platform.runLater(() -> displayArea.setText("Error: " + ex.getMessage()));
                }
            }).start();

        } catch (Exception ex) {
            displayArea.setText("Invalid URL: " + ex.getMessage());
        }
    }

}

