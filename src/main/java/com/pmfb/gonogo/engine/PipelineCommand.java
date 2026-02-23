package com.pmfb.gonogo.engine;

import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Command(
        name = "pipeline",
        description = "Pipeline orchestration commands.",
        subcommands = {
                PipelineRunCommand.class
        }
)
public final class PipelineCommand implements Callable<Integer> {
    @Spec
    private CommandSpec spec;

    @Override
    public Integer call() {
        spec.commandLine().usage(System.out);
        return 0;
    }
}
