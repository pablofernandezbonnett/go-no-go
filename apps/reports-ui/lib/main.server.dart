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
import 'package:path/path.dart' as p;
import 'package:shelf/shelf.dart';
import 'package:shelf/shelf_io.dart' as shelf_io;
import 'package:shelf_router/shelf_router.dart';

// Imports the [App] component.
import 'app.dart';
import 'backend/engine_catalog_repository.dart';
import 'backend/evaluation_runner.dart';
import 'backend/evaluation_url_history_repository.dart';
import 'constants/evaluation_contract.dart';
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
  final bindAddress = _resolveBindAddress(Platform.environment);
  var router = Router();
  final engineRoot = _resolveEngineRoot(Platform.environment);
  final gradlew = _resolveGradlew(Platform.environment);
  final reportsConfig = ReportsRootConfig.fromEnvironment();
  final reportsRoot = reportsConfig.resolveDirectory();
  final reportSource = ReportSource(config: reportsConfig);
  const reportIndexBuilder = ReportIndexBuilder();
  final engineCatalogRepository = EngineCatalogRepository(engineRoot: engineRoot);
  final evaluationUrlHistoryRepository = EvaluationUrlHistoryRepository(reportsRoot: reportsRoot);
  final evaluationRunner = EngineEvaluationRunner(
    engineRoot: engineRoot,
    reportsRoot: reportsRoot,
    gradlewCommand: gradlew,
  );

  // Route your api requests to your own endpoint.
  router.get(healthApiPath, (request) {
    return Response.ok('ok');
  });

  router.get(reportsIndexApiPath, (request) async {
    final sourceResult = await reportSource.discoverArtifacts();
    final index = reportIndexBuilder.build(sourceResult);
    return Response.ok(
      jsonEncode(index.toJson()),
      headers: const {
        'content-type': jsonContentType,
      },
    );
  });

  router.get(evaluateOptionsApiPath, (request) async {
    try {
      final catalog = await engineCatalogRepository.loadEvaluateCatalog();
      return Response.ok(
        jsonEncode(catalog.toJson()),
        headers: const {'content-type': jsonContentType},
      );
    } catch (error) {
      return Response.internalServerError(
        body: jsonEncode({
          'error': evaluationErrorOptionsLoadFailed,
          'message': 'Failed to load evaluation options.',
        }),
        headers: const {'content-type': jsonContentType},
      );
    }
  });

  router.get(evaluateUrlHistoryApiPath, (request) async {
    try {
      final history = await evaluationUrlHistoryRepository.load();
      return Response.ok(
        jsonEncode(history.toJson()),
        headers: const {'content-type': jsonContentType},
      );
    } catch (error) {
      return Response.internalServerError(
        body: jsonEncode({
          'error': evaluationErrorHistoryLoadFailed,
          'message': 'Failed to load evaluation URL history.',
        }),
        headers: const {'content-type': jsonContentType},
      );
    }
  });

  router.get(evaluateUrlHistoryDetailApiPath, (request) async {
    final url = request.url.queryParameters['url']?.trim() ?? '';
    if (url.isEmpty) {
      return Response(
        HttpStatus.badRequest,
        body: jsonEncode({
          'error': evaluationErrorInvalidRequest,
          'message': 'Query parameter "url" is required.',
        }),
        headers: const {'content-type': jsonContentType},
      );
    }

    try {
      final payload = await evaluationUrlHistoryRepository.loadLatestAdHocDetail(url);
      if (payload == null) {
        return Response(
          HttpStatus.notFound,
          body: jsonEncode({
            'error': evaluationErrorHistoryDetailNotFound,
            'message': 'No saved ad-hoc evaluation was found for this URL.',
          }),
          headers: const {'content-type': jsonContentType},
        );
      }
      return Response.ok(
        jsonEncode(payload),
        headers: const {'content-type': jsonContentType},
      );
    } catch (error) {
      return Response.internalServerError(
        body: jsonEncode({
          'error': evaluationErrorHistoryDetailLoadFailed,
          'message': 'Failed to load the saved ad-hoc evaluation.',
        }),
        headers: const {'content-type': jsonContentType},
      );
    }
  });

  router.post(evaluateApiPath, (request) async {
    try {
      final body = await request.readAsString();
      final decoded = jsonDecode(body);
      if (decoded is! Map<String, dynamic>) {
        return Response(
          HttpStatus.badRequest,
          body: jsonEncode({
            'error': evaluationErrorInvalidPayload,
            'message': 'Request body must be a JSON object.',
          }),
          headers: const {'content-type': jsonContentType},
        );
      }

      final evaluationRequest = await EvaluationRequest.fromJson(decoded);
      final catalog = await engineCatalogRepository.loadEvaluateCatalog();
      final result = await evaluationRunner.evaluate(
        evaluationRequest,
        catalog.personas.map((persona) => persona.id).toList(growable: false),
      );
      return Response.ok(
        jsonEncode(result),
        headers: const {'content-type': jsonContentType},
      );
    } on FormatException catch (error) {
      return Response(
        HttpStatus.badRequest,
        body: jsonEncode({
          'error': evaluationErrorInvalidRequest,
          'message': error.message,
        }),
        headers: const {'content-type': jsonContentType},
      );
    } on StateError catch (error) {
      return Response.internalServerError(
        body: jsonEncode({
          'error': evaluationErrorEvaluationFailed,
          'message': _safeEvaluationMessage(error.message),
        }),
        headers: const {'content-type': jsonContentType},
      );
    } catch (error) {
      return Response.internalServerError(
        body: jsonEncode({
          'error': evaluationErrorEvaluationFailed,
          'message': 'The engine evaluation could not be completed.',
        }),
        headers: const {'content-type': jsonContentType},
      );
    }
  });

  // Use [serveApp] instead of [runApp] to get a shelf handler you can mount.
  router.mount(
    '/',
    serveApp((request, render) {
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

  var server = await shelf_io.serve(handler, bindAddress, port, shared: true);

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

const reportsUiBindHostEnvVar = 'REPORTS_UI_BIND_HOST';

/// Keeps track of the currently running http server.
HttpServer? activeServer;

/// Keeps track of the last created reload lock.
/// This is needed to track reloads which might happen in quick succession.
Object? activeReloadLock;

Directory _resolveEngineRoot(Map<String, String> environment) {
  final configured = environment[engineRootEnvVar];
  if (configured != null && configured.trim().isNotEmpty) {
    return Directory(configured.trim()).absolute;
  }

  final cwd = Directory.current.absolute.path;
  final defaultRoot = p.normalize(p.join(cwd, '..', '..', 'services', 'engine'));
  return Directory(defaultRoot).absolute;
}

String _resolveGradlew(Map<String, String> environment) {
  final configured = environment[engineGradlewEnvVar];
  if (configured != null && configured.trim().isNotEmpty) {
    return configured.trim();
  }
  return './gradlew';
}

int _resolvePort(Map<String, String> environment) {
  const fallbackPort = 8792;
  final candidates = [environment[reportsUiPortEnvVar], environment[fallbackPortEnvVar]];
  for (final candidate in candidates) {
    final parsed = int.tryParse(candidate?.trim() ?? '');
    if (parsed != null && parsed > 0 && parsed <= 65535) {
      return parsed;
    }
  }
  return fallbackPort;
}

InternetAddress _resolveBindAddress(Map<String, String> environment) {
  final configured = environment[reportsUiBindHostEnvVar]?.trim() ?? '';
  if (configured.isEmpty || configured.toLowerCase() == 'localhost') {
    return InternetAddress.loopbackIPv4;
  }

  final parsed = InternetAddress.tryParse(configured);
  if (parsed != null) {
    return parsed;
  }
  throw ArgumentError.value(
    configured,
    reportsUiBindHostEnvVar,
    'Use localhost or an IP literal such as 127.0.0.1 or 0.0.0.0.',
  );
}

String _safeEvaluationMessage(String message) {
  final trimmed = message.trim();
  if (trimmed.isEmpty) {
    return 'The engine evaluation could not be completed.';
  }
  if (trimmed.contains(Platform.pathSeparator) || trimmed.contains('\\')) {
    return 'The engine evaluation could not be completed.';
  }
  return trimmed;
}
