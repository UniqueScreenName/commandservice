package ca.gc.customs.observability.commands;

import java.util.Map;

/** Sleeps for a number of seconds, then carries on. */
public class DelayCommand implements Command {

    private final double delay_time_in_ms;

    public DelayCommand(double milliseconds) {
        this.delay_time_in_ms = milliseconds;
    }

    public CommandResult execute(Map<String, String> headers) throws InterruptedException {
        Thread.sleep((long) (delay_time_in_ms));
        return CommandResult.next();
    }
}
