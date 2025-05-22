package org.cre.service;

import io.quarkus.runtime.StartupEvent;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import org.cre.library.workflow.manager.exceptions.InvalidFlowConfigurationException;
import org.pf4j.PluginWrapper;
import java.io.IOException;
import java.util.HashMap;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.cre.library.workflow.manager.WorkflowManager;
import org.cre.library.workflow.manager.WorkflowStep;
import org.pf4j.DefaultPluginManager;
import org.pf4j.PluginManager;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@ApplicationScoped
@Path("/cre") // Base path for JAX-RS, dynamic routes will be absolute or relative to root
public class CreResource {

    private final WorkflowManager workflowManager;
    private final PluginManager pluginManager;

    @Inject
    Router router; // Inject Vert.x Router

    public CreResource() { // Constructor-based initialization
        // This tells PF4J to look for a 'plugins' directory relative to where the app is running.
        // When packaged, this will be target/quarkus-app/plugins/
        // When running in dev mode (e.g. mvn quarkus:dev), it looks for cre-project-final/services/cre-service/plugins
        // So we might need two strategies or ensure dev mode also has plugins in the right spot.
        // For dev mode, plugins are siblings: ../../plugins relative to target/classes
        
        String pluginsDirProperty = System.getProperty("pf4j.pluginsDir");
        if (pluginsDirProperty == null || pluginsDirProperty.isEmpty()) {
            // Default for packaged app: ./plugins
            // Default for quarkus:dev: needs to be ../../plugins relative to services/cre-service/target/classes
            // Or an absolute path.
            // A common strategy is to have quarkus:dev copy them too.
             if (new File("target/quarkus-app").exists()) { // Likely packaged mode
                System.setProperty("pf4j.pluginsDir", "plugins");
             } else { // Likely dev mode or test
                // For `mvn quarkus:dev` from the `cre-service` module root, or `mvn compile quarkus:dev` from parent.
                // The `pluginManager` by default will create a `plugins` folder in the current working dir if it doesn't exist.
                // Or we can point it to where the built plugin JARs are.
                // The parent pom builds modules, so plugins jars are in their respective target folders.
                // e.g. ../../plugins/logging-step/target/logging-step-1.0-SNAPSHOT.jar
                // This is complex for dev mode without a build step that places them centrally.
                // Simplest for now: expect plugins to be copied to `services/cre-service/plugins` manually or by a profile for dev.
                // For now, let DefaultPluginManager use its default (current working dir + "plugins")
                // Or point it to the *source* of plugins if running in IDE and built by parent.
                // Let's use a simpler path for now and document dev mode requirements.
                 System.setProperty("pf4j.pluginsDir", "plugins"); // Expects ./plugins relative to execution
                 System.out.println("Using 'plugins' directory for PF4J. Ensure it's populated for dev mode (e.g., in services/cre-service/plugins).");
            }
        }
        // Initialize pluginManager with no explicit path to use system property or PF4J default ("plugins")
        this.pluginManager = new DefaultPluginManager(); 
        pluginManager.loadPlugins();
        pluginManager.startPlugins();

        // Construct map of active WorkflowStep plugins (pluginId -> instance)
        Map<String, WorkflowStep> activeWorkflowSteps = new HashMap<>();
        List<PluginWrapper> startedPlugins = pluginManager.getStartedPlugins();

        if (startedPlugins.isEmpty()) {
            System.err.println("No plugins were started by PF4J.");
        } else {
            System.out.println("Found " + startedPlugins.size() + " started plugins.");
            for (PluginWrapper wrapper : startedPlugins) {
                String pluginId = wrapper.getPluginId();
                System.out.println("Checking plugin for WorkflowStep extensions: " + pluginId);
                // Note: getExtensions(extensionClass, pluginId) is the correct PF4J method
                List<WorkflowStep> extensions = pluginManager.getExtensions(WorkflowStep.class, pluginId);
                if (!extensions.isEmpty()) {
                    // Assuming one WorkflowStep extension per plugin for simplicity
                    activeWorkflowSteps.put(pluginId, extensions.get(0));
                    System.out.println("Found and mapped WorkflowStep from plugin: " + pluginId);
                } else {
                    System.out.println("No WorkflowStep extensions found in plugin: " + pluginId);
                }
            }
        }
        
        if (activeWorkflowSteps.isEmpty()) {
            System.err.println("No active WorkflowStep plugins found/mapped!");
        } else {
            System.out.println("Total active WorkflowStep plugins mapped: " + activeWorkflowSteps.size());
        }


        String flowJsonPath = findFlowJsonPath();
        System.out.println("flow.json path: " + flowJsonPath);

        this.workflowManager = new WorkflowManager(activeWorkflowSteps, flowJsonPath);
        try {
            this.workflowManager.loadFlow();
            System.out.println("WorkflowManager initialized and flow.json loaded and validated.");
        } catch (InvalidFlowConfigurationException | IOException e) {
            System.err.println("FATAL: Failed to load or validate flow.json: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("CreResource initialization failed: Invalid or unreadable flow configuration.", e);
        } catch (Exception e) { // Catch other potential runtime exceptions during loadFlow
            System.err.println("FATAL: Unexpected error during WorkflowManager initialization: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("CreResource initialization failed due to unexpected error.", e);
        }
    }

    // Method to setup dynamic routes based on flow.json
    void setupDynamicRoutes(@Observes StartupEvent ev) { // Router is injected as a field
        System.out.println("Setting up dynamic routes...");
        List<Map<String, Object>> flowSteps = workflowManager.getFlowSteps();

        if (flowSteps == null || flowSteps.isEmpty()) {
            System.err.println("No flow steps available from WorkflowManager to create dynamic routes.");
            return;
        }

        for (Map<String, Object> stepDefinition : flowSteps) {
            String type = (String) stepDefinition.get("type");
            if ("rest-http-request-step".equals(type)) {
                Map<String, Object> config = (Map<String, Object>) stepDefinition.get("config");
                if (config != null && config.containsKey("url") && config.containsKey("method")) {
                    String urlPath = (String) config.get("url");
                    String httpMethod = ((String) config.get("method")).toUpperCase();

                    System.out.println("Registering dynamic route: " + httpMethod + " " + urlPath);

                    switch (httpMethod) {
                        case "POST":
                            router.post(urlPath).handler(this::handleDynamicRoute);
                            break;
                        case "GET":
                            router.get(urlPath).handler(this::handleDynamicRoute);
                            break;
                        // Add other methods like PUT, DELETE as needed
                        default:
                            System.err.println("Unsupported HTTP method for dynamic route: " + httpMethod + " at path " + urlPath);
                            break;
                    }
                }
            }
        }
    }

    private void handleDynamicRoute(RoutingContext routingContext) {
        Map<String, Object> inputMap = new java.util.HashMap<>();

        // Extract body for POST/PUT, etc.
        if (routingContext.body() != null && routingContext.body().asJsonObject() != null) {
            inputMap.putAll(routingContext.body().asJsonObject().getMap());
        }

        // Extract query parameters for GET or if present in other methods
        routingContext.queryParams().forEach(entry -> inputMap.put(entry.getKey(), entry.getValue()));

        // Extract path parameters
        routingContext.pathParams().forEach(inputMap::put);
        
        // Add request headers to the input map, prefixing with "header_"
        routingContext.request().headers().forEach(entry -> inputMap.put("header_" + entry.getKey(), entry.getValue()));


        try {
            // The dynamic route corresponds to a specific step in the workflow.
            // However, the current WorkflowManager.execute runs the *entire* flow.
            // For true single-step execution via dynamic routes, WorkflowManager would need
            // a method like executeStep(stepId, inputMap, stepConfig)
            // For now, we execute the whole flow as per current WorkflowManager capability.
            Object result = workflowManager.execute(inputMap);
            routingContext.response()
                          .putHeader("content-type", "application/json")
                          .end(Json.encodePrettily(result));
        } catch (Exception e) {
            e.printStackTrace();
            routingContext.response()
                          .setStatusCode(500)
                          .putHeader("content-type", "application/json")
                          .end(new JsonObject().put("error", "Workflow execution failed: " + e.getMessage()).encodePrettily());
        }
    }

    // Keep the generic /execute endpoint for now
    @POST
    @Path("/execute")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response executeWorkflow(Map<String, Object> input) {
        try {
            Object result = workflowManager.execute(input);
            return Response.ok(result).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                           .entity("{\"error\":\"Workflow execution failed: " + e.getMessage() + "\"}")
                           .build();
        }
    }

    private String findFlowJsonPath() {
        try {
            URL flowJsonUrl = Thread.currentThread().getContextClassLoader().getResource("flow.json");
            if (flowJsonUrl != null) {
                 return Paths.get(flowJsonUrl.toURI()).toString();
            }

            URL resource = getClass().getClassLoader().getResource("flow.json");
             if (resource != null) {
                return Paths.get(resource.toURI()).toString();
            }
            
            File f = new File("src/main/resources/flow.json");
             if (f.exists() && f.isFile()){
                 System.out.println("Found flow.json via direct file access: " + f.getAbsolutePath());
                 return f.getAbsolutePath();
             }
             // Attempt relative path from project root if in dev/test environment
             File projectRootFlow = new File("../../../src/main/resources/flow.json"); // Adjust based on typical CWD
             if (projectRootFlow.exists() && projectRootFlow.isFile()){
                  System.out.println("Found flow.json via relative path: " + projectRootFlow.getAbsolutePath());
                 return projectRootFlow.getAbsolutePath();
             }


            throw new IllegalStateException("flow.json not found via ClassLoader, direct file access, or relative path.");

        } catch (URISyntaxException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to load flow.json due to URI syntax error", e);
        }
    }
}
