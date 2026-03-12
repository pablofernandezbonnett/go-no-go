package com.pmfb.gonogo.engine.config;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParseResult;

public final class RuntimeOptionResolver {
    private RuntimeOptionResolver() {
    }

    public static int resolveInt(CommandSpec spec, String optionName, int cliValue, int configuredValue) {
        return isMatched(spec, optionName) ? cliValue : configuredValue;
    }

    public static long resolveLong(CommandSpec spec, String optionName, long cliValue, long configuredValue) {
        return isMatched(spec, optionName) ? cliValue : configuredValue;
    }

    public static String resolveString(
            CommandSpec spec,
            String optionName,
            String cliValue,
            String configuredValue
    ) {
        return isMatched(spec, optionName) ? cliValue : configuredValue;
    }

    private static boolean isMatched(CommandSpec spec, String optionName) {
        if (spec == null || spec.commandLine() == null) {
            return false;
        }
        ParseResult parseResult = spec.commandLine().getParseResult();
        return parseResult != null && parseResult.hasMatchedOption(optionName);
    }
}
