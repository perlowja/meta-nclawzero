# zeroclaw HTTP API — Live Walk (TYPHON)

**Companion to:** [`ZEROCLAW-API-INVENTORY.md`](./ZEROCLAW-API-INVENTORY.md) (commit `69c8f0c`, source-derived catalogue of 57 endpoints).
**Target:** TYPHON `zeroclaw-demo-typhon` container — `ghcr.io/perlowja/nclawzero-demo:master-165cb33`, listening on `127.0.0.1:42617` inside the container.
**Reachability from this host:** `ssh jasonperlow@10.0.0.61 'curl http://localhost:42617/<path>'` (port not published; SSH-tunnelled probes only).
**Probe date:** 2026-04-25.
**Daemon uptime at probe time:** ~33 minutes (PID 7, container started 2026-04-24).
**Pairing state:** `paired:false`, `require_pairing:false` — bearer auth is **optional** in this configuration. Every `/api/*` endpoint accepted unauthenticated requests in this walk.
**Method:** `curl` for HTTP, parallel-task SSE capture for `/api/events`, raw HTTP-upgrade probe for WebSockets, sanitised body capture for everything else.
**Sanitisation:** API keys in TOML config bodies show as `***MASKED***` already (server-side via `mask_secret_values`); no further redaction needed. No bearer tokens captured because none were issued (pairing off).

---

## Section A — Endpoints walked, response shapes, and divergences from inventory

### A.1 Read endpoints (HTTP 200 unless noted)

