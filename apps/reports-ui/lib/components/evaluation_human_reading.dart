import 'package:jaspr/dom.dart';
import 'package:jaspr/jaspr.dart';

import '../models/human_reading_payload.dart';
import '../pages/reports_view_helpers.dart';

class EvaluationHumanReadingSection extends StatelessComponent {
  const EvaluationHumanReadingSection({
    super.key,
    required this.humanReading,
    required this.positiveSignals,
    required this.negativeSignals,
  });

  final HumanReadingPayload humanReading;
  final List<String> positiveSignals;
  final List<String> negativeSignals;

  @override
  Component build(BuildContext context) {
    final fitPills = _fitPills(humanReading);
    return div(classes: 'context-shell', [
      div(classes: 'human-reading-hero', [
        div(classes: 'human-reading-kicker', [.text('Quick read')]),
        p(classes: 'human-reading-summary', [.text(_summaryText(humanReading))]),
      ]),
      if (fitPills.isNotEmpty)
        div(classes: 'human-reading-fit-grid', [
          for (final pill in fitPills) _FitPill(descriptor: pill),
        ]),
      if (humanReading.whyStillInteresting.isNotEmpty || humanReading.whyWasteOfTime.isNotEmpty)
        div(classes: 'human-reading-reasons-grid', [
          _NarrativeListCard(
            title: 'Why it may still be worth a look',
            toneClass: 'positive',
            emptyMessage: 'The engine did not call out any specific upside beyond the summary.',
            items: humanReading.whyStillInteresting,
          ),
          _NarrativeListCard(
            title: 'What may waste your time',
            toneClass: 'risk',
            emptyMessage: 'The engine did not call out any major time-wasting risk here.',
            items: humanReading.whyWasteOfTime,
          ),
        ]),
      div(classes: 'signal-summary-grid', [
        _SignalSummaryCard(
          title: 'What helps',
          toneClass: 'positive',
          emptyMessage: 'No explicit upside signals were emitted for this result.',
          values: positiveSignals,
        ),
        _SignalSummaryCard(
          title: 'What to watch',
          toneClass: 'risk',
          emptyMessage: 'No explicit caution signals were emitted for this result.',
          values: negativeSignals,
        ),
      ]),
    ]);
  }

  List<_FitDescriptor> _fitPills(HumanReadingPayload humanReading) {
    final descriptors = <_FitDescriptor>[];
    void add(String label, String value) {
      final normalized = value.trim();
      if (normalized.isEmpty) {
        return;
      }
      descriptors.add(
        _FitDescriptor(label: label, value: _fitLevelLabel(normalized), toneClass: _fitToneClass(normalized)),
      );
    }

    add('Access', humanReading.accessFit);
    add('Execution', humanReading.executionFit);
    add('Domain', humanReading.domainFit);
    add('Opportunity', humanReading.opportunityQuality);
    add('Interview ROI', humanReading.interviewRoi);
    return descriptors;
  }

  String _summaryText(HumanReadingPayload humanReading) {
    final summary = humanReading.summary.trim();
    if (summary.isNotEmpty) {
      return summary;
    }
    for (final item in humanReading.whyStillInteresting) {
      final trimmed = item.trim();
      if (trimmed.isNotEmpty) {
        return trimmed;
      }
    }
    for (final item in humanReading.whyWasteOfTime) {
      final trimmed = item.trim();
      if (trimmed.isNotEmpty) {
        return trimmed;
      }
    }
    return 'The engine did not provide a plain-language summary for this result yet.';
  }
}

class _SignalSummaryCard extends StatelessComponent {
  const _SignalSummaryCard({
    required this.title,
    required this.values,
    required this.emptyMessage,
    required this.toneClass,
  });

  final String title;
  final List<String> values;
  final String emptyMessage;
  final String toneClass;

  @override
  Component build(BuildContext context) {
    final previewValues = values
        .take(3)
        .map(describeDecisionFactor)
        .where((item) => item.isNotEmpty)
        .toList(growable: false);
    final overflowCount = values.length - previewValues.length;

    return div(classes: 'signal-summary-card $toneClass', [
      div(classes: 'signal-summary-title', [.text(title)]),
      if (previewValues.isEmpty)
        p(classes: 'signal-summary-empty', [.text(emptyMessage)])
      else ...[
        ul(classes: 'signal-summary-list', [
          for (final item in previewValues) li([.text(item)]),
        ]),
        if (overflowCount > 0)
          p(classes: 'signal-summary-overflow', [.text('+$overflowCount more in the raw engine details')]),
      ],
    ]);
  }
}

class _NarrativeListCard extends StatelessComponent {
  const _NarrativeListCard({
    required this.title,
    required this.toneClass,
    required this.emptyMessage,
    required this.items,
  });

  final String title;
  final String toneClass;
  final String emptyMessage;
  final List<String> items;

  @override
  Component build(BuildContext context) {
    final cleanedItems = items.map((item) => item.trim()).where((item) => item.isNotEmpty).toList(growable: false);
    return div(classes: 'human-reading-reason-card $toneClass', [
      div(classes: 'human-reading-reason-title', [.text(title)]),
      if (cleanedItems.isEmpty)
        p(classes: 'human-reading-reason-empty', [.text(emptyMessage)])
      else
        ul(classes: 'human-reading-reason-list', [
          for (final item in cleanedItems) li([.text(item)]),
        ]),
    ]);
  }
}

class _FitPill extends StatelessComponent {
  const _FitPill({required this.descriptor});

  final _FitDescriptor descriptor;

  @override
  Component build(BuildContext context) {
    return div(classes: 'human-reading-fit ${descriptor.toneClass}', [
      div(classes: 'human-reading-fit-label', [.text(descriptor.label)]),
      div(classes: 'human-reading-fit-value', [.text(descriptor.value)]),
    ]);
  }
}

class _FitDescriptor {
  const _FitDescriptor({
    required this.label,
    required this.value,
    required this.toneClass,
  });

  final String label;
  final String value;
  final String toneClass;
}

String _fitLevelLabel(String raw) {
  return switch (raw.trim().toLowerCase()) {
    'strong' => 'Strong',
    'mixed' => 'Mixed',
    'weak' => 'Weak',
    _ => humanizeIdentifier(raw),
  };
}

String _fitToneClass(String raw) {
  return switch (raw.trim().toLowerCase()) {
    'strong' => 'fit-strong',
    'mixed' => 'fit-mixed',
    'weak' => 'fit-weak',
    _ => 'fit-neutral',
  };
}
