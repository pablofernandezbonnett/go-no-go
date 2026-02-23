package com.pmfb.gonogo.engine.report;

import java.util.List;

public final class NoopTrendAlertSink implements TrendAlertSink {
    private static final String MESSAGE_NO_DISPATCH = "No dispatch (" + TrendAlertSinkFactory.SINK_NONE + ").";

    @Override
    public String id() {
        return TrendAlertSinkFactory.SINK_NONE;
    }

    @Override
    public DispatchResult dispatch(List<TrendAlert> alerts, TrendAlertDispatchContext context) {
        return new DispatchResult(id(), 0, MESSAGE_NO_DISPATCH);
    }
}
