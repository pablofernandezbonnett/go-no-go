package com.pmfb.gonogo.engine;

import java.util.concurrent.Callable;
import picocli.CommandLine.Command;

@Command(
        name = "run",
        mixinStandardHelpOptions = true,
        description = "Shortcut for a full default execution (`pipeline run-all`)."
)
public final class RunCommand implements Callable<Integer> {
    @Override
    public Integer call() {
        return new PipelineRunAllCommand().call();
    }
}
