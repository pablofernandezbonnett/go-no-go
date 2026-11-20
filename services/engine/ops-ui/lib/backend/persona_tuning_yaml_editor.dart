import 'package:yaml/yaml.dart';

class PersonaTuningYamlEditor {
  const PersonaTuningYamlEditor();

  static final _weightKeyPattern = RegExp(r'^[a-z0-9_]+$');
  static const _rankingStrategies = {
    'by_score',
    'by_language_ease',
    'by_reputation',
    'by_composite',
  };

  String update(
    String source, {
    required String personaId,
    required Map<String, int> weights,
    required String rankingStrategy,
    required int? minimumSalaryYen,
  }) {
    if (!_rankingStrategies.contains(rankingStrategy)) {
      throw const FormatException('Invalid ranking strategy.');
    }
    if (minimumSalaryYen != null && minimumSalaryYen <= 0) {
      throw const FormatException('Minimum salary must be positive when provided.');
    }
    if (weights.keys.any((key) => !_weightKeyPattern.hasMatch(key))) {
      throw const FormatException('Signal weight names must use lowercase letters, numbers, or underscores.');
    }

    final document = loadYamlNode(source);
    if (document is! YamlMap || document.nodes['personas'] is! YamlList) {
      throw StateError('personas.yaml has unexpected format.');
    }
    final personas = document.nodes['personas'] as YamlList;
    final index = personas.nodes.indexWhere((node) => node is YamlMap && node['id'] == personaId);
    if (index < 0) {
      throw StateError('Persona "$personaId" not found.');
    }

    final entry = personas.nodes[index] as YamlMap;
    final lines = source.split('\n');
    final endLine = index + 1 < personas.length ? personas.nodes[index + 1].span.start.line : personas.span.end.line;
    final replacements = <String, List<String>?>{
      'ranking_strategy': rankingStrategy == 'by_score' ? null : ['    ranking_strategy: $rankingStrategy'],
      'minimum_salary_yen': minimumSalaryYen == null ? null : ['    minimum_salary_yen: $minimumSalaryYen'],
      'signal_weights': weights.isEmpty
          ? null
          : [
              '    signal_weights:',
              for (final weight in weights.entries) '      ${weight.key}: ${weight.value}',
            ],
    };

    final edits = <_LineEdit>[];
    final missing = <String>[];
    for (final field in replacements.keys) {
      final start = _fieldLine(entry, field);
      if (start == null) {
        if (replacements[field] != null) missing.add(field);
        continue;
      }
      edits.add(_LineEdit(start, _fieldEnd(lines, start, endLine), replacements[field] ?? const []));
    }
    if (missing.isNotEmpty) {
      edits.add(_LineEdit(endLine, endLine, [for (final field in missing) ...replacements[field]!]));
    }

    edits.sort((left, right) => right.start.compareTo(left.start));
    for (final edit in edits) {
      lines.replaceRange(edit.start, edit.end, edit.replacement);
    }
    final updated = lines.join('\n');
    final validated = loadYaml(updated);
    if (validated is! YamlMap || validated['personas'] is! YamlList) {
      throw StateError('Edited personas.yaml has unexpected format.');
    }
    final updatedPersonas = validated['personas'] as YamlList;
    final updatedEntry = updatedPersonas.whereType<YamlMap>().where((item) => item['id'] == personaId).firstOrNull;
    if (updatedEntry == null ||
        updatedEntry['ranking_strategy'] != (rankingStrategy == 'by_score' ? null : rankingStrategy) ||
        updatedEntry['minimum_salary_yen'] != minimumSalaryYen ||
        !_sameWeights(updatedEntry['signal_weights'], weights)) {
      throw StateError('Edited persona tuning did not validate.');
    }
    return updated;
  }

  int? _fieldLine(YamlMap entry, String field) {
    for (final key in entry.nodes.keys) {
      if (key is YamlNode && key.value == field) {
        return key.span.start.line;
      }
    }
    return null;
  }

  int _fieldEnd(List<String> lines, int start, int entryEnd) {
    var end = start + 1;
    while (end < entryEnd && end < lines.length) {
      final line = lines[end];
      if (line.trim().isEmpty) break;
      if (line.length - line.trimLeft().length <= 4) break;
      end++;
    }
    return end;
  }

  bool _sameWeights(Object? value, Map<String, int> expected) {
    if (value == null) return expected.isEmpty;
    if (value is! YamlMap || value.length != expected.length) return false;
    for (final item in expected.entries) {
      if (value[item.key] != item.value) return false;
    }
    return true;
  }
}

class _LineEdit {
  const _LineEdit(this.start, this.end, this.replacement);

  final int start;
  final int end;
  final List<String> replacement;
}
