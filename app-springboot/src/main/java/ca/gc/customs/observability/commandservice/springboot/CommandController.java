package ca.gc.customs.observability.commandservice.springboot;

import ca.gc.customs.observability.commands.CommandResult;
import ca.gc.customs.observability.commands.CommandRunner;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CommandController {

    @PostMapping("/process")
    public ResponseEntity<String> process(@RequestBody String body,
                                          @RequestHeader Map<String, String> headers) {
        CommandResult result = CommandRunner.run(body, headers);
        return ResponseEntity.status(result.status)
                .contentType(MediaType.TEXT_PLAIN)
                .body(result.message);
    }

    @GetMapping("/health")
    public String health() {
        return "OK";
    }
}
