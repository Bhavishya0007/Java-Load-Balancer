package lb;

import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class LoadBalancerServer {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: LoadBalancerServer <listen-port> <host:port>[,<host:port>...]");
            System.out.println("Example: LoadBalancerServer 8080 localhost:9001,localhost:9002,localhost:9003");
            System.exit(1);
        }

        int listenPort = Integer.parseInt(args[0]);
        List<Backend> backends = new ArrayList<>();
        for (String hostPort : args[1].split(",")) {
            String[] parts = hostPort.trim().split(":");
            backends.add(new Backend(parts[0], Integer.parseInt(parts[1])));
        }

        BackendPool pool = new BackendPool(backends);

        HealthChecker healthChecker = new HealthChecker(pool);
        healthChecker.start(5);

        HttpServer server = HttpServer.create(new InetSocketAddress(listenPort), 0);
        server.createContext("/", new ProxyHandler(pool));
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        System.out.printf("Load balancer listening on :%d, backends=%s%n", listenPort, backends);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            healthChecker.stop();
            server.stop(1);
        }));
    }
}
