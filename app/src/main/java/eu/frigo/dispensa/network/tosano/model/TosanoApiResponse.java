package eu.frigo.dispensa.network.tosano.model;

import com.google.gson.annotations.SerializedName;

public class TosanoApiResponse {

    @SerializedName("response")
    private ResponseStatus responseStatus;

    @SerializedName("user")
    private User user;

    @SerializedName("data")
    private DataContainer data;

    // Getters e Setters
    public ResponseStatus getResponseStatus() {
        return responseStatus;
    }

    public void setResponseStatus(ResponseStatus responseStatus) {
        this.responseStatus = responseStatus;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public DataContainer getData() {
        return data;
    }

    public void setData(DataContainer data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "TosanoApiResponse{" +
                "responseStatus=" + responseStatus +
                ", user=" + user +
                ", data=" + data +
                '}';
    }
}
