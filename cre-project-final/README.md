# CRE-Service (Configurable Runtime Execution Service)

## Overview

The Configurable Runtime Execution (CRE) Service is a Java-based application designed to host and execute workflows composed of PF4J (Plugin Framework for Java) plugins. The execution flow is defined in a `flow.json` file. The project leverages Quarkus for the host application, PF4J for plugin management, and is built with Java 21.

## Project Structure

The project follows a multi-module Maven layout:

*   `cre-project-final/`: The root directory containing the parent POM (`pom.xml`) that manages all sub-modules.
    *   `library/workflow-manager/`: A shared library module. It defines the core `WorkflowStep` interface that all plugins must implement and contains the `WorkflowManager` responsible for parsing `flow.json` and orchestrating workflow execution.
    *   `services/cre-service/`: The main Quarkus application. It acts as the host for PF4J plugins, integrates the `WorkflowManager`, and exposes REST endpoints. This module handles dynamic plugin loading and endpoint registration.
    *   `plugins/logging-step/`: An example PF4J plugin. This plugin implements `WorkflowStep` and provides a simple logging mechanism, printing received data to the console.
    *   `plugins/rest-http-request-step/`: Another example PF4J plugin. This plugin is designed to be triggered by dynamic REST endpoints configured via `flow.json`. It demonstrates how plugins can be configured and participate in workflows initiated by HTTP requests.

## Prerequisites

*   JDK 21
*   Apache Maven (e.g., 3.8.x or newer)

## Building the Project

To build the entire project, navigate to the root directory (`cre-project-final/`) and run:

```bash
mvn clean install
```
or
```bash
mvn clean package
```

This command will perform the following actions:
*   Compile all Java source code for each module.
*   Package the `library/workflow-manager` module as a JAR.
*   Package the PF4J plugins (`plugins/logging-step` and `plugins/rest-http-request-step`) as JAR files. These JARs will include necessary PF4J manifest entries.
*   Package the `services/cre-service` Quarkus application into a runnable JAR.
*   As part of the `cre-service` packaging phase, the `maven-dependency-plugin` will copy the plugin JARs (e.g., `logging-step.jar`, `rest-http-request-step.jar` - names are stripped of versions by `stripVersion=true`) into the `services/cre-service/target/quarkus-app/plugins/` directory.

## Running the Application (`cre-service`)

### Packaged Mode (Recommended for Production/Testing)

1.  **Build the Project:** Ensure you have built the project using `mvn clean install` from the root directory.
2.  **Navigate:** Change your directory to `services/cre-service/target/quarkus-app/`.
    ```bash
    cd services/cre-service/target/quarkus-app/
    ```
3.  **Run:** Execute the application using the following command:
    ```bash
    java -jar quarkus-run.jar
    ```
    The `cre-service` application will start, and PF4J will load plugins from the `plugins/` subdirectory located within `services/cre-service/target/quarkus-app/`.

### Development Mode

1.  **Build Plugins:** First, ensure all plugins and the `workflow-manager` library are built and installed in your local Maven repository. From the root directory (`cre-project-final/`):
    ```bash
    mvn clean install
    ```
2.  **Prepare Plugin Directory for `cre-service`:**
    *   Create a directory named `plugins` inside the `services/cre-service/` module:
        ```bash
        # From the cre-project-final root directory:
        mkdir -p services/cre-service/plugins
        ```
    *   Copy the plugin JARs (with their versions) from their respective `target` directories into this newly created `services/cre-service/plugins/` directory.
        ```bash
        # From the cre-project-final root directory:
        cp plugins/logging-step/target/logging-step-1.0-SNAPSHOT.jar services/cre-service/plugins/
        cp plugins/rest-http-request-step/target/rest-http-request-step-1.0-SNAPSHOT.jar services/cre-service/plugins/
        ```
3.  **Run in Dev Mode:** Navigate to the `services/cre-service/` directory:
    ```bash
    cd services/cre-service/
    ```
    Then, start the Quarkus application in development mode:
    ```bash
    mvn quarkus:dev
    ```
    The `CreResource` class is configured to look for a `plugins` directory relative to its execution context (e.g., `services/cre-service/plugins` when running `mvn quarkus:dev` from the `services/cre-service` directory). PF4J will load the versioned JARs from this location.

## How it Works

### PF4J Plugin Loading

*   The `CreResource` class in the `cre-service` module initializes PF4J's `PluginManager` upon application startup.
*   PF4J loads plugins from the configured `plugins` directory. 
    * In **packaged mode**, this is `quarkus-app/plugins/` (containing version-stripped JARs).
    * In **dev mode**, this is typically `services/cre-service/plugins/` (containing versioned JARs copied manually).
