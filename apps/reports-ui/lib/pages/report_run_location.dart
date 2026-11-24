import 'package:jaspr/jaspr.dart';

import 'report_run_location_stub.dart' if (dart.library.js_interop) 'report_run_location_web.dart';

/// Keeps the run in sync with the browser history after a report page hydrates.
mixin ReportRunLocation<T extends StatefulComponent> on State<T> {
  String? get initialRouteRunId;
  void onRouteRunChanged();

  String? _routeRunId;
  void Function()? _stopWatching;

  String? get routeRunId => _routeRunId;

  @override
  void initState() {
    super.initState();
    _routeRunId = initialRouteRunId;
    _stopWatching = watchReportLocation(() {
      final runId = Uri.base.queryParameters['run'];
      if (_routeRunId == runId) return;
      _routeRunId = runId;
      onRouteRunChanged();
    });
  }

  @override
  void dispose() {
    _stopWatching?.call();
    super.dispose();
  }
}
