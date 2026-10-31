package ca.gc.customs.observability.commands;

import java.util.Map;

/** Sleeps for a number of seconds, then carries on. */
public class DelayCommand implements Command {

    private final double seconds;

    public DelayCommand(double seconds) {
        this.seconds = seconds;
    }

    public CommandResult execute(Map<String, String> headers) throws InterruptedException {
        Thread.sleep((long) (seconds * 1000));
        return CommandResult.next();
    }
}
