import 'package:jaspr/dom.dart';
import 'package:jaspr/jaspr.dart';
import 'package:jaspr_router/jaspr_router.dart';

import '../constants/evaluation_contract.dart';
import '../constants/theme.dart';

class Header extends StatelessComponent {
  const Header({super.key});

  @override
  Component build(BuildContext context) {
    final routeState = RouteState.maybeOf(context);
    final activePath = routeState?.subloc ?? context.url.split('?').first;

    return aside(classes: 'sidebar', [
      div(classes: 'sidebar-brand', [
        div(classes: 'brand-dot', []),
        div(classes: 'brand-copy', [
          div(classes: 'brand-title', [.text('Go/No-Go')]),
          div(classes: 'brand-subtitle', [.text('Reports UI')]),
        ]),
      ]),
      nav(classes: 'sidebar-nav', [
        for (final route in [
          (label: 'Runs', path: '/'),
          (label: 'Evaluate', path: evaluatePageRoute),
          (label: 'Batch', path: '/batch'),
          (label: 'Job Detail', path: '/job'),
          (label: 'Weekly', path: '/weekly'),
          (label: 'Context', path: '/context'),
          (label: 'Trends', path: '/trends'),
        ])
          Link(
            to: route.path,
            classes: activePath == route.path ? 'nav-item active' : 'nav-item',
            child: .text(route.label),
          ),
      ]),
      p(classes: 'sidebar-note', [
        .text('Reports and ad-hoc evaluation results from engine artifacts.'),
      ]),
    ]);
  }

  @css
  static List<StyleRule> get styles => [
    css('.sidebar').styles(
      width: 16.rem,
      minHeight: 100.vh,
      boxSizing: .borderBox,
      padding: .all(1.2.rem),
      backgroundColor: const Color('#0f1728'),
      color: Colors.white,
      display: .flex,
      flexDirection: .column,
      gap: Gap.all(1.rem),
      raw: const {
        'position': 'sticky',
        'top': '0',
      },
    ),
    css('.sidebar-brand').styles(
      display: .flex,
      alignItems: .center,
      gap: Gap.all(0.6.rem),
    ),
    css('.brand-dot').styles(
      width: 0.9.rem,
      height: 0.9.rem,
      radius: .all(.circular(999.px)),
      backgroundColor: const Color('#26c6da'),
    ),
    css('.brand-title').styles(
      fontWeight: .w700,
      fontSize: 1.05.rem,
      color: Colors.white,
    ),
    css('.brand-subtitle').styles(
      fontSize: 0.8.rem,
      color: const Color('#b8c4d8'),
    ),
    css('.sidebar-nav').styles(
      display: .flex,
      flexDirection: .column,
      gap: Gap.all(0.35.rem),
    ),
    css('.sidebar-nav .nav-item').styles(
      display: .block,
      padding: .symmetric(horizontal: 0.7.rem, vertical: 0.55.rem),
      radius: .all(.circular(8.px)),
      color: const Color('#dbe3f0'),
      textDecoration: TextDecoration(line: .none),
      fontWeight: .w600,
      fontSize: 0.92.rem,
      raw: const {
        'transition': 'background 140ms ease, color 140ms ease',
      },
    ),
    css('.sidebar-nav .nav-item:hover').styles(
      backgroundColor: const Color('#1f2c44'),
      color: Colors.white,
    ),
    css('.sidebar-nav .nav-item.active').styles(
      backgroundColor: primaryColor,
      color: Colors.white,
    ),
    css('.sidebar-note').styles(
      margin: Spacing.only(top: 0.5.rem),
      color: const Color('#9fb0ca'),
      fontSize: 0.78.rem,
      lineHeight: 1.35.em,
    ),
    css.media(const MediaQuery.raw('(max-width: 920px)'), [
      css('.sidebar').styles(
        width: 100.percent,
        minHeight: 0.px,
        padding: .all(0.85.rem),
        raw: const {
          'position': 'static',
        },
      ),
      css('.sidebar-nav').styles(
        display: .flex,
        flexDirection: .row,
        flexWrap: .nowrap,
        overflow: Overflow.auto,
        raw: const {
          '-webkit-overflow-scrolling': 'touch',
        },
      ),
      css('.sidebar-nav .nav-item').styles(
        raw: const {
          'flex': '0 0 auto',
        },
      ),
      css('.sidebar-note').styles(
        display: .none,
      ),
    ]),
    css.media(const MediaQuery.raw('(max-width: 620px)'), [
      css('.sidebar').styles(
        padding: .all(0.7.rem),
      ),
      css('.brand-subtitle').styles(
        display: .none,
      ),
      css('.sidebar-nav .nav-item').styles(
        padding: .symmetric(horizontal: 0.62.rem, vertical: 0.42.rem),
        fontSize: 0.84.rem,
      ),
    ]),
  ];
}
