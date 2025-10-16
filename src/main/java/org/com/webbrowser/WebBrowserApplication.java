package org.com.webbrowser;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class WebBrowserApplication extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(WebBrowserApplication.class.getResource("auth.fxml"));
        Scene scene = new Scene(loader.load(), 400, 450);
        stage.setTitle("Login / Register");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}