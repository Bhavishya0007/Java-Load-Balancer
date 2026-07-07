package lb;

import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/** Trivial backend used to test the load balancer end-to-end. Responds with its own port. */
public class BackendServer {

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(args[0]);

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", exchange -> {
            String response = "Hello from backend on port " + port + "\n";
            exchange.sendResponseHeaders(200, response.length());
            exchange.getResponseBody().write(response.getBytes());
            exchange.close();
        });
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        System.out.println("Backend listening on :" + port);
    }
}
