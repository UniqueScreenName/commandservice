package ca.gc.customs.observability.commandservice.websphere;

import ca.gc.customs.observability.commands.CommandResult;
import ca.gc.customs.observability.commands.CommandRunner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/")
public class CommandResource {

    @POST
    @Path("process")
    @Produces(MediaType.TEXT_PLAIN)
    public Response process(String body, @Context HttpHeaders httpHeaders) {
        Map<String, String> headers = new HashMap<String, String>();
        for (Map.Entry<String, List<String>> h : httpHeaders.getRequestHeaders().entrySet()) {
            headers.put(h.getKey(), h.getValue().get(0));
        }
        CommandResult result = CommandRunner.run(body, headers);
        return Response.status(result.status).entity(result.message).build();
    }

    @GET
    @Path("health")
    @Produces(MediaType.TEXT_PLAIN)
    public String health() {
        return "OK";
    }
}
