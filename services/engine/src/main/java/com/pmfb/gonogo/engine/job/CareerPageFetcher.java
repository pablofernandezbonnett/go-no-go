package com.pmfb.gonogo.engine.job;

import java.io.IOException;
import java.time.Duration;

public interface CareerPageFetcher {
    CareerPageHttpFetcher.FetchResult fetch(String url, Duration timeout, String userAgent)
            throws IOException, InterruptedException;
}
