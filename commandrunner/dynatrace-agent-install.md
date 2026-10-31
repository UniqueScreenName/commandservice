# Dynatrace Agent Install Guide: EC2, Fargate, Lambda

> Placeholders: `<ENV>` = environment ID, `<TOKEN>` = API token, `<REGION>` = AWS region.
> Flag names, image tags, and layer ARNs vary by OneAgent version. Verify against your tenant's Deploy page and `oneagentctl --help`.

## Quick comparison

| | EC2 | ECS Fargate | Lambda |
|---|---|---|---|
| Monitoring level | Full-stack (host + deep app) | App-only (code modules) | App-only (layer) |
| Delivery | Installer per instance | Init/sidecar container copying code modules to a shared volume | Dynatrace Lambda layer |
| Host metrics | Yes | No | No |
| Auto-injection | Yes, at process start | No, via `LD_PRELOAD` env var | Yes, via exec wrapper |
| Host group / host tags | Yes | No (use `DT_*` env vars) | No (use env vars) |

---

## 1. EC2 (full-stack)

### Prerequisites
- API token with `InstallerDownload` scope. Store it in Secrets Manager or SSM Parameter Store, never in user data.
- Instance profile with read access to that secret (and to S3 if you stage the installer there).
- Outbound HTTPS to your tenant or ActiveGate.

### Install flow
1. Fetch the installer (Dynatrace API, S3, or your repo manager). Pin the version and verify the signature/checksum.
2. Run the installer with your settings.
3. Start the apps **after** the install completes. Injection happens at process start, so JVMs already running must be restarted.

### Example user data
```bash
#!/bin/bash
set -euo pipefail

TOKEN=$(aws secretsmanager get-secret-value --secret-id dynatrace/installer-token \
  --query SecretString --output text)

curl -fsSL -o /tmp/oneagent.sh \
  -H "Authorization: Api-Token ${TOKEN}" \
  "https://<ENV>.live.dynatrace.com/api/v1/deployment/installer/agent/unix/default/latest?arch=x86"
# Verify signature per Dynatrace docs before running.

/bin/sh /tmp/oneagent.sh \
  --set-monitoring-mode=fullstack \
  --set-host-group=prod-orders \
  --set-host-tag=env=prod \
  --set-host-property=costcenter=1234 \
  --set-host-property=appname=orders

# Start apps only after the agent is installed
systemctl restart openliberty springboot-app
```

### Installer / `oneagentctl` options

| Option | Purpose |
|---|---|
| `--set-server`, `--set-tenant`, `--set-tenant-token` | Connection (pre-filled in Deploy-page installers) |
| `--set-proxy` | Proxy address |
| `--set-host-group=<name>` | Host group (one per host; affects process group identity) |
| `--set-network-zone=<zone>` | Network zone |
| `--set-monitoring-mode=fullstack\|infra-only\|discovery` | Monitoring mode |
| `--set-host-name=<name>` | Custom host name |
| `--set-host-id-source=<...>` | Host identity source (important for cloned AMIs) |
| `--set-host-tag=<key>=<value>` | Host tag (repeatable) |
| `--set-host-property=<key>=<value>` | Host metadata, e.g. cost center, app name (repeatable) |
| `--set-app-log-content-access=true\|false` | Log content access |
| `--set-system-logs-access-enabled=true\|false` | System log access |
| `--set-auto-update-enabled=true\|false` | Auto-update |
| `--set-watchdog-portrange=<from>:<to>` | Watchdog ports |
| `INSTALL_PATH=`, `LOG_PATH=`, `DATA_STORAGE=` | Directories (installer args) |

Only the connection details are effectively required, and the Deploy-page installer already includes them.

Post-install checks: `oneagentctl --get-monitoring-mode`, `--get-host-tags`, `--get-host-properties`. Logs: `/var/lib/dynatrace/oneagent/log`.

### Process group and service naming (set on the app, not the installer)
Set on the JVM process, e.g. via systemd `Environment=`:

| Variable | Purpose |
|---|---|
| `DT_CLUSTER_ID` | Process group name |
| `DT_NODE_ID` | Process instance name |
| `DT_APPLICATION_ID` | Application ID |
| `DT_TAGS` | Process/service tags (space-separated `key=value`) |
| `DT_CUSTOM_PROP` | Custom process properties |

