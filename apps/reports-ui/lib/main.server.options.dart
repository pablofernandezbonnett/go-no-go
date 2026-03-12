// dart format off
// ignore_for_file: type=lint

// GENERATED FILE, DO NOT MODIFY
// Generated with jaspr_builder

import 'package:jaspr/server.dart';
import 'package:go_no_go_reports_ui/components/header.dart' as _header;
import 'package:go_no_go_reports_ui/pages/batch.dart' as _batch;
import 'package:go_no_go_reports_ui/pages/company_context.dart'
    as _company_context;
import 'package:go_no_go_reports_ui/pages/evaluate.dart' as _evaluate;
import 'package:go_no_go_reports_ui/pages/job_detail.dart' as _job_detail;
import 'package:go_no_go_reports_ui/pages/runs.dart' as _runs;
import 'package:go_no_go_reports_ui/pages/trends_alerts.dart' as _trends_alerts;
import 'package:go_no_go_reports_ui/pages/weekly.dart' as _weekly;
import 'package:go_no_go_reports_ui/app.dart' as _app;

/// Default [ServerOptions] for use with your Jaspr project.
///
/// Use this to initialize Jaspr **before** calling [runApp].
///
/// Example:
/// ```dart
/// import 'main.server.options.dart';
///
/// void main() {
///   Jaspr.initializeApp(
///     options: defaultServerOptions,
///   );
///
///   runApp(...);
/// }
/// ```
ServerOptions get defaultServerOptions => ServerOptions(
  clientId: 'main.client.dart.js',
  clients: {
    _batch.BatchPage: ClientTarget<_batch.BatchPage>('batch'),
    _company_context.CompanyContextPage:
        ClientTarget<_company_context.CompanyContextPage>('company_context'),
    _evaluate.EvaluatePage: ClientTarget<_evaluate.EvaluatePage>('evaluate'),
    _job_detail.JobDetailPage: ClientTarget<_job_detail.JobDetailPage>(
      'job_detail',
    ),
    _runs.RunsPage: ClientTarget<_runs.RunsPage>('runs'),
    _trends_alerts.TrendsAlertsPage:
        ClientTarget<_trends_alerts.TrendsAlertsPage>('trends_alerts'),
    _weekly.WeeklyPage: ClientTarget<_weekly.WeeklyPage>('weekly'),
  },
  styles: () => [..._header.Header.styles, ..._app.App.styles],
);
