import 'dart:convert';
import 'dart:io';

import '../constants/evaluation_contract.dart';
import 'evaluation_input_safety.dart';

const _defaultTimeoutSeconds = 20;
const _engineRunCommand = 'run';
const _engineEvaluateInputCommand = 'evaluate-input';
const _engineOutputFormatFlag = '--output-format';
const _engineOutputAnalysisFileFlag = '--output-analysis-file';
const _engineTimeoutFlag = '--timeout-seconds';
const _enginePersonaFlag = '--persona';
const _engineCandidateProfileFlag = '--candidate-profile';
const _engineJobUrlFlag = '--job-url';
const _engineStdinFlag = '--stdin';
const _engineJsonOutputFormat = 'json';
const _gradleQuietFlag = '--quiet';
const _gradleConsolePlainFlag = '--console=plain';
const _engineArgsPrefix = '--args=';
const _adhocUrlSuffix = 'url';
const _adhocTextSuffix = 'text';
const _fileExtension = '.yaml';
const _stableNameMaxLength = 72;

const _inputSafety = EvaluationInputSafety();

class EvaluationRequest {
  const EvaluationRequest({
    required this.inputMode,
    required this.personaId,
    required this.candidateProfileId,
    required this.jobUrl,
    required this.rawText,
  });

  final String inputMode;
  final String personaId;
  final String candidateProfileId;
  final String jobUrl;
  final String rawText;

  static EvaluationRequest fromJson(Map<String, dynamic> json) {
    final inputMode = (json['inputMode']?.toString() ?? inputModeRawText).trim().toLowerCase();
    final personaId = (json['personaId']?.toString() ?? '').trim();
    final candidateProfileId = (json['candidateProfileId']?.toString() ?? '').trim();
    final jobUrl = (json['jobUrl']?.toString() ?? '').trim();
    final rawText = json['rawText']?.toString() ?? '';

    if (personaId.isEmpty) {
      throw const FormatException('personaId is required.');
    }
    if (inputMode != inputModeUrl && inputMode != inputModeRawText) {
      throw const FormatException('inputMode must be url or raw_text.');
    }

    final sanitizedJobUrl = inputMode == inputModeUrl ? _inputSafety.validateUrl(jobUrl) : '';
    final sanitizedRawText = inputMode == inputModeRawText ? _inputSafety.sanitizeRawText(rawText) : '';

    return EvaluationRequest(
      inputMode: inputMode,
      personaId: personaId,
      candidateProfileId: candidateProfileId.isEmpty ? candidateProfileNone : candidateProfileId,
      jobUrl: sanitizedJobUrl,
      rawText: sanitizedRawText,
    );
  }

  List<String> resolvePersonaIds(List<String> availablePersonaIds) {
    if (personaId == allPersonasOptionId) {
      if (availablePersonaIds.isEmpty) {
        throw const FormatException('No personas are available for evaluation.');
      }
      return availablePersonaIds;
    }
    if (!availablePersonaIds.contains(personaId)) {
      throw FormatException('Unknown personaId: $personaId');
    }
    return [personaId];
  }
}

class EngineEvaluationRunner {
  EngineEvaluationRunner({
    required this.engineRoot,
    required this.reportsRoot,
    required this.gradlewCommand,
  });

  final Directory engineRoot;
  final Directory reportsRoot;
  final String gradlewCommand;

  Future<Map<String, Object>> evaluate(EvaluationRequest request, List<String> availablePersonaIds) async {
    final personaIds = request.resolvePersonaIds(availablePersonaIds);
    final results = <Map<String, dynamic>>[];
    for (final personaId in personaIds) {
      results.add(await _evaluateSingle(request, personaId));
    }
    return {
      'requested_persona_id': request.personaId,
      'requested_candidate_profile_id': request.candidateProfileId,
      'results': results,
    };
  }

  Future<Map<String, dynamic>> _evaluateSingle(EvaluationRequest request, String personaId) async {
    final artifactFile = await _buildArtifactFile(request, personaId);
    final engineArgs = <String>[
      _engineEvaluateInputCommand,
      _enginePersonaFlag,
      personaId,
      _engineOutputFormatFlag,
      _engineJsonOutputFormat,
      _engineOutputAnalysisFileFlag,
      artifactFile.path,
      _engineTimeoutFlag,
      _defaultTimeoutSeconds.toString(),
    ];

    if (request.candidateProfileId == candidateProfileNone) {
      engineArgs.addAll([_engineCandidateProfileFlag, candidateProfileNone]);
    } else if (request.candidateProfileId != candidateProfileAuto) {
      engineArgs.addAll([_engineCandidateProfileFlag, request.candidateProfileId]);
    }
    if (request.inputMode == inputModeUrl) {
      engineArgs.addAll([_engineJobUrlFlag, request.jobUrl]);
    } else {
      engineArgs.add(_engineStdinFlag);
    }

    final process = await Process.start(
      gradlewCommand,
      [
        _gradleQuietFlag,
        _gradleConsolePlainFlag,
        _engineRunCommand,
        '$_engineArgsPrefix${_buildGradleRunArgs(engineArgs)}',
      ],
      workingDirectory: engineRoot.path,
      runInShell: false,
    );

    if (request.inputMode == inputModeRawText) {
      process.stdin.write(request.rawText);
    }
    await process.stdin.close();

    final stdoutFuture = process.stdout.transform(utf8.decoder).join();
    final stderrFuture = process.stderr.transform(utf8.decoder).join();
    final exitCode = await process.exitCode;
    final stdoutText = await stdoutFuture;
    final stderrText = await stderrFuture;

    if (exitCode != 0) {
      throw StateError(_buildFailureMessage(exitCode, stdoutText, stderrText));
    }

    final decoded = _parseJsonPayload(stdoutText);
    if (decoded is! Map<String, dynamic>) {
      throw StateError('Engine evaluation returned an invalid JSON payload.');
    }

    decoded['analysis_file'] = _relativeToReportsRoot(artifactFile);
    return decoded;
  }

