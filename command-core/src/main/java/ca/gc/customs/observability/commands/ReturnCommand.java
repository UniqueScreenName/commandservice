package ca.gc.customs.observability.commands;

import java.util.Map;

/** Stops and returns a fixed status and message. */
public class ReturnCommand implements Command {

    private final int code;
    private final String message;

    public ReturnCommand(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public CommandResult execute(Map<String, String> headers) {
        return CommandResult.respond(code, message);
    }
}
