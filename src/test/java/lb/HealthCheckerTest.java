package lb;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HealthCheckerTest {

    @Test
    void marksListeningBackendAsHealthy() throws IOException {
        try (ServerSocket listening = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            Backend backend = new Backend("localhost", listening.getLocalPort());
            HealthChecker checker = new HealthChecker(new BackendPool(List.of(backend)));

            checker.checkAll();

            assertTrue(backend.isHealthy());
        }
    }

    @Test
    void marksUnreachableBackendAsUnhealthy() throws IOException {
        int freePort;
        try (ServerSocket temp = new ServerSocket(0)) {
            freePort = temp.getLocalPort();
        }
        // port is now closed again, so nothing is listening on it

        Backend backend = new Backend("localhost", freePort);
        HealthChecker checker = new HealthChecker(new BackendPool(List.of(backend)));

        checker.checkAll();

        assertFalse(backend.isHealthy());
    }

    @Test
    void recoversBackendOnceItStartsListeningAgain() throws IOException {
        int port;
        try (ServerSocket temp = new ServerSocket(0)) {
            port = temp.getLocalPort();
        }

        Backend backend = new Backend("localhost", port);
        HealthChecker checker = new HealthChecker(new BackendPool(List.of(backend)));

        checker.checkAll();
        assertFalse(backend.isHealthy());

        try (ServerSocket listening = new ServerSocket(port, 0, InetAddress.getLoopbackAddress())) {
            checker.checkAll();
            assertTrue(backend.isHealthy());
        }
    }
}
