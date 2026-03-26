import 'dart:convert';

import 'package:http/http.dart' as http;

import '../models/ops_models.dart';

class OpsApiClient {
  const OpsApiClient();

  static const String _jsonHeader = 'application/json';

  Future<HealthPayload> fetchHealth() async {
    final response = await http.get(Uri.parse('/api/health'));
    if (response.statusCode != 200) {
      throw StateError('Failed to load health (${response.statusCode}).');
    }
    final decoded = jsonDecode(response.body);
    if (decoded is! Map<String, dynamic>) {
      throw StateError('Invalid health response format.');
    }
    return HealthPayload.fromJson(decoded);
  }

  Future<OpsConfigPayload> fetchConfig() async {
    final response = await http.get(Uri.parse('/api/config'));
    if (response.statusCode != 200) {
      throw StateError('Failed to load config (${response.statusCode}).');
    }

    final decoded = jsonDecode(response.body);
    if (decoded is! Map<String, dynamic>) {
      throw StateError('Invalid config response format.');
    }

    return OpsConfigPayload.fromJson(decoded);
  }

  Future<List<RunPayload>> fetchRuns() async {
    final response = await http.get(Uri.parse('/api/runs'));
    if (response.statusCode != 200) {
      throw StateError('Failed to load runs (${response.statusCode}).');
    }

    final decoded = jsonDecode(response.body);
    if (decoded is! Map<String, dynamic>) {
      throw StateError('Invalid runs response format.');
    }

    final runsRaw = decoded['runs'];
    if (runsRaw is! List) {
      return const <RunPayload>[];
    }

    return runsRaw.whereType<Map<String, dynamic>>().map(RunPayload.fromJson).toList();
  }

  Future<RunPayload> fetchRun(String runId) async {
    final response = await http.get(Uri.parse('/api/runs/$runId'));
    if (response.statusCode != 200) {
      throw StateError('Failed to load run details (${response.statusCode}).');
    }

    final decoded = jsonDecode(response.body);
    if (decoded is! Map<String, dynamic>) {
      throw StateError('Invalid run response format.');
    }

    return RunPayload.fromJson(decoded);
  }

  Future<RunPayload> createRun(Map<String, Object?> request) async {
    final response = await http.post(
      Uri.parse('/api/runs'),
      headers: const {'content-type': _jsonHeader},
      body: jsonEncode(request),
    );

    final decoded = jsonDecode(response.body);
    if (response.statusCode != 202) {
      if (decoded is Map<String, dynamic>) {
        throw StateError(decoded['message']?.toString() ?? 'Run submission failed (${response.statusCode}).');
      }
      throw StateError('Run submission failed (${response.statusCode}).');
    }

    if (decoded is! Map<String, dynamic>) {
      throw StateError('Invalid run creation response format.');
    }

    return RunPayload.fromJson(decoded);
  }

  Future<CompanyPayload> createCompany(Map<String, Object?> request) async {
    final response = await http.post(
      Uri.parse('/api/config/companies'),
      headers: const {'content-type': _jsonHeader},
      body: jsonEncode(request),
    );

    final decoded = jsonDecode(response.body);
    if (response.statusCode != 201) {
      if (decoded is Map<String, dynamic>) {
        throw StateError(decoded['message']?.toString() ?? 'Company create failed (${response.statusCode}).');
      }
      throw StateError('Company create failed (${response.statusCode}).');
    }

    if (decoded is! Map<String, dynamic>) {
      throw StateError('Invalid company creation response format.');
    }

    final companyRaw = decoded['company'];
    if (companyRaw is! Map<String, dynamic>) {
      throw StateError('Missing company payload in response.');
    }

    return CompanyPayload.fromJson(companyRaw);
  }

  Future<String> createPersona(Map<String, Object?> request) async {
    final response = await http.post(
      Uri.parse('/api/config/personas'),
      headers: const {'content-type': _jsonHeader},
      body: jsonEncode(request),
    );

    final decoded = jsonDecode(response.body);
    if (response.statusCode != 201) {
      if (decoded is Map<String, dynamic>) {
        throw StateError(decoded['message']?.toString() ?? 'Persona create failed (${response.statusCode}).');
      }
      throw StateError('Persona create failed (${response.statusCode}).');
    }

    if (decoded is! Map<String, dynamic>) {
      throw StateError('Invalid persona creation response format.');
    }

    final personaId = decoded['personaId']?.toString() ?? '';
    if (personaId.isEmpty) {
      throw StateError('Missing persona id in response.');
    }
    return personaId;
  }

  Future<List<Map<String, Object>>> fetchSignalCatalog() async {
    final response = await http.get(Uri.parse('/api/signals'));
    if (response.statusCode != 200) {
      throw StateError('Failed to load signal catalog (${response.statusCode}).');
    }
    final decoded = jsonDecode(response.body);
    if (decoded is! Map<String, dynamic>) {
      throw StateError('Invalid signal catalog response format.');
    }
    final signals = decoded['signals'];
    if (signals is! List) {
      return const [];
    }
    return signals.whereType<Map<String, dynamic>>().map((entry) => Map<String, Object>.from(entry)).toList();
  }

  Future<Map<String, dynamic>> fetchPersonaDetail(String id) async {
    final response = await http.get(Uri.parse('/api/config/personas/$id'));
    if (response.statusCode == 404) {
      throw StateError('Persona not found: $id');
    }
    if (response.statusCode != 200) {
      throw StateError('Failed to load persona detail (${response.statusCode}).');
    }
    final decoded = jsonDecode(response.body);
    if (decoded is! Map<String, dynamic>) {
      throw StateError('Invalid persona detail response format.');
    }
    return decoded;
  }

  Future<Map<String, dynamic>> updatePersonaTuning(
    String id,
    Map<String, int> weights,
    String strategy,
    int? minimumSalaryYen,
  ) async {
    final response = await http.put(
      Uri.parse('/api/config/personas/$id/tuning'),
      headers: const {'content-type': _jsonHeader},
      body: jsonEncode({
        'signalWeights': weights,
        'rankingStrategy': strategy,
        'minimumSalaryYen': minimumSalaryYen,
      }),
    );
    final decoded = jsonDecode(response.body);
    if (response.statusCode == 404) {
      throw StateError('Persona not found: $id');
    }
    if (response.statusCode != 200) {
      if (decoded is Map<String, dynamic>) {
        throw StateError(decoded['message']?.toString() ?? 'Tuning update failed (${response.statusCode}).');
      }
      throw StateError('Tuning update failed (${response.statusCode}).');
    }
    if (decoded is! Map<String, dynamic>) {
      throw StateError('Invalid tuning update response format.');
    }
    return decoded;
  }
}
