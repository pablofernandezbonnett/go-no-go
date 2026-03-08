package com.pmfb.gonogo.engine;

import picocli.CommandLine;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new GoNoGoCommand()).execute(args);
        System.exit(exitCode);
    }
}
