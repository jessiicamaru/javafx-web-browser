package org.com.webbrowser.session;

import org.com.webbrowser.model.User;

public class UserSession {
    private static UserSession instance;
    private User currentUser;

    private UserSession() {}

    public static UserSession getInstance() {
        if (instance == null) {
            instance = new UserSession();
        }
        return instance;
    }

    // Gán thông tin user khi login thành công
    public void setUser(User user) {
        this.currentUser = user;
    }

    public User getUser() {
        return currentUser;
    }

    public Integer getUserId() {
        return currentUser != null ? currentUser.getId() : null;
    }

    public String getUsername() {
        return currentUser != null ? currentUser.getUsername() : null;
    }

    public boolean isLoggedIn() {
        return currentUser != null;
    }

    public void clear() {
        currentUser = null;
    }
}
