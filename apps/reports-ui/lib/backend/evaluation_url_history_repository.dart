import 'dart:io';

import 'package:path/path.dart' as p;
import 'package:yaml/yaml.dart';

import '../constants/evaluation_contract.dart';

const _jobsDirectory = 'jobs';
const _yamlExtension = '.yaml';
const _descriptionSourceUrlPrefix = 'Source URL: ';
const _descriptionFetchedAtPrefix = 'Fetched At: ';
const _genericListingPathSegments = <String>{
  'career',
  'careers',
  'job',
  'jobs',
  'opening',
  'openings',
  'position',
  'positions',
  'recruit',
  'recruitment',
  'hiring',
};

class EvaluationUrlHistoryRepository {
  EvaluationUrlHistoryRepository({required this.reportsRoot});

  final Directory reportsRoot;

  Future<EvaluationUrlHistoryCatalog> load() async {
    final itemsByUrl = <String, _EvaluationUrlHistoryEntry>{};
    final latestAdHocByUrl = <String, _AdHocEvaluationHistoryEntry>{};
    await _loadJobHistory(itemsByUrl);
    await _loadAdHocHistory(itemsByUrl, latestAdHocByUrl);
    final items = itemsByUrl.values.toList()..sort((left, right) => right.generatedAt.compareTo(left.generatedAt));
    return EvaluationUrlHistoryCatalog(
      items: items.map((item) => item.toJson(savedEvaluation: latestAdHocByUrl[item.url])).toList(growable: false),
    );
  }

  Future<Map<String, Object?>?> loadLatestAdHocDetail(String url) async {
    final entry = await _findLatestAdHocEntry(url);
    if (entry == null) {
      return null;
    }
    return _readAdHocPayload(entry.file);
  }

  Future<void> _loadJobHistory(Map<String, _EvaluationUrlHistoryEntry> itemsByUrl) async {
    final directory = Directory(p.join(reportsRoot.path, _jobsDirectory));
    if (!await directory.exists()) {
      return;
    }

    final entries = <_EvaluationUrlHistoryEntry>[];
    await for (final entity in directory.list(recursive: true, followLinks: false)) {
      if (entity is! File || !entity.path.endsWith(_yamlExtension)) {
        continue;
      }

      final parsed = await _parseJobFile(entity);
      if (parsed == null) {
        continue;
      }
      entries.add(parsed);
    }

    final titleCountsByUrl = <String, Set<String>>{};
    for (final entry in entries) {
      final titles = titleCountsByUrl.putIfAbsent(entry.url, () => <String>{});
      titles.add(_normalizeTitle(entry.title));
    }

    for (final entry in entries) {
      final titles = titleCountsByUrl[entry.url] ?? const <String>{};
      if (titles.length > 1) {
        continue;
      }
      if (!_isConcreteJobUrl(entry.url)) {
        continue;
      }
      _putLatest(itemsByUrl, entry);
    }
  }

  Future<void> _loadAdHocHistory(
    Map<String, _EvaluationUrlHistoryEntry> itemsByUrl,
    Map<String, _AdHocEvaluationHistoryEntry> latestAdHocByUrl,
  ) async {
    final directory = Directory(p.join(reportsRoot.path, adHocEvaluationsDirectory));
    if (!await directory.exists()) {
      return;
    }

    await for (final entity in directory.list(recursive: false, followLinks: false)) {
      if (entity is! File || !entity.path.endsWith(_yamlExtension)) {
        continue;
      }

      final parsed = await _parseAdHocFile(entity);
      if (parsed == null) {
        continue;
      }
      if (!_isConcreteJobUrl(parsed.url)) {
        continue;
      }
      _putLatest(itemsByUrl, parsed);
      final current = latestAdHocByUrl[parsed.url];
      if (current == null || parsed.generatedAt.isAfter(current.generatedAt)) {
        latestAdHocByUrl[parsed.url] = parsed;
      }
    }
  }

