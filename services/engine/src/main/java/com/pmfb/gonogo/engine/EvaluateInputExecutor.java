package com.pmfb.gonogo.engine;

import java.util.List;

@FunctionalInterface
interface EvaluateInputExecutor {
    int execute(List<String> args);
}