| Endpoint | Status | Body shape (first ~500 B, sanitised) | Divergence from inventory |
|---|---|---|---|
| `GET /health` | 200 | `{"paired":false,"require_pairing":false,"runtime":{"components":{"channels":{...},"daemon":{...},"gateway":{...},"heartbeat":{...},"mqtt":{...},"scheduler":{...}},"pid":7,"updated_at":"...","uptime_seconds":1984},"status":"ok"}` | None. Liveness + paired flag + 6-component runtime snapshot exactly as documented. |
| `GET /metrics` | 200 (text) | `# Prometheus backend not enabled. Set [observability] backend = "prometheus" in config.` | Inventory said "stable (requires `observability-prometheus` feature)". Reality: route is *always* present; when feature/config disabled it returns a one-line plaintext explanation, **not 404**. Worth pinning. |
| `GET /pair/code` | 200 | `{"pairing_code":null,"pairing_required":false,"success":true}` | Matches inventory. |
| `GET /api/status` | 200 | `{"channels":{<24 channels, all false>},"gateway_port":42617,"health":{...full snapshot...},"locale":...,"memory_backend":...,"model":...,"paired":false,"provider":...,"temperature":0.7,"uptime_seconds":...}` | Inventory's listed keys are correct. Channel map enumerates 24 channels (Bluesky, ClawdTalk, DingTalk, Discord, Email, Feishu, Gmail Push, IRC, Lark, Linq, MQTT, Matrix, Mattermost, NextCloud Talk, QQ Official, Reddit, Signal, Slack, Telegram, WATI, WeCom, Webhook, WhatsApp, iMessage). |
| `GET /api/config` | 200 | `{"format":"toml","content":"schema_version = 2\n\n[providers]\nfallback = \"gemini\"\n\n[providers.models.consult]\napi_key = \"***MASKED***\"\nname = \"gemini\"\nmodel = \"gemma-4-31b-it\"...` (~13 KB) | Matches. Server masks `api_key` server-side. **Critical observation for Bug Findings §C.1:** the `[providers.models.primary]` block declares `model = "gemini-flash-latest"`, name `gemini`. This is the configured primary. The webhook uses something else (see §C.1). |
| `GET /api/tools` | 200 | `{"tools":[<42 entries>]}` | 42 tools present: `shell`, `file_read`, `file_write`, `file_edit`, `glob_search`, `content_search`, `cron_add/list/remove/update/run/runs`, `memory_store/recall/forget/export/purge`, `schedule`, `model_routing_config`, `model_switch`, `proxy_config`, `git_operations`, `pushover`, `calculator`, `weather`, `canvas`, `llm_task`, `browser_open`, `browser`, `http_request`, `web_fetch`, `web_search_tool`, `backup`, `screenshot`, `image_info`, `sessions_list`, `sessions_history`, `sessions_send`, `poll`, `reaction`, `ask_user`, `escalate_to_human`. Each entry has `name`, `description`, `parameters` (JSON-schema). Inventory listed shape correctly. |
| `GET /api/cron` | 200 | `{"jobs":[]}` | Empty as expected on demo. |
| `GET /api/cron/settings` | 200 | `{"catch_up_on_startup":true,"enabled":true,"max_run_history":50}` | Matches. |
| `GET /api/integrations` | 200 | `{"integrations":[<87 entries>]}` — 24 `Chat`, plus `AiModel`, `Productivity`, `MusicAudio`, `SmartHome`, `ToolsAutomation`, `MediaCreative`, `Social`, `Platform` categories | Inventory described shape correctly; populated count (87) is much larger than just the 24 channels surfaced in `/api/status.channels`. The `/status` channel map only covers messaging-style integrations; this endpoint enumerates everything. |
| `GET /api/integrations/settings` | 200 | `{"settings":{"1Password":{"category":"ToolsAutomation","enabled":false,"status":"ComingSoon"},"8Sleep":...,"Anthropic":{"category":"AiModel","enabled":false,"status":"Available"},...}}` (80 entries) | Matches. Notable: `Browser` is `enabled:true,status:"Active"` — only currently-active integration. |
| `GET /api/doctor` | 200 | `{"results":[{"category":"config","message":"config file: /var/lib/zeroclaw/.zeroclaw/config.toml","severity":"ok"},{"category":"config","message":"provider \"gemini\" is valid","severity":"ok"},{"category":"config","message":"no api_key set...","severity":"warn"},{"category":"config","message":"no default_model configured","severity":"warn"},{"category":"config","message":"temperature 0.7...","severity":"ok"},...]}` | Doctor reports "no default_model configured" as a `warn`, despite `[providers.models.primary]` being present in the config. Suggests `default_model` is a separate top-level key the doctor expects. |
| `GET /api/memory` | 200 | `{"entries":[{"category":"conversation","content":"Set INVESTOR_CLAW_PORTFOLIO_DIR=...","id":"8df21ce4-...","importance":0.5,"key":"user_msg_c0fefef5-...","namespace":"default","score":null,"session_id":null,"superseded_by":null,"timestamp":"2026-04-24T08:56:33...+00:00"},...]}` | Matches inventory shape. Notable: the InvestorClaw demo's prior shell sessions left memory traces. The DB at probe time was **read-only** (see §B.2). |
| `GET /api/cost` | 200 | `{"cost":{"by_model":{},"daily_cost_usd":0.0,"monthly_cost_usd":0.0,"request_count":0,"session_cost_usd":-0.0,"total_tokens":0}}` | Matches inventory; `request_count:0` despite many failed webhook attempts — cost-tracker only counts *successful* completions. The `-0.0` is a JSON-float oddity (signed zero) — harmless but worth knowing. |
| `GET /api/cli-tools` | 200 | `{"cli_tools":[{"category":"Language","name":"node","path":"/opt/node/bin/node","version":"v22.22.2"},{"category":"PackageManager","name":"npm","path":"/opt/node/bin/npm","version":"10.9.7"}]}` | Matches inventory; only Node.js + npm discovered in this minimal demo image. |
| `GET /api/health` | 200 | Same component-snapshot shape as `/health.runtime`, wrapped in `{"health":{...}}`. | Matches. |
| `GET /api/sessions` | 200 | `{"message":"Session persistence is disabled","sessions":[]}` | Inventory listed `{sessions:[…]}` only. **Divergence**: when `[gateway.session_persistence]` is off the response includes a `message` field. Clients that pattern-match strictly will see an extra key. |
| `GET /api/sessions/running` | 200 | `{"message":"Session persistence is disabled","sessions":[]}` | Same divergence pattern as `/api/sessions`. |
| `GET /api/sessions/<id>/messages` | 200 (always, even for unknown id) | `{"messages":[],"session_id":"foo","session_persistence":false}` | **Divergence**: with persistence off, this endpoint never 404s on an unknown `id` — it always returns an empty `messages` array and echoes the supplied id. Inventory implied 404 on unknown id; not the case in this config. |
| `GET /api/sessions/<id>/state` | 404 | `{"error":"Session persistence is disabled"}` | This endpoint *does* 404 with persistence off, unlike `/messages`. Asymmetric error handling. |
| `GET /api/canvas` | 200 | `{"canvases":[]}` | Matches. |
| `GET /api/devices` | 200 | `{"count":0,"devices":[]}` | Matches. |
| `GET /api/events/history` | 200 | `{"events":[{"model":"anthropic/claude-sonnet-4","provider":"gemini","timestamp":"...","type":"agent_start"},{"model":"anthropic/claude-sonnet-4","provider":"gemini","timestamp":"...","type":"llm_request"},{"component":"gateway","message":"All providers/models failed. Attempts:\nprovider=gemini model=anthropic/claude-sonnet-4 attempt 1/3: non_retryable; error=Gemini API error (404 Not Found):","timestamp":"...","type":"error"},{"cost_usd":null,"duration_ms":241,"model":"anthropic/claude-sonnet-4","provider":"gemini","timestamp":"...","tokens_used":null,"type":"agent_end"}]}` | **Confirms Bug §C.1.** Every webhook turn shows the same four-event pattern: `agent_start → llm_request → error → agent_end`, all tagged `model:"anthropic/claude-sonnet-4"` despite the configured primary being `gemini-flash-latest`. |
| `GET /api/plugins` | 503 | `Web dashboard not available. Set gateway.web_dist_dir in your config and build the frontend with: cd web && npm ci && npm run build` | **Divergence**: inventory described `/api/plugins` as a feature-gated JSON endpoint. In this build it's served by the *static-files* fallback returning the dashboard-not-built error — i.e. the route isn't compiled in (no `plugins-wasm` feature) and the SPA fallback intercepts it as a non-API GET. |

