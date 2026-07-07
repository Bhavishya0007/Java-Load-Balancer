package lb;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class BackendPool {
    private final List<Backend> backends;
    private final AtomicInteger rrIndex = new AtomicInteger(0);

    public BackendPool(List<Backend> backends) {
        if (backends.isEmpty()) {
            throw new IllegalArgumentException("backend pool cannot be empty");
        }
        this.backends = backends;
    }

    public List<Backend> all() {
        return backends;
    }

    /** Picks the next healthy backend, round-robin. Returns null if none are healthy. */
    public Backend next() {
        List<Backend> healthy = backends.stream().filter(Backend::isHealthy).toList();
        if (healthy.isEmpty()) {
            return null;
        }
        int i = Math.floorMod(rrIndex.getAndIncrement(), healthy.size());
        return healthy.get(i);
    }
}
