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

  router.get('/api/signals', (request) {
    return _jsonResponse({'signals': _kSignalCatalog});
  });

  router.get('/api/config/personas/<id>', (request, String id) async {
    try {
      final detail = await configRepository.loadPersonaDetail(id);
      if (detail == null) {
        return _jsonResponse(
          {'error': 'persona_not_found', 'id': id},
          statusCode: HttpStatus.notFound,
        );
      }
      return _jsonResponse(detail.toJson());
    } catch (error) {
      return _internalErrorResponse('persona_load_failed', 'Failed to load persona detail.');
    }
  });

  router.put('/api/config/personas/<id>/tuning', (request, String id) async {
    try {
      final body = await request.readAsString();
      final decoded = jsonDecode(body);
      if (decoded is! Map<String, dynamic>) {
        return _jsonResponse(
          {'error': 'invalid_payload', 'message': 'Request body must be a JSON object.'},
          statusCode: HttpStatus.badRequest,
        );
      }

      Map<String, int> parseWeights(dynamic raw) {
        if (raw is! Map) return const {};
        return Map.fromEntries(
          raw.entries.where((e) => e.value is num).map((e) => MapEntry(e.key.toString(), (e.value as num).toInt())),
        );
      }

      final weights = parseWeights(decoded['signalWeights']);
      final strategy = decoded['rankingStrategy']?.toString() ?? '';
      final minimumSalaryYen = _parseOptionalInt(decoded['minimumSalaryYen']);

      final updated = await configRepository.updatePersonaTuning(
        id,
        weights,
        strategy,
        minimumSalaryYen,
      );
      return _jsonResponse(updated.toJson());
    } on StateError catch (error) {
      final msg = error.message;
      if (msg.contains('not found')) {
        return _jsonResponse(
          {'error': 'persona_not_found', 'message': 'Persona not found.'},
          statusCode: HttpStatus.notFound,
        );
      }
      return _internalErrorResponse('tuning_failed', 'Failed to update persona tuning.');
    } on FormatException catch (error) {
      return _jsonResponse(
        {'error': 'invalid_request', 'message': error.message},
        statusCode: HttpStatus.badRequest,
      );
    } catch (error) {
      return _internalErrorResponse('tuning_failed', 'Failed to update persona tuning.');
    }
  });

  router.get('/api/health', (request) {
    return _jsonResponse(
      {
        'status': 'ok',
      },
    );
  });

  router.get('/api/config', (request) async {
    try {
      final catalog = await configRepository.loadCatalog();
      return _jsonResponse(catalog.toJson());
    } catch (error) {
      return _internalErrorResponse('config_load_failed', 'Failed to load configuration.');
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
      return _internalErrorResponse('company_create_failed', 'Failed to create company.');
    }
  });

  router.post('/api/config/personas', (request) async {
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

      List<String> parseList(String key) {
        final raw = decoded[key];
        if (raw is! List) {
          return const [];
        }
        return raw.map((item) => item.toString()).toList();
      }

      Map<String, int> parseWeights(dynamic raw) {
        if (raw is! Map) return const {};
        return Map.fromEntries(
          raw.entries.where((e) => e.value is num).map((e) => MapEntry(e.key.toString(), (e.value as num).toInt())),
        );
      }

      final createdId = await configRepository.addPersona(
        PersonaCreateInput(
          id: decoded['id']?.toString() ?? '',
          description: decoded['description']?.toString() ?? '',
          priorities: parseList('priorities'),
          hardNo: parseList('hardNo'),
          acceptableIf: parseList('acceptableIf'),
          signalWeights: parseWeights(decoded['signalWeights']),
          rankingStrategy: decoded['rankingStrategy']?.toString() ?? '',
          minimumSalaryYen: _parseOptionalInt(decoded['minimumSalaryYen']),
        ),
      );
      return _jsonResponse(
        {
          'status': 'created',
          'personaId': createdId,
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
          'error': 'persona_conflict',
          'message': error.message,
        },
        statusCode: HttpStatus.conflict,
      );
    } catch (error) {
      return _internalErrorResponse('persona_create_failed', 'Failed to create persona.');
    }
  });

  router.get('/api/runs', (request) {
    final runs = runManager.listRuns().map((item) => item.toJson()).toList();
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
    return _jsonResponse(run.toJson());
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
      return _jsonResponse(run.toJson(), statusCode: HttpStatus.accepted);
    } on FormatException catch (error) {
      return _jsonResponse(
        {
          'error': 'invalid_request',
          'message': error.message,
        },
        statusCode: HttpStatus.badRequest,
      );
    } catch (error) {
      return _internalErrorResponse('run_submit_failed', 'Failed to submit run.');
    }
  });

  router.mount(
    '/',
    serveApp((request, render) {
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
              color: const Color('#101828'),
              fontFamily: const .list([FontFamily('Space Grotesk'), FontFamilies.sansSerif]),
              backgroundColor: const Color('#eef2f7'),
            ),
            css('*').styles(boxSizing: .borderBox),
          ],
          body: const App(),
        ),
      );
    }),
  );

  final handler = const Pipeline().addMiddleware(logRequests()).addHandler(router.call);

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
  final candidates = [environment['OPS_UI_PORT'], environment['PORT']];
  for (final candidate in candidates) {
    final parsed = int.tryParse(candidate?.trim() ?? '');
    if (parsed != null && parsed > 0 && parsed <= 65535) {
      return parsed;
    }
  }
  return fallbackPort;
}