  Future<_AdHocEvaluationHistoryEntry?> _findLatestAdHocEntry(String url) async {
    final normalizedUrl = url.trim();
    if (normalizedUrl.isEmpty) {
      return null;
    }

    final directory = Directory(p.join(reportsRoot.path, adHocEvaluationsDirectory));
    if (!await directory.exists()) {
      return null;
    }

    _AdHocEvaluationHistoryEntry? latest;
    await for (final entity in directory.list(recursive: false, followLinks: false)) {
      if (entity is! File || !entity.path.endsWith(_yamlExtension)) {
        continue;
      }

      final parsed = await _parseAdHocFile(entity);
      if (parsed == null || parsed.url != normalizedUrl) {
        continue;
      }
      if (latest == null || parsed.generatedAt.isAfter(latest.generatedAt)) {
        latest = parsed;
      }
    }
    return latest;
  }

  void _putLatest(
    Map<String, _EvaluationUrlHistoryEntry> itemsByUrl,
    _EvaluationUrlHistoryEntry candidate,
  ) {
    final current = itemsByUrl[candidate.url];
    if (current == null || candidate.generatedAt.isAfter(current.generatedAt)) {
      itemsByUrl[candidate.url] = candidate;
    }
  }

  Future<_EvaluationUrlHistoryEntry?> _parseJobFile(File file) async {
    final document = await _readYamlDocument(file);
    if (document == null) {
      return null;
    }

    final description = _readString(document, 'description');
    final url = _extractPrefixedLine(description, _descriptionSourceUrlPrefix);
    if (url.isEmpty) {
      return null;
    }

    final generatedAt = _parseDateTime(
      _extractPrefixedLine(description, _descriptionFetchedAtPrefix),
      await _fileModifiedAt(file),
    );

    return _EvaluationUrlHistoryEntry(
      url: url,
      companyName: _readString(document, 'company_name'),
      title: _readString(document, 'title'),
      generatedAt: generatedAt,
      sourceKind: historySourceKindPipelineJob,
      persona: '',
      candidateProfile: '',
    );
  }

  Future<_AdHocEvaluationHistoryEntry?> _parseAdHocFile(File file) async {
    final document = await _readYamlDocument(file);
    if (document == null) {
      return null;
    }

    final source = _readMap(document, 'source');
    final sourceKind = _readString(source, 'kind');
    final url = _readString(source, 'url');
    if (sourceKind != inputModeUrl || url.isEmpty) {
      return null;
    }

    final jobInput = _readMap(document, 'job_input');
    final generatedAt = _parseDateTime(
      _readString(document, 'generated_at'),
      await _fileModifiedAt(file),
    );

    return _AdHocEvaluationHistoryEntry(
      url: url,
      companyName: _readString(jobInput, 'company_name'),
      title: _readString(jobInput, 'title'),
      generatedAt: generatedAt,
      sourceKind: historySourceKindAdHoc,
      persona: _readString(document, 'persona'),
      candidateProfile: _readString(document, 'candidate_profile'),
      file: file,
    );
  }

  Future<Map<String, Object?>?> _readAdHocPayload(File file) async {
    final document = await _readYamlDocument(file);
    if (document == null) {
      return null;
    }

    final payload = _toJsonValue(document);
    if (payload is! Map<String, Object?>) {
      return null;
    }

    payload.remove('analysis_file');
    final source = payload['source'];
    if (source is Map<String, Object?>) {
      source['file'] = '';
      payload['source'] = source;
    }
    return payload;
  }

  Future<YamlMap?> _readYamlDocument(File file) async {
    try {
      final raw = await file.readAsString();
      final decoded = loadYaml(raw);
      return decoded is YamlMap ? decoded : null;
    } catch (_) {
      return null;
    }
  }

  Future<DateTime> _fileModifiedAt(File file) async {
    final stat = await file.stat();
    return stat.modified.toUtc();
  }

  DateTime _parseDateTime(String rawValue, DateTime fallback) {
    final parsed = DateTime.tryParse(rawValue.trim());
    return parsed?.toUtc() ?? fallback;
  }

