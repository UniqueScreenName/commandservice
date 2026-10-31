package ca.gc.customs.observability.commands;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Locale;

/** Creates a Command from one JSON entry, based on its "command" name. */
public class CommandFactory {

    /** @throws IllegalArgumentException if the entry is invalid */
    public static Command create(JsonNode node) {
        String name = node.path("command").asText("").toLowerCase(Locale.ROOT);
        JsonNode p = node.path("parameters");

        switch (name) {
            case "return":
                if (!p.path("return_code").isInt()) {
                    throw new IllegalArgumentException("'return' needs an integer return_code");
                }
                return new ReturnCommand(p.get("return_code").asInt(), p.path("message").asText(""));

            case "delay":
                if (!p.path("delay_time_in_ms").isNumber()) {
                    throw new IllegalArgumentException("'delay_time_in_ms' needs a numeric delay time (>0) (milliseconds)");
                }
                return new DelayCommand(p.get("delay_time_in_ms").asDouble());

            case "forward":
                if (p.path("url").asText("").isEmpty() || !p.path("payload").isObject()) {
                    throw new IllegalArgumentException("'forward' needs a url and a payload object");
                }
                
                String method=p.path("method").asText("GET").toUpperCase();
                if (method.equals("GET") || method.equals("POST"))  {
                      throw new IllegalArgumentException("'method' must be GET or POST");
                }
                return new ForwardCommand(p.get("url").asText(), method,p.get("payload").toString(),
                        p.path("passthrough").asBoolean(false));

            default:
                throw new IllegalArgumentException("Unknown command: '" + name + "'");
        }
    }
}
