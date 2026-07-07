package lb;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class HealthChecker {
    private static final int CONNECT_TIMEOUT_MS = 1000;

    private final BackendPool pool;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public HealthChecker(BackendPool pool) {
        this.pool = pool;
    }

    public void start(long intervalSeconds) {
        scheduler.scheduleAtFixedRate(this::checkAll, 0, intervalSeconds, TimeUnit.SECONDS);
    }

    public void stop() {
        scheduler.shutdownNow();
    }

    void checkAll() {
        for (Backend backend : pool.all()) {
            boolean ok = ping(backend);
            if (ok != backend.isHealthy()) {
                System.out.printf("[health] %s is now %s%n", backend, ok ? "UP" : "DOWN");
            }
            backend.setHealthy(ok);
        }
    }

    private boolean ping(Backend backend) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(backend.host(), backend.port()), CONNECT_TIMEOUT_MS);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
