import 'package:jaspr/dom.dart';
import 'package:jaspr/jaspr.dart';
import 'package:jaspr_router/jaspr_router.dart';

import 'components/header.dart';
import 'constants/evaluation_contract.dart';
import 'pages/batch.dart';
import 'pages/company_context.dart';
import 'pages/evaluate.dart';
import 'pages/job_detail.dart';
import 'pages/runs.dart';
import 'pages/trends_alerts.dart';
import 'pages/weekly.dart';

// The main component of your application.
//
// By using multi-page routing, this component is built during server pre-rendering.
// Nested route components are mounted on the client.
class App extends StatelessComponent {
  const App({super.key});

  @override
  Component build(BuildContext context) {
    return div(classes: 'main', [
      div(classes: 'layout', [
        const Header(),
        div(classes: 'content-shell', [
          Router(
            routes: [
              Route(path: '/', title: 'Runs', builder: (context, state) => const RunsPage()),
              Route(path: evaluatePageRoute, title: 'Evaluate', builder: (context, state) => const EvaluatePage()),
              Route(path: '/batch', title: 'Batch', builder: (context, state) => const BatchPage()),
              Route(path: '/job', title: 'Job Detail', builder: (context, state) => const JobDetailPage()),
              Route(path: '/weekly', title: 'Weekly', builder: (context, state) => const WeeklyPage()),
              Route(
                path: '/context',
                title: 'Company Context',
                builder: (context, state) => const CompanyContextPage(),
              ),
              Route(path: '/trends', title: 'Trends & Alerts', builder: (context, state) => const TrendsAlertsPage()),
            ],
          ),
        ]),
      ]),
    ]);
  }

