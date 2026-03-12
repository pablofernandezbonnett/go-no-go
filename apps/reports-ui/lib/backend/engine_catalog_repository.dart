import 'dart:io';

import 'package:yaml/yaml.dart';

class EngineCatalogRepository {
  EngineCatalogRepository({
    required this.engineRoot,
  });

  final Directory engineRoot;

  Future<EngineEvaluateCatalog> loadEvaluateCatalog() async {
    final configDir = Directory(_join(engineRoot.path, 'config'));
    final personas = await _loadPersonas(File(_join(configDir.path, 'personas.yaml')));
    final candidateProfiles = await _loadCandidateProfiles(Directory(_join(configDir.path, 'candidate-profiles')));
    return EngineEvaluateCatalog(
      personas: personas,
      candidateProfiles: candidateProfiles,
    );
  }

  Future<List<EngineOptionItem>> _loadPersonas(File file) async {
    if (!await file.exists()) {
      return const [];
    }
    final decoded = loadYaml(await file.readAsString());
    final rawItems = decoded is YamlMap ? decoded['personas'] : null;
    if (rawItems is! YamlList) {
      return const [];
    }

    final items = <EngineOptionItem>[];
    for (final rawItem in rawItems) {
      if (rawItem is! YamlMap) {
        continue;
      }
      final id = rawItem['id']?.toString().trim() ?? '';
      if (id.isEmpty) {
        continue;
      }
      final description = rawItem['description']?.toString().trim() ?? '';
      items.add(
        EngineOptionItem(
          id: id,
          label: id,
          description: description,
        ),
      );
    }
    return items;
  }

  Future<List<EngineOptionItem>> _loadCandidateProfiles(Directory directory) async {
    if (!await directory.exists()) {
      return const [];
    }

    final files = await directory
        .list()
        .where((entity) => entity is File)
        .cast<File>()
        .where((file) => file.path.endsWith('.yaml') || file.path.endsWith('.yml'))
        .toList();
    files.sort((left, right) => left.path.compareTo(right.path));

    final items = <EngineOptionItem>[];
    for (final file in files) {
      final decoded = loadYaml(await file.readAsString());
      final candidate = decoded is YamlMap ? decoded['candidate'] : null;
      if (candidate is! YamlMap) {
        continue;
      }
      final id = _basenameWithoutExtension(file.path);
      final name = candidate['name']?.toString().trim() ?? id;
      final title = candidate['title']?.toString().trim() ?? '';
      items.add(
        EngineOptionItem(
          id: id,
          label: title.isEmpty ? name : '$name ($title)',
          description: title,
        ),
      );
    }
    return items;
  }

  String _basenameWithoutExtension(String path) {
    final normalized = path.replaceAll('\\', '/');
    final name = normalized.split('/').last;
    final dot = name.lastIndexOf('.');
    return dot >= 0 ? name.substring(0, dot) : name;
  }

  String _join(String left, String right) {
    if (left.endsWith(Platform.pathSeparator)) {
      return '$left$right';
    }
    return '$left${Platform.pathSeparator}$right';
  }
}

class EngineEvaluateCatalog {
  const EngineEvaluateCatalog({
    required this.personas,
    required this.candidateProfiles,
  });

  final List<EngineOptionItem> personas;
  final List<EngineOptionItem> candidateProfiles;

  Map<String, Object> toJson() {
    return {
      'personas': personas.map((item) => item.toJson()).toList(),
      'candidateProfiles': candidateProfiles.map((item) => item.toJson()).toList(),
    };
  }
}

class EngineOptionItem {
  const EngineOptionItem({
    required this.id,
    required this.label,
    required this.description,
  });

  final String id;
  final String label;
  final String description;

  Map<String, Object> toJson() {
    return {
      'id': id,
      'label': label,
      'description': description,
    };
  }
}
