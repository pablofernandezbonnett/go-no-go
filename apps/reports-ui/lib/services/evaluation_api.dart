import 'dart:convert';

import 'package:http/http.dart' as http;

import '../constants/evaluation_contract.dart';
import '../models/evaluation_payload.dart';

class EvaluationApiClient {
  const EvaluationApiClient();

  Future<EvaluationOptionsPayload> fetchOptions() async {
    final response = await http.get(Uri.parse(evaluateOptionsApiPath));
    if (response.statusCode != 200) {
      throw StateError('Failed to load evaluation options (${response.statusCode}).');
    }
    final decoded = jsonDecode(response.body);
    if (decoded is! Map<String, dynamic>) {
      throw StateError('Invalid evaluation options response format.');
    }
    return EvaluationOptionsPayload.fromJson(decoded);
  }

  Future<EvaluationUrlHistoryPayload> fetchUrlHistory() async {
    final response = await http.get(Uri.parse(evaluateUrlHistoryApiPath));
    if (response.statusCode != 200) {
      throw StateError('Failed to load URL history (${response.statusCode}).');
    }
    final decoded = jsonDecode(response.body);
    if (decoded is! Map<String, dynamic>) {
      throw StateError('Invalid URL history response format.');
    }
    return EvaluationUrlHistoryPayload.fromJson(decoded);
  }

  Future<EvaluationSessionPayload> evaluate(Map<String, Object?> request) async {
    final response = await http.post(
      Uri.parse(evaluateApiPath),
      headers: const {'content-type': jsonContentType},
      body: jsonEncode(request),
    );
    final decoded = jsonDecode(response.body);
    if (response.statusCode != 200) {
      if (decoded is Map<String, dynamic>) {
        throw StateError(decoded['message']?.toString() ?? 'Evaluation failed (${response.statusCode}).');
      }
      throw StateError('Evaluation failed (${response.statusCode}).');
    }
    if (decoded is! Map<String, dynamic>) {
      throw StateError('Invalid evaluation response format.');
    }
    return EvaluationSessionPayload.fromJson(decoded);
  }
}
