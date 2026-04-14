package com.pmfb.gonogo.engine;

import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

@Command(
        name = "gonogo",
        mixinStandardHelpOptions = true,
        version = "go-no-go-engine 0.1.0",
        description = "CLI decision engine for GO/NO-GO job evaluation.",
        subcommands = {
                ConfigCommand.class,
                FetchCommand.class,
                FetchWebCommand.class,
                EvaluateCommand.class,
                EvaluateInputCommand.class,
                RerunAdHocEvaluationsCommand.class,
                NormalizeAdHocCompanyNamesCommand.class,
                RerunAdHocMatrixCommand.class,
                CheckCommand.class,
                BatchEvaluateCommand.class,
                WeeklyDigestCommand.class,
                PipelineCommand.class,
                TuiCommand.class,
                ScheduleCommand.class,
                RunCommand.class
        }
)
public final class GoNoGoCommand implements Callable<Integer> {
    @Spec
    private CommandSpec spec;

    @Override
    public Integer call() {
        spec.commandLine().usage(System.out);
        return 0;
    }
}
