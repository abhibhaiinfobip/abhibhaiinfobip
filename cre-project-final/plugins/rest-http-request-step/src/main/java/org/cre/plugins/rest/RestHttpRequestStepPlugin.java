package org.cre.plugins.rest;

import org.cre.library.workflow.manager.WorkflowStep;
import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;

import java.util.HashMap;
import java.util.Map;

public class RestHttpRequestStepPlugin extends Plugin {

    public RestHttpRequestStepPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public void start() {
        System.out.println("RestHttpRequestStepPlugin.start()");
        // In the revised plan, this plugin does not register HTTP endpoints itself.
        // CreResource will handle dynamic endpoint creation based on flow.json.
    }

    @Override
    public void stop() {
        System.out.println("RestHttpRequestStepPlugin.stop()");
    }

    @Extension
    public static class RestHttpRequestExecutor implements WorkflowStep {

        @Override
        public Object execute(Map<String, Object> input, Map<String, Object> config) {
            System.out.println("RestHttpRequestStepPlugin: Executing with input -> " + input + ", config -> " + config);

            // In this revised model, the plugin's 'execute' method is called as part of the workflow
            // when a dynamically registered endpoint (managed by CreResource) is hit.
            // This method would contain the logic to process the request data (input)
            // according to its 'config' (e.g., preparing for an external call, data transformation).

            // For now, let's simulate some processing and return an updated map.
            Map<String, Object> output = new HashMap<>(input);
            output.put("restPluginProcessed", true);
            output.put("configurationUsed", config);

            // If this plugin were to make an actual outgoing HTTP call as per its original intent,
            // it would use java.net.http.HttpClient here, configured with details from 'config'.
            // Example:
            // String url = (String) config.get("targetUrl"); // Assuming 'targetUrl' in config
            // if (url != null) {
            //     HttpClient client = HttpClient.newHttpClient();
            //     HttpRequest request = HttpRequest.newBuilder()
            //           .uri(URI.create(url))
            //           .GET() // Or POST, PUT, etc., based on config.get("method")
            //           .build();
            //     try {
            //         HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            //         output.put("externalApiResponse", response.body());
            //     } catch (IOException | InterruptedException e) {
            //         output.put("externalApiError", e.getMessage());
            //         e.printStackTrace();
            //     }
            // }
            return output;
        }
    }
}
