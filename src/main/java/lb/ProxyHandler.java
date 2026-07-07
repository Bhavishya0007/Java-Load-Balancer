package lb;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ProxyHandler implements HttpHandler {
    private static final int CONNECT_TIMEOUT_MS = 2000;
    private static final int READ_TIMEOUT_MS = 5000;
    private static final int MAX_ATTEMPTS = 2;

    // Headers that must not be copied across hops.
    private static final Set<String> HOP_BY_HOP = Set.of(
            "connection", "keep-alive", "transfer-encoding", "upgrade", "host", "content-length");

    private final BackendPool pool;

    public ProxyHandler(BackendPool pool) {
        this.pool = pool;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        IOException lastError = null;

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            Backend backend = pool.next();
            if (backend == null) {
                sendError(exchange, 503, "No healthy backends available");
                return;
            }
            try {
                forward(exchange, backend);
                return;
            } catch (IOException e) {
                lastError = e;
                System.out.printf("[proxy] backend %s failed: %s%n", backend, e.getMessage());
                backend.setHealthy(false);
            }
        }

        sendError(exchange, 502, "Bad Gateway: " + (lastError != null ? lastError.getMessage() : "unknown error"));
    }

    private void forward(HttpExchange exchange, Backend backend) throws IOException {
        URL url;
        try {
            URI requestUri = exchange.getRequestURI();
            URI backendUri = new URI("http", null, backend.host(), backend.port(),
                    requestUri.getPath(), requestUri.getQuery(), null);
            url = backendUri.toURL();
        } catch (URISyntaxException e) {
            throw new IOException("invalid backend URI", e);
        }
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(exchange.getRequestMethod());
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setInstanceFollowRedirects(false);

        for (Map.Entry<String, List<String>> header : exchange.getRequestHeaders().entrySet()) {
            if (HOP_BY_HOP.contains(header.getKey().toLowerCase())) {
                continue;
            }
            for (String value : header.getValue()) {
                conn.addRequestProperty(header.getKey(), value);
            }
        }

        byte[] requestBody = exchange.getRequestBody().readAllBytes();
        if (requestBody.length > 0 || methodAllowsBody(exchange.getRequestMethod())) {
            conn.setDoOutput(true);
            try (OutputStream out = conn.getOutputStream()) {
                out.write(requestBody);
            }
        }

        int status;
        InputStream responseStream;
        try {
            status = conn.getResponseCode();
            responseStream = conn.getInputStream();
        } catch (IOException e) {
            status = conn.getResponseCode();
            responseStream = conn.getErrorStream();
            if (responseStream == null) {
                throw e;
            }
        }

        for (Map.Entry<String, List<String>> header : conn.getHeaderFields().entrySet()) {
            if (header.getKey() == null || HOP_BY_HOP.contains(header.getKey().toLowerCase())) {
                continue;
            }
            exchange.getResponseHeaders().put(header.getKey(), header.getValue());
        }

        byte[] responseBody = responseStream.readAllBytes();
        exchange.sendResponseHeaders(status, responseBody.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBody);
        }
    }

    private boolean methodAllowsBody(String method) {
        return method.equals("POST") || method.equals("PUT") || method.equals("PATCH");
    }

    private void sendError(HttpExchange exchange, int status, String message) throws IOException {
        byte[] body = message.getBytes();
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }
}
