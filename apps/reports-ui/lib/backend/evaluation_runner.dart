import 'dart:convert';
import 'dart:io';

import '../constants/evaluation_contract.dart';
import 'evaluation_input_safety.dart';

const _defaultTimeoutSeconds = 20;
const _minimumTimeoutSeconds = 5;
const _maximumTimeoutSeconds = 120;
const _engineRunCommand = 'run';
const _engineEvaluateInputCommand = 'evaluate-input';
const _engineOutputFormatFlag = '--output-format';
const _engineOutputAnalysisFileFlag = '--output-analysis-file';
const _engineTimeoutFlag = '--timeout-seconds';
const _enginePersonaFlag = '--persona';
const _engineCandidateProfileFlag = '--candidate-profile';
const _engineCompanyNameFlag = '--company-name';
const _engineTitleFlag = '--title';
const _engineJobUrlFlag = '--job-url';
const _engineStdinFlag = '--stdin';
const _engineJsonOutputFormat = 'json';
const _gradleQuietFlag = '--quiet';
const _gradleConsolePlainFlag = '--console=plain';
const _engineArgsPrefix = '--args=';
const _adhocUrlSuffix = 'url';
const _adhocTextSuffix = 'text';

const _inputSafety = EvaluationInputSafety();

class EvaluationRequest {
  const EvaluationRequest({
    required this.inputMode,
    required this.personaId,
    required this.candidateProfileId,
    required this.jobUrl,
    required this.rawText,
    required this.companyName,
    required this.title,
    required this.timeoutSeconds,
  });

  final String inputMode;
  final String personaId;
  final String candidateProfileId;
  final String jobUrl;
  final String rawText;
  final String companyName;
  final String title;
  final int timeoutSeconds;

  static EvaluationRequest fromJson(Map<String, dynamic> json) {
    final inputMode = (json['inputMode']?.toString() ?? inputModeRawText).trim().toLowerCase();
    final personaId = (json['personaId']?.toString() ?? '').trim();
    final candidateProfileId = (json['candidateProfileId']?.toString() ?? candidateProfileAuto).trim();
    final jobUrl = (json['jobUrl']?.toString() ?? '').trim();
    final rawText = json['rawText']?.toString() ?? '';
    final companyName = (json['companyName']?.toString() ?? '').trim();
    final title = (json['title']?.toString() ?? '').trim();
    final timeoutSeconds = _parseInt(json['timeoutSeconds'], fallback: _defaultTimeoutSeconds);

    if (personaId.isEmpty) {
      throw const FormatException('personaId is required.');
    }
    if (inputMode != inputModeUrl && inputMode != inputModeRawText) {
      throw const FormatException('inputMode must be url or raw_text.');
    }
    if (timeoutSeconds < _minimumTimeoutSeconds || timeoutSeconds > _maximumTimeoutSeconds) {
      throw const FormatException('timeoutSeconds must be between 5 and 120.');
    }

    final sanitizedJobUrl = inputMode == inputModeUrl ? _inputSafety.validateUrl(jobUrl) : '';
    final sanitizedRawText = inputMode == inputModeRawText ? _inputSafety.sanitizeRawText(rawText) : '';

    return EvaluationRequest(
      inputMode: inputMode,
      personaId: personaId,
      candidateProfileId: candidateProfileId.isEmpty ? candidateProfileAuto : candidateProfileId,
      jobUrl: sanitizedJobUrl,
      rawText: sanitizedRawText,
      companyName: _inputSafety.sanitizeOverride(companyName),
      title: _inputSafety.sanitizeOverride(title),
      timeoutSeconds: timeoutSeconds,
    );
  }

  static int _parseInt(Object? raw, {required int fallback}) {
    if (raw is num) {
      return raw.toInt();
    }
    return int.tryParse(raw?.toString().trim() ?? '') ?? fallback;
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

  Future<Map<String, dynamic>> evaluate(EvaluationRequest request) async {
    final artifactFile = await _buildArtifactFile(request);
    final engineArgs = <String>[
      _engineEvaluateInputCommand,
      _enginePersonaFlag,
      request.personaId,
      _engineOutputFormatFlag,
      _engineJsonOutputFormat,
      _engineOutputAnalysisFileFlag,
      artifactFile.path,
      _engineTimeoutFlag,
      request.timeoutSeconds.toString(),
    ];

    if (request.candidateProfileId == candidateProfileNone) {
      engineArgs.addAll([_engineCandidateProfileFlag, candidateProfileNone]);
    } else if (request.candidateProfileId != candidateProfileAuto) {
      engineArgs.addAll([_engineCandidateProfileFlag, request.candidateProfileId]);
    }
    if (request.companyName.isNotEmpty) {
      engineArgs.addAll([_engineCompanyNameFlag, request.companyName]);
    }
    if (request.title.isNotEmpty) {
      engineArgs.addAll([_engineTitleFlag, request.title]);
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

  Future<File> _buildArtifactFile(EvaluationRequest request) async {
    final dir = Directory(_join(reportsRoot.path, adHocEvaluationsDirectory));
    await dir.create(recursive: true);
    final timestamp = DateTime.now()
        .toUtc()
        .toIso8601String()
        .replaceAll(':', '')
        .replaceAll('-', '')
        .replaceAll('.', '');
    final suffix = request.inputMode == inputModeUrl ? _adhocUrlSuffix : _adhocTextSuffix;
    return File(_join(dir.path, 'adhoc-$timestamp-$suffix.yaml'));
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
}
