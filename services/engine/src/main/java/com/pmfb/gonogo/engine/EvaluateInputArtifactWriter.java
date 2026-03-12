package com.pmfb.gonogo.engine;

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
        root.put("generated_at", analysis.generatedAt());
        root.put("persona", analysis.personaId());
        root.put("candidate_profile", analysis.candidateProfileId());
        root.put("source", toSourceMap(analysis.source()));
        root.put("job_input", toJobInputMap(analysis.jobInput()));
        root.put("evaluation", toEvaluationMap(analysis.evaluation()));
        root.put("normalization_warnings", analysis.normalizationWarnings());
        return root;
    }

    private Map<String, Object> toSourceMap(EvaluateInputAnalysis.SourceDetails source) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("kind", source.kind());
        if (!source.url().isBlank()) {
            map.put("url", source.url());
        }
        if (!source.file().isBlank()) {
            map.put("file", source.file());
        }
        if (!source.rawText().isBlank()) {
            map.put("raw_text", source.rawText());
        }
        return map;
    }

    private Map<String, Object> toJobInputMap(JobInput jobInput) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("company_name", jobInput.companyName());
        map.put("title", jobInput.title());
        map.put("location", jobInput.location());
        map.put("salary_range", jobInput.salaryRange());
        map.put("remote_policy", jobInput.remotePolicy());
        map.put("description", jobInput.description());
        return map;
    }

    private Map<String, Object> toEvaluationMap(com.pmfb.gonogo.engine.decision.EvaluationResult evaluation) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("verdict", evaluation.verdict().name());
        map.put("score", evaluation.score());
        map.put("raw_score", evaluation.rawScore());
        map.put("raw_score_min", evaluation.rawScoreMin());
        map.put("raw_score_max", evaluation.rawScoreMax());
        map.put("language_friction_index", evaluation.languageFrictionIndex());
        map.put("company_reputation_index", evaluation.companyReputationIndex());
        map.put("hard_reject_reasons", evaluation.hardRejectReasons());
        map.put("positive_signals", evaluation.positiveSignals());
        map.put("risk_signals", evaluation.riskSignals());
        map.put("reasoning", evaluation.reasoning());
        return map;
    }
}
