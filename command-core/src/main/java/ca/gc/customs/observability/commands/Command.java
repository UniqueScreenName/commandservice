package ca.gc.customs.observability.commands;

import java.util.Map;

public interface Command {

    /** @param headers request headers to pass on to any downstream calls */
    CommandResult execute(Map<String, String> headers) throws Exception;
}
