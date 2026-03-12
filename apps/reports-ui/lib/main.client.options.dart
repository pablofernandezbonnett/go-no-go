// dart format off
// ignore_for_file: type=lint

// GENERATED FILE, DO NOT MODIFY
// Generated with jaspr_builder

import 'package:jaspr/client.dart';

import 'package:go_no_go_reports_ui/pages/batch.dart' deferred as _batch;
import 'package:go_no_go_reports_ui/pages/company_context.dart'
    deferred as _company_context;
import 'package:go_no_go_reports_ui/pages/evaluate.dart' deferred as _evaluate;
import 'package:go_no_go_reports_ui/pages/job_detail.dart'
    deferred as _job_detail;
import 'package:go_no_go_reports_ui/pages/runs.dart' deferred as _runs;
import 'package:go_no_go_reports_ui/pages/trends_alerts.dart'
    deferred as _trends_alerts;
import 'package:go_no_go_reports_ui/pages/weekly.dart' deferred as _weekly;

/// Default [ClientOptions] for use with your Jaspr project.
///
/// Use this to initialize Jaspr **before** calling [runApp].
///
/// Example:
/// ```dart
/// import 'main.client.options.dart';
///
/// void main() {
///   Jaspr.initializeApp(
///     options: defaultClientOptions,
///   );
///
///   runApp(...);
/// }
/// ```
ClientOptions get defaultClientOptions => ClientOptions(
  clients: {
    'batch': ClientLoader(
      (p) => _batch.BatchPage(),
      loader: _batch.loadLibrary,
    ),
    'company_context': ClientLoader(
      (p) => _company_context.CompanyContextPage(),
      loader: _company_context.loadLibrary,
    ),
    'evaluate': ClientLoader(
      (p) => _evaluate.EvaluatePage(),
      loader: _evaluate.loadLibrary,
    ),
    'job_detail': ClientLoader(
      (p) => _job_detail.JobDetailPage(),
      loader: _job_detail.loadLibrary,
    ),
    'runs': ClientLoader((p) => _runs.RunsPage(), loader: _runs.loadLibrary),
    'trends_alerts': ClientLoader(
      (p) => _trends_alerts.TrendsAlertsPage(),
      loader: _trends_alerts.loadLibrary,
    ),
    'weekly': ClientLoader(
      (p) => _weekly.WeeklyPage(),
      loader: _weekly.loadLibrary,
    ),
  },
);
