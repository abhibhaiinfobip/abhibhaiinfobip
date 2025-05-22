package org.cre.library.workflow.manager;

import org.pf4j.ExtensionPoint;
import java.util.Map;

public interface WorkflowStep extends ExtensionPoint {
    Object execute(Map<String, Object> input, Map<String, Object> config);
}
