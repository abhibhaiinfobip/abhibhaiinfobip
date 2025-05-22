package org.cre.library.workflow.manager.config;

public class PluginConfigSchema {
    private final String keyName;
    private final Class<?> type;
    private final boolean required;

    public PluginConfigSchema(String keyName, Class<?> type, boolean required) {
        this.keyName = keyName;
        this.type = type;
        this.required = required;
    }

    public String getKeyName() {
        return keyName;
    }

    public Class<?> getType() {
        return type;
    }

    public boolean isRequired() {
        return required;
    }
}
