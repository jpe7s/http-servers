package software.sava.http_servers.core.response;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public interface HttpResponse {

  HttpResponse EMPTY = response(200, "application/json", "{}".getBytes(StandardCharsets.US_ASCII));

  static HttpResponse response(final int statusCode, final String contentType, final byte[] body) {
    return new HttpBytesResponse(statusCode, contentType, Map.of(), body);
  }

  static HttpResponse response(final int statusCode,
                               final String contentType,
                               final Map<String, String> headers,
                               final byte[] body) {
    return new HttpBytesResponse(statusCode, contentType, headers, body);
  }

  static HttpResponse response(final String contentType, final byte[] body) {
    return response(200, contentType, body);
  }

  static HttpResponse response(final String contentType, final String body) {
    return response(200, contentType, body.getBytes(StandardCharsets.UTF_8));
  }

  static HttpResponse json(final int statusCode, final byte[] body) {
    return response(statusCode, "application/json", body);
  }

  static HttpResponse json(final byte[] body) {
    return json(200, body);
  }

  static HttpResponse json(final int statusCode, final String body) {
    return response(statusCode, "application/json", body.getBytes(StandardCharsets.UTF_8));
  }

  static HttpResponse json(final String body) {
    return json(200, body);
  }

  /// The status a backend writes for a handler's response: `statusCode` itself when it is a
  /// final status, `200` through `599`. A `1xx` is an interim answer that RFC 9110 §15.2 says a
  /// final response must follow, and nothing outside `100`–`599` is a valid status (§15), so a
  /// handler that returns either has failed like one that throws: every backend writes the
  /// status this returns, and answers the [IllegalStateException] it throws with `500`.
  ///
  /// @throws IllegalStateException if `statusCode` is not a final status
  static int checkFinalStatus(final int statusCode) {
    if (statusCode < 200 || statusCode > 599) {
      throw new IllegalStateException("A handler's response needs a final status (200-599), not " + statusCode);
    }
    return statusCode;
  }

  int statusCode();

  String contentType();

  /// @return additional response headers (beyond {@code Content-Type}) to be written, never {@code null}.
  default Map<String, String> headers() {
    return Map.of();
  }

  /// @return a copy of this response with the given response header set.
  default HttpResponse withHeader(final String name, final String value) {
    final var headers = new LinkedHashMap<>(headers());
    headers.put(name, value);
    return new HttpBytesResponse(statusCode(), contentType(), headers, body());
  }

  byte[] body();
}