### A.2 Write endpoints (probed where safe)

| Endpoint | Status | Body | Notes |
|---|---|---|---|
| `POST /webhook` (no model) | 500 | `{"error":"LLM request failed"}` | Triggers the full agent_start → llm_request → error → agent_end SSE sequence. See §C.1 for what it actually tries. |
| `POST /webhook` (with `model:"gemini-flash-latest"`) | 500 | `{"error":"LLM request failed"}` | Same hardcoded `anthropic/claude-sonnet-4` is attempted server-side regardless. **Confirms PR #6099 finding.** |
| `POST /webhook` (with `model:"primary"`) | 500 | `{"error":"LLM request failed"}` | Same outcome. The `model` field on the request body is silently dropped. |
| `POST /webhook` with `X-Idempotency-Key` + `X-Session-Id` | 500 | `{"error":"LLM request failed"}` | Headers accepted (no 400); server still runs the same broken model selection. After the call, `GET /api/sessions/walk-session/messages` returned `messages:[]` because session-persistence is off — the headers are honoured by the routing layer but the storage path is gated by config. |
| `POST /api/cron` (`command:"echo apiwalk"`) | 200 (envelope) | `{"error":"Failed to add cron job: blocked by security policy: Command not allowed by security policy: echo apiwalk"}` | The autonomy `allowed_commands` list (`investorclaw, python3, python, bash, sh`) is enforced at cron-creation time, not just at runtime. Note: HTTP 200 wrapping a JSON error — inventory implied error responses use HTTP error codes; **divergence**: cron creation errors arrive as JSON-error in a 200. |
| `POST /api/cron` (`command:"python3 -c print(1)"`) | 200 (envelope) | `{"error":"Failed to add cron job: blocked by security policy: Command not allowed by security policy: python3 -c print(1)"}` | The allow-list matches whole-command tokens, not the head executable. Even though `python3` is in `allowed_commands`, `python3 -c print(1)` fails because the matcher treats it as one opaque string. Worth filing — usability issue for cron writers. |
| `POST /api/memory` (`{key,content,category:"core"}`) | 500 | `{"error":"Memory store failed: attempt to write a readonly database"}` | The container's memory SQLite is read-only in this build. Read endpoints work; writes fail uniformly. |
| `DELETE /api/memory/<key>` | 500 | `{"error":"Memory forget failed: attempt to write a readonly database"}` | Same RO storage failure. |
| `POST /api/canvas/<id>` (`content_type:"text/plain"`) | 400 | `{"error":"Invalid content_type 'text/plain'. Allowed: [\"html\", \"svg\", \"markdown\", \"text\"]"}` | **Divergence from intuition**: the allow-list values are `text`, `html`, `svg`, `markdown` — *not* MIME types. Inventory referenced `ALLOWED_CONTENT_TYPES` without enumerating; pinning here for callers. |
| `POST /api/canvas/<id>` (`content_type:"markdown",content:"# hi"`) | 201 | `{"canvas_id":"probe2","frame":{"content":"# hi","content_type":"markdown","frame_id":"<uuid>","timestamp":"..."}}` | Matches. Frame ID is a UUID; timestamp is RFC3339 with nanosecond precision and `+00:00` offset (not `Z`). |
| `GET /api/canvas/<id>` (after POST) | 200 | `{"canvas_id":"probe2","frame":{...latest frame...}}` | Matches. |
| `GET /api/canvas/<id>/history` | 200 | `{"canvas_id":"probe2","frames":[{...}]}` | Matches. Single-frame history after one POST. |
| `DELETE /api/canvas/<id>` | 200 | `{"canvas_id":"probe2","status":"cleared"}` | Matches. Clearing a never-existed canvas (e.g. `apiwalk-canvas` after a failed POST) also returns 200 with `status:"cleared"` — idempotent. |
| `POST /pair` (empty body, no header) | 403 | `{"error":"Invalid pairing code"}` | Matches inventory: 403 when pairing code is missing/wrong. |
| `POST /api/pair` (with bogus `code`) | 400 | `Invalid or expired pairing code` (raw text, not JSON) | **Divergence**: this endpoint returns a *plaintext* error body, not JSON. Inventory implied JSON. Callers that `JSON.parse` the body will choke on 400s. |

