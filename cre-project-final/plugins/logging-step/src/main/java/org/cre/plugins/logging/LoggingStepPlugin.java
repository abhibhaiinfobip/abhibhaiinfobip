package org.cre.plugins.logging;

import org.cre.library.workflow.manager.WorkflowStep;
import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;

import java.util.Map;

public class LoggingStepPlugin extends Plugin {

    public LoggingStepPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public void start() {
        System.out.println("LoggingStepPlugin.start()");
    }

    @Override
    public void stop() {
        System.out.println("LoggingStepPlugin.stop()");
    }

    @Extension
    public static class LoggingStepExecutor implements WorkflowStep {
        // ANSI escape code for red text
        public static final String ANSI_RED = "\u001B[31m";
        // ANSI escape code to reset color
        public static final String ANSI_RESET = "\u001B[0m";

        @Override
        public Object execute(Map<String, Object> input, Map<String, Object> config) {
            System.err.println(ANSI_RED + "LoggingStepPlugin: Executing with input -> " + input + ", config -> " + config + ANSI_RESET);
            // For this plugin, we just log and return the input as is
            return input;
        }
    }
}
