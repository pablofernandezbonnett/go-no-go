/// The entrypoint for the **server** environment.
library;

import 'dart:convert';
import 'dart:io';

import 'package:jaspr/dom.dart';
import 'package:jaspr/server.dart';
import 'package:path/path.dart' as p;
import 'package:shelf/shelf.dart';
import 'package:shelf/shelf_io.dart' as shelf_io;
import 'package:shelf_router/shelf_router.dart';

import 'app.dart';
import 'backend/engine_config_repository.dart';
import 'backend/run_manager.dart';

// This file is generated automatically by Jaspr, do not remove or edit.
import 'main.server.options.dart';

void main() async {
  Jaspr.initializeApp(options: defaultServerOptions);

  final engineRoot = _resolveEngineRoot(Platform.environment);
  final gradlew = _resolveGradlew(Platform.environment);
  final configRepository = EngineConfigRepository(engineRoot: engineRoot);
  final runManager = RunManager(engineRoot: engineRoot, gradlewCommand: gradlew);

  final router = Router();

  router.get('/api/health', (request) {
    return _jsonResponse(
      {
        'status': 'ok',
        'engineRoot': engineRoot.path,
      },
    );
  });

  router.get('/api/config', (request) async {
    try {
      final catalog = await configRepository.loadCatalog();
      return _jsonResponse(catalog.toJson());
    } catch (error) {
      return _jsonResponse(
        {
          'error': 'config_load_failed',
          'message': error.toString(),
        },
        statusCode: HttpStatus.internalServerError,
      );
    }
  });

  router.post('/api/config/companies', (request) async {
    try {
      final body = await request.readAsString();
      final decoded = jsonDecode(body);
      if (decoded is! Map<String, dynamic>) {
        return _jsonResponse(
          {
            'error': 'invalid_payload',
            'message': 'Request body must be a JSON object.',
          },
          statusCode: HttpStatus.badRequest,
        );
      }

      final created = await configRepository.addCompany(
        CompanyCreateInput(
          id: decoded['id']?.toString() ?? '',
          name: decoded['name']?.toString() ?? '',
          careerUrl: decoded['careerUrl']?.toString() ?? '',
          corporateUrl: decoded['corporateUrl']?.toString() ?? '',
          typeHint: decoded['typeHint']?.toString() ?? '',
          region: decoded['region']?.toString() ?? '',
          notes: decoded['notes']?.toString() ?? '',
        ),
      );
      return _jsonResponse(
        {
          'status': 'created',
          'company': created.toJson(),
        },
        statusCode: HttpStatus.created,
      );
    } on FormatException catch (error) {
      return _jsonResponse(
        {
          'error': 'invalid_request',
          'message': error.message,
        },
        statusCode: HttpStatus.badRequest,
      );
    } on StateError catch (error) {
      return _jsonResponse(
        {
          'error': 'company_conflict',
          'message': error.message,
        },
        statusCode: HttpStatus.conflict,
      );
    } catch (error) {
      return _jsonResponse(
        {
          'error': 'company_create_failed',
          'message': error.toString(),
        },
        statusCode: HttpStatus.internalServerError,
      );
    }
  });

  router.get('/api/runs', (request) {
    final runs = runManager.listRuns().map((item) => item.toJson(includeLogs: false)).toList();
    return _jsonResponse({'runs': runs});
  });

  router.get('/api/runs/<runId>', (request, String runId) {
    final run = runManager.getRun(runId);
    if (run == null) {
      return _jsonResponse(
        {
          'error': 'run_not_found',
          'runId': runId,
        },
        statusCode: HttpStatus.notFound,
      );
    }
    return _jsonResponse(run.toJson(includeLogs: true));
  });

  router.post('/api/runs', (request) async {
    try {
      final body = await request.readAsString();
      final decoded = jsonDecode(body);
      if (decoded is! Map<String, dynamic>) {
        return _jsonResponse(
          {
            'error': 'invalid_payload',
            'message': 'Request body must be a JSON object.',
          },
          statusCode: HttpStatus.badRequest,
        );
      }

      final runRequest = RunRequest.fromJson(decoded);
      final run = await runManager.submit(runRequest);
      return _jsonResponse(run.toJson(includeLogs: true), statusCode: HttpStatus.accepted);
    } on FormatException catch (error) {
      return _jsonResponse(
        {
          'error': 'invalid_request',
          'message': error.message,
        },
        statusCode: HttpStatus.badRequest,
      );
    } catch (error) {
      return _jsonResponse(
        {
          'error': 'run_submit_failed',
          'message': error.toString(),
        },
        statusCode: HttpStatus.internalServerError,
      );
    }
  });

  router.mount('/', serveApp((request, render) {
    return render(
      Document(
        title: 'Go/No-Go Engine Operations UI',
        viewport: 'width=device-width, initial-scale=1, viewport-fit=cover',
        styles: [
          css.import('https://fonts.googleapis.com/css2?family=Space+Grotesk:wght@400;500;700&display=swap'),
          css('html, body').styles(
            width: 100.percent,
            minHeight: 100.vh,
            padding: .zero,
            margin: .zero,
            fontFamily: const .list([FontFamily('Space Grotesk'), FontFamilies.sansSerif]),
            backgroundColor: const Color('#f5f3ef'),
            color: const Color('#1f2a2e'),
          ),
          css('*').styles(boxSizing: .borderBox),
        ],
        body: const App(),
      ),
    );
  }));

  final handler = const Pipeline().addMiddleware(logRequests()).addHandler(router);

  final reloadLock = activeReloadLock = Object();
  final port = _resolvePort(Platform.environment);
  final server = await shelf_io.serve(handler, InternetAddress.anyIPv4, port, shared: true);

  if (reloadLock != activeReloadLock) {
    await server.close();
    return;
  }

  await activeServer?.close();
  activeServer = server;
  print('Operations UI at http://${server.address.host}:${server.port}');
}

HttpServer? activeServer;
Object? activeReloadLock;

Directory _resolveEngineRoot(Map<String, String> environment) {
  final configured = environment['ENGINE_ROOT'];
  if (configured != null && configured.trim().isNotEmpty) {
    return Directory(configured.trim()).absolute;
  }

  final cwd = Directory.current.absolute.path;
  final defaultRoot = p.normalize(p.join(cwd, '..'));
  return Directory(defaultRoot).absolute;
}

String _resolveGradlew(Map<String, String> environment) {
  final configured = environment['ENGINE_GRADLEW'];
  if (configured != null && configured.trim().isNotEmpty) {
    return configured.trim();
  }
  return './gradlew';
}

int _resolvePort(Map<String, String> environment) {
  const fallbackPort = 8791;
  final candidates = [environment['PORT'], environment['OPS_UI_PORT']];
  for (final candidate in candidates) {
    final parsed = int.tryParse(candidate?.trim() ?? '');
    if (parsed != null && parsed > 0 && parsed <= 65535) {
      return parsed;
    }
  }
  return fallbackPort;
}

Response _jsonResponse(Object payload, {int statusCode = HttpStatus.ok}) {
  return Response(
    statusCode,
    body: jsonEncode(payload),
    headers: const {
      'content-type': 'application/json',
      'cache-control': 'no-store',
    },
  );
}
