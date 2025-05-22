package org.cre.library.workflow.manager;

import org.cre.library.workflow.manager.config.PluginConfigSchema;
import org.cre.library.workflow.manager.config.PluginConfigSchemaRegistry;
import org.cre.library.workflow.manager.exceptions.InvalidFlowConfigurationException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.pf4j.PluginManager; // Added for findPluginByType
import org.pf4j.PluginWrapper; 

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap; // For the map in findPluginByType

public class WorkflowManager {
    // Option 1: Pass PluginManager
    // private final PluginManager pluginManager;
    // private final List<WorkflowStep> workflowExtensions; // Raw list of extensions

    // Option 2: Pass pre-constructed map (Preferred for cleaner WorkflowManager)
    private final Map<String, WorkflowStep> activePlugins; // Map of PluginID to WorkflowStep instance

    private final String flowJsonPath;
    private List<Map<String, Object>> parsedFlowSteps;
    private final PluginConfigSchemaRegistry schemaRegistry;

    // Constructor for Option 2 (Preferred)
    public WorkflowManager(Map<String, WorkflowStep> activePlugins, String flowJsonPath) {
        this.activePlugins = activePlugins;
        this.flowJsonPath = flowJsonPath;
        this.schemaRegistry = new PluginConfigSchemaRegistry();
    }

    // Example Constructor for Option 1 (If passing PluginManager)
    // public WorkflowManager(List<WorkflowStep> workflowExtensions, PluginManager pluginManager, String flowJsonPath) {
    //     this.workflowExtensions = workflowExtensions;
    //     this.pluginManager = pluginManager; // Store plugin manager
    //     this.activePlugins = new HashMap<>(); // Will be populated if needed, or findPluginByType uses pluginManager directly
    //     this.flowJsonPath = flowJsonPath;
    //     this.schemaRegistry = new PluginConfigSchemaRegistry();
    // }


    public void loadFlow() throws IOException, InvalidFlowConfigurationException {
        String content = new String(Files.readAllBytes(Paths.get(flowJsonPath)));
        JSONObject flowJson = new JSONObject(content);
        JSONArray stepsArray = flowJson.getJSONArray("steps");

        List<Map<String, Object>> tempParsedFlowSteps = new ArrayList<>();
        for (int i = 0; i < stepsArray.length(); i++) {
            JSONObject stepJson = stepsArray.getJSONObject(i);
            Map<String, Object> stepMap = stepJson.toMap();

            String pluginType = (String) stepMap.get("type");
            if (pluginType == null || pluginType.trim().isEmpty()) {
                throw new InvalidFlowConfigurationException("Step " + i + " (id: " + stepMap.get("id") +") in flow.json is missing 'type' field.");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> configMap = (Map<String, Object>) stepMap.get("config");
            if (configMap == null) {
                configMap = Collections.emptyMap(); 
            }

            List<PluginConfigSchema> schemas = schemaRegistry.getSchema(pluginType);
            if (schemas != null) {
                for (PluginConfigSchema schema : schemas) {
                    Object value = configMap.get(schema.getKeyName());
                    if (schema.isRequired()) {
                        if (value == null) {
                            throw new InvalidFlowConfigurationException(
                                "Validation failed for step with type '" + pluginType + "' (id: " + stepMap.get("id") + "): Required config key '" + schema.getKeyName() + "' is missing.");
                        }
                    }
                    if (value != null) {
                        if (!isTypeCompatible(schema.getType(), value.getClass())) {
                             throw new InvalidFlowConfigurationException(
                                "Validation failed for step with type '" + pluginType + "' (id: " + stepMap.get("id") + "): Config key '" + schema.getKeyName() + 
                                "' has incorrect type. Expected " + schema.getType().getSimpleName() + ", but got " + value.getClass().getSimpleName() + " (value: " + value +").");
                        }
                    }
                }
                 // Optional: Check for unknown keys
                for (String actualKey : configMap.keySet()) {
                    boolean knownKey = schemas.stream().anyMatch(s -> s.getKeyName().equals(actualKey));
                    if (!knownKey) {
                        System.out.println("Warning: Unknown configuration key '" + actualKey + "' found for plugin type '" + pluginType + "' (id: " + stepMap.get("id") + ").");
                    }
                }
            } else {
                 System.out.println("Warning: No configuration schema found for plugin type '" + pluginType + "' (id: " + stepMap.get("id") + "). Skipping config validation for this plugin.");
            }
            tempParsedFlowSteps.add(stepMap);
        }
        this.parsedFlowSteps = tempParsedFlowSteps;
    }
    
    // Basic type compatibility check, can be expanded
    private boolean isTypeCompatible(Class<?> expectedType, Class<?> actualType) {
        if (expectedType.isAssignableFrom(actualType)) {
            return true;
        }
        if (Number.class.isAssignableFrom(expectedType) && Number.class.isAssignableFrom(actualType)) {
            // e.g. schema expects Double, actual is Integer. This is generally acceptable.
            return true;
        }
        // Add other specific compatible conversions if needed, e.g. String to Enum, etc.
        return false;
    }

    public Object execute(Map<String, Object> initialInput) {
        if (parsedFlowSteps == null) {
            throw new IllegalStateException("Flow not loaded or failed to load. Cannot execute.");
        }

        Object currentData = initialInput;
        for (Map<String, Object> stepConfig : parsedFlowSteps) {
            String pluginType = (String) stepConfig.get("type");
            WorkflowStep stepPlugin = findPluginByType(pluginType); // Uses the new activePlugins map

            if (stepPlugin == null) {
                System.err.println("Plugin not found for type: " + pluginType + " (id: " + stepConfig.get("id") + "). Skipping step.");
                continue;
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> configForPlugin = (Map<String, Object>) stepConfig.get("config");
            if (configForPlugin == null) {
                configForPlugin = Collections.emptyMap();
            }

            try {
                System.out.println("Executing plugin: " + pluginType + " (id: " + stepConfig.get("id") + ")");
                currentData = stepPlugin.execute((Map<String, Object>) currentData, configForPlugin);
            } catch (ClassCastException e) {
                String errorMessage = "Error during plugin execution: Data type mismatch. Plugin " + pluginType + " (id: " + stepConfig.get("id") + ")";
                System.err.println(errorMessage + ": " + e.getMessage());
                throw new RuntimeException(errorMessage, e);
            } catch (Exception e) {
                String errorMessage = "Error during plugin execution: " + pluginType + " (id: " + stepConfig.get("id") + ")";
                System.err.println(errorMessage + ": " + e.getMessage());
                e.printStackTrace();
                throw new RuntimeException(errorMessage, e);
            }

            if (currentData == null) {
                System.err.println("Warning: Plugin " + pluginType + " (id: " + stepConfig.get("id") + ") returned null. Subsequent plugins may fail.");
            }
        }
        return currentData;
    }

    private WorkflowStep findPluginByType(String type) {
        // This now uses the pre-resolved map of active plugins
        return this.activePlugins.get(type);
    }
    
    public List<Map<String, Object>> getFlowSteps() {
        return this.parsedFlowSteps;
    }
}
