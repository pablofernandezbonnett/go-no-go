import 'dart:io';

class ReportsRootConfig {
  const ReportsRootConfig({
    required this.reportsRoot,
  });

  static const String envVarName = 'REPORTS_ROOT';
  static const String defaultReportsRoot = 'output';

  final String reportsRoot;

  factory ReportsRootConfig.fromEnvironment({
    Map<String, String>? environment,
  }) {
    final env = environment ?? Platform.environment;
    final configured = env[envVarName];
    final value = (configured == null || configured.trim().isEmpty) ? defaultReportsRoot : configured.trim();
    return ReportsRootConfig(reportsRoot: value);
  }

  String resolveAbsolutePath({
    String? currentWorkingDirectory,
  }) {
    final cwd = currentWorkingDirectory ?? Directory.current.path;
    if (_isAbsolutePath(reportsRoot)) {
      return Directory(reportsRoot).absolute.path;
    }

    final base = Directory(cwd).absolute.path;
    return Directory('$base${Platform.pathSeparator}$reportsRoot').absolute.path;
  }

  Directory resolveDirectory({
    String? currentWorkingDirectory,
  }) {
    return Directory(
      resolveAbsolutePath(
        currentWorkingDirectory: currentWorkingDirectory,
      ),
    );
  }
}

bool _isAbsolutePath(String path) {
  if (path.startsWith('/')) {
    return true;
  }

  return RegExp(r'^[A-Za-z]:[\\/]').hasMatch(path);
}