  // Defines the css styles for elements of this component.
  //
  // By using the @css annotation, these will be rendered automatically to css inside the <head> of your page.
  // Must be a variable or getter of type [List<StyleRule>].
  @css
  static List<StyleRule> get styles => [
    css('body').styles(
      backgroundColor: const Color('#eef2f7'),
      color: const Color('#101828'),
      lineHeight: 1.45.em,
    ),
    css('.main', [
      css('&').styles(
        display: .flex,
        minHeight: 100.vh,
        flexDirection: .column,
      ),
    ]),
    css('.layout').styles(
      display: .flex,
      minHeight: 100.vh,
      alignItems: .start,
    ),
    css('.content-shell').styles(
      display: .flex,
      flexDirection: .column,
      flex: Flex(grow: 1),
      padding: .all(1.25.rem),
      boxSizing: .borderBox,
      raw: const {
        'min-width': '0',
      },
    ),
    css('section.page').styles(
      width: 100.percent,
      padding: .all(1.4.rem),
      boxSizing: .borderBox,
    ),
    css('section.page h1').styles(
      margin: Spacing.only(bottom: 0.65.rem),
      fontSize: 2.35.rem,
      color: const Color('#0f1728'),
    ),
    css('.page-header').styles(
      display: .flex,
      flexDirection: .column,
      gap: Gap.all(0.3.rem),
      margin: Spacing.only(bottom: 0.95.rem),
    ),
    css('.page-header h1').styles(
      margin: .zero,
    ),
    css('.page-description').styles(
      margin: .zero,
      color: const Color('#475569'),
      fontSize: 0.98.rem,
      maxWidth: 54.rem,
      raw: const {
        'display': '-webkit-box',
        '-webkit-box-orient': 'vertical',
        '-webkit-line-clamp': '2',
        'overflow': 'hidden',
      },
    ),
    css('section.page h2').styles(
      margin: Spacing.only(top: 1.rem, bottom: 0.5.rem),
      fontSize: 1.5.rem,
    ),
    css('section.page h3').styles(
      margin: Spacing.only(top: 0.9.rem, bottom: 0.35.rem),
      fontSize: 1.15.rem,
    ),
    css('section.page p').styles(
      lineHeight: 1.45.em,
    ),
    css('section.page code').styles(
      backgroundColor: const Color('#eff3fa'),
      padding: .symmetric(horizontal: 0.22.rem, vertical: 0.1.rem),
      radius: .all(.circular(4.px)),
      raw: const {
        'overflow-wrap': 'anywhere',
      },
    ),
    css('section.page table').styles(
      width: 100.percent,
      margin: Spacing.only(top: 0.2.rem),
      raw: const {
        'border-collapse': 'collapse',
        'background': '#ffffff',
      },
    ),
    css('.table-scroll').styles(
      width: 100.percent,
      overflow: Overflow.auto,
    ),
    css('section.page th, section.page td').styles(
      border: Border.all(width: 1.px, color: const Color('#d5dbe5')),
      padding: .all(0.58.rem),
      raw: const {
        'vertical-align': 'top',
      },
    ),
    css('section.page th').styles(
      backgroundColor: const Color('#edf3fb'),
      fontWeight: .w700,
    ),
    css('.table-header-cell').styles(
      display: .flex,
      justifyContent: JustifyContent.spaceBetween,
      alignItems: .center,
      gap: Gap.all(0.55.rem),
      fontWeight: .w700,
      raw: const {
        'min-width': '5.5rem',
        'overflow-x': 'auto',
        'overflow-y': 'hidden',
        'resize': 'horizontal',
      },
    ),
    css('.table-col-compact .table-header-cell').styles(
      raw: const {'width': '6rem'},
    ),
    css('.table-col-medium .table-header-cell').styles(
      raw: const {'width': '9rem'},
    ),
    css('.table-col-wide .table-header-cell').styles(
      raw: const {'width': '13rem'},
    ),
    css('.table-col-xwide .table-header-cell').styles(
      raw: const {'width': '18rem'},
    ),
    css('.table-header-label').styles(
      raw: const {
        'white-space': 'nowrap',
      },
    ),
    css('.table-header-grip').styles(
      color: const Color('#64748b'),
      fontFamily: const .list([FontFamily('IBM Plex Mono'), FontFamilies.monospace]),
      fontSize: 0.78.rem,
      fontWeight: .w700,
      raw: const {
        'user-select': 'none',
      },
    ),
    css('section.page pre').styles(
      whiteSpace: WhiteSpace.preWrap,
      border: Border.all(width: 1.px, color: const Color('#d5dbe5')),
      padding: .all(0.9.rem),
      radius: .all(.circular(8.px)),
      backgroundColor: const Color('#fbfcfe'),
      overflow: Overflow.auto,
    ),
    css('.actions').styles(
      display: .flex,
      flexWrap: .wrap,
      gap: Gap.all(0.7.rem),
    ),
    css('.actions a').styles(
      color: const Color('#01589B'),
      textDecoration: TextDecoration(line: .none),
      fontWeight: .w600,
    ),
    css('.card').styles(
      backgroundColor: Colors.white,
      border: Border.all(width: 1.px, color: const Color('#d9dfeb')),
      radius: .all(.circular(12.px)),
      padding: .all(1.05.rem),
      margin: Spacing.only(bottom: 0.9.rem),
      shadow: BoxShadow(
        color: Color('#0f172814'),
        offsetX: 0.px,
        offsetY: 4.px,
        blur: 10.px,
      ),
    ),
    css('.card h2, .card h3').styles(
      margin: Spacing.only(top: 0.1.rem, bottom: 0.45.rem),
    ),
    css('.artifact-frame').styles(
      border: Border.all(width: 1.px, color: const Color('#d7dfeb')),
      radius: .all(.circular(12.px)),
      backgroundColor: const Color('#f8fafc'),
      overflow: Overflow.hidden,
    ),
    css('.artifact-frame-header').styles(
      display: .flex,
      justifyContent: JustifyContent.spaceBetween,
      alignItems: .start,
      gap: Gap.all(0.8.rem),
      padding: .all(0.9.rem),
      border: Border.only(
        bottom: BorderSide(width: 1.px, color: const Color('#e2e8f0')),
      ),
      backgroundColor: Colors.white,
    ),
    css('.artifact-frame-copy').styles(
      display: .flex,
      flexDirection: .column,
      gap: Gap.all(0.12.rem),
      flex: Flex(grow: 1),
    ),
    css('.artifact-frame-title').styles(
      margin: .zero,
      color: const Color('#0f172a'),
      fontSize: 1.02.rem,
      fontWeight: .w700,
    ),
    css('.artifact-frame-meta').styles(
      margin: .zero,
      color: const Color('#475569'),
      fontSize: 0.9.rem,
    ),
    css('.artifact-frame-badge').styles(
      border: Border.all(width: 1.px, color: const Color('#cbd5e1')),
      radius: .all(.circular(999.px)),
      backgroundColor: const Color('#f8fafc'),
      color: const Color('#334155'),
      padding: .symmetric(horizontal: 0.7.rem, vertical: 0.28.rem),
      fontSize: 0.8.rem,
      fontWeight: .w700,
      raw: const {
        'white-space': 'nowrap',
      },
    ),
    css('.artifact-code').styles(
      margin: .zero,
      padding: .all(1.rem),
      color: const Color('#1e293b'),
      backgroundColor: const Color('#f8fafc'),
      fontFamily: const .list([FontFamily('IBM Plex Mono'), FontFamilies.monospace]),
      fontSize: 0.88.rem,
      raw: const {
        'line-height': '1.58',
        'white-space': 'pre-wrap',
        'word-break': 'break-word',
        'max-height': '60vh',
      },
      overflow: Overflow.auto,
    ),
    css('.run-tabs, .digest-tabs, .context-tabs').styles(
      display: .flex,
      flexWrap: .wrap,
      gap: Gap.all(0.45.rem),
      margin: Spacing.only(bottom: 0.8.rem),
    ),
    css('.run-tab, .digest-tab, .context-tab').styles(
      display: .inlineBlock,
      padding: .symmetric(horizontal: 0.75.rem, vertical: 0.36.rem),
      border: Border.all(width: 1.px, color: const Color('#c1cad8')),
      radius: .all(.circular(999.px)),
      backgroundColor: Colors.white,
      color: const Color('#223041'),
      textDecoration: TextDecoration(line: .none),
      fontSize: 0.9.rem,
      fontWeight: .w600,
    ),
    css('button.run-tab, button.digest-tab').styles(
      border: Border.all(width: 1.px, color: const Color('#c1cad8')),
      radius: .all(.circular(999.px)),
      backgroundColor: Colors.white,
      color: const Color('#223041'),
      padding: .symmetric(horizontal: 0.75.rem, vertical: 0.36.rem),
      cursor: Cursor.pointer,
      fontSize: 0.9.rem,
      fontWeight: .w600,
    ),
    css('button.run-tab:hover, button.digest-tab:hover').styles(
      border: Border.all(width: 1.px, color: const Color('#8ca7c6')),
      backgroundColor: const Color('#f8fbff'),
      color: const Color('#143a63'),
    ),
    css('.run-tab.active, .digest-tab.active, .context-tab.active').styles(
      backgroundColor: const Color('#01589B'),
      border: Border.all(width: 1.px, color: const Color('#01589B')),
      color: Colors.white,
    ),
    css('button.run-tab.active, button.digest-tab.active').styles(
      backgroundColor: const Color('#01589B'),
      border: Border.all(width: 1.px, color: const Color('#01589B')),
      color: Colors.white,
    ),
    css('.controls').styles(
      display: .flex,
      flexWrap: .wrap,
      gap: Gap.all(0.8.rem),
      margin: Spacing.only(top: 0.25.rem, bottom: 0.5.rem),
    ),
    css('.controls label').styles(
      display: .flex,
      flexDirection: .column,
      gap: Gap.all(0.25.rem),
      color: const Color('#334155'),
      fontSize: 0.9.rem,
      fontWeight: .w600,
    ),
    css('.controls select').styles(
      padding: .all(0.35.rem),
      border: Border.all(width: 1.px, color: const Color('#c6ceda')),
      radius: .all(.circular(6.px)),
      backgroundColor: Colors.white,
    ),
    css('.controls input, .controls textarea').styles(
      padding: .all(0.5.rem),
      border: Border.all(width: 1.px, color: const Color('#c6ceda')),
      radius: .all(.circular(8.px)),
      backgroundColor: Colors.white,
      fontFamily: const .list([FontFamily('Space Grotesk'), FontFamilies.sansSerif]),
      fontSize: 0.95.rem,
    ),
    css('.controls textarea').styles(
      minHeight: 16.rem,
      raw: const {
        'resize': 'vertical',
      },
    ),
    css('.controls button').styles(
      padding: .symmetric(horizontal: 0.95.rem, vertical: 0.62.rem),
      border: Border.all(width: 1.px, color: const Color('#01589B')),
      radius: .all(.circular(8.px)),
      backgroundColor: const Color('#01589B'),
      color: Colors.white,
      fontWeight: .w700,
      cursor: Cursor.pointer,
    ),
    css('.controls button:disabled').styles(
      backgroundColor: const Color('#94a3b8'),
      border: Border.all(width: 1.px, color: const Color('#94a3b8')),
      cursor: Cursor.notAllowed,
    ),
    css('.form-grid').styles(
      display: Display.grid,
      gap: Gap.all(0.9.rem),
      raw: const {
        'grid-template-columns': 'repeat(auto-fit, minmax(220px, 1fr))',
      },
    ),
    css('.form-grid .span-full').styles(
      raw: const {
        'grid-column': '1 / -1',
      },
    ),
    css('.summary-grid').styles(
      display: Display.grid,
      gap: Gap.all(0.65.rem),
      margin: Spacing.only(top: 0.5.rem, bottom: 1.rem),
      raw: const {
        'grid-template-columns': 'repeat(auto-fit, minmax(180px, 1fr))',
      },
    ),
    css('.metric').styles(
      border: Border.all(width: 1.px, color: const Color('#d8dfeb')),
      radius: .all(.circular(8.px)),
      padding: .all(0.65.rem),
      backgroundColor: Colors.white,
    ),
    css('.metric-label').styles(
      color: const Color('#475569'),
      fontSize: 0.82.rem,
    ),
    css('.metric-value').styles(
      color: const Color('#0f172a'),
      fontWeight: .w700,
    ),
    css('.error').styles(
      color: const Color('#b42318'),
      fontWeight: .w600,
      margin: Spacing.only(bottom: 0.75.rem),
    ),
    css('.button-secondary').styles(
      border: Border.all(width: 1.px, color: const Color('#bfd3ea')),
      backgroundColor: Colors.white,
      color: const Color('#01589B'),
    ),
    css('.button-secondary:hover').styles(
      backgroundColor: const Color('#f8fbff'),
      color: const Color('#01477d'),
    ),
    css('.collapsible-header').styles(
      display: .flex,
      justifyContent: JustifyContent.spaceBetween,
      alignItems: .start,
      gap: Gap.all(0.8.rem),
      margin: Spacing.only(bottom: 0.4.rem),
    ),
    css('.collapsible-header-copy').styles(
      display: .flex,
      flexDirection: .column,
      gap: Gap.all(0.15.rem),
      flex: Flex(grow: 1),
    ),
    css('.collapsible-summary').styles(
      margin: .zero,
      color: const Color('#475569'),
      fontSize: 0.92.rem,
    ),
    css('.collapsible-body').styles(
      margin: Spacing.only(top: 0.5.rem),
    ),
    css('.history-source-cell').styles(
      display: .flex,
      flexDirection: .column,
      gap: Gap.all(0.2.rem),
    ),
    css('.history-source-cell p').styles(
      margin: .zero,
    ),
    css('.history-saved-note').styles(
      color: const Color('#475569'),
      fontSize: 0.83.rem,
    ),
    css('.history-actions').styles(
      display: .flex,
      flexWrap: .wrap,
      gap: Gap.all(0.45.rem),
    ),
    css('.history-link-cell').styles(
      display: .flex,
      flexDirection: .column,
      gap: Gap.all(0.18.rem),
    ),
    css('.history-link-meta').styles(
      margin: .zero,
      color: const Color('#64748b'),
      fontSize: 0.82.rem,
    ),
    css('.history-pagination').styles(
      display: .flex,
      flexDirection: .column,
      gap: Gap.all(0.45.rem),
      margin: Spacing.only(top: 0.2.rem, bottom: 0.7.rem),
    ),
    css('.history-pagination-controls').styles(
      display: .flex,
      flexWrap: .wrap,
      justifyContent: JustifyContent.spaceBetween,
      alignItems: .end,
      gap: Gap.all(0.75.rem),
      margin: .zero,
    ),
    css('.history-pagination-buttons').styles(
      display: .flex,
      flexWrap: .wrap,
      alignItems: .center,
      gap: Gap.all(0.5.rem),
    ),
    css('.history-pagination-status').styles(
      display: .inlineFlex,
      alignItems: .center,
      border: Border.all(width: 1.px, color: const Color('#cbd5e1')),
      radius: .all(.circular(999.px)),
      backgroundColor: const Color('#f8fafc'),
      color: const Color('#334155'),
      padding: .symmetric(horizontal: 0.75.rem, vertical: 0.4.rem),
      fontSize: 0.88.rem,
      fontWeight: .w700,
    ),
    css('.history-pagination button:disabled').styles(
      backgroundColor: const Color('#e2e8f0'),
      border: Border.all(width: 1.px, color: const Color('#cbd5e1')),
      color: const Color('#94a3b8'),
      cursor: Cursor.notAllowed,
    ),
    css('.icon-button').styles(
      display: .inlineFlex,
      alignItems: .center,
      justifyContent: JustifyContent.center,
      minWidth: 2.25.rem,
      minHeight: 2.25.rem,
      padding: .all(0.45.rem),
    ),
    css('.icon-button svg').styles(
      raw: const {
        'pointer-events': 'none',
      },
    ),
    css('.modal-backdrop').styles(
      display: .flex,
      justifyContent: JustifyContent.center,
      alignItems: .center,
      padding: .all(1.2.rem),
      backgroundColor: const Color('#0f172a99'),
      raw: const {
        'position': 'fixed',
        'inset': '0',
        'z-index': '1000',
      },
    ),
    css('.modal-surface').styles(
      width: 100.percent,
      maxWidth: 72.rem,
      maxHeight: 88.vh,
      backgroundColor: Colors.white,
      border: Border.all(width: 1.px, color: const Color('#d9dfeb')),
      radius: .all(.circular(16.px)),
      shadow: BoxShadow(
        color: Color('#0f172840'),
        offsetX: 0.px,
        offsetY: 12.px,
        blur: 28.px,
      ),
      overflow: Overflow.hidden,
    ),
    css('.modal-header').styles(
      display: .flex,
      justifyContent: JustifyContent.spaceBetween,
      alignItems: .start,
      gap: Gap.all(1.rem),
      padding: .all(1.rem),
      border: Border.only(
        bottom: BorderSide(width: 1.px, color: const Color('#e2e8f0')),
      ),
      backgroundColor: const Color('#f8fafc'),
    ),
    css('.modal-header-copy').styles(
      display: .flex,
      flexDirection: .column,
      gap: Gap.all(0.18.rem),
      flex: Flex(grow: 1),
    ),
    css('.modal-title').styles(
      margin: .zero,
      color: const Color('#0f172a'),
    ),
    css('.modal-meta').styles(
      margin: .zero,
      color: const Color('#475569'),
      fontSize: 0.92.rem,
    ),
    css('.modal-actions').styles(
      display: .flex,
      flexWrap: .wrap,
      gap: Gap.all(0.55.rem),
      justifyContent: JustifyContent.end,
    ),
    css('.modal-body').styles(
      padding: .all(1.rem),
      overflow: Overflow.auto,
      raw: const {'max-height': 'calc(88vh - 5.4rem)'},
    ),
    css('section.page button').styles(
      border: Border.none,
      radius: .all(.circular(7.px)),
      backgroundColor: const Color('#01589B'),
      color: Colors.white,
      padding: .symmetric(horizontal: 0.8.rem, vertical: 0.45.rem),
      cursor: Cursor.pointer,
      fontWeight: .w600,
    ),
    css('section.page button:hover').styles(
      backgroundColor: const Color('#01477d'),
    ),
    css('.context-shell').styles(
      display: .flex,
      flexDirection: .column,
      gap: Gap.all(1.rem),
    ),
    css('.human-reading-hero').styles(
      border: Border.all(width: 1.px, color: const Color('#c7d7eb')),
      radius: .all(.circular(12.px)),
      padding: .all(0.95.rem),
      backgroundColor: const Color('#f8fbff'),
    ),
    css('.human-reading-kicker').styles(
      color: const Color('#01589B'),
      fontSize: 0.78.rem,
      fontWeight: .w700,
      raw: const {
        'letter-spacing': '0.08em',
        'text-transform': 'uppercase',
      },
    ),
    css('.human-reading-summary').styles(
      margin: Spacing.only(top: 0.45.rem, bottom: 0.rem),
      color: const Color('#0f172a'),
      fontSize: 1.03.rem,
      fontWeight: .w600,
      lineHeight: 1.55.em,
    ),
    css('.human-reading-fit-grid').styles(
      display: Display.grid,
      gap: Gap.all(0.65.rem),
      raw: const {
        'grid-template-columns': 'repeat(auto-fit, minmax(140px, 1fr))',
      },
    ),
    css('.human-reading-fit').styles(
      border: Border.all(width: 1.px, color: const Color('#d8dfeb')),
      radius: .all(.circular(10.px)),
      padding: .all(0.7.rem),
      backgroundColor: Colors.white,
    ),
    css('.human-reading-fit.fit-strong').styles(
      backgroundColor: const Color('#f0fdf4'),
      border: Border.all(width: 1.px, color: const Color('#bbf7d0')),
    ),
    css('.human-reading-fit.fit-mixed').styles(
      backgroundColor: const Color('#fffbeb'),
      border: Border.all(width: 1.px, color: const Color('#fde68a')),
    ),
    css('.human-reading-fit.fit-weak').styles(
      backgroundColor: const Color('#fff7ed'),
      border: Border.all(width: 1.px, color: const Color('#fed7aa')),
    ),
    css('.human-reading-fit-label').styles(
      color: const Color('#475569'),
      fontSize: 0.8.rem,
      margin: Spacing.only(bottom: 0.18.rem),
    ),
    css('.human-reading-fit-value').styles(
      color: const Color('#0f172a'),
      fontWeight: .w700,
    ),
    css('.human-reading-reasons-grid').styles(
      display: Display.grid,
      gap: Gap.all(0.65.rem),
      raw: const {
        'grid-template-columns': 'repeat(auto-fit, minmax(260px, 1fr))',
      },
    ),
    css('.human-reading-reason-card').styles(
      border: Border.all(width: 1.px, color: const Color('#d8dfeb')),
      radius: .all(.circular(10.px)),
      padding: .all(0.8.rem),
      backgroundColor: Colors.white,
    ),
    css('.human-reading-reason-card.positive').styles(
      backgroundColor: const Color('#f0fdf4'),
      border: Border.all(width: 1.px, color: const Color('#bbf7d0')),
    ),
    css('.human-reading-reason-card.risk').styles(
      backgroundColor: const Color('#fff7ed'),
      border: Border.all(width: 1.px, color: const Color('#fed7aa')),
    ),
    css('.human-reading-reason-title').styles(
      color: const Color('#0f172a'),
      fontWeight: .w700,
      margin: Spacing.only(bottom: 0.35.rem),
    ),
    css('.human-reading-reason-list').styles(
      margin: Spacing.only(top: 0.1.rem, bottom: 0.rem, left: 1.rem),
      padding: .zero,
    ),
    css('.human-reading-reason-list li').styles(
      margin: Spacing.only(bottom: 0.24.rem),
    ),
    css('.human-reading-reason-empty').styles(
      margin: .zero,
      color: const Color('#475569'),
      fontSize: 0.9.rem,
    ),
    css('.signal-summary-grid').styles(
      display: Display.grid,
      gap: Gap.all(0.65.rem),
      raw: const {
        'grid-template-columns': 'repeat(auto-fit, minmax(240px, 1fr))',
      },
    ),
    css('.signal-summary-card').styles(
      border: Border.all(width: 1.px, color: const Color('#d8dfeb')),
      radius: .all(.circular(8.px)),
      padding: .all(0.75.rem),
      backgroundColor: Colors.white,
    ),
    css('.signal-summary-card.positive').styles(
      backgroundColor: const Color('#f0fdf4'),
      border: Border.all(width: 1.px, color: const Color('#bbf7d0')),
    ),
    css('.signal-summary-card.risk').styles(
      backgroundColor: const Color('#fff7ed'),
      border: Border.all(width: 1.px, color: const Color('#fed7aa')),
    ),
    css('.signal-summary-title').styles(
      color: const Color('#0f172a'),
      fontWeight: .w700,
      margin: Spacing.only(bottom: 0.35.rem),
    ),
    css('.signal-summary-list').styles(
      margin: Spacing.only(top: 0.25.rem, bottom: 0.2.rem, left: 1.0.rem),
      padding: .zero,
    ),
    css('.signal-summary-list li').styles(
      margin: Spacing.only(bottom: 0.18.rem),
    ),
    css('.signal-summary-empty, .signal-summary-overflow').styles(
      margin: .zero,
      color: const Color('#475569'),
      fontSize: 0.88.rem,
    ),
    css('.disclosure').styles(
      border: Border.all(width: 1.px, color: const Color('#d7dfeb')),
      radius: .all(.circular(12.px)),
      backgroundColor: const Color('#f8fafc'),
      overflow: Overflow.hidden,
    ),
    css('.disclosure-summary').styles(
      cursor: Cursor.pointer,
      padding: .all(0.9.rem),
      backgroundColor: Colors.white,
      raw: const {
        'list-style': 'none',
      },
    ),
    css('.disclosure-summary:hover').styles(
      backgroundColor: const Color('#f8fbff'),
    ),
    css('.disclosure-summary::-webkit-details-marker').styles(
      raw: const {
        'display': 'none',
      },
    ),
    css('.disclosure-summary-row').styles(
      display: .flex,
      justifyContent: JustifyContent.spaceBetween,
      alignItems: .center,
      gap: Gap.all(0.9.rem),
    ),
    css('.disclosure-copy').styles(
      display: .flex,
      flexDirection: .column,
      gap: Gap.all(0.16.rem),
      flex: Flex(grow: 1),
    ),
    css('.disclosure-title').styles(
      color: const Color('#0f172a'),
      fontWeight: .w700,
    ),
    css('.disclosure-description').styles(
      margin: .zero,
      color: const Color('#475569'),
      fontSize: 0.9.rem,
    ),
    css('.disclosure-cue').styles(
      display: .inlineFlex,
      alignItems: .center,
      border: Border.all(width: 1.px, color: const Color('#cbd5e1')),
      radius: .all(.circular(999.px)),
      backgroundColor: const Color('#f8fafc'),
      color: const Color('#334155'),
      width: 2.rem,
      height: 2.rem,
      justifyContent: JustifyContent.center,
      fontSize: 0.9.rem,
      fontWeight: .w700,
    ),
    css('.disclosure-cue-icon').styles(
      raw: const {
        'display': 'inline-block',
        'transition': 'transform 140ms ease',
      },
    ),
    css('details[open] .disclosure-cue-icon').styles(
      raw: const {
        'transform': 'rotate(180deg)',
      },
    ),
    css('.disclosure-body').styles(
      padding: .all(0.9.rem),
      border: Border.only(
        top: BorderSide(width: 1.px, color: const Color('#e2e8f0')),
      ),
      backgroundColor: const Color('#f8fafc'),
    ),
    css('.context-selector-group').styles(
      display: .flex,
      flexDirection: .column,
      gap: Gap.all(0.45.rem),
    ),
    css('.context-selector-copy').styles(
      display: .flex,
      flexDirection: .column,
      gap: Gap.all(0.15.rem),
    ),
    css('.context-selector-label').styles(
      color: const Color('#0f172a'),
      fontSize: 0.95.rem,
      fontWeight: .w700,
    ),
    css('.context-selector-helper').styles(
      margin: .zero,
      color: const Color('#475569'),
      fontSize: 0.9.rem,
    ),
    css('.context-run-tabs, .context-tabs').styles(
      display: .flex,
      flexWrap: .wrap,
      gap: Gap.all(0.45.rem),
    ),
    css('.context-run-tab, .context-tab').styles(
      border: Border.all(width: 1.px, color: const Color('#c1cad8')),
      radius: .all(.circular(999.px)),
      backgroundColor: Colors.white,
      color: const Color('#223041'),
      padding: .symmetric(horizontal: 0.8.rem, vertical: 0.38.rem),
      fontSize: 0.9.rem,
      fontWeight: .w600,
      cursor: Cursor.pointer,
    ),
    css('.context-run-tab:hover, .context-tab:hover').styles(
      border: Border.all(width: 1.px, color: const Color('#8ca7c6')),
      backgroundColor: const Color('#f8fbff'),
      color: const Color('#143a63'),
    ),
    css('.context-run-tab.active, .context-tab.active').styles(
      backgroundColor: const Color('#01589B'),
      border: Border.all(width: 1.px, color: const Color('#01589B')),
      color: Colors.white,
    ),
    css('.context-viewer').styles(
      border: Border.all(width: 1.px, color: const Color('#d7dfeb')),
      radius: .all(.circular(12.px)),
      backgroundColor: const Color('#f8fafc'),
      overflow: Overflow.hidden,
    ),
    css('.context-viewer-header').styles(
      display: .flex,
      justifyContent: JustifyContent.spaceBetween,
      alignItems: .start,
      gap: Gap.all(0.9.rem),
      padding: .all(0.95.rem),
      border: Border.only(
        bottom: BorderSide(width: 1.px, color: const Color('#e2e8f0')),
      ),
      backgroundColor: Colors.white,
    ),
    css('.context-viewer-copy').styles(
      display: .flex,
      flexDirection: .column,
      gap: Gap.all(0.18.rem),
      flex: Flex(grow: 1),
    ),
    css('.context-viewer-kicker').styles(
      color: const Color('#01589B'),
      fontSize: 0.78.rem,
      fontWeight: .w700,
      raw: const {
        'letter-spacing': '0.08em',
        'text-transform': 'uppercase',
      },
    ),
    css('.context-viewer-title').styles(
      margin: .zero,
      color: const Color('#0f172a'),
      fontSize: 1.2.rem,
      fontWeight: .w700,
    ),
    css('.context-viewer-meta').styles(
      margin: .zero,
      color: const Color('#475569'),
      fontSize: 0.92.rem,
    ),
    css('.context-viewer-badge').styles(
      border: Border.all(width: 1.px, color: const Color('#cbd5e1')),
      radius: .all(.circular(999.px)),
      backgroundColor: const Color('#f8fafc'),
      color: const Color('#334155'),
      padding: .symmetric(horizontal: 0.7.rem, vertical: 0.28.rem),
      fontSize: 0.82.rem,
      fontWeight: .w700,
      raw: const {
        'white-space': 'nowrap',
      },
    ),
    css('.context-viewer-badge.refreshing').styles(
      border: Border.all(width: 1.px, color: const Color('#bfdbfe')),
      backgroundColor: const Color('#eff6ff'),
      color: const Color('#1d4ed8'),
    ),
    css('.context-text').styles(
      margin: .zero,
      padding: .all(1.rem),
      color: const Color('#1e293b'),
      backgroundColor: const Color('#f8fafc'),
      fontFamily: const .list([FontFamily('IBM Plex Mono'), FontFamilies.monospace]),
      fontSize: 0.88.rem,
      raw: const {
        'line-height': '1.58',
        'white-space': 'pre-wrap',
        'word-break': 'break-word',
        'max-height': '60vh',
      },
      overflow: Overflow.auto,
    ),
    css.media(const MediaQuery.raw('(max-width: 920px)'), [
      css('.layout').styles(
        flexDirection: .column,
      ),
      css('.content-shell').styles(
        padding: .all(0.65.rem),
      ),
      css('section.page').styles(
        padding: .all(0.75.rem),
      ),
      css('section.page h1').styles(
        fontSize: 2.rem,
      ),
      css('.card').styles(
        padding: .all(0.8.rem),
      ),
      css('.controls label').styles(
        width: 100.percent,
      ),
      css('.controls select').styles(
        width: 100.percent,
      ),
      css('.collapsible-header').styles(
        flexDirection: .column,
      ),
      css('.context-viewer-header').styles(
        flexDirection: .column,
      ),
      css('.artifact-frame-header').styles(
        flexDirection: .column,
      ),
      css('.modal-header').styles(
        flexDirection: .column,
      ),
      css('.modal-actions').styles(
        width: 100.percent,
        justifyContent: JustifyContent.start,
      ),
      css('.disclosure-summary-row').styles(
        flexDirection: .column,
        alignItems: .start,
      ),
      css('.history-pagination-controls').styles(
        flexDirection: .column,
        alignItems: .start,
      ),
      css('.history-pagination-buttons').styles(
        width: 100.percent,
        justifyContent: JustifyContent.start,
      ),
      css('section.page table').styles(
        display: .block,
        overflow: Overflow.auto,
        raw: const {
          'white-space': 'nowrap',
        },
      ),
    ]),
    css.media(const MediaQuery.raw('(max-width: 620px)'), [
      css('.content-shell').styles(
        padding: .all(0.4.rem),
      ),
      css('section.page').styles(
        padding: .all(0.45.rem),
      ),
      css('section.page h1').styles(
        fontSize: 1.75.rem,
      ),
      css('section.page h2').styles(
        fontSize: 1.25.rem,
      ),
      css('section.page h3').styles(
        fontSize: 1.02.rem,
      ),
      css('.card').styles(
        padding: .all(0.72.rem),
      ),
      css('.run-tabs, .digest-tabs, .context-tabs').styles(
        flexWrap: .nowrap,
        overflow: Overflow.auto,
        raw: const {
          '-webkit-overflow-scrolling': 'touch',
        },
      ),
      css('.run-tab, .digest-tab, .context-tab').styles(
        raw: const {
          'flex': '0 0 auto',
        },
      ),
      css('.summary-grid').styles(
        raw: const {
          'grid-template-columns': '1fr',
        },
      ),
      css('.context-run-tabs, .context-tabs').styles(
        flexWrap: .nowrap,
        overflow: Overflow.auto,
        raw: const {
          '-webkit-overflow-scrolling': 'touch',
        },
      ),
      css('.context-run-tab, .context-tab').styles(
        raw: const {
          'flex': '0 0 auto',
        },
      ),
      css('.context-text').styles(
        padding: .all(0.85.rem),
        raw: const {
          'max-height': '55vh',
        },
      ),
      css('.artifact-code').styles(
        padding: .all(0.85.rem),
        raw: const {
          'max-height': '55vh',
        },
      ),
      css('.modal-backdrop').styles(
        padding: .all(0.55.rem),
      ),
      css('.modal-body').styles(
        padding: .all(0.8.rem),
      ),
    ]),
  ];
}
