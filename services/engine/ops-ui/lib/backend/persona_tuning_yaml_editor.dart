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
    if (entry.style != CollectionStyle.BLOCK) {
      throw const FormatException('Persona tuning requires a block mapping.');
    }
    final separator = source.contains('\r\n') ? '\r\n' : '\n';
    final lines = source.split(separator);
    final fields = {
      for (final field in entry.nodes.entries) (field.key as YamlNode).value: field,
    };
    final indent = ' ' * (fields.values.first.key as YamlNode).span.start.column;
    final replacements = <String, List<String>>{
      'ranking_strategy': [if (rankingStrategy != 'by_score') '${indent}ranking_strategy: $rankingStrategy'],
      'minimum_salary_yen': [if (minimumSalaryYen != null) '${indent}minimum_salary_yen: $minimumSalaryYen'],
      'signal_weights': [
        if (weights.isNotEmpty) ...[
          '${indent}signal_weights:',
          for (final weight in weights.entries) '$indent  ${weight.key}: ${weight.value}',
        ],
      ],
    };

    final edits = <_LineEdit>[];
    final missing = <String>[];
    for (final replacement in replacements.entries) {
      final field = fields[replacement.key];
      if (field == null) {
        missing.addAll(replacement.value);
        continue;
      }
      final key = field.key as YamlNode;
      final start = key.span.start.line;
      final end = _lineAfter(field.value).clamp(start + 1, lines.length);
      final prefix = lines[start].substring(0, key.span.start.column).trimRight();
      if ((prefix.isNotEmpty && prefix.trimLeft() != '-') ||
          field.value.span.start.offset < key.span.end.offset ||
          fields.values.any((other) {
            final line = (other.key as YamlNode).span.start.line;
            return !identical(other, field) && line >= start && line < end;
          })) {
        throw const FormatException('Cannot safely edit this persona tuning field.');
      }
      final comments = _comments(lines, key, field.value, start, end);
      edits.add(
        _LineEdit(start, end, [
          if (prefix.isNotEmpty) prefix,
          for (final comment in comments) '$indent$comment',
          ...replacement.value,
        ]),
      );
    }
    if (missing.isNotEmpty) {
      final end = _lineAfter(entry).clamp(0, lines.length);
      edits.add(_LineEdit(end, end, missing));
    }

    edits.sort((left, right) => right.start.compareTo(left.start));
    for (final edit in edits) {
      lines.replaceRange(edit.start, edit.end, edit.replacement);
    }
    final updated = lines.join(separator);
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

  int _lineAfter(YamlNode node) {
    // Block collection spans can end at the next key, after intervening comments.
    if (node is YamlMap && node.style == CollectionStyle.BLOCK && node.nodes.isNotEmpty) {
      final last = node.nodes.entries.last;
      final keyEnd = _lineAfter(last.key as YamlNode);
      final valueEnd = _lineAfter(last.value);
      return valueEnd > keyEnd ? valueEnd : keyEnd;
    }
    if (node is YamlList && node.style == CollectionStyle.BLOCK && node.nodes.isNotEmpty) {
      return _lineAfter(node.nodes.last);
    }
    final end = node.span.end;
    return end.line + (end.column == 0 ? 0 : 1);
  }

  List<String> _comments(List<String> lines, YamlNode key, YamlNode value, int start, int end) {
    final comments = <int, String>{
      for (var line = start; line < end; line++)
        if (lines[line].trimLeft().startsWith('#')) line: lines[line].trimLeft(),
    };

    void keepTrailingComment(YamlNode node, {bool afterKey = false}) {
      final location = node.span.end;
      if (location.line < start || location.line >= end) return;
      var suffix = lines[location.line].substring(location.column).trimLeft();
      if (afterKey && suffix.startsWith(':')) suffix = suffix.substring(1).trimLeft();
      if (suffix.startsWith('#')) comments[location.line] = suffix;
    }

    keepTrailingComment(key, afterKey: true);
    if (value is YamlMap && value.style == CollectionStyle.BLOCK) {
      for (final weight in value.nodes.values) {
        keepTrailingComment(weight);
      }
    } else {
      keepTrailingComment(value);
    }
    return [
      for (var line = start; line < end; line++)
        if (comments.containsKey(line)) comments[line]!,
    ];
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
