"""
AWS Lambda version of command-runner. Handler: command_lambda.lambda_handler

Runs a command document like {"commands": [...]}:
  return  - stop and respond with return_code / message
  delay   - sleep delay_time seconds, then carry on
  forward - POST a nested command document to another service. A 4xx/5xx response (or a
            connection failure) stops with that error; otherwise carry on.
            With "passthrough": true, the downstream status and body are returned instead.

Works behind API Gateway (REST or HTTP API), a Function URL, or invoked directly with the
command document as the event. Python 3.9+, standard library only.
"""
import base64
import json
import logging
import time
import urllib.error
import urllib.request
from collections import namedtuple

log = logging.getLogger()
log.setLevel(logging.INFO)

FORWARD_TIMEOUT_SECONDS = 30

# finished=True means "stop here and use this as the response"
Result = namedtuple("Result", ["status", "message", "finished"])


def respond(status, message):
    return Result(status, message, True)


def proceed():
    return Result(200, "", False)


# ---- the three commands ---------------------------------------------------------------

class ReturnCommand:
    def __init__(self, code, message):
        self.code = code
        self.message = message

    def execute(self, headers):
        return respond(self.code, self.message)


class DelayCommand:
    def __init__(self, seconds):
        self.seconds = seconds

    def execute(self, headers):
        time.sleep(self.seconds)
        return proceed()


class ForwardCommand:
    def __init__(self, url, payload, passthrough):
        self.url = url
        self.payload = payload
        self.passthrough = passthrough

    def execute(self, headers):
        request = urllib.request.Request(self.url, data=self.payload.encode("utf-8"), method="POST")
        request.add_header("Content-Type", "application/json")
        for name, value in headers.items():
            request.add_header(name, value)

        try:
            with urllib.request.urlopen(request, timeout=FORWARD_TIMEOUT_SECONDS) as response:
                status, body = response.status, response.read().decode("utf-8")
        except urllib.error.HTTPError as e:          # 4xx/5xx is still a response
            status, body = e.code, e.read().decode("utf-8")
        except Exception as e:                       # connection failure, timeout, ...
            return respond(502, "Forward to %s failed: %r" % (self.url, e))

        if self.passthrough or status >= 400:
            return respond(status, body)
        return proceed()


# ---- factory: one JSON entry -> one Command -------------------------------------------

def is_number(value):
    return isinstance(value, (int, float)) and not isinstance(value, bool)


def create_command(entry):
    """Raises ValueError if the entry is invalid."""
    if not isinstance(entry, dict):
        raise ValueError("Each command must be a JSON object")
    name = str(entry.get("command", "")).lower()
    params = entry.get("parameters") or {}
    if not isinstance(params, dict):
        raise ValueError("'parameters' must be an object")

    if name == "return":
        code = params.get("return_code")
        if not isinstance(code, int) or isinstance(code, bool):
            raise ValueError("'return' needs an integer return_code")
        return ReturnCommand(code, str(params.get("message", "")))

    if name == "delay":
        seconds = params.get("delay_time")
        if not is_number(seconds) or seconds < 0:
            raise ValueError("'delay' needs a non-negative numeric delay_time (seconds)")
        return DelayCommand(seconds)

    if name == "forward":
        url, payload = params.get("url"), params.get("payload")
        if not url or not isinstance(payload, dict):
            raise ValueError("'forward' needs a url and a payload object")
        return ForwardCommand(url, json.dumps(payload), params.get("passthrough") is True)

    raise ValueError("Unknown command: '%s'" % name)


# ---- runner ---------------------------------------------------------------------------

def run(body, request_headers):
    """Parse the command document and run it. Returns a Result."""
    if not body:
        return respond(400, "Request body must be a JSON command document")
    try:
        doc = json.loads(body)
        entries = doc if isinstance(doc, list) else (doc.get("commands") if isinstance(doc, dict) else None)
        if not isinstance(entries, list):
            return respond(400, "Expected a 'commands' array")
        commands = [create_command(entry) for entry in entries]
    except ValueError as e:                          # bad JSON or an invalid command
        return respond(400, str(e))

    # Only plain correlation IDs are copied downstream. A tracing agent or layer (OpenTelemetry,
    # Dynatrace, X-Ray) adds its own trace headers; with none, also copy "traceparent" here.
    headers = {k: v for k, v in request_headers.items()
               if k.lower() in ("x-request-id", "x-correlation-id")}

    for entry, command in zip(entries, commands):
        log.info("Executing %s: %s", entry.get("command"), entry.get("description", ""))
        try:
            result = command.execute(headers)
        except Exception as e:
            return respond(500, "Command failed: %r" % e)
        if result.finished:
            return result
    return respond(200, "OK")


# ---- Lambda entry point ---------------------------------------------------------------

def lambda_handler(event, context):
    method = event.get("httpMethod") or event.get("requestContext", {}).get("http", {}).get("method", "")
    path = event.get("path") or event.get("rawPath") or ""

    if method == "GET" and path.endswith("/health"):
        result = respond(200, "OK")
    else:
        if "body" in event:                          # API Gateway / Function URL
            body = event["body"]
            if event.get("isBase64Encoded") and body:
                body = base64.b64decode(body).decode("utf-8")
        else:                                        # direct invoke: the event is the document
            body = json.dumps(event)
        result = run(body, event.get("headers") or {})

    return {
        "statusCode": result.status,
        "headers": {"Content-Type": "text/plain; charset=utf-8"},
        "body": result.message,
    }
