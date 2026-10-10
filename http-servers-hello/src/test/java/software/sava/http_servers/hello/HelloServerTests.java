package software.sava.http_servers.hello;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.sava.http_servers.core.server.HttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/// Boots the demo against every backend discovered over ServiceLoader — the only place the
/// provider wiring (`module-info` `provides`/`uses`) is exercised end to end.
final class HelloServerTests {

  private static int freePort() throws Exception {
    try (final var socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }

  private static java.net.http.HttpResponse<String> get(final HttpClient client, final int port, final String path)
      throws Exception {
    return client.send(
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build(),
        BodyHandlers.ofString()
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "JDKHttpServerBuilderFactory",
      "JettyServerBuilderFactory",
      "FusionAuthBuilderFactory",
      "HelidonBuilderFactory",
      "NettyBuilderFactory"
  })
  void helloServesAndExcludedPathDoesNot(final String factoryName) throws Exception {
    try (final var started = startRetrying(factoryName, HelloServerTests::freePort);
         final var client = HttpClient.newHttpClient()) {
      final var hello = get(client, started.port(), "/hello");
      assertEquals(200, hello.statusCode(), factoryName);
      assertEquals("application/json", hello.headers().firstValue("Content-Type").orElse(null), factoryName);
      assertTrue(hello.body().contains("\"message\": \"Hello\""), factoryName + " body: " + hello.body());

      final var excluded = get(client, started.port(), "/exclude");
      assertEquals(404, excluded.statusCode(),
          factoryName + ": the wiring exclusion must keep /exclude unregistered"
      );
    }
  }

  @Test
  void helidonBindFailuresAreRetriedButRemainBounded() throws Exception {
    // Holding the listener open forces a bind conflict on every attempt, without racing.
    try (final var occupied = new ServerSocket(0, 50, InetAddress.getByName("localhost"))) {
      final var attempts = new AtomicInteger();
      final var failure = assertThrows(IOException.class, () -> {
            try (final var _ = startRetrying("HelidonBuilderFactory", () -> {
                  attempts.incrementAndGet();
                  return occupied.getLocalPort();
                }
            )) {
              fail("A listener on an occupied address must not start");
            }
          }
      );
      assertEquals(3, attempts.get(), "Helidon's cause-less startup failure must reach the bounded retry");
      assertTrue(failure.getMessage().startsWith("Helidon listener failed to start on localhost:" + occupied.getLocalPort()));
    }
  }

  @Test
  void helidonRecoversWhenTheFirstPortIsOccupied() throws Exception {
    try (final var occupied = new ServerSocket(0, 50, InetAddress.getByName("localhost"))) {
      final var attempts = new AtomicInteger();
      try (final var started = startRetrying("HelidonBuilderFactory", () ->
          attempts.getAndIncrement() == 0 ? occupied.getLocalPort() : freePort()
      );
           final var client = HttpClient.newHttpClient()) {
        assertNotEquals(occupied.getLocalPort(), started.port());
        assertTrue(attempts.get() >= 2 && attempts.get() <= 3);
        final var response = get(client, started.port(), "/hello");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"message\": \"Hello\""));
      }
    }
  }

  @Test
  void unrelatedStartupFailuresAreNotRetried() {
    final var attempts = new AtomicInteger();
    final var failure = assertThrows(IllegalStateException.class, () -> {
          try (final var unexpected = startRetrying("NoSuchFactory", () -> {
                attempts.incrementAndGet();
                return 0;
              }
          )) {
            fail("An unknown factory must not start a server");
          }
        }
    );
    assertEquals(1, attempts.get());
    assertTrue(failure.getMessage().contains("NoSuchFactory"));
  }

  /// The only end-to-end exercise of `HttpServerBuilderFactory.findFirst`'s success path:
  /// this module has all five backends on the path, so discovery must yield a builder in
  /// both worlds the tools run in (module path here, class path under PIT minions). The
  /// path stays an accepted `NO_COVERAGE` in core's own suite — core ships no provider —
  /// but it is no longer unobserved.
  @Test
  void findFirstDiscoversABackend() {
    assertNotNull(software.sava.http_servers.core.server.HttpServerBuilderFactory.findFirst(),
        "five backends provide HttpServerBuilderFactory; findFirst must yield a builder"
    );
  }

  @Test
  void unknownFactoryNameThrows() {
    final var thrown = assertThrows(IllegalStateException.class,
        () -> HelloServer.start("NoSuchFactory", "localhost", 0)
    );
    assertTrue(thrown.getMessage().contains("NoSuchFactory"));
  }

  @Test
  void helloHandlerBuildsTheConstantResponse() {
    final var response = new HelloHandler().httpResponse(null);
    assertEquals(200, response.statusCode());
    assertEquals("application/json", response.contentType());
    assertTrue(new String(response.body()).contains("\"message\": \"Hello\""));
  }

  /// `freePort`'s probe-close-rebind window can race a parallel test to the port; retry
  /// with a fresh port when the loser's bind fails.
  private static StartedServer startRetrying(final String factoryName,
                                             final Callable<Integer> nextPort) throws Exception {
    for (int attempt = 0; ; ++attempt) {
      final int port = nextPort.call();
      try {
        final var server = HelloServer.start(factoryName, "localhost", port);
        assertNotNull(server);
        return new StartedServer(server, port);
      } catch (final Exception e) {
        boolean lostThePortRace = false;
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
          if (cause instanceof java.net.BindException || cause instanceof java.net.ConnectException) {
            lostThePortRace = true;
          }
          // Helidon logs the bind cause and throws only this adapter-specific IOException.
          // Retry that startup failure for this address, never arbitrary IOExceptions.
          if ("HelidonBuilderFactory".equals(factoryName)
              && cause instanceof IOException
              && String.valueOf(cause.getMessage()).startsWith("Helidon listener failed to start on localhost:" + port + ';')) {
            lostThePortRace = true;
          }
        }
        if (attempt == 2 || !lostThePortRace) {
          throw e;
        }
      }
    }
  }

  private record StartedServer(HttpServer server, int port) implements AutoCloseable {
    @Override
    public void close() throws Exception {
      server.close();
    }
  }
}