  bool _isConcreteJobUrl(String rawUrl) {
    final uri = Uri.tryParse(rawUrl.trim());
    if (uri == null) {
      return false;
    }
    if (!uri.isScheme('http') && !uri.isScheme('https')) {
      return false;
    }
    if (uri.queryParameters.isNotEmpty || uri.fragment.trim().isNotEmpty) {
      return true;
    }

    final segments = uri.pathSegments
        .map(_normalizePathSegment)
        .where((segment) => segment.isNotEmpty)
        .toList(growable: false);
    if (segments.isEmpty) {
      return false;
    }

    final lastSegment = segments.last;
    if (_genericListingPathSegments.contains(lastSegment)) {
      return false;
    }
    return _looksLikeJobSlug(lastSegment);
  }

  String _extractPrefixedLine(String block, String prefix) {
    if (block.isEmpty) {
      return '';
    }
    for (final rawLine in block.split(RegExp(r'\r?\n'))) {
      final line = rawLine.trim();
      if (line.startsWith(prefix)) {
        return line.substring(prefix.length).trim();
      }
    }
    return '';
  }

  Map<dynamic, dynamic> _readMap(Map<dynamic, dynamic> source, String key) {
    final value = source[key];
    return value is Map ? value : const {};
  }

  String _readString(Map<dynamic, dynamic> source, String key) {
    final value = source[key];
    return value?.toString().trim() ?? '';
  }

  String _normalizePathSegment(String value) {
    return value.trim().toLowerCase();
  }

  String _normalizeTitle(String value) {
    return value.trim().toLowerCase();
  }

  bool _looksLikeJobSlug(String segment) {
    if (segment.contains('-') || segment.contains('_')) {
      return true;
    }
    return RegExp(r'\d').hasMatch(segment);
  }

  Object? _toJsonValue(Object? value) {
    if (value == null || value is String || value is num || value is bool) {
      return value;
    }
    if (value is List) {
      final rawList = value;
      return rawList.map(_toJsonValue).toList(growable: false);
    }
    if (value is Map) {
      final rawMap = value;
      return Map<String, Object?>.fromEntries(
        rawMap.entries.map((entry) => MapEntry(entry.key.toString(), _toJsonValue(entry.value))),
      );
    }
    return value.toString();
  }
}

class EvaluationUrlHistoryCatalog {
  const EvaluationUrlHistoryCatalog({required this.items});

  final List<Map<String, Object?>> items;

  Map<String, Object> toJson() {
    return {'items': items};
  }
}

class _EvaluationUrlHistoryEntry {
  const _EvaluationUrlHistoryEntry({
    required this.url,
    required this.companyName,
    required this.title,
    required this.generatedAt,
    required this.sourceKind,
    required this.persona,
    required this.candidateProfile,
  });

  final String url;
  final String companyName;
  final String title;
  final DateTime generatedAt;
  final String sourceKind;
  final String persona;
  final String candidateProfile;

  Map<String, Object?> toJson({_AdHocEvaluationHistoryEntry? savedEvaluation}) {
    return {
      'url': url,
      'company_name': companyName,
      'title': title,
      'generated_at': generatedAt.toUtc().toIso8601String(),
      'source_kind': sourceKind,
      'persona': persona,
      'candidate_profile': candidateProfile,
      'saved_evaluation_available': savedEvaluation != null,
      'saved_evaluation_generated_at': savedEvaluation?.generatedAt.toUtc().toIso8601String() ?? '',
      'saved_evaluation_persona': savedEvaluation?.persona ?? '',
      'saved_evaluation_candidate_profile': savedEvaluation?.candidateProfile ?? '',
    };
  }
}

class _AdHocEvaluationHistoryEntry extends _EvaluationUrlHistoryEntry {
  const _AdHocEvaluationHistoryEntry({
    required super.url,
    required super.companyName,
    required super.title,
    required super.generatedAt,
    required super.sourceKind,
    required super.persona,
    required super.candidateProfile,
    required this.file,
  });

  final File file;
}