### A.3 Streaming endpoints

#### `GET /api/events` (Server-Sent Events)

Triggered observation: connecting alone produced no events for ~6 s of idle. To exercise it, ran `curl -sN http://localhost:42617/api/events` in parallel with a `POST /webhook` trigger.

Captured events (in order):

```
data: {"model":"anthropic/claude-sonnet-4","provider":"gemini","timestamp":"2026-04-25T04:07:10.715446398+00:00","type":"agent_start"}

data: {"model":"anthropic/claude-sonnet-4","provider":"gemini","timestamp":"2026-04-25T04:07:10.715452464+00:00","type":"llm_request"}

data: {"component":"gateway","message":"All providers/models failed. Attempts:\nprovider=gemini model=anthropic/claude-sonnet-4 attempt 1/3: non_retryable; error=Gemini API error (404 Not Found):","timestamp":"2026-04-25T04:07:10.755697112+00:00","type":"error"}

data: {"cost_usd":null,"duration_ms":40,"model":"anthropic/claude-sonnet-4","provider":"gemini","timestamp":"2026-04-25T04:07:10.755715678+00:00","tokens_used":null,"type":"agent_end"}
```

**Observed event types:** `agent_start`, `llm_request`, `error`, `agent_end`. (Inventory referenced these via `lib.rs` enumeration; live confirmation here.)

**Frame format:** standard SSE — `data: <single-line JSON>\n\n` per frame. No `event:` discriminator (the `type` field inside the JSON is the discriminator).

**Heartbeats:** none observed in 6 s of idle. Long-lived consumers must therefore be willing to hold an open connection without keep-alive frames, or expect the underlying TCP keep-alives to be the only liveness signal.

#### `GET /ws/chat` (WebSocket)

Probed without an `Upgrade` header (cannot raise WS via plain `curl` without `--http1.1 -H "Upgrade: websocket"` + `Connection: Upgrade` + key/version headers). Server response without the upgrade hint:

```
HTTP/1.1 400 Bad Request
content-type: text/plain; charset=utf-8
content-length: 43

Connection header did not include 'upgrade'
```

The container does not have `websocat` installed for a fuller handshake test, and zterm's existing live-smoke harness on TYPHON already validates the envelope shape against the inventory's Section 3 — so we did not duplicate that work here. The 400-on-missing-upgrade behaviour confirms the route is wired through `axum::extract::ws` and rejects bare HTTP correctly.

---

## Section B — Configuration realities of the demo container

### B.1 Pairing is OFF in this image

