package org.cre.library.workflow.manager.exceptions;

public class InvalidFlowConfigurationException extends RuntimeException {
    public InvalidFlowConfigurationException(String message) {
        super(message);
    }

    public InvalidFlowConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
