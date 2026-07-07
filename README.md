# Simple Java Load Balancer

An L7 HTTP reverse proxy load balancer built on the JDK's built-in
`com.sun.net.httpserver.HttpServer` (no external dependencies).

## Design

- `Backend` — a single upstream (host, port, healthy flag).
- `BackendPool` — round-robin selection over currently healthy backends
  (`AtomicInteger` index, no locking needed on the hot path).
- `HealthChecker` — background thread that TCP-connects to each backend
  every few seconds and flips its healthy flag.
- `ProxyHandler` — per-request handler: picks a backend, forwards the
  request (headers + body), streams the response back, retries once
  against another backend on failure, otherwise returns 502/503.
- `LoadBalancerServer` — wires it all together; thread-per-request via a
  cached thread pool.
- `BackendServer` — trivial test backend that just echoes its own port,
  for exercising the load balancer end-to-end.

## Tests

Unit tests use JUnit 5 via Maven:

```sh
mvn test
```

- `BackendTest` — basic state (defaults, getters, `toString`).
- `BackendPoolTest` — round-robin order, skipping unhealthy backends, empty-pool
  rejection, and thread-safety of concurrent `next()` calls.
- `HealthCheckerTest` — a real `ServerSocket` stands in for a healthy backend;
  an unbound port stands in for a dead one; verifies the healthy flag flips
  both ways.
- `ProxyHandlerTest` — end-to-end against real `HttpServer` instances: forwards
  and returns backend responses, round-robins across backends, fails over to
  the next backend when one is unreachable, and returns 503/502 when none or
  all backends are down.

## Build & run

Everything runs through Maven — `mvn compile` once, then `exec:java` to launch
either class:

```sh
mvn compile

# terminal 1: three test backends on 9001-9003
mvn exec:java -Dexec.mainClass=lb.BackendServer -Dexec.args="9001" &
mvn exec:java -Dexec.mainClass=lb.BackendServer -Dexec.args="9002" &
mvn exec:java -Dexec.mainClass=lb.BackendServer -Dexec.args="9003" &

# terminal 2: load balancer on 8080, forwarding to those backends
mvn exec:java -Dexec.args="8080 localhost:9001,localhost:9002,localhost:9003"

# terminal 3: hit it a few times, watch it round-robin
curl localhost:8080/
curl localhost:8080/
curl localhost:8080/
```

`lb.LoadBalancerServer` is the default `mainClass` (set in `pom.xml`), so it
only needs `-Dexec.args`; the backend needs `-Dexec.mainClass` to override it.

Kill one of the backend processes and within ~5s (health check interval)
it drops out of rotation; requests keep succeeding against the remaining
backends. Bring it back and it's added back automatically.

## Not included (out of scope for "simple")

- Weighted / least-connections balancing
- Sticky sessions
- TLS termination
- Config file / hot reload
- Metrics export
