package com.pmfb.gonogo.engine.report;

import java.util.List;

public final class StdoutTrendAlertSink implements TrendAlertSink {
    private static final String MESSAGE_NO_ALERTS = "No alerts to print.";
    private static final String MESSAGE_DISPATCHED = "Printed alerts to " + TrendAlertSinkFactory.SINK_STDOUT + ".";

    @Override
    public String id() {
        return TrendAlertSinkFactory.SINK_STDOUT;
    }

    @Override
    public DispatchResult dispatch(List<TrendAlert> alerts, TrendAlertDispatchContext context) {
        if (alerts == null || alerts.isEmpty()) {
            return new DispatchResult(id(), 0, MESSAGE_NO_ALERTS);
        }

        System.out.println("trend_alert_dispatch:");
        System.out.println("sink: " + id());
        System.out.println("persona: " + context.personaId());
        System.out.println("candidate_profile: " + context.candidateProfileId());
        System.out.println("generated_at: " + context.generatedAt());
        for (TrendAlert alert : alerts) {
            System.out.println(
                    "- [" + alert.severity() + "] " + alert.code() + ": " + alert.message()
            );
        }

        return new DispatchResult(id(), alerts.size(), MESSAGE_DISPATCHED);
    }
}
