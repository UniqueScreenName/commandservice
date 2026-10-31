package ca.gc.customs.observability.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/** Parses a command document and runs its commands in order. Used by every REST front end. */
public class CommandRunner {

    private static final Logger LOG = Logger.getLogger(CommandRunner.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * @param json           request body: {"commands": [...]} or a bare array
     * @param requestHeaders incoming request headers
     * @return what to send back: 400 for a bad document, otherwise whatever the commands produce
     *         (200 "OK" if none of them stops execution)
     */
    public static CommandResult run(String json, Map<String, String> requestHeaders) {
        JsonNode entries;
        List<Command> commands = new ArrayList<Command>();
        try {
            JsonNode root = MAPPER.readTree(json);
            entries = root.isArray() ? root : root.path("commands");
            if (!entries.isArray()) {
                return CommandResult.respond(400, "Expected a 'commands' array");
            }
            for (JsonNode entry : entries) {
                commands.add(CommandFactory.create(entry));
            }
        } catch (IllegalArgumentException e) {
            return CommandResult.respond(400, e.getMessage());
        } catch (Exception e) {
            return CommandResult.respond(400, "Invalid JSON: " + e.getMessage());
        }

        Map<String, String> forwardHeaders = correlationHeaders(requestHeaders);
        for (int i = 0; i < commands.size(); i++) {
            LOG.info("Executing " + entries.get(i).path("command").asText()
                    + ": " + entries.get(i).path("description").asText());
            try {
                CommandResult result = commands.get(i).execute(forwardHeaders);
                if (result.finished) {
                    return result;
                }
            } catch (Exception e) {
                return CommandResult.respond(500, "Command failed: " + e);
            }
        }
        return CommandResult.respond(200, "OK");
    }

    /**
     * Only plain correlation IDs are copied to downstream calls. A tracing agent (OpenTelemetry,
     * Dynatrace) adds its own trace headers to outgoing HttpURLConnection calls. If you run
     * without an agent, also copy "traceparent" here.
     */
    private static Map<String, String> correlationHeaders(Map<String, String> incoming) {
        Map<String, String> result = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> h : incoming.entrySet()) {
            String name = h.getKey();
            if (name.equalsIgnoreCase("x-request-id") || name.equalsIgnoreCase("x-correlation-id")) {
                result.put(name, h.getValue());
            }
        }
        return result;
    }
}
