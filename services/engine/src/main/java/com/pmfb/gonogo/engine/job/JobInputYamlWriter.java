package com.pmfb.gonogo.engine.job;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

public final class JobInputYamlWriter {
    public void write(Path outputFile, JobInput jobInput) throws IOException {
        Path parent = outputFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setSplitLines(false);
        options.setIndent(2);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("company_name", jobInput.companyName());
        root.put("title", jobInput.title());
        root.put("location", jobInput.location());
        root.put("salary_range", jobInput.salaryRange());
        root.put("remote_policy", jobInput.remotePolicy());
        root.put("description", jobInput.description());

        try (Writer writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8)) {
            new Yaml(options).dump(root, writer);
        }
    }
}
