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
  static const String _descriptionKey = 'description';
  static const String _prioritiesKey = 'priorities';
  static const String _hardNoKey = 'hard_no';
  static const String _acceptableIfKey = 'acceptable_if';

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

  Future<CompanyOption> addCompany(CompanyCreateInput input) async {
    final normalized = input.normalized();
    normalized.validate();

    final companiesPath = p.join(engineRoot.path, _configFolder, _companiesFile);
    final companiesFile = File(companiesPath);
    final currentContent = await _readCurrentContent(companiesFile);
    final existingCompanies = await _loadCompanies(companiesFile);
    final duplicated = existingCompanies.any((entry) => entry.id == normalized.id);
    if (duplicated) {
      throw StateError('Company id "${normalized.id}" already exists.');
    }

    final entryBlock = _buildCompanyEntry(normalized);
    final nextContent = _appendEntryUnderRoot(currentContent, entryBlock, _companiesKey);
    await companiesFile.writeAsString(nextContent);

    return CompanyOption(id: normalized.id, name: normalized.name);
  }

  Future<String> addPersona(PersonaCreateInput input) async {
    final normalized = input.normalized();
    normalized.validate();

    final personasPath = p.join(engineRoot.path, _configFolder, _personasFile);
    final personasFile = File(personasPath);
    final currentContent = await _readCurrentContent(personasFile);
    final existingPersonas = await _loadPersonaIds(personasFile);
    final duplicated = existingPersonas.any((entry) => entry == normalized.id);
    if (duplicated) {
      throw StateError('Persona id "${normalized.id}" already exists.');
    }

    final entryBlock = _buildPersonaEntry(normalized);
    final nextContent = _appendEntryUnderRoot(currentContent, entryBlock, _personasKey);
    await personasFile.writeAsString(nextContent);

    return normalized.id;
  }

  Future<String> _readCurrentContent(File companiesFile) async {
    if (!await companiesFile.exists()) {
      return '';
    }
    return companiesFile.readAsString();
  }

  String _appendEntryUnderRoot(String currentContent, String entryBlock, String rootKey) {
    if (currentContent.trim().isEmpty) {
      final buffer = StringBuffer();
      buffer.writeln('$rootKey:');
      buffer.writeln();
      buffer.write(entryBlock);
      return buffer.toString();
    }

    final buffer = StringBuffer();
    buffer.write(currentContent);
    if (!currentContent.endsWith('\n')) {
      buffer.write('\n');
    }
    buffer.write('\n');
    buffer.write(entryBlock);
    return buffer.toString();
  }

  String _buildCompanyEntry(CompanyCreateInput input) {
    final buffer = StringBuffer();
    buffer.writeln('  - $_idKey: ${_yamlValue(input.id)}');
    buffer.writeln('    $_nameKey: ${_yamlValue(input.name)}');
    buffer.writeln('    career_url: ${_yamlValue(input.careerUrl)}');
    buffer.writeln('    corporate_url: ${_yamlValue(input.corporateUrl)}');
    buffer.writeln('    type_hint: ${_yamlValue(input.typeHint)}');
    buffer.writeln('    region: ${_yamlValue(input.region)}');
    buffer.writeln('    notes: ${_yamlValue(input.notes)}');
    buffer.writeln('    profile_tags: []');
    buffer.writeln('    risk_tags: []');
    return buffer.toString();
  }

  String _buildPersonaEntry(PersonaCreateInput input) {
    final buffer = StringBuffer();
    buffer.writeln('  - $_idKey: ${_yamlValue(input.id)}');
    buffer.writeln('    $_descriptionKey: ${_yamlValue(input.description)}');
    buffer.writeln('    $_prioritiesKey:');
    for (final item in input.priorities) {
      buffer.writeln('      - ${_yamlValue(item)}');
    }
    buffer.writeln();
    buffer.writeln('    $_hardNoKey:');
    for (final item in input.hardNo) {
      buffer.writeln('      - ${_yamlValue(item)}');
    }
    buffer.writeln();
    buffer.writeln('    $_acceptableIfKey:');
    for (final item in input.acceptableIf) {
      buffer.writeln('      - ${_yamlValue(item)}');
    }
    return buffer.toString();
  }

  String _yamlValue(String value) {
    const safeScalarPattern = r'^[a-zA-Z0-9_.-]+$';
    final safe = RegExp(safeScalarPattern);
    if (safe.hasMatch(value)) {
      return value;
    }
    return '\'${value.replaceAll('\'', '\'\'')}\'';
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

class CompanyCreateInput {
  const CompanyCreateInput({
    required this.id,
    required this.name,
    required this.careerUrl,
    required this.corporateUrl,
    required this.typeHint,
    required this.region,
    required this.notes,
  });

  static const String _defaultTypeHint = 'product';
  static const String _defaultRegion = 'japan';
  static const String _defaultNotes = 'Added from Operations UI.';

  final String id;
  final String name;
  final String careerUrl;
  final String corporateUrl;
  final String typeHint;
  final String region;
  final String notes;

  CompanyCreateInput normalized() {
    return CompanyCreateInput(
      id: id.trim(),
      name: name.trim(),
      careerUrl: careerUrl.trim(),
      corporateUrl: corporateUrl.trim(),
      typeHint: typeHint.trim().isEmpty ? _defaultTypeHint : typeHint.trim(),
      region: region.trim().isEmpty ? _defaultRegion : region.trim(),
      notes: notes.trim().isEmpty ? _defaultNotes : notes.trim(),
    );
  }

  void validate() {
    if (id.isEmpty) {
      throw const FormatException('Field "id" is required.');
    }
    if (name.isEmpty) {
      throw const FormatException('Field "name" is required.');
    }
    if (careerUrl.isEmpty) {
      throw const FormatException('Field "careerUrl" is required.');
    }
    if (corporateUrl.isEmpty) {
      throw const FormatException('Field "corporateUrl" is required.');
    }
  }
}

class PersonaCreateInput {
  const PersonaCreateInput({
    required this.id,
    required this.description,
    required this.priorities,
    required this.hardNo,
    required this.acceptableIf,
  });

  static const String _defaultDescription = 'Added from Operations UI.';
  static const List<String> _defaultPriorities = [
    'english_environment',
    'product_company',
    'hybrid_work',
  ];
  static const List<String> _defaultHardNo = [
    'consulting_company',
    'onsite_only',
    'salary_missing',
  ];
  static const List<String> _defaultAcceptableIf = [
    'hybrid_partial',
    'japanese_not_blocking',
  ];

  final String id;
  final String description;
  final List<String> priorities;
  final List<String> hardNo;
  final List<String> acceptableIf;

  PersonaCreateInput normalized() {
    return PersonaCreateInput(
      id: id.trim(),
      description: description.trim().isEmpty ? _defaultDescription : description.trim(),
      priorities: _normalizeList(priorities, _defaultPriorities),
      hardNo: _normalizeList(hardNo, _defaultHardNo),
      acceptableIf: _normalizeList(acceptableIf, _defaultAcceptableIf),
    );
  }

  void validate() {
    if (id.isEmpty) {
      throw const FormatException('Field "id" is required.');
    }
  }

  static List<String> _normalizeList(List<String> values, List<String> fallback) {
    final normalized = values.map((item) => item.trim()).where((item) => item.isNotEmpty).toSet().toList();
    if (normalized.isNotEmpty) {
      return normalized;
    }
    return List<String>.from(fallback);
  }
}
