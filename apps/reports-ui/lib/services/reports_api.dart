import 'dart:convert';

import 'package:http/http.dart' as http;

import '../constants/evaluation_contract.dart';
import '../models/reports_index_payload.dart';

class ReportsApiClient {
  const ReportsApiClient();

  static ReportsIndexPayload? _cachedIndex;
  static Future<ReportsIndexPayload>? _pendingIndex;
  static final Map<String, ReportRunPayload> _cachedRuns = {};
  static final Map<String, Future<ReportRunPayload>> _pendingRuns = {};

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
      if (forceRefresh) _cachedRuns.clear();
      return index;
    } finally {
      if (identical(_pendingIndex, request)) {
        _pendingIndex = null;
      }
    }
  }

  Future<ReportsIndexPayload> fetchIndexWithRun({
    String? runId,
    required String view,
    bool forceRefresh = false,
  }) async {
    final index = await fetchIndex(forceRefresh: forceRefresh);
    if (index.runs.isEmpty) return index;
    final selected = index.runs.where((run) => run.runId == runId).firstOrNull ?? index.runs.first;
    final detail = await fetchRun(selected.runId, view: view);
    return index.withRun(detail);
  }

  Future<ReportRunPayload> fetchRun(String runId, {required String view}) async {
    final key = '$view:$runId';
    final cached = _cachedRuns[key];
    if (cached != null) return cached;
    final pending = _pendingRuns[key];
    if (pending != null) return pending;

    final request = _fetchRunFromApi(runId, view);
    _pendingRuns[key] = request;
    try {
      final run = await request;
      _cachedRuns[key] = run;
      return run;
    } finally {
      _pendingRuns.remove(key);
    }
  }

  Future<ReportRunPayload> _fetchRunFromApi(String runId, String view) async {
    final uri = Uri.parse(reportsRunApiPath).replace(queryParameters: {'run': runId, 'view': view});
    final response = await http.get(uri);
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw StateError('Failed to load report run (${response.statusCode}).');
    }
    final decoded = jsonDecode(response.body);
    if (decoded is! Map<String, dynamic>) {
      throw StateError('Invalid report run response format.');
    }
    return ReportRunPayload.fromJson(decoded);
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
