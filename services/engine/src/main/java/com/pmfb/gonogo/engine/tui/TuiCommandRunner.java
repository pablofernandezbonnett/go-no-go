package com.pmfb.gonogo.engine.tui;

import java.util.List;

@FunctionalInterface
public interface TuiCommandRunner {
    int execute(List<String> commandArgs);
}
