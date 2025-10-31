module org.com.webbrowser {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires org.jsoup;
    requires com.google.gson;
    requires io.github.cdimascio.dotenv.java;

    opens org.com.webbrowser.model to com.google.gson;
    opens org.com.webbrowser to javafx.fxml;

    exports org.com.webbrowser;
    exports org.com.webbrowser.controller;
    opens org.com.webbrowser.controller to javafx.fxml;
}