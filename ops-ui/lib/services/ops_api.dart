import 'dart:convert';

import 'package:http/http.dart' as http;

import '../models/ops_models.dart';

class OpsApiClient {
  const OpsApiClient();

  static const String _jsonHeader = 'application/json';

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

    return runsRaw
        .whereType<Map<String, dynamic>>()
        .map(RunPayload.fromJson)
        .toList();
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
}