*   Each plugin JAR must contain a `MANIFEST.MF` with PF4J-specific entries, such as `Plugin-Id`, `Plugin-Class`, and `Plugin-Version`. These are configured in each plugin's `pom.xml` using the `maven-jar-plugin`.
*   Plugins that are intended to be part of a workflow must implement the `org.cre.library.workflow.manager.WorkflowStep` interface and be annotated with `@org.pf4j.Extension`.

### `flow.json`

*   This configuration file is located at `services/cre-service/src/main/resources/flow.json`.
*   It defines the sequence of steps that constitute a workflow.
*   Each step in the `steps` array has:
    *   `id`: A unique identifier for the step instance within the flow (currently informational).
    *   `type`: A string that **must match** the `Plugin-Id` of a loaded PF4J plugin. This is how the `WorkflowManager` identifies which plugin to execute for a given step.
    *   `config`: A JSON object containing specific configuration parameters for that plugin instance within the workflow.

### `WorkflowManager`

*   Located in the `library/workflow-manager` module.
*   It is responsible for:
    1.  Loading and parsing the `flow.json` file.
    2.  Receiving a list of all loaded `WorkflowStep` plugin extensions from `CreResource`.
    3.  Executing the workflow: For each step defined in `flow.json`, it finds the corresponding loaded plugin (matching `type` to `Plugin-Id`) and calls its `execute` method, passing the current data map and the step-specific `config`.

### Dynamic Endpoint Registration

*   The `CreResource` class in `cre-service` uses Quarkus's Vert.x integration to dynamically register HTTP endpoints at startup.
*   It parses `flow.json` using `WorkflowManager.getFlowSteps()`.
*   If a step in `flow.json` has a `type` of `rest-http-request-step`, `CreResource` uses the `config.url` and `config.method` from that step's definition to create a new Vert.x HTTP route (e.g., POST /cre/xml).
*   When an HTTP request arrives at one of these dynamically registered endpoints:
    1.  Request data (body, query parameters, path parameters, headers) is extracted and placed into a `Map<String, Object>`.
    2.  This map becomes the initial input for the **entire workflow** defined in `flow.json`.
    3.  The `WorkflowManager` executes the complete sequence of steps.
    4.  The final result from the last step in the workflow is returned as the HTTP response.

## Example Usage

Ensure the `cre-service` application is running using either packaged or development mode instructions above.

### Triggering a Dynamic Endpoint

The `flow.json` is pre-configured with a `rest-http-request-step`:
```json
{
  "steps": [
    {
      "id": "httprequest",
      "type": "rest-http-request-step",
      "config": {
        "url": "/cre/xml",
        "method": "POST"
      }
    },
    {
      "id": "logger",
      "type": "logging-step",
      "config": {}
    }
  ]
}
```
This will dynamically register a `POST` endpoint at `/cre/xml`.

1.  **Send a POST request:**
    ```bash
    curl -X POST -H "Content-Type: application/json" -d '{"key": "value", "data": 123}' http://localhost:8080/cre/xml
    ```

2.  **Expected Behavior:**
    *   The request hits the dynamically registered `/cre/xml` endpoint in `CreResource`.
    *   The `WorkflowManager` executes the flow:
        *   The `rest-http-request-step` plugin's `execute` method is called. It processes the input and its config. (Currently, it adds `restPluginProcessed: true` and `configurationUsed` to the data map).
        *   The output of the `rest-http-request-step` becomes the input for the `logging-step`.
        *   The `LoggingStepPlugin`'s `execute` method is called, logging the received data map (which includes `restPluginProcessed: true`) to STDERR in red.
    *   The final output of the workflow (the map returned by `logging-step`) is sent as the HTTP response.
    *   You should see console output from `LoggingStepPlugin`, similar to:
        ```
        LoggingStepPlugin: Executing with input -> {key=value, data=123, restPluginProcessed=true, configurationUsed={url=/cre/xml, method=POST}}, config -> {}
        ```
        (Note: The ANSI red color might not render correctly in all terminals/log viewers. The exact map content in the log will reflect the data passed through the workflow.)

### Triggering the Generic `/cre/execute` Endpoint

This JAX-RS endpoint in `CreResource` can be used to execute the entire workflow defined in `flow.json` with an arbitrary JSON payload as the initial input.

1.  **Send a POST request:**
    ```bash
    curl -X POST -H "Content-Type: application/json" -d '{"initialKey": "initialValue", "anotherProp": 42}' http://localhost:8080/cre/execute
    ```

2.  **Expected Behavior:**
    *   The `WorkflowManager` executes the flow defined in `flow.json` starting with `{"initialKey": "initialValue", "anotherProp": 42}`.
    *   The `rest-http-request-step` will execute first, modifying the data.
    *   The `logging-step` will then execute, logging the data it receives from the previous step.
    *   The HTTP response will be the final data map returned by the `logging-step`.
    *   Console output from `LoggingStepPlugin` will show the data after processing by `rest-http-request-step`.
```
