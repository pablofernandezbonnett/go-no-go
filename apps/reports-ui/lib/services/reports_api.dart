import 'dart:convert';

import 'package:http/http.dart' as http;

import '../models/reports_index_payload.dart';

class ReportsApiClient {
  const ReportsApiClient();

  Future<ReportsIndexPayload> fetchIndex() async {
    final response = await http.get(Uri.parse('/api/reports/index'));
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw StateError('Failed to load reports index (${response.statusCode}).');
    }

    final decoded = jsonDecode(response.body);
    if (decoded is! Map<String, dynamic>) {
      throw StateError('Invalid reports index response format.');
    }

    return ReportsIndexPayload.fromJson(decoded);
  }
}
