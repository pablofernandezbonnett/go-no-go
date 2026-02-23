import 'dart:io';

import 'package:path/path.dart' as p;
import 'package:yaml/yaml.dart';

class EngineConfigCatalog {
  const EngineConfigCatalog({
    required this.personaIds,
    required this.companies,
  });

  final List<String> personaIds;
  final List<CompanyOption> companies;

  Map<String, Object?> toJson() {
    return {
      'personaIds': personaIds,
      'companies': companies.map((item) => item.toJson()).toList(),
    };
  }
}

class CompanyOption {
  const CompanyOption({
    required this.id,
    required this.name,
  });

  final String id;
  final String name;

  Map<String, Object?> toJson() {
    return {
      'id': id,
      'name': name,
    };
  }
}

class EngineConfigRepository {
  const EngineConfigRepository({
    required this.engineRoot,
  });

  static const String _personasFile = 'personas.yaml';
  static const String _companiesFile = 'companies.yaml';
  static const String _configFolder = 'config';
  static const String _personasKey = 'personas';
  static const String _companiesKey = 'companies';
  static const String _idKey = 'id';
  static const String _nameKey = 'name';

  final Directory engineRoot;

  Future<EngineConfigCatalog> loadCatalog() async {
    final personasPath = p.join(engineRoot.path, _configFolder, _personasFile);
    final companiesPath = p.join(engineRoot.path, _configFolder, _companiesFile);

    final personaIds = await _loadPersonaIds(File(personasPath));
    final companies = await _loadCompanies(File(companiesPath));

    return EngineConfigCatalog(
      personaIds: personaIds,
      companies: companies,
    );
  }

  Future<List<String>> _loadPersonaIds(File personasFile) async {
    if (!await personasFile.exists()) {
      return const [];
    }

    final decoded = loadYaml(await personasFile.readAsString());
    if (decoded is! YamlMap) {
      return const [];
    }

    final personas = decoded[_personasKey];
    if (personas is! YamlList) {
      return const [];
    }

    final values = <String>[];
    for (final entry in personas) {
      if (entry is! YamlMap) {
        continue;
      }
      final id = entry[_idKey]?.toString().trim() ?? '';
      if (id.isEmpty) {
        continue;
      }
      values.add(id);
    }

    values.sort();
    return values;
  }

  Future<List<CompanyOption>> _loadCompanies(File companiesFile) async {
    if (!await companiesFile.exists()) {
      return const [];
    }

    final decoded = loadYaml(await companiesFile.readAsString());
    if (decoded is! YamlMap) {
      return const [];
    }

    final companies = decoded[_companiesKey];
    if (companies is! YamlList) {
      return const [];
    }

    final values = <CompanyOption>[];
    for (final entry in companies) {
      if (entry is! YamlMap) {
        continue;
      }
      final id = entry[_idKey]?.toString().trim() ?? '';
      if (id.isEmpty) {
        continue;
      }
      final name = entry[_nameKey]?.toString().trim();
      values.add(CompanyOption(id: id, name: (name == null || name.isEmpty) ? id : name));
    }

    values.sort((left, right) => left.id.compareTo(right.id));
    return values;
  }
}
