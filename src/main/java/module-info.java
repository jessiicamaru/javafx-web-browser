module org.com.webbrowser {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires org.jsoup;

    opens org.com.webbrowser to javafx.fxml;
    exports org.com.webbrowser;
    exports org.com.webbrowser.controller;
    opens org.com.webbrowser.controller to javafx.fxml;
}