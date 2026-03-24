package com.pmfb.gonogo.engine.tui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class TuiAppTest {
    @Test
    void runsConfigValidateFromMenuAndReturnsToHome() throws IOException {
        Path tempDir = Files.createTempDirectory("gonogo-tui-test");
        Path configDir = tempDir.resolve("config");
        writeConfig(configDir);
        TuiConfigContext context = TuiConfigContext.load(configDir);

        String input = """
                5


                6
                """;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        List<List<String>> executedCommands = new ArrayList<>();
        TuiPrompts prompts = new LineBasedTuiPrompts(
                new BufferedReader(new StringReader(input)),
                new PrintWriter(output, true, StandardCharsets.UTF_8)
        );
        TuiCommandRunner runner = commandArgs -> {
            executedCommands.add(List.copyOf(commandArgs));
            return 0;
        };

        int exitCode = new TuiApp(context, prompts, runner).run();

        assertEquals(0, exitCode);
        assertEquals(List.of(List.of("config", "validate", "--config-dir=" + configDir)), executedCommands);
        String rendered = output.toString(StandardCharsets.UTF_8);
        assertTrue(rendered.contains("gonogo config validate"));
        assertTrue(rendered.contains("Exiting TUI."));
    }

    private void writeConfig(Path configDir) throws IOException {
        Files.createDirectories(configDir);
        Files.writeString(
                configDir.resolve("companies.yaml"),
                """
                        companies:
                          - id: moneyforward
                            name: Money Forward
                            career_url: https://corp.moneyforward.com/recruit/
                            type_hint: fintech_product
                            region: japan
                            notes: "Product company."
                        """,
                StandardCharsets.UTF_8
        );
        Files.writeString(
                configDir.resolve("personas.yaml"),
                """
                        personas:
                          - id: product_expat_engineer
                            description: "Product-oriented expat engineer"
                            priorities:
                              - english_environment
                            hard_no:
                              - onsite_only
                              - salary_missing
                            acceptable_if:
                              - hybrid_partial
                        """,
                StandardCharsets.UTF_8
        );
        Files.writeString(
                configDir.resolve("blacklist.yaml"),
                """
                        blacklisted_companies: []
                        """,
                StandardCharsets.UTF_8
        );
    }
}
