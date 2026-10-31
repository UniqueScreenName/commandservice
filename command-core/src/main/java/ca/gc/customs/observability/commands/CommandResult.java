package ca.gc.customs.observability.commands;

/** Outcome of a command. If finished, execution stops and this becomes the HTTP response. */
public class CommandResult {

    public final int status;
    public final String message;
    public final boolean finished;

    private CommandResult(int status, String message, boolean finished) {
        this.status = status;
        this.message = message;
        this.finished = finished;
    }

    /** Stop and respond with this status and message. */
    public static CommandResult respond(int status, String message) {
        return new CommandResult(status, message, true);
    }

    /** Carry on with the next command. */
    public static CommandResult next() {
        return new CommandResult(200, "", false);
    }
}
