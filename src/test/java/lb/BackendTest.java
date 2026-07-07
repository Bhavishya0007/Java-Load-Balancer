package lb;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BackendTest {

    @Test
    void isHealthyByDefault() {
        assertTrue(new Backend("localhost", 8080).isHealthy());
    }

    @Test
    void exposesHostAndPort() {
        Backend backend = new Backend("example.com", 9000);
        assertEquals("example.com", backend.host());
        assertEquals(9000, backend.port());
    }

    @Test
    void toStringIsHostColonPort() {
        assertEquals("example.com:9000", new Backend("example.com", 9000).toString());
    }
}
