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
    await _loadJobHistory(itemsByUrl);
    await _loadAdHocHistory(itemsByUrl);
    final items = itemsByUrl.values.toList()..sort((left, right) => right.generatedAt.compareTo(left.generatedAt));
    return EvaluationUrlHistoryCatalog(
      items: items.map((item) => item.toJson()).toList(growable: false),
    );
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

  Future<void> _loadAdHocHistory(Map<String, _EvaluationUrlHistoryEntry> itemsByUrl) async {
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
    }
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

  Future<_EvaluationUrlHistoryEntry?> _parseAdHocFile(File file) async {
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

    return _EvaluationUrlHistoryEntry(
      url: url,
      companyName: _readString(jobInput, 'company_name'),
      title: _readString(jobInput, 'title'),
      generatedAt: generatedAt,
      sourceKind: historySourceKindAdHoc,
      persona: _readString(document, 'persona'),
      candidateProfile: _readString(document, 'candidate_profile'),
    );
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
}

class EvaluationUrlHistoryCatalog {
  const EvaluationUrlHistoryCatalog({required this.items});

  final List<Map<String, String>> items;

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

  Map<String, String> toJson() {
    return {
      'url': url,
      'company_name': companyName,
      'title': title,
      'generated_at': generatedAt.toUtc().toIso8601String(),
      'source_kind': sourceKind,
      'persona': persona,
      'candidate_profile': candidateProfile,
    };
  }
}
