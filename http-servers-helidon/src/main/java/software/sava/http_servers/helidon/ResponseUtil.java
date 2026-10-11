package software.sava.http_servers.helidon;

import io.helidon.http.HeaderNames;
import io.helidon.webserver.http.ServerResponse;
import software.sava.http_servers.core.response.HttpResponse;

/// Writes a core [HttpResponse] onto a Helidon `ServerResponse`. The status written is the one
/// [HttpResponse#checkFinalStatus] returns, so a status that is not final throws before anything
/// is set and `HelidonController` answers it with its JSON 500. The bodyless statuses are
/// Helidon's own no-entity set — 204, 205 and 304 — all three of which Netty's codec also sends
/// without content, while the JDK and Jetty backends treat only 204 and 304 as bodyless.
final class ResponseUtil {

  private static final String JSON = "application/json";

  static void writeJson(final ServerResponse response, final byte[] responseBytes) {
    response.header(HeaderNames.CONTENT_TYPE, JSON);
    response.send(responseBytes);
  }

  static void writeResponse(final ServerResponse response, final HttpResponse httpResponse) {
    final int statusCode = HttpResponse.checkFinalStatus(httpResponse.statusCode());
    response.status(statusCode);
    response.header(HeaderNames.CONTENT_TYPE, httpResponse.contentType());
    for (final var header : httpResponse.headers().entrySet()) {
      response.header(header.getKey(), header.getValue());
    }
    if (statusCode == 204 || statusCode == 205 || statusCode == 304) {
      // Helidon answers 500 rather than stripping an entity from a bodyless status, and adds
      // `Content-Length: 0` to these by design (Http1ServerResponse, upstream PR 9408). Its
      // no-entity set is {204, 205, 304}: 205 is bodyless here and on Netty, whose
      // `HttpResponseEncoder` drops a 205's content too, while the JDK and Jetty backends send
      // whatever the handler attached (RFC 9110 s15.3.6 says a 205 must not generate content).
      response.send();
    } else {
      response.send(httpResponse.body());
    }
  }

  private ResponseUtil() {
  }
}
