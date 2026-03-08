/// The entrypoint for the **server** environment.
///
/// The [main] method will only be executed on the server during pre-rendering.
/// To run code on the client, check the `main.client.dart` file.
library;

import 'dart:convert';
import 'dart:io';

import 'package:jaspr/dom.dart';
// Server-specific Jaspr import.
import 'package:jaspr/server.dart';
import 'package:shelf/shelf.dart';
import 'package:shelf/shelf_io.dart' as shelf_io;
import 'package:shelf_router/shelf_router.dart';

// Imports the [App] component.
import 'app.dart';
import 'report_access/report_access.dart';

// This file is generated automatically by Jaspr, do not remove or edit.
import 'main.server.options.dart';

/// Initializes the custom shelf server.
///
/// The main() function will be called again during development when hot-reloading.
/// Custom backend implementations must take care of properly managing open http servers
/// and other resources that might be re-created when hot-reloading.
void main() async {
  Jaspr.initializeApp(
    options: defaultServerOptions,
  );

  final port = _resolvePort(Platform.environment);
  var router = Router();
  final reportsConfig = ReportsRootConfig.fromEnvironment();
  final reportSource = ReportSource(config: reportsConfig);
  const reportIndexBuilder = ReportIndexBuilder();

  // Route your api requests to your own endpoint.
  router.get('/api/health', (request) {
    return Response.ok('ok');
  });

  router.get('/api/reports/index', (request) async {
    final sourceResult = await reportSource.discoverArtifacts();
    final index = reportIndexBuilder.build(sourceResult);
    return Response.ok(
      jsonEncode(index.toJson()),
      headers: const {
        'content-type': 'application/json',
      },
    );
  });

  // Use [serveApp] instead of [runApp] to get a shelf handler you can mount.
  router.mount(
    '/',
    serveApp((request, render) {
      // Optionally do something with `request`
      print("Request uri is ${request.requestedUri} (${request.url})");
      // Return a server-rendered response by calling `render()` with your root component
      return render(
        Document(
          title: 'go_no_go_reports_ui',
          viewport: 'width=device-width, initial-scale=1, viewport-fit=cover',
          styles: [
            // Special import rule to include to another css file.
            css.import('https://fonts.googleapis.com/css2?family=Space+Grotesk:wght@400;500;700&display=swap'),
            // Each style rule takes a valid css selector and a set of styles.
            // Styles are defined using type-safe css bindings and can be freely chained and nested.
            css('html, body').styles(
              width: 100.percent,
              minHeight: 100.vh,
              padding: .zero,
              margin: .zero,
              fontFamily: const .list([FontFamily('Space Grotesk'), FontFamilies.sansSerif]),
            ),
            css('*').styles(
              boxSizing: .borderBox,
            ),
          ],
          body: App(),
        ),
      );
    }),
  );

  var handler =
      const Pipeline() //
          .addMiddleware(logRequests())
          .addHandler(router);

  // Object to resolve async locking of reloads.
  var reloadLock = activeReloadLock = Object();

  var server = await shelf_io.serve(handler, InternetAddress.anyIPv4, port, shared: true);

  // If the reload lock changed, another reload happened and we should abort.
  if (reloadLock != activeReloadLock) {
    server.close();
    return;
  }

  // Else we can safely update the active server.
  activeServer?.close();
  activeServer = server;

  print('Serving at http://${server.address.host}:${server.port}');
}

/// Keeps track of the currently running http server.
HttpServer? activeServer;

/// Keeps track of the last created reload lock.
/// This is needed to track reloads which might happen in quick succession.
Object? activeReloadLock;

int _resolvePort(Map<String, String> environment) {
  const fallbackPort = 8787;
  final raw = environment['PORT'];
  final parsed = raw == null ? null : int.tryParse(raw.trim());
  if (parsed == null || parsed <= 0 || parsed > 65535) {
    return fallbackPort;
  }
  return parsed;
}