Alternative: tenant-side process group detection rules, which avoid touching service files.

### Notes
- If baking an AMI, **do not start OneAgent before creating the image**, or clones may share a host identity.
- SSM State Manager is a good alternative to user data because it can re-apply and upgrade after launch.
- Cost center on services: use `DT_TAGS`, or auto-tagging rules derived from host properties.
- Do not share one OneAgent install across hosts (e.g., via EFS). It is not supported for full-stack.

---

## 2. ECS Fargate (app-only, sidecar/init pattern)

Fargate gives no host access, so use the OneAgent **code modules** via an init container that copies the agent into a shared volume.

### Prerequisites
- API token with `InstallerDownload` scope to pull the code-modules image (or mirror it to ECR).
- Tenant token and connection point (from the Deploy API connection info endpoint or the Deploy page).
- Secrets in Secrets Manager, injected via the task definition `secrets` block.

### Task definition outline
1. **Shared volume:** a bind-mount volume (e.g., `oneagent`) mounted in both containers.
2. **Init container** (`essential: false`):
   - Image: `<ENV>.live.dynatrace.com/linux/oneagent-codemodules:java` (or your ECR mirror).
   - Copies the agent files into the shared volume, then exits. Use the exact command from the Dynatrace Fargate docs for your version.
3. **App container:**
   - `dependsOn` the init container with condition `SUCCESS`.
   - Mounts the shared volume (read-only is fine), e.g. at `/opt/dynatrace/oneagent`.
   - Environment variables:

```json
[
  {"name": "LD_PRELOAD", "value": "/opt/dynatrace/oneagent/agent/lib64/liboneagentproc.so"},
  {"name": "DT_TENANT", "value": "<ENV>"},
  {"name": "DT_CONNECTION_POINT", "value": "https://<ENV>.live.dynatrace.com/communication"},
  {"name": "DT_CLUSTER_ID", "value": "orders-service"},
  {"name": "DT_TAGS", "value": "env=prod costcenter=1234 appname=orders"}
]
```
   - Secret: `DT_TENANTTOKEN` from Secrets Manager.

### Notes
- This is **app-only**: traces, services, and request data for injected processes. No host or OS metrics.
- No host group or `oneagentctl`. Use `DT_*` variables and tenant-side rules for naming and tags.
- Match the code-modules technology tag (`java`, etc.) to your runtime, and the architecture (x86/ARM) to the task's CPU architecture.
- Mirror the image into ECR to avoid pulling from Dynatrace at every task start.

---

## 3. Lambda (layer)

### Steps
1. In the tenant, use **Deploy OneAgent → AWS Lambda** to get the layer ARN and configuration for your region, runtime, and architecture (x86_64 or arm64).
2. Add the layer to the function (console, IaC, or CLI).
3. Set the environment variables from the wizard. Typical ones:

| Variable | Purpose |
|---|---|
| `AWS_LAMBDA_EXEC_WRAPPER` | Points at the Dynatrace wrapper (value from the wizard, e.g. `/opt/dynatrace`) |
| `DT_TENANT` | Environment ID |
| `DT_CLUSTER_ID` | Cluster ID from the wizard |
| `DT_CONNECTION_BASE_URL` | Tenant URL |
| `DT_CONNECTION_AUTH_TOKEN` | Ingest token. Store in Secrets Manager or SSM and fetch securely rather than as plaintext |

4. Ensure the function has outbound access to the tenant. In a VPC without NAT, use an ActiveGate or a VPC endpoint-based route.

### Notes
- Traces and function-level data only. No host layer exists.
- Layer ARNs and variable names change between layer versions, so always copy from the wizard.
- Add tags to functions (cost center, app name) at the AWS level and ingest them via the AWS integration if you want them in Dynatrace.

---

## Choosing the right mode

- Need host metrics plus deep Java monitoring → **EC2 + full-stack**.
- No host access (Fargate, Lambda) → **app-only code modules or layer**.
- Hosts you only want visibility on → `--set-monitoring-mode=infra-only`.
- Switching modes needs no reinstall (`oneagentctl --set-monitoring-mode=...`), but **restart apps** so injection changes take effect.
