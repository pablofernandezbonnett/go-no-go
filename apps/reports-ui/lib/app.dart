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
    ),
    css('.content-shell').styles(
      display: .flex,
      flexDirection: .column,
      flex: Flex(grow: 1),
      padding: .all(1.25.rem),
      boxSizing: .borderBox,
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
    css('.run-tab.active, .digest-tab.active, .context-tab.active').styles(
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
    css('.collapsible-header').styles(
      display: .flex,
      justifyContent: JustifyContent.spaceBetween,
      alignItems: .flexStart,
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
    ]),
  ];
}
