package eu.frigo.dispensa.sync.webdav;

public class WebDavConfig {
    public final String url;
    public final String username;
    public final String password;
    public final String path;
    public final String pantryKey;
    public final boolean isShared;

    public WebDavConfig(String url, String username, String password, String path, String pantryKey, boolean isShared) {
        this.url = url;
        this.username = username;
        this.password = password;
        this.path = path;
        this.pantryKey = pantryKey;
        this.isShared = isShared;
    }
}