  Future<File> _buildArtifactFile(EvaluationRequest request, String personaId) async {
    final dir = Directory(_join(reportsRoot.path, adHocEvaluationsDirectory));
    await dir.create(recursive: true);
    final scope = _sanitizeFileSegment('$personaId--${request.candidateProfileId}');
    if (request.inputMode == inputModeUrl) {
      final sourceUri = Uri.parse(request.jobUrl);
      final host = _sanitizeFileSegment(sourceUri.host.isEmpty ? _adhocUrlSuffix : sourceUri.host);
      final pathSegment = _sanitizeFileSegment(
        sourceUri.pathSegments.isEmpty ? _adhocUrlSuffix : sourceUri.pathSegments.last,
      );
      final stableStem = _truncateFileSegment('$host-$pathSegment', _stableNameMaxLength);
      final stableHash = _stableHash(request.jobUrl);
      return File(_join(dir.path, 'adhoc-url-$scope-$stableStem-$stableHash$_fileExtension'));
    }

    final timestamp = DateTime.now()
        .toUtc()
        .toIso8601String()
        .replaceAll(':', '')
        .replaceAll('-', '')
        .replaceAll('.', '');
    return File(_join(dir.path, 'adhoc-$timestamp-$scope-$_adhocTextSuffix$_fileExtension'));
  }

  dynamic _parseJsonPayload(String stdoutText) {
    final trimmed = stdoutText.trim();
    if (trimmed.isEmpty) {
      return null;
    }
    try {
      return jsonDecode(trimmed);
    } catch (_) {
      final firstBrace = trimmed.indexOf('{');
      final lastBrace = trimmed.lastIndexOf('}');
      if (firstBrace < 0 || lastBrace <= firstBrace) {
        return null;
      }
      return jsonDecode(trimmed.substring(firstBrace, lastBrace + 1));
    }
  }

  String _relativeToReportsRoot(File artifactFile) {
    final reportsPath = reportsRoot.absolute.path;
    final artifactPath = artifactFile.absolute.path;
    final prefix = reportsPath.endsWith(Platform.pathSeparator) ? reportsPath : '$reportsPath${Platform.pathSeparator}';
    if (artifactPath.startsWith(prefix)) {
      return artifactPath.substring(prefix.length).replaceAll('\\', '/');
    }
    return artifactPath;
  }

  String _buildFailureMessage(int exitCode, String stdoutText, String stderrText) {
    final stderr = stderrText.trim();
    final stdout = stdoutText.trim();
    if (stderr.isNotEmpty) {
      return 'Engine evaluation failed ($exitCode): $stderr';
    }
    if (stdout.isNotEmpty) {
      return 'Engine evaluation failed ($exitCode): $stdout';
    }
    return 'Engine evaluation failed with exit code $exitCode.';
  }

  String _buildGradleRunArgs(List<String> args) {
    return args.map(_quoteIfNeeded).join(' ');
  }

  String _quoteIfNeeded(String input) {
    if (!input.contains(RegExp(r'[\s"\\]'))) {
      return input;
    }
    final escaped = input.replaceAll(r'\', r'\\').replaceAll('"', r'\"');
    return '"$escaped"';
  }

  String _join(String left, String right) {
    if (left.endsWith(Platform.pathSeparator)) {
      return '$left$right';
    }
    return '$left${Platform.pathSeparator}$right';
  }

  String _sanitizeFileSegment(String input) {
    final normalized = input
        .toLowerCase()
        .replaceAll(RegExp(r'[^a-z0-9]+'), '-')
        .replaceAll(RegExp(r'-{2,}'), '-')
        .replaceAll(RegExp(r'^-|-$'), '');
    return normalized.isEmpty ? 'item' : normalized;
  }

  String _truncateFileSegment(String input, int maxLength) {
    if (input.length <= maxLength) {
      return input;
    }
    return input.substring(0, maxLength);
  }

  String _stableHash(String value) {
    var hash = 0xcbf29ce484222325;
    for (final codeUnit in value.codeUnits) {
      hash ^= codeUnit;
      hash = (hash * 0x100000001b3) & 0xffffffffffffffff;
    }
    return hash.toRadixString(16).padLeft(16, '0');
  }
}