int? _parseOptionalInt(Object? raw) {
  if (raw is num) {
    return raw.toInt();
  }
  final text = raw?.toString().trim() ?? '';
  if (text.isEmpty) {
    return null;
  }
  return int.tryParse(text.replaceAll(',', ''));
}

Response _internalErrorResponse(String errorCode, String message) {
  return _jsonResponse(
    {
      'error': errorCode,
      'message': message,
    },
    statusCode: HttpStatus.internalServerError,
  );
}

// Static signal catalog mirroring SignalRegistry.java (55 signals).
const List<Map<String, Object>> _kSignalCatalog = [
  // Positive signals
  {'name': 'salary_transparency', 'type': 'positive', 'priority_group': 'salary', 'default_weight': 2},
  {'name': 'hybrid_work', 'type': 'positive', 'priority_group': 'hybrid_work', 'default_weight': 2},
  {'name': 'remote_friendly', 'type': 'positive', 'priority_group': 'hybrid_work', 'default_weight': 2},
  {'name': 'english_environment', 'type': 'positive', 'priority_group': 'english_environment', 'default_weight': 2},
  {'name': 'product_company', 'type': 'positive', 'priority_group': 'product_company', 'default_weight': 2},
  {'name': 'engineering_culture', 'type': 'positive', 'priority_group': 'engineering_culture', 'default_weight': 2},
  {'name': 'engineering_environment', 'type': 'positive', 'priority_group': 'engineering_culture', 'default_weight': 2},
  {'name': 'inhouse_product_engineering', 'type': 'positive', 'priority_group': 'product_company', 'default_weight': 2},
  {
    'name': 'global_team_collaboration',
    'type': 'positive',
    'priority_group': 'english_environment',
    'default_weight': 2,
  },
  {
    'name': 'english_support_environment',
    'type': 'positive',
    'priority_group': 'english_environment',
    'default_weight': 2,
  },
  {
    'name': 'visa_sponsorship_support',
    'type': 'positive',
    'priority_group': 'english_environment',
    'default_weight': 2,
  },
  {'name': 'work_life_balance', 'type': 'positive', 'priority_group': 'work_life_balance', 'default_weight': 2},
  {'name': 'stability', 'type': 'positive', 'priority_group': 'stability', 'default_weight': 2},
  {'name': 'company_reputation_positive', 'type': 'positive', 'priority_group': 'stability', 'default_weight': 2},
  {
    'name': 'company_reputation_positive_strong',
    'type': 'positive',
    'priority_group': 'stability',
    'default_weight': 2,
  },
  {'name': 'candidate_stack_fit', 'type': 'positive', 'priority_group': 'engineering_culture', 'default_weight': 2},
  {'name': 'candidate_domain_fit', 'type': 'positive', 'priority_group': 'product_company', 'default_weight': 2},
  {'name': 'candidate_seniority_fit', 'type': 'positive', 'priority_group': 'engineering_culture', 'default_weight': 2},
  {'name': 'product_pm_collaboration', 'type': 'positive', 'priority_group': 'product_company', 'default_weight': 2},
  {'name': 'engineering_maturity', 'type': 'positive', 'priority_group': 'engineering_culture', 'default_weight': 2},
  {'name': 'casual_interview', 'type': 'positive', 'priority_group': 'engineering_culture', 'default_weight': 2},
  {'name': 'async_communication', 'type': 'positive', 'priority_group': 'work_life_balance', 'default_weight': 2},
  {'name': 'real_flextime', 'type': 'positive', 'priority_group': 'work_life_balance', 'default_weight': 2},
  {'name': 'low_overtime_disclosed', 'type': 'positive', 'priority_group': 'work_life_balance', 'default_weight': 2},
  // Risk signals
  {'name': 'salary_low_confidence', 'type': 'risk', 'priority_group': 'salary', 'default_weight': 2},
  {'name': 'salary_below_persona_floor', 'type': 'risk', 'priority_group': 'salary', 'default_weight': 3},
  {'name': 'onsite_bias', 'type': 'risk', 'priority_group': 'hybrid_work', 'default_weight': 2},
  {'name': 'language_friction', 'type': 'risk', 'priority_group': 'english_environment', 'default_weight': 3},
  {'name': 'language_friction_critical', 'type': 'risk', 'priority_group': 'english_environment', 'default_weight': 7},
  {'name': 'consulting_risk', 'type': 'risk', 'priority_group': 'product_company', 'default_weight': 3},
  {'name': 'overtime_risk', 'type': 'risk', 'priority_group': 'work_life_balance', 'default_weight': 3},
  {'name': 'engineering_environment_risk', 'type': 'risk', 'priority_group': 'work_life_balance', 'default_weight': 2},
  {'name': 'startup_risk', 'type': 'risk', 'priority_group': 'stability', 'default_weight': 3},
  {
    'name': 'role_mismatch_manager_vs_ic_title',
    'type': 'risk',
    'priority_group': 'engineering_culture',
    'default_weight': 5,
  },
  {'name': 'role_identity_mismatch', 'type': 'risk', 'priority_group': 'engineering_culture', 'default_weight': 6},
  {'name': 'intermediary_contract_risk', 'type': 'risk', 'priority_group': 'product_company', 'default_weight': 5},
  {'name': 'inclusion_contradiction', 'type': 'risk', 'priority_group': 'english_environment', 'default_weight': 5},
  {'name': 'pre_ipo_risk', 'type': 'risk', 'priority_group': 'stability', 'default_weight': 4},
  {'name': 'manager_scope_salary_misaligned', 'type': 'risk', 'priority_group': 'salary', 'default_weight': 5},
  {'name': 'workload_policy_risk', 'type': 'risk', 'priority_group': 'work_life_balance', 'default_weight': 6},
  {'name': 'holiday_policy_risk', 'type': 'risk', 'priority_group': 'work_life_balance', 'default_weight': 5},
  {'name': 'location_mobility_risk', 'type': 'risk', 'priority_group': 'hybrid_work', 'default_weight': 4},
  {'name': 'salary_range_anomaly', 'type': 'risk', 'priority_group': 'salary', 'default_weight': 6},
  {'name': 'debt_first_culture_risk', 'type': 'risk', 'priority_group': 'engineering_culture', 'default_weight': 6},
  {'name': 'hypergrowth_execution_risk', 'type': 'risk', 'priority_group': 'stability', 'default_weight': 5},
  {'name': 'company_reputation_risk', 'type': 'risk', 'priority_group': 'stability', 'default_weight': 3},
  {'name': 'company_reputation_risk_high', 'type': 'risk', 'priority_group': 'stability', 'default_weight': 3},
  {'name': 'candidate_stack_gap', 'type': 'risk', 'priority_group': 'engineering_culture', 'default_weight': 3},
  {'name': 'candidate_domain_gap', 'type': 'risk', 'priority_group': 'product_company', 'default_weight': 3},
  {
    'name': 'candidate_seniority_mismatch',
    'type': 'risk',
    'priority_group': 'engineering_culture',
    'default_weight': 4,
  },
  {'name': 'algorithmic_interview_risk', 'type': 'risk', 'priority_group': 'engineering_culture', 'default_weight': 2},
  {'name': 'pressure_culture_risk', 'type': 'risk', 'priority_group': 'work_life_balance', 'default_weight': 2},
  {'name': 'fake_flextime_risk', 'type': 'risk', 'priority_group': 'work_life_balance', 'default_weight': 2},
  {
    'name': 'traditional_corporate_process_risk',
    'type': 'risk',
    'priority_group': 'english_environment',
    'default_weight': 2,
  },
  {'name': 'customer_site_risk', 'type': 'risk', 'priority_group': 'product_company', 'default_weight': 2},
];

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
