package eu.frigo.dispensa.sync.webdav;

public class WebDavConfig {
    public final String url;
    public final String username;
    public final String password;
    public final String path;

    public WebDavConfig(String url, String username, String password, String path) {
        this.url = url;
        this.username = username;
        this.password = password;
        this.path = path;
    }
}
