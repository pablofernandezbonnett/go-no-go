package com.pmfb.gonogo.engine.tui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pmfb.gonogo.engine.config.CandidateProfileConfig;
import com.pmfb.gonogo.engine.config.CompanyConfig;
import com.pmfb.gonogo.engine.config.EvaluationRuntimeConfig;
import com.pmfb.gonogo.engine.config.FetchWebRuntimeConfig;
import com.pmfb.gonogo.engine.config.PersonaConfig;
import com.pmfb.gonogo.engine.tui.TuiExecutionPlanFactory.FetchWebRequest;
import com.pmfb.gonogo.engine.tui.TuiExecutionPlanFactory.PipelineRunRequest;
import com.pmfb.gonogo.engine.tui.TuiExecutionPlanFactory.QuickCheckMode;
import com.pmfb.gonogo.engine.tui.TuiExecutionPlanFactory.QuickCheckRequest;
import com.pmfb.gonogo.engine.tui.TuiExecutionPlanFactory.RunAllRequest;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class TuiExecutionPlanFactoryTest {
    private final TuiExecutionPlanFactory factory = new TuiExecutionPlanFactory();

    @Test
    void buildsShortRunAllCommandWhenUsingDefaults() {
        TuiConfigContext context = testContext();

        TuiExecutionPlan plan = factory.buildRunAll(
                context,
                new RunAllRequest(
                        context.personaIds(),
                        "",
                        context.companyIds(),
                        false,
                        false,
                        TuiExecutionPlanFactory.DEFAULT_RUN_ALL_FETCH_MAX_JOBS,
                        context.fetchWebDefaults().requestDelayMillis(),
                        context.fetchWebDefaults().maxConcurrency(),
                        context.evaluationDefaults().maxConcurrency(),
                        TuiExecutionPlanFactory.DEFAULT_PERSONA_CONCURRENCY
                )
        );

        assertEquals(List.of("pipeline", "run-all"), plan.commandArgs());
        assertEquals("gonogo pipeline run-all", plan.logicalCommand());
    }

    @Test
    void buildsPipelineRunCommandWithSelectedOverrides() {
        TuiConfigContext context = testContext();

        TuiExecutionPlan plan = factory.buildPipelineRun(
                context,
                new PipelineRunRequest(
                        "product_pragmatic_engineer",
                        "pmfb",
                        true,
                        List.of("mercari"),
                        true,
                        8,
                        1600,
                        2,
                        3
                )
        );

        assertEquals(
                List.of(
                        "pipeline",
                        "run",
                        "--persona=product_pragmatic_engineer",
                        "--candidate-profile=pmfb",
                        "--fetch-web-first",
                        "--company-ids=mercari",
                        "--incremental-only",
                        "--fetch-web-max-jobs-per-company=8",
                        "--fetch-web-request-delay-millis=1600",
                        "--fetch-web-max-concurrency=2",
                        "--evaluate-max-concurrency=3"
                ),
                plan.commandArgs()
        );
    }

    @Test
    void usesPlaceholderWhenPreviewingMultilineRawTextCheck() {
        TuiConfigContext context = testContext();

        TuiExecutionPlan plan = factory.buildQuickCheck(
                context,
                new QuickCheckRequest(
                        QuickCheckMode.RAW_TEXT,
                        "product_expat_engineer",
                        "",
                        """
                                Company: Money Forward
                                Title: Backend Engineer
                                """,
                        TuiExecutionPlanFactory.DEFAULT_CHECK_TIMEOUT_SECONDS,
                        ""
                )
        );

        assertEquals(
                List.of(
                        "check",
                        "--mode=raw-text",
                        """
                                Company: Money Forward
                                Title: Backend Engineer
                                """
                ),
                plan.commandArgs()
        );
        assertEquals(
                List.of("check", "--mode=raw-text", "<multiline raw text>"),
                plan.displayArgs()
        );
        assertTrue(plan.gradleCommand().contains("<multiline raw text>"));
    }

    @Test
    void buildsFetchWebCommandWithAdvancedFlags() {
        TuiConfigContext context = testContext();

        TuiExecutionPlan plan = factory.buildFetchWeb(
                context,
                new FetchWebRequest(
                        List.of("moneyforward"),
                        9,
                        1800,
                        3,
                        2,
                        true,
                        true
                )
        );

        assertEquals(
                List.of(
                        "fetch-web",
                        "--company-ids=moneyforward",
                        "--max-jobs-per-company=9",
                        "--request-delay-millis=1800",
                        "--max-concurrency=3",
                        "--max-concurrency-per-host=2",
                        "--disable-cache",
                        "--disable-company-context"
                ),
                plan.commandArgs()
        );
    }

    private TuiConfigContext testContext() {
        return new TuiConfigContext(
                Path.of("config"),
                List.of(
                        new CompanyConfig(
                                "moneyforward",
                                "Money Forward",
                                "https://corp.moneyforward.com/recruit/",
                                "fintech_product",
                                "japan",
                                "Product company."
                        ),
                        new CompanyConfig(
                                "mercari",
                                "Mercari",
                                "https://careers.mercari.com/",
                                "marketplace_product",
                                "japan",
                                "Marketplace product company."
                        )
                ),
                List.of(
                        new PersonaConfig(
                                "product_expat_engineer",
                                "Product-oriented expat engineer",
                                List.of("english_environment"),
                                List.of("onsite_only"),
                                List.of("hybrid_partial")
                        ),
                        new PersonaConfig(
                                "product_pragmatic_engineer",
                                "Pragmatic product engineer",
                                List.of("salary"),
                                List.of("salary_missing"),
                                List.of("hybrid_partial")
                        )
                ),
                List.of(
                        new CandidateProfileConfig(
                                "pmfb",
                                "Demo Candidate",
                                "Product Engineer",
                                "Tokyo",
                                10,
                                List.of("java"),
                                List.of("dart"),
                                List.of(),
                                List.of("fintech"),
                                List.of(),
                                List.of()
                        )
                ),
                new FetchWebRuntimeConfig(20, "ua", 2, 300, 1200, 4, 1, "strict", 720),
                new EvaluationRuntimeConfig(4)
        );
    }
}
