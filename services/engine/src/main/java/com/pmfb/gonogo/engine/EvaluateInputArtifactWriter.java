package com.pmfb.gonogo.engine;

import com.pmfb.gonogo.engine.job.JobInputFieldKeys;
import com.pmfb.gonogo.engine.job.JobInput;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

final class EvaluateInputArtifactWriter {
    void write(Path outputFile, EvaluateInputAnalysis analysis) throws IOException {
        Path parent = outputFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(outputFile, toYaml(analysis), StandardCharsets.UTF_8);
    }

    private String toYaml(EvaluateInputAnalysis analysis) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        return new Yaml(options).dump(toYamlMap(analysis));
    }

    private Map<String, Object> toYamlMap(EvaluateInputAnalysis analysis) {
        LinkedHashMap<String, Object> root = new LinkedHashMap<>();
        root.put(EvaluateInputFieldKeys.GENERATED_AT, analysis.generatedAt());
        root.put(EvaluateInputFieldKeys.PERSONA, analysis.personaId());
        root.put(EvaluateInputFieldKeys.CANDIDATE_PROFILE, analysis.candidateProfileId());
        root.put(EvaluateInputFieldKeys.SOURCE, toSourceMap(analysis.source()));
        root.put(EvaluateInputFieldKeys.JOB_INPUT, toJobInputMap(analysis.jobInput()));
        root.put(EvaluateInputFieldKeys.EVALUATION, toEvaluationMap(analysis.evaluation()));
        root.put(EvaluateInputFieldKeys.NORMALIZATION_WARNINGS, analysis.normalizationWarnings());
        return root;
    }

    private Map<String, Object> toSourceMap(EvaluateInputAnalysis.SourceDetails source) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put(EvaluateInputFieldKeys.KIND, source.kind());
        if (!source.url().isBlank()) {
            map.put(EvaluateInputFieldKeys.URL, source.url());
        }
        if (!source.file().isBlank()) {
            map.put(EvaluateInputFieldKeys.FILE, source.file());
        }
        if (!source.rawText().isBlank()) {
            map.put(EvaluateInputFieldKeys.RAW_TEXT, source.rawText());
        }
        return map;
    }

    private Map<String, Object> toJobInputMap(JobInput jobInput) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put(JobInputFieldKeys.COMPANY_NAME, jobInput.companyName());
        map.put(JobInputFieldKeys.TITLE, jobInput.title());
        map.put(JobInputFieldKeys.LOCATION, jobInput.location());
        map.put(JobInputFieldKeys.SALARY_RANGE, jobInput.salaryRange());
        map.put(JobInputFieldKeys.REMOTE_POLICY, jobInput.remotePolicy());
        map.put(JobInputFieldKeys.DESCRIPTION, jobInput.description());
        return map;
    }

    private Map<String, Object> toEvaluationMap(com.pmfb.gonogo.engine.decision.EvaluationResult evaluation) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put(EvaluateInputFieldKeys.VERDICT, evaluation.verdict().name());
        map.put(EvaluateInputFieldKeys.SCORE, evaluation.score());
        map.put(EvaluateInputFieldKeys.RAW_SCORE, evaluation.rawScore());
        map.put(EvaluateInputFieldKeys.RAW_SCORE_MIN, evaluation.rawScoreMin());
        map.put(EvaluateInputFieldKeys.RAW_SCORE_MAX, evaluation.rawScoreMax());
        map.put(EvaluateInputFieldKeys.LANGUAGE_FRICTION_INDEX, evaluation.languageFrictionIndex());
        map.put(EvaluateInputFieldKeys.COMPANY_REPUTATION_INDEX, evaluation.companyReputationIndex());
        map.put(EvaluateInputFieldKeys.HARD_REJECT_REASONS, evaluation.hardRejectReasons());
        map.put(EvaluateInputFieldKeys.POSITIVE_SIGNALS, evaluation.positiveSignals());
        map.put(EvaluateInputFieldKeys.RISK_SIGNALS, evaluation.riskSignals());
        map.put(EvaluateInputFieldKeys.REASONING, evaluation.reasoning());
        map.put(EvaluateInputFieldKeys.HUMAN_READING, toHumanReadingMap(evaluation.humanReading()));
        return map;
    }

    private Map<String, Object> toHumanReadingMap(com.pmfb.gonogo.engine.decision.HumanReading humanReading) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put(EvaluateInputFieldKeys.ACCESS_FIT, humanReading.accessFit().serialized());
        map.put(EvaluateInputFieldKeys.EXECUTION_FIT, humanReading.executionFit().serialized());
        map.put(EvaluateInputFieldKeys.DOMAIN_FIT, humanReading.domainFit().serialized());
        map.put(EvaluateInputFieldKeys.OPPORTUNITY_QUALITY, humanReading.opportunityQuality().serialized());
        map.put(EvaluateInputFieldKeys.INTERVIEW_ROI, humanReading.interviewRoi().serialized());
        map.put(EvaluateInputFieldKeys.SUMMARY, humanReading.summary());
        map.put(EvaluateInputFieldKeys.WHY_STILL_INTERESTING, humanReading.whyStillInteresting());
        map.put(EvaluateInputFieldKeys.WHY_WASTE_OF_TIME, humanReading.whyWasteOfTime());
        return map;
    }
}
