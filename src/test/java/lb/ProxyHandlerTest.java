package lb;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class ProxyHandlerTest {

    private final List<HttpServer> serversToStop = new ArrayList<>();
    private final HttpClient client = HttpClient.newHttpClient();

    @AfterEach
    void tearDown() {
        for (HttpServer server : serversToStop) {
            server.stop(0);
        }
    }

    private Backend startBackend(String responseBody) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = responseBody.getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        serversToStop.add(server);
        return new Backend("localhost", server.getAddress().getPort());
    }

    private HttpServer startLoadBalancer(BackendPool pool) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", new ProxyHandler(pool));
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        serversToStop.add(server);
        return server;
    }

    private HttpResponse<String> get(HttpServer lb, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + lb.getAddress().getPort() + path))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void forwardsRequestToBackendAndReturnsItsResponse() throws Exception {
        Backend backend = startBackend("hello from backend");
        HttpServer lb = startLoadBalancer(new BackendPool(List.of(backend)));

        HttpResponse<String> response = get(lb, "/anything");

        assertEquals(200, response.statusCode());
        assertEquals("hello from backend", response.body());
    }

    @Test
    void roundRobinsAcrossMultipleBackends() throws Exception {
        Backend b1 = startBackend("one");
        Backend b2 = startBackend("two");
        HttpServer lb = startLoadBalancer(new BackendPool(List.of(b1, b2)));

        assertEquals("one", get(lb, "/").body());
        assertEquals("two", get(lb, "/").body());
        assertEquals("one", get(lb, "/").body());
    }

    @Test
    void fallsBackToNextBackendWhenFirstIsUnreachable() throws Exception {
        // A backend that is marked healthy but nothing is actually listening on its port,
        // simulating the window between a crash and the next health check.
        Backend deadButMarkedHealthy = new Backend("localhost", 1);
        Backend live = startBackend("still alive");
        HttpServer lb = startLoadBalancer(new BackendPool(List.of(deadButMarkedHealthy, live)));

        HttpResponse<String> response = get(lb, "/");

        assertEquals(200, response.statusCode());
        assertEquals("still alive", response.body());
        assertFalse(deadButMarkedHealthy.isHealthy(), "failed backend should be marked unhealthy after the failed attempt");
    }

    @Test
    void returns503WhenNoBackendsAreHealthy() throws Exception {
        Backend unhealthy = new Backend("localhost", 1);
        unhealthy.setHealthy(false);
        HttpServer lb = startLoadBalancer(new BackendPool(List.of(unhealthy)));

        HttpResponse<String> response = get(lb, "/");

        assertEquals(503, response.statusCode());
    }

    @Test
    void returns502WhenAllBackendsAreUnreachable() throws Exception {
        Backend deadA = new Backend("localhost", 1);
        Backend deadB = new Backend("localhost", 2);
        HttpServer lb = startLoadBalancer(new BackendPool(List.of(deadA, deadB)));

        HttpResponse<String> response = get(lb, "/");

        assertEquals(502, response.statusCode());
    }
}
