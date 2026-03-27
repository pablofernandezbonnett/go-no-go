import 'dart:io';

import 'package:path/path.dart' as p;
import 'package:yaml/yaml.dart';

class EngineConfigCatalog {
  const EngineConfigCatalog({
    required this.personaIds,
    required this.candidateProfileIds,
    required this.companies,
  });

  final List<String> personaIds;
  final List<String> candidateProfileIds;
  final List<CompanyOption> companies;

  Map<String, Object?> toJson() {
    return {
      'personaIds': personaIds,
      'candidateProfileIds': candidateProfileIds,
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

class PersonaDetail {
  const PersonaDetail({
    required this.id,
    required this.description,
    required this.priorities,
    required this.hardNo,
    required this.acceptableIf,
    required this.signalWeights,
    required this.rankingStrategy,
    required this.minimumSalaryYen,
  });

  final String id;
  final String description;
  final String rankingStrategy;
  final List<String> priorities;
  final List<String> hardNo;
  final List<String> acceptableIf;
  final Map<String, int> signalWeights;
  final int? minimumSalaryYen;

  Map<String, Object?> toJson() {
    return {
      'id': id,
      'description': description,
      'priorities': priorities,
      'hard_no': hardNo,
      'acceptable_if': acceptableIf,
      'signal_weights': signalWeights,
      'ranking_strategy': rankingStrategy,
      'minimum_salary_yen': minimumSalaryYen,
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
  static const String _candidateProfilesFolder = 'candidate-profiles';
  static const String _personasKey = 'personas';
  static const String _companiesKey = 'companies';
  static const String _idKey = 'id';
  static const String _nameKey = 'name';
  static const String _descriptionKey = 'description';
  static const String _prioritiesKey = 'priorities';
  static const String _hardNoKey = 'hard_no';
  static const String _acceptableIfKey = 'acceptable_if';
  static const String _rankingStrategyKey = 'ranking_strategy';
  static const String _signalWeightsKey = 'signal_weights';
  static const String _minimumSalaryYenKey = 'minimum_salary_yen';

  final Directory engineRoot;

  Future<EngineConfigCatalog> loadCatalog() async {
    final personasPath = p.join(engineRoot.path, _configFolder, _personasFile);
    final companiesPath = p.join(engineRoot.path, _configFolder, _companiesFile);
    final candidateProfilesPath = p.join(
      engineRoot.path,
      _configFolder,
      _candidateProfilesFolder,
    );

    final personaIds = await _loadPersonaIds(File(personasPath));
    final companies = await _loadCompanies(File(companiesPath));
    final candidateProfileIds = await _loadCandidateProfileIds(
      Directory(candidateProfilesPath),
    );

    return EngineConfigCatalog(
      personaIds: personaIds,
      candidateProfileIds: candidateProfileIds,
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

  Future<PersonaDetail?> loadPersonaDetail(String id) async {
    final personasPath = p.join(engineRoot.path, _configFolder, _personasFile);
    final personasFile = File(personasPath);
    if (!await personasFile.exists()) {
      return null;
    }

    final decoded = loadYaml(await personasFile.readAsString());
    if (decoded is! YamlMap) {
      return null;
    }

    final personas = decoded[_personasKey];
    if (personas is! YamlList) {
      return null;
    }

    for (final entry in personas) {
      if (entry is! YamlMap) {
        continue;
      }
      final entryId = entry[_idKey]?.toString().trim() ?? '';
      if (entryId != id) {
        continue;
      }

      final description = entry[_descriptionKey]?.toString().trim() ?? '';
      final rankingStrategy = entry[_rankingStrategyKey]?.toString().trim() ?? 'by_score';
      final minimumSalaryYen = _parseOptionalInt(entry[_minimumSalaryYenKey]);

      List<String> parseStringList(String key) {
        final raw = entry[key];
        if (raw is! YamlList) return const [];
        return raw.whereType<String>().map((s) => s.trim()).where((s) => s.isNotEmpty).toList();
      }

      Map<String, int> parseWeights() {
        final raw = entry[_signalWeightsKey];
        if (raw is! YamlMap) return const {};
        final result = <String, int>{};
        for (final k in raw.keys) {
          final v = raw[k];
          if (v is num) {
            result[k.toString()] = v.toInt();
          }
        }
        return Map.unmodifiable(result);
      }

      return PersonaDetail(
        id: entryId,
        description: description,
        priorities: parseStringList(_prioritiesKey),
        hardNo: parseStringList(_hardNoKey),
        acceptableIf: parseStringList(_acceptableIfKey),
        signalWeights: parseWeights(),
        rankingStrategy: rankingStrategy.isEmpty ? 'by_score' : rankingStrategy,
        minimumSalaryYen: minimumSalaryYen,
      );
    }

    return null;
  }

  Future<PersonaDetail> updatePersonaTuning(
    String id,
    Map<String, int> weights,
    String strategy,
    int? minimumSalaryYen,
  ) async {
    final existing = await loadPersonaDetail(id);
    if (existing == null) {
      throw StateError('Persona "$id" not found.');
    }

    final personasPath = p.join(engineRoot.path, _configFolder, _personasFile);
    final personasFile = File(personasPath);
    final rawContent = await personasFile.readAsString();

    // Load all persona details, rebuild entire file.
    final decoded = loadYaml(rawContent);
    if (decoded is! YamlMap) {
      throw StateError('personas.yaml has unexpected format.');
    }

    final personas = decoded[_personasKey];
    if (personas is! YamlList) {
      throw StateError('personas.yaml missing personas list.');
    }

    // Collect all PersonaDetail objects, replacing tuning for the target persona.
    final normalizedStrategy = strategy.trim().isEmpty ? 'by_score' : strategy.trim().toLowerCase();
    final allDetails = <PersonaDetail>[];

    for (final entry in personas) {
      if (entry is! YamlMap) continue;
      final entryId = entry[_idKey]?.toString().trim() ?? '';

      List<String> parseStringList(String key) {
        final raw = entry[key];
        if (raw is! YamlList) return const [];
        return raw.whereType<String>().map((s) => s.trim()).where((s) => s.isNotEmpty).toList();
      }

      Map<String, int> parseWeights() {
        final raw = entry[_signalWeightsKey];
        if (raw is! YamlMap) return const {};
        final result = <String, int>{};
        for (final k in raw.keys) {
          final v = raw[k];
          if (v is num) {
            result[k.toString()] = v.toInt();
          }
        }
        return result;
      }

      final entryDescription = entry[_descriptionKey]?.toString().trim() ?? '';
      final entryStrategy = entry[_rankingStrategyKey]?.toString().trim() ?? 'by_score';

      if (entryId == id) {
        allDetails.add(
          PersonaDetail(
            id: entryId,
            description: entryDescription,
            priorities: parseStringList(_prioritiesKey),
            hardNo: parseStringList(_hardNoKey),
            acceptableIf: parseStringList(_acceptableIfKey),
            signalWeights: weights,
            rankingStrategy: normalizedStrategy,
            minimumSalaryYen: minimumSalaryYen,
          ),
        );
      } else {
        allDetails.add(
          PersonaDetail(
            id: entryId,
            description: entryDescription,
            priorities: parseStringList(_prioritiesKey),
            hardNo: parseStringList(_hardNoKey),
            acceptableIf: parseStringList(_acceptableIfKey),
            signalWeights: parseWeights(),
            rankingStrategy: entryStrategy.isEmpty ? 'by_score' : entryStrategy,
            minimumSalaryYen: _parseOptionalInt(entry[_minimumSalaryYenKey]),
          ),
        );
      }
    }

    // Extract header comments from the original file (lines before 'personas:').
    final headerLines = <String>[];
    for (final line in rawContent.split('\n')) {
      if (line.trimLeft().startsWith('personas:')) break;
      headerLines.add(line);
    }
    final header = headerLines.join('\n');

    // Rebuild the full file.
    final buffer = StringBuffer();
    if (header.trim().isNotEmpty) {
      buffer.write(header);
      if (!header.endsWith('\n')) buffer.write('\n');
    }
    buffer.writeln('$_personasKey:');

    for (int i = 0; i < allDetails.length; i++) {
      final detail = allDetails[i];
      final input = PersonaCreateInput(
        id: detail.id,
        description: detail.description,
        priorities: detail.priorities,
        hardNo: detail.hardNo,
        acceptableIf: detail.acceptableIf,
        signalWeights: detail.signalWeights,
        rankingStrategy: detail.rankingStrategy,
        minimumSalaryYen: detail.minimumSalaryYen,
      );
      if (i > 0) buffer.writeln();
      buffer.write(_buildPersonaEntry(input));
    }

    await personasFile.writeAsString(buffer.toString());

    return PersonaDetail(
      id: id,
      description: existing.description,
      priorities: existing.priorities,
      hardNo: existing.hardNo,
      acceptableIf: existing.acceptableIf,
      signalWeights: weights,
      rankingStrategy: normalizedStrategy,
      minimumSalaryYen: minimumSalaryYen,
    );
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
    if (input.minimumSalaryYen != null) {
      buffer.writeln();
      buffer.writeln('    $_minimumSalaryYenKey: ${input.minimumSalaryYen}');
    }
    // ranking_strategy (only write if non-default)
    if (input.rankingStrategy != 'by_score') {
      buffer.writeln('    $_rankingStrategyKey: ${input.rankingStrategy}');
    }
    // signal_weights (only write if non-empty)
    if (input.signalWeights.isNotEmpty) {
      buffer.writeln();
      buffer.writeln('    $_signalWeightsKey:');
      for (final e in input.signalWeights.entries) {
        buffer.writeln('      ${e.key}: ${e.value}');
      }
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

  Future<List<String>> _loadCandidateProfileIds(Directory profilesDir) async {
    if (!await profilesDir.exists()) {
      return const [];
    }

    final values = <String>[];
    await for (final entity in profilesDir.list(followLinks: false)) {
      if (entity is! File || !entity.path.endsWith('.yaml')) {
        continue;
      }

      final decoded = loadYaml(await entity.readAsString());
      if (decoded is YamlMap) {
        final id = decoded[_idKey]?.toString().trim() ?? '';
        if (id.isNotEmpty) {
          values.add(id);
          continue;
        }
      }

      values.add(p.basenameWithoutExtension(entity.path));
    }

    values.sort();
    return values.toSet().toList();
  }

  int? _parseOptionalInt(Object? raw) {
    if (raw is num) {
      return raw.toInt();
    }
    final text = raw?.toString().trim() ?? '';
    if (text.isEmpty) {
      return null;
    }
    return int.tryParse(text.replaceAll(',', ''));
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
    this.signalWeights = const {},
    this.rankingStrategy = '',
    this.minimumSalaryYen,
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
  final Map<String, int> signalWeights;
  final String rankingStrategy;
  final int? minimumSalaryYen;

  PersonaCreateInput normalized() {
    return PersonaCreateInput(
      id: id.trim(),
      description: description.trim().isEmpty ? _defaultDescription : description.trim(),
      priorities: _normalizeList(priorities, _defaultPriorities),
      hardNo: _normalizeList(hardNo, _defaultHardNo),
      acceptableIf: _normalizeList(acceptableIf, _defaultAcceptableIf),
      signalWeights: signalWeights,
      rankingStrategy: rankingStrategy.trim().isEmpty ? 'by_score' : rankingStrategy.trim().toLowerCase(),
      minimumSalaryYen: minimumSalaryYen,
    );
  }

  void validate() {
    if (id.isEmpty) {
      throw const FormatException('Field "id" is required.');
    }
    if (minimumSalaryYen != null && minimumSalaryYen! <= 0) {
      throw const FormatException('Field "minimumSalaryYen" must be positive when provided.');
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
