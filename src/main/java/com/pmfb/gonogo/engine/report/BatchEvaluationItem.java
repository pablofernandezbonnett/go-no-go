package com.pmfb.gonogo.engine.report;

import com.pmfb.gonogo.engine.decision.EvaluationResult;
import com.pmfb.gonogo.engine.job.JobInput;

public record BatchEvaluationItem(
        String sourceFile,
        JobInput job,
        EvaluationResult evaluation,
        String jobKey,
        String fingerprint,
        String changeStatus
) {
    public BatchEvaluationItem(String sourceFile, JobInput job, EvaluationResult evaluation) {
        this(sourceFile, job, evaluation, "", "", ChangeStatuses.UNCHANGED);
    }
}
