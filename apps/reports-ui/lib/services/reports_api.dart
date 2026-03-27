import 'dart:convert';

import 'package:http/http.dart' as http;

import '../constants/evaluation_contract.dart';
import '../models/reports_index_payload.dart';

class ReportsApiClient {
  const ReportsApiClient();

  static ReportsIndexPayload? _cachedIndex;
  static Future<ReportsIndexPayload>? _pendingIndex;

  ReportsIndexPayload? peekCachedIndex() => _cachedIndex;

  Future<ReportsIndexPayload> fetchIndex({bool forceRefresh = false}) async {
    if (!forceRefresh && _cachedIndex != null) {
      return _cachedIndex!;
    }

    if (!forceRefresh && _pendingIndex != null) {
      return _pendingIndex!;
    }

    final request = _fetchIndexFromApi();
    _pendingIndex = request;
    try {
      final index = await request;
      _cachedIndex = index;
      return index;
    } finally {
      if (identical(_pendingIndex, request)) {
        _pendingIndex = null;
      }
    }
  }

  Future<ReportsIndexPayload> _fetchIndexFromApi() async {
    final response = await http.get(Uri.parse(reportsIndexApiPath));
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
