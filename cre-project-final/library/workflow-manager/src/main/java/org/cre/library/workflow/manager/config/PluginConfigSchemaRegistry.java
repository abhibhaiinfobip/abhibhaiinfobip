package org.cre.library.workflow.manager.config;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PluginConfigSchemaRegistry {
    private final Map<String, List<PluginConfigSchema>> schemas = new HashMap<>();

    public PluginConfigSchemaRegistry() {
        // Initialize schemas for known plugins
        // For logging-step (no specific config required beyond what's in flow.json by default)
        // An empty list means no specific keys are checked by this schema, but 'config' object itself can exist.
        schemas.put("logging-step", Collections.emptyList());

        // For rest-http-request-step
        schemas.put("rest-http-request-step", List.of(
            new PluginConfigSchema("url", String.class, true),
            new PluginConfigSchema("method", String.class, true)
            // Future: new PluginConfigSchema("headers", Map.class, false),
            // Future: new PluginConfigSchema("payload", String.class, false)
        ));

        // Schemas for other plugin types would be added here.
    }

    /**
     * Retrieves the configuration schema for a given plugin type.
     * @param pluginType The ID of the plugin (e.g., "logging-step").
     * @return A list of PluginConfigSchema objects, or null if the plugin type is not registered.
     */
    public List<PluginConfigSchema> getSchema(String pluginType) {
        return schemas.get(pluginType);
    }

    /**
     * Registers or updates a configuration schema for a plugin type.
     * (Could be used if schemas are discovered dynamically in the future).
     * @param pluginType The ID of the plugin.
     * @param schema The list of PluginConfigSchema objects defining the schema.
     */
    public void registerSchema(String pluginType, List<PluginConfigSchema> schema) {
        schemas.put(pluginType, schema);
    }
}
