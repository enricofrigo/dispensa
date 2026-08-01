package eu.frigo.dispensa.sync.webdav;

public class WebDavConfig {
    public final String url;
    public final String username;
    public final String password;
    public final String path;
    public final String pantryKey;
    public final String pantryName;
    public final String ownerDeviceId;
    public final boolean isShared;

    public WebDavConfig(String url, String username, String password, String path, String pantryKey, String pantryName, String ownerDeviceId, boolean isShared) {
        this.url = url;
        this.username = username;
        this.password = password;
        this.path = path;
        this.pantryKey = pantryKey;
        this.pantryName = pantryName;
        this.ownerDeviceId = ownerDeviceId;
        this.isShared = isShared;
    }
}
