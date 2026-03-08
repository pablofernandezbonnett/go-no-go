package com.pmfb.gonogo.engine.report;

import java.io.IOException;
import java.util.List;

public interface TrendAlertSink {
    String id();

    DispatchResult dispatch(List<TrendAlert> alerts, TrendAlertDispatchContext context) throws IOException;

    record DispatchResult(
            String sinkId,
            int delivered,
            String message
    ) {
    }
}
