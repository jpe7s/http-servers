package software.sava.http_servers.jetty;

import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.util.Callback;

import java.io.IOException;

/// The page Jetty writes for an error it answers itself — a response write it refuses before
/// the head is committed, a request its parser or `UriCompliance` rejects. The default page
/// prints the message it is handed, which for a failure is the failure's `toString()`; this
/// one prints the status reason in its place.
final class JettyErrorHandler extends ErrorHandler {

  @Override
  protected void generateResponse(final Request request,
                                  final Response response,
                                  final int code,
                                  final String message,
                                  final Throwable cause,
                                  final Callback callback) throws IOException {
    super.generateResponse(request, response, code, HttpStatus.getMessage(code), null, callback);
  }
}