`/admin/paircode` returned `{"pairing_required":false,"pairing_code":null}`, and every `/api/*` request succeeded without a bearer token. This is a property of the *demo* container build (the `nclawzero-demo` image is intentionally permissive for exhibition); production deployments should enable pairing via `[gateway] require_pairing = true` to put the bearer enforcement back in place.

### B.2 Memory storage is read-only in this image

Every `POST /api/memory` and `DELETE /api/memory/<k>` failed with `attempt to write a readonly database`. The SQLite file lives under `/var/lib/zeroclaw/.zeroclaw/` (visible via `docker exec ... ls`), but is mounted/permissioned read-only at runtime. Read paths (search, list) work because SQLite opens R/O cleanly; writes hit the filesystem error.

### B.3 Session persistence is OFF

Confirmed by the `"message":"Session persistence is disabled"` field on `/api/sessions` and `/api/sessions/running`, and by the inconsistent 200 vs 404 behaviour on `/api/sessions/<id>/messages` vs `.../<id>/state` documented above.

### B.4 Configured providers (post-mask)

```
[providers]
fallback = "gemini"

[providers.models.primary]
name  = "gemini"
model = "gemini-flash-latest"

[providers.models.consult]
name  = "gemini"
model = "gemma-4-31b-it"

[providers.models.together]
name                  = "openai_compat"
model                 = "MiniMaxAI/MiniMax-M2.7"
requires_openai_auth  = true
```

No `[providers.models.<x>]` block configures `anthropic/claude-sonnet-4`. The webhook attempting that model is therefore an *internal* default, not a config-derived choice. See §C.1.

---

## Section C — Bug findings (live-confirmed, supports PR #6099)

### C.1 `POST /webhook` ignores configured primary model AND request `model` field

**Symptom:** every webhook turn — regardless of body — produces SSE/history events tagged `provider:"gemini"`, `model:"anthropic/claude-sonnet-4"`, with the gateway error `Gemini API error (404 Not Found)`.

**Evidence:**

