module org.com.webbrowser {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;

    opens org.com.webbrowser to javafx.fxml;
    exports org.com.webbrowser;
}