# command-runner

Same function, three runtimes. Each exposes `POST /command-app/process` (body: a command document,
see `examples/`) and a health check at `/command-app/health`. Response status = the `return_code`,
body = the `message` (text/plain); invalid documents get 400.

| Module | Stack | Target |
|---|---|---|
| `command-core` | Plain Java 8: Command, 3 commands, CommandFactory, CommandRunner | shared |
| `app-springboot` | Spring Boot 2.7 jar + Dockerfile | AWS Fargate |
| `app-websphere` | Java EE 6 WAR (JAX-RS 1.1) | WebSphere traditional 8.5.5 |
| `app-liberty` | WAR (JAX-RS 2.1) packaged as an Open Liberty runnable jar | EC2 |

## Build
    mvn clean package          # JDK 8

## Deploy
- **Fargate:** `docker build -t command-app app-springboot`, push to ECR, fill in and register
  `app-springboot/deploy/ecs-task-definition.json`. ALB health check path: `/command-app/health`.
- **WebSphere:** `wsadmin.sh -lang jython -f app-websphere/deploy/install_app.py /tmp/command-app.war <node> <server>`
- **EC2 / Liberty:** upload `app-liberty/target/command-app-liberty.jar` to S3 and use
  `app-liberty/deploy/ec2-userdata.sh` as instance user data.

## forward
Parameters: `url`, `payload` (nested command document), `method` (GET or POST), optional `passthrough` (default false).
- default: downstream 4xx/5xx or connection failure stops execution with that error; otherwise the next command runs.
- `passthrough: true`: downstream status and body are returned to the caller whatever the status.

## Tracing
Only `x-request-id` / `x-correlation-id` are copied to downstream calls. An OpenTelemetry or Dynatrace
agent adds trace headers to `HttpURLConnection` calls itself. With no agent, add `traceparent` to the
filter in `CommandRunner.correlationHeaders`.