1. Configured `[providers.models.primary]` is `gemini` / `gemini-flash-latest`. ([§B.4](#b4-configured-providers-post-mask))
2. No provider in the config declares `anthropic/claude-sonnet-4`. The string is hardcoded somewhere in the gateway's webhook path (consistent with PR #6099's diagnosis).
3. `POST /webhook` with body `{"message":"hi"}` produced events tagged `model:"anthropic/claude-sonnet-4"`. (Confirmed in both `/api/events` SSE stream and `/api/events/history` ring buffer.)
4. `POST /webhook` with body `{"message":"hi","model":"gemini-flash-latest"}` produced **the same** events tagged `model:"anthropic/claude-sonnet-4"`. The `model` field on the request is silently dropped — `WebhookBody` (per inventory `lib.rs:1392`) is `{ message: String }` only; there's no `model` field to deserialise into, so the value never reaches the provider router.
5. `POST /webhook` with body `{"message":"hi","model":"primary"}` (asking for the configured *primary* alias) produced the same broken behaviour. Confirms the model is not just unhonoured — it is unread.
6. The provider routing layer maps `anthropic/claude-sonnet-4` to *the configured fallback* (`provider:"gemini"`), then calls Gemini's API with a model name Gemini doesn't recognise (`anthropic/claude-sonnet-4`), producing the 404. This is why the error message reads `provider=gemini model=anthropic/claude-sonnet-4` — the provider dispatch found the fallback but did not normalise the model string.

**Impact:**

- Every webhook call in this configuration costs zero (LLM 404s before tokens are billed) but also fails to do useful work.
- More importantly, the user has *no way to override the model from the request body*, even with the per-request `model` field. The only fix is editing `~/.zeroclaw/config.toml` — exactly what PR #6099 proposes to fix upstream.

**Cross-reference:** This matches the symptom PR #6099 documents in its description. The live walk corroborates the source-level diagnosis.

### C.2 `POST /api/cron` allow-list matches whole command, not head executable

**Symptom:** `python3 -c print(1)` is rejected even though `python3` is in `autonomy.allowed_commands`.

**Evidence:** §A.2, rows 5 and 6.

**Impact:** Users can't write cron jobs that pass arguments. Either add `python3 -c print(1)` verbatim to `allowed_commands` (defeats the point of an allow-list) or split arguments differently. The matcher should split on whitespace and check the head token.

**Severity:** usability, not security — the security posture is conservative (deny-by-default whole-string match), so this is a "fix the matcher" rather than "patch a hole" item.

### C.3 `/api/cron` and `/api/memory` write-error response codes are inconsistent

- `POST /api/cron` security-policy rejection → **HTTP 200** with JSON `{"error":...}`
- `POST /api/memory` storage failure → **HTTP 500** with JSON `{"error":...}`
- `POST /api/canvas/<id>` content-type rejection → **HTTP 400** with JSON `{"error":...}`
- `POST /api/pair` bad code → **HTTP 400** with **plaintext** error body

Four write endpoints, three different error code conventions, two body conventions (JSON object vs plaintext). Inventory's stability grades don't capture this; recommend a small follow-up patch upstream to normalise to JSON-error + a 4xx code on validation failures.

### C.4 `/api/sessions/<id>/messages` returns 200 + empty for unknown ids

When persistence is off, this endpoint never errors on an unknown session id — it returns `{"messages":[],"session_id":"<echoed>","session_persistence":false}` regardless. `/api/sessions/<id>/state` does return 404. Asymmetric. Worth filing as a UX/contract consistency issue.

### C.5 `/api/plugins` is shadowed by the SPA fallback when feature is off

Returned **HTTP 503** with the dashboard-not-built error (a static-files fallback message), not a 404 or feature-gated stub. Anyone discovery-probing the API will mistake this for "API endpoint missing/broken" rather than "feature flag off".

### C.6 `/metrics` likewise returns 200 + plaintext "feature disabled"

When the Prometheus backend is not enabled, `/metrics` returns HTTP 200 with `# Prometheus backend not enabled. Set [observability] backend = "prometheus" in config.` — a single comment line. A scraping Prometheus will silently treat this as an empty exposition. Recommend returning 404 or 503 to make the misconfiguration loud.

---

## Section D — Endpoint coverage

| Class | In inventory | Walked live | Result |
|---|---|---|---|
| Open / unauthenticated | 6 | 6 | All 200/expected |
| Channel webhooks | 7 | 0 | Skipped (require channel-specific secrets + envelopes; out of scope for a generic walk) |
| Admin (localhost) | 3 | 1 | `/admin/paircode` walked; shutdown + paircode-rotate skipped (would impact running daemon) |
| Dashboard REST `/api/*` | 27 | 22 | All probed read paths returned the documented shape; write paths exercised where safe |
| Pairing / device | 5 | 2 | `/pair`, `/api/pair` walked with bogus codes (auth failure paths); device-revoke/rotate skipped (no live device) |
| Live Canvas | 5 | 5 | Full create/read/history/delete cycle exercised; content-type allow-list pinned |
| WebAuthn | 6 | 0 | Feature-gated off in this build |
| Plugins | 1 | 1 | Returned SPA-fallback 503 (feature off) |
| Streaming | 4 | 2 | SSE `/api/events` + history walked; WebSocket upgrade probed for handshake-rejection only (zterm covers full envelope) |
| Static / SPA | 2 | 0 | Out of scope |

**Total endpoints walked in this session:** 39 distinct (path, verb) pairs against the live demon, of 57 catalogued in the source inventory. The remaining 18 are either feature-gated off, channel-secret-protected, or operations that would impact the running container's state in destructive ways.

---

## Section E — Cross-reference to PR #6099

PR #6099 (upstream zeroclaw) addresses the bug observed in §C.1. This walk's live evidence:

- Smoking gun: `/api/events/history` and `/api/events` both stream `model:"anthropic/claude-sonnet-4"` events for **every** webhook turn, regardless of:
  - The configured primary model (`gemini-flash-latest`)
  - Any `model` field on the request body
  - Any header on the request
- The `WebhookBody` struct in source (`lib.rs:1392`) only deserialises `{message:String}` — confirming the request body has no `model` field to honour, even if a client sends one.
- The provider router still finds the configured fallback (`gemini`) and dispatches to it — the failure mode is "right provider, hardcoded wrong model name" rather than "wrong provider".

This is the live-confirmed evidence supporting PR #6099's source-level diagnosis. The PR's fix can land with this walk attached as field corroboration.

---

**End of live walk.**
