package com.pmfb.gonogo.engine.job;

public record JobInput(
        String companyName,
        String title,
        String location,
        String salaryRange,
        String remotePolicy,
        String description
) {
}
