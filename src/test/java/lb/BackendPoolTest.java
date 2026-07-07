package lb;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BackendPoolTest {

    @Test
    void rejectsEmptyPool() {
        assertThrows(IllegalArgumentException.class, () -> new BackendPool(List.of()));
    }

    @Test
    void cyclesThroughBackendsInRoundRobinOrder() {
        Backend a = new Backend("a", 1);
        Backend b = new Backend("b", 2);
        Backend c = new Backend("c", 3);
        BackendPool pool = new BackendPool(List.of(a, b, c));

        assertEquals(a, pool.next());
        assertEquals(b, pool.next());
        assertEquals(c, pool.next());
        assertEquals(a, pool.next());
    }

    @Test
    void skipsUnhealthyBackends() {
        Backend a = new Backend("a", 1);
        Backend b = new Backend("b", 2);
        Backend c = new Backend("c", 3);
        b.setHealthy(false);
        BackendPool pool = new BackendPool(List.of(a, b, c));

        assertEquals(a, pool.next());
        assertEquals(c, pool.next());
        assertEquals(a, pool.next());
    }

    @Test
    void returnsNullWhenNoBackendsAreHealthy() {
        Backend a = new Backend("a", 1);
        a.setHealthy(false);
        BackendPool pool = new BackendPool(List.of(a));

        assertNull(pool.next());
    }

    @Test
    void isThreadSafeUnderConcurrentSelection() throws InterruptedException {
        Backend a = new Backend("a", 1);
        Backend b = new Backend("b", 2);
        BackendPool pool = new BackendPool(List.of(a, b));

        int threads = 8;
        int perThread = 1000;
        java.util.concurrent.atomic.AtomicInteger total = new java.util.concurrent.atomic.AtomicInteger();
        Thread[] workers = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            workers[i] = new Thread(() -> {
                for (int j = 0; j < perThread; j++) {
                    if (pool.next() != null) {
                        total.incrementAndGet();
                    }
                }
            });
        }
        for (Thread t : workers) t.start();
        for (Thread t : workers) t.join();

        assertEquals(threads * perThread, total.get());
    }
}
