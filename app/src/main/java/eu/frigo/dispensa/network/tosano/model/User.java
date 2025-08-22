// User.java
package eu.frigo.dispensa.network.tosano.model;

import com.google.gson.annotations.SerializedName;

public class User {

    @SerializedName("userId")
    private int userId;

    @SerializedName("login")
    private String login;

    @SerializedName("profileConfirmed")
    private boolean profileConfirmed;

    // Getters e Setters
    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public boolean isProfileConfirmed() {
        return profileConfirmed;
    }

    public void setProfileConfirmed(boolean profileConfirmed) {
        this.profileConfirmed = profileConfirmed;
    }

    @Override
    public String toString() {
        return "User{" +
                "userId=" + userId +
                ", login='" + login + '\'' +
                ", profileConfirmed=" + profileConfirmed +
                '}';
    }
}