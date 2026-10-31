package ca.gc.customs.observability.commands;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.Scanner;

/**
 * POSTs the payload (a nested command document) to another service.
 *
 * Default: a downstream status of 400+ (or a connection failure) stops execution with that
 * error; otherwise the next command runs.
 * With passthrough=true the downstream status and body are returned whatever the status.
 */
public class ForwardCommand implements Command {

    private final String url;
    private final String payload;
    private final boolean passthrough;

    public ForwardCommand(String url, String payload, boolean passthrough) {
        this.url = url;
        this.payload = payload;
        this.passthrough = passthrough;
    }

    public CommandResult execute(Map<String, String> headers) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            for (Map.Entry<String, String> h : headers.entrySet()) {
                conn.setRequestProperty(h.getKey(), h.getValue());
            }
            try (OutputStream out = conn.getOutputStream()) {
                out.write(payload.getBytes("UTF-8"));
            }

            int status = conn.getResponseCode();
            String body = read(status >= 400 ? conn.getErrorStream() : conn.getInputStream());
            if (passthrough || status >= 400) {
                return CommandResult.respond(status, body);
            }
            return CommandResult.next();
        } catch (IOException e) {
            return CommandResult.respond(502, "Forward to " + url + " failed: " + e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String read(InputStream in) {
        if (in == null) {
            return "";
        }
        try (Scanner s = new Scanner(in, "UTF-8")) {
            s.useDelimiter("\\A");
            return s.hasNext() ? s.next() : "";
        }
    }
}
