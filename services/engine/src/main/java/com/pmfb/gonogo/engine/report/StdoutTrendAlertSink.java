package com.pmfb.gonogo.engine.report;

import java.util.List;

public final class StdoutTrendAlertSink implements TrendAlertSink {
    private static final String MESSAGE_NO_ALERTS = "No alerts to print.";
    private static final String MESSAGE_DISPATCHED = "Printed alerts to " + TrendAlertSinkFactory.SINK_STDOUT + ".";
    private static final String LABEL_TREND_ALERT_DISPATCH = "trend_alert_dispatch:";
    private static final String FIELD_SINK = "sink";
    private static final String FIELD_PERSONA = "persona";
    private static final String FIELD_CANDIDATE_PROFILE = "candidate_profile";
    private static final String FIELD_GENERATED_AT = "generated_at";

    @Override
    public String id() {
        return TrendAlertSinkFactory.SINK_STDOUT;
    }

    @Override
    public DispatchResult dispatch(List<TrendAlert> alerts, TrendAlertDispatchContext context) {
        if (alerts == null || alerts.isEmpty()) {
            return new DispatchResult(id(), 0, MESSAGE_NO_ALERTS);
        }

        System.out.println(LABEL_TREND_ALERT_DISPATCH);
        System.out.println(FIELD_SINK + ": " + id());
        System.out.println(FIELD_PERSONA + ": " + context.personaId());
        System.out.println(FIELD_CANDIDATE_PROFILE + ": " + context.candidateProfileId());
        System.out.println(FIELD_GENERATED_AT + ": " + context.generatedAt());
        for (TrendAlert alert : alerts) {
            System.out.println(
                    "- [" + alert.severity() + "] " + alert.code() + ": " + alert.message()
            );
        }

        return new DispatchResult(id(), alerts.size(), MESSAGE_DISPATCHED);
    }
}
