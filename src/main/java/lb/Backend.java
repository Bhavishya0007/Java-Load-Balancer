package lb;

public class Backend {
    private final String host;
    private final int port;
    private volatile boolean healthy = true;

    public Backend(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public boolean isHealthy() {
        return healthy;
    }

    public void setHealthy(boolean healthy) {
        this.healthy = healthy;
    }

    @Override
    public String toString() {
        return host + ":" + port;
    }
}
