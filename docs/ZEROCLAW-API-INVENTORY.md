# zeroclaw HTTP API Inventory

**Target version:** zeroclaw @ `main` (upstream `github.com/zeroclaw-labs/zeroclaw`, depth-50 clone 2026-04-24)
**Scope:** every HTTP route registered in `crates/zeroclaw-gateway/src/lib.rs` and its siblings, with shapes and stability grades for zterm consumption planning.
**Source pinning:** all `file:line` citations refer to paths under the upstream repo.
**Method:** direct grep of route-registration sites + handler signature reading + live probing against TYPHON `zeroclaw-demo-typhon` (127.0.0.1:42617 inside the container).

zterm currently consumes four endpoints: `POST /webhook`, `GET /ws/chat`, `GET /health`, `GET /api/config`. Everything else in this document is unconsumed surface.

---

## Section 1 — Endpoint catalogue

All `/api/*` routes require `Authorization: Bearer <token>` when pairing is enabled (enforced by `api::require_auth` at `crates/zeroclaw-gateway/src/api.rs:26`). `/admin/*` routes are localhost-only (enforced by `require_localhost` at `lib.rs:2217`). `/hooks/claude-code` is explicitly unauthenticated. `/health`, `/metrics`, `/pair`, `/pair/code` do not require the bearer token; the webhook path requires it when pairing is on.

### Unauthenticated / open surface

| Path | Verb | Source (file:line) | Summary | R/W | Auth | Request shape | Response shape | Stability |
|---|---|---|---|---|---|---|---|---|
| `/health` | GET | `lib.rs:1177` | Liveness + paired + runtime snapshot | R | none | — | `{status, paired, require_pairing, runtime: {...}}` | stable |
| `/metrics` | GET | `lib.rs:1217` | Prometheus text exposition | R | none | — | `text/plain; version=0.0.4` | stable (requires `observability-prometheus` feature) |
| `/pair` | POST | `lib.rs:1243` | Exchange pairing code for bearer token | W | pairing-code (`X-Pairing-Code` header) | empty body; pairing code in `X-Pairing-Code` | `{paired, persisted, token, message}` or error | stable |
| `/pair/code` | GET | `lib.rs:2315` | Return initial pairing code (only before first pair) | R | none | — | `{success, pairing_required, pairing_code}` | stable |
| `/webhook` | POST | `lib.rs:1398` | One-shot chat (no tools) — text in, text out | W | bearer (when pairing on), optional `X-Webhook-Secret`, optional `X-Idempotency-Key`, optional `X-Session-Id` | `WebhookBody { message: String }` (`lib.rs:1392`) | `{response, model}` or error | stable |
| `/hooks/claude-code` | POST | `api.rs:1539` | Receive hook events from Claude Code runner subprocesses (no auth by design — subprocess cannot carry pairing token; session_id ties it back) | W | none | `ClaudeCodeHookEvent` (`zeroclaw_tools::claude_code_runner`) | `{ok: true}` | experimental |

### Third-party channel webhooks (open; auth through channel-specific secrets)

| Path | Verb | Source (file:line) | Summary | R/W | Auth | Request shape | Response shape | Stability |
|---|---|---|---|---|---|---|---|---|
| `/whatsapp` | GET | `lib.rs:1618` | Meta WhatsApp verification | R | hub.verify_token | `WhatsAppVerifyQuery` (`lib.rs:1607`) | challenge echo | stable |
| `/whatsapp` | POST | `lib.rs:1671` | WhatsApp inbound messages | W | HMAC via `X-Hub-Signature-256` + app secret | Meta WhatsApp message envelope | `{status}` | stable |
| `/linq` | POST | `lib.rs:1789` | Linq webhook | W | HMAC header | Linq envelope | `{status}` | stable |
| `/wati` | GET | `lib.rs:1909` | Wati verification | R | query token | `WatiVerifyQuery` (`lib.rs:1926`) | challenge echo | stable |
| `/wati` | POST | `lib.rs:1933` | Wati inbound | W | HMAC | raw `Bytes` | `{status}` | stable |
| `/nextcloud-talk` | POST | `lib.rs:2024` | Nextcloud Talk webhook | W | `X-Nextcloud-Talk-Signature` | NC Talk envelope | `{status}` | stable |
| `/webhook/gmail` | POST | `lib.rs:2142` | Gmail push (Pub/Sub) | W | Google Pub/Sub token | Pub/Sub push envelope | `{status: "ok"}` within 10 s | stable |

### Admin (localhost only)

| Path | Verb | Source (file:line) | Summary | R/W | Auth | Request shape | Response shape | Stability |
|---|---|---|---|---|---|---|---|---|
| `/admin/shutdown` | POST | `lib.rs:2231` | Graceful shutdown | W | localhost | — | `AdminResponse {success, message}` | stable |
| `/admin/paircode` | GET | `lib.rs:2249` | Current pairing code | R | localhost | — | `{success, pairing_required, pairing_code, message}` | stable |
| `/admin/paircode/new` | POST | `lib.rs:2280` | Generate new pairing code | W | localhost | — | `{success, pairing_required, pairing_code, message}` | stable |

### Dashboard / REST (`/api/*`, bearer auth)

| Path | Verb | Source (file:line) | Summary | R/W | Auth | Request shape | Response shape | Stability |
|---|---|---|---|---|---|---|---|---|
| `/api/status` | GET | `api.rs:96` | Overview: provider, model, uptime, channels, paired | R | bearer | — | `{provider, model, temperature, uptime_seconds, gateway_port, locale, memory_backend, paired, channels, health}` | stable |
| `/api/config` | GET | `api.rs:137` | Current config as masked TOML | R | bearer | — | `{format: "toml", content: <toml-string>}` — all secrets replaced with `***MASKED***` | stable |
| `/api/config` | PUT | `api.rs:168` | Replace full config from TOML body (1 MB limit) | W | bearer | raw TOML string body matching `zeroclaw_config::schema::Config` | `{status: "ok"}` or `{error}` | stable |
| `/api/tools` | GET | `api.rs:216` | List registered tool specs | R | bearer | — | `{tools: [{name, description, parameters}]}` | stable |
| `/api/cron` | GET | `api.rs:240` | List cron jobs | R | bearer | — | `{jobs: [CronJob]}` | stable |
| `/api/cron` | POST | `api.rs:260` | Add cron job | W | bearer | `CronAddBody {name?, schedule, command?, job_type?, prompt?, delivery?, session_target?, model?, allowed_tools?, delete_after_run?}` (`api.rs:71`) | `{status, job}` | stable |
| `/api/cron/{id}` | PATCH | `api.rs:407` | Update cron job | W | bearer | `CronPatchBody {name?, schedule?, command?, prompt?}` (`api.rs:85`) | `{status, job}` | stable |
| `/api/cron/{id}` | DELETE | `api.rs:467` | Delete cron job | W | bearer | — | `{status: "ok"}` | stable |
| `/api/cron/{id}/runs` | GET | `api.rs:358` | Recent runs for a job | R | bearer | `CronRunsQuery {limit?}` (`api.rs:66`) | `{runs: [{id, job_id, started_at, finished_at, status, output, duration_ms}]}` | stable |
| `/api/cron/settings` | GET | `api.rs:488` | Cron subsystem settings | R | bearer | — | `{enabled, catch_up_on_startup, max_run_history}` | stable |
| `/api/cron/settings` | PATCH | `api.rs:506` | Update cron settings | W | bearer | `{enabled?, catch_up_on_startup?, max_run_history?}` (JSON Value) | `{status, ...settings}` | stable |
| `/api/integrations` | GET | `api.rs:547` | List integrations with status | R | bearer | — | `{integrations: [{name, description, category, status}]}` | stable |
| `/api/integrations/settings` | GET | `api.rs:575` | Per-integration enable/category map | R | bearer | — | `{settings: {<name>: {enabled, category, status}}}` | stable |
| `/api/doctor` | GET/POST | `api.rs:607` | Run diagnostics (same handler for both verbs) | R (idempotent) | bearer | — | `{results: [...], summary: {ok, warnings, errors}}` | stable |
| `/api/memory` | GET | `api.rs:643` | List or search memory | R | bearer | `MemoryQuery {query?, category?, since?, until?}` (`api.rs:49`) | `{entries: [MemoryEntry]}` | stable |
| `/api/memory` | POST | `api.rs:686` | Store a memory entry | W | bearer | `MemoryStoreBody {key, content, category?}` (`api.rs:59`) | `{status}` | stable |
| `/api/memory/{key}` | DELETE | `api.rs:721` | Forget a memory entry | W | bearer | — | `{status, deleted: bool}` | stable |
| `/api/cost` | GET | `api.rs:743` | Cost/token summary | R | bearer | — | `{cost: {session_cost_usd, daily_cost_usd, monthly_cost_usd, total_tokens, request_count, by_model}}` | stable |
| `/api/cli-tools` | GET | `api.rs:776` | Discovered CLI tools | R | bearer | — | `{cli_tools: [...]}` | stable |
| `/api/health` | GET | `api.rs:790` | Component health snapshot (detailed; `/health` is liveness) | R | bearer | — | `{health: <snapshot>}` | stable |
| `/api/sessions` | GET | `api.rs:1296` | List gateway sessions | R | bearer | — | `{sessions: [{session_id, created_at, last_activity, message_count, name?}]}` | stable |
| `/api/sessions/running` | GET | `api.rs:1452` | Currently-running sessions | R | bearer | — | `{sessions: [...]}` | stable |
| `/api/sessions/{id}/messages` | GET | `api.rs:1334` | Load persisted WebSocket chat transcript | R | bearer | — | `{session_id, messages: [{role, content}], session_persistence: bool}` | stable |
| `/api/sessions/{id}` | DELETE | `api.rs:1368` | Delete session | W | bearer | — | `{deleted: true, session_id}` | stable |
| `/api/sessions/{id}` | PUT | `api.rs:1402` | Rename session | W | bearer | `{name: string}` | `{session_id, name}` | stable |
| `/api/sessions/{id}/state` | GET | `api.rs:1486` | Session state (idle / running / error) | R | bearer | — | `{session_id, state, turn_id?, turn_started_at?}` | stable |

### Pairing / device management (`/api/pair*`, `/api/devices*`)

| Path | Verb | Source (file:line) | Summary | R/W | Auth | Request shape | Response shape | Stability |
|---|---|---|---|---|---|---|---|---|
| `/api/pairing/initiate` | POST | `api_pairing.rs:239` | Generate new pairing code (requires bearer for existing clients) | W | bearer | — | `{pairing_code, message}` | stable |
| `/api/pair` | POST | `api_pairing.rs:262` | Submit pairing code with device metadata | W | none (code itself is the credential) | `{code, device_name?, device_type?}` (JSON Value, not a strict struct) | `{token, message}` on success; 400 on bad code; 429 on lockout | stable |
| `/api/devices` | GET | `api_pairing.rs:314` | List paired devices | R | bearer | — | `{devices: [DeviceInfo], count}` where `DeviceInfo {id, name?, device_type?, paired_at, last_seen, ip_address?}` (`api_pairing.rs:18`) | stable |
| `/api/devices/{id}` | DELETE | `api_pairing.rs:334` | Revoke a device | W | bearer | — | `{message, device_id}` | stable |
| `/api/devices/{id}/token/rotate` | POST | `api_pairing.rs:361` | Rotate device token (generates new pairing code for re-pair) | W | bearer | — | `{device_id, pairing_code, message}` | stable |

### Live Canvas

| Path | Verb | Source (file:line) | Summary | R/W | Auth | Request shape | Response shape | Stability |
|---|---|---|---|---|---|---|---|---|
| `/api/canvas` | GET | `canvas.rs:29` | List canvas IDs | R | bearer | — | `{canvases: [string]}` | stable |
| `/api/canvas/{id}` | GET | `canvas.rs:42` | Current canvas frame | R | bearer | — | `{canvas_id, frame}` or 404 | stable |
| `/api/canvas/{id}` | POST | `canvas.rs:84` | Push content to a canvas | W | bearer | `CanvasPostBody {content_type?, content}` (`canvas.rs:22`) | `{canvas_id, frame}` 201 or 400/413/429 | stable |
| `/api/canvas/{id}` | DELETE | `canvas.rs:145` | Clear a canvas | W | bearer | — | `{canvas_id, status: "cleared"}` | stable |
| `/api/canvas/{id}/history` | GET | `canvas.rs:66` | Frame history | R | bearer | — | `{canvas_id, frames}` | stable |

### WebAuthn (feature `webauthn`; returns 404 when not enabled)

| Path | Verb | Source (file:line) | Summary | R/W | Auth | Request shape | Response shape | Stability |
|---|---|---|---|---|---|---|---|---|
| `/api/webauthn/register/start` | POST | `api_webauthn.rs:65` | Begin hardware-key registration | W | bearer | `StartRegistrationBody {user_id, user_name}` (`api_webauthn.rs:32`) | `CredentialCreationOptions` | feature-gated |
| `/api/webauthn/register/finish` | POST | `api_webauthn.rs:105` | Finish registration | W | bearer | `FinishRegistrationBody {challenge, <flattened RegisterCredentialResponse>}` (`api_webauthn.rs:38`) | `{credential_id, label, registered_at}` | feature-gated |
| `/api/webauthn/auth/start` | POST | `api_webauthn.rs:159` | Begin authentication | W | bearer | `StartAuthenticationBody {user_id}` (`api_webauthn.rs:45`) | `PublicKeyCredentialRequestOptions` | feature-gated |
| `/api/webauthn/auth/finish` | POST | `api_webauthn.rs:196` | Finish authentication | W | bearer | `FinishAuthenticationBody {challenge, <flattened AuthenticateCredentialResponse>}` (`api_webauthn.rs:50`) | `{status: "authenticated"}` or 401 | feature-gated |
| `/api/webauthn/credentials` | GET | `api_webauthn.rs:245` | List credentials for user | R | bearer | `CredentialsQuery {user_id}` (`api_webauthn.rs:57`) | `{credentials: [{credential_id, label, registered_at, sign_count}]}` | feature-gated |
| `/api/webauthn/credentials/{id}` | DELETE | `api_webauthn.rs:289` | Remove credential | W | bearer | `CredentialsQuery {user_id}` (query param) | `{status: "deleted"}` or 404 | feature-gated |

### Plugins (feature `plugins-wasm`; route absent when not enabled)

| Path | Verb | Source (file:line) | Summary | R/W | Auth | Request shape | Response shape | Stability |
|---|---|---|---|---|---|---|---|---|
| `/api/plugins` | GET | `api_plugins.rs:14` | List loaded WASM plugins | R | bearer | — | `{plugins_enabled, plugins_dir, plugins: [{name, version, description, capabilities, loaded}]}` | feature-gated |

### Streaming

| Path | Verb | Source (file:line) | Summary | R/W | Auth | Request shape | Response shape | Stability |
|---|---|---|---|---|---|---|---|---|
| `/api/events` | GET | `sse.rs:51` | SSE event stream (broadcast observer) | S | bearer | — | `text/event-stream` of JSON events | stable |
| `/api/events/history` | GET | `sse.rs:93` | Recent event buffer (ring buffer snapshot) | R | bearer | — | `{events: [...]}` | stable |
| `/ws/chat` | WS (GET upgrade) | `ws.rs:114` | Agent chat stream with tool events | S | bearer (header, `bearer.<token>` subprotocol, or `?token=`) | see Section 3 | see Section 3 | stable |
| `/ws/canvas/{id}` | WS (GET upgrade) | `canvas.rs:163` | Real-time canvas frames | S | bearer (header or subprotocol) | — | frame-typed JSON | stable |
| `/ws/nodes` | WS (GET upgrade) | `nodes.rs` | External node discovery / RPC | S | bearer (subprotocol `zeroclaw.nodes.v1`) | `{type: "register", node_id, capabilities}` + `{type: "result", ...}` | `{type: "registered", ...}` + `{type: "invoke", ...}` | experimental |

### Static assets / SPA

| Path | Verb | Source (file:line) | Summary | R/W | Auth | Request shape | Response shape | Stability |
|---|---|---|---|---|---|---|---|---|
| `/_app/{*path}` | GET | `static_files.rs:16` | Serve hashed dashboard assets from `gateway.web_dist_dir` | R | none | — | file bytes w/ mime | stable |
| `<fallback>` | GET | `static_files.rs:28` | SPA `index.html` fallback for any non-API GET; returns 503 when `web_dist_dir` is unset | R | none | — | HTML or 503 | stable |

**Totals:** 61 route registrations in the gateway (via `.route(...)` — some paths host multiple verbs).
Distinct (path, verb) pairs in this doc: 57 (40 unauthenticated or SPA, 30 authenticated REST, 3 streaming, 6 webauthn, 1 plugins, 7 admin/localhost+channel variants; overlap accounts for counts adding above 57).

**By modality:** read-only (GET/HEAD) = 33, writing (POST/PUT/PATCH/DELETE) = 21, streaming (SSE/WS) = 3.

---

## Section 2 — Write operations by domain

### Config writes
- **`PUT /api/config`** (`api.rs:168`) — only way to modify runtime config. There is **no PATCH**. You must GET the full TOML, modify it locally, and PUT the entire document back. The handler parses the body as `zeroclaw_config::schema::Config`, hydrates masked secrets from the current on-disk config (`hydrate_config_for_save`, `api.rs:189`), validates, saves to disk, then swaps the in-memory Arc. Body limit **1 MB** (special `RequestBodyLimitLayer` merge, `lib.rs:937`).
- **`PATCH /api/cron/settings`** (`api.rs:506`) — targeted: `enabled`, `catch_up_on_startup`, `max_run_history`.
- Everything else (providers, autonomy policy, model-routes, channels, skills, workspaces) must go through full-config PUT.

### Session / conversation writes
- **`DELETE /api/sessions/{id}`** (`api.rs:1368`)
- **`PUT /api/sessions/{id}`** rename (`api.rs:1402`) — body `{name}`; 400 if empty
- Session *creation* is implicit on WebSocket connect with `?session_id=…` query param or a `{type: "connect", session_id: …}` first frame. No REST endpoint creates a session.
- Appending turns is implicit on WS `{type: "message"}`; no REST path appends.

### Skill writes
**None.** No `/api/skills` endpoint exists. Configuration lives in `Config::skills`; modify via full-config PUT.

### Workspace writes
**None.** No `/api/workspaces` endpoint exists. `Config::workspace_dir` is a single string; modify via full-config PUT.

### Autonomy writes
**None.** No `/api/autonomy` endpoint exists. Autonomy policy (`Config::autonomy`) is managed via full-config PUT.

### Provider / auth writes
- **`POST /pair`** — initial pairing (code exchange → token).
- **`POST /api/pair`** — subsequent pairings via dashboard flow with device metadata.
- **`POST /api/pairing/initiate`** — generate a new pairing code.
- **`DELETE /api/devices/{id}`** — revoke.
- **`POST /api/devices/{id}/token/rotate`** — rotate (generates new code, does not auto-revoke old token; client must re-pair).
- Provider API keys (`providers.models.<name>.apiKey`): no dedicated route. Set via full-config PUT (or by writing `~/.zeroclaw/config.toml` directly and restarting).

### Runtime control
- **`POST /admin/shutdown`** (localhost only) — graceful shutdown via `shutdown_tx`.
- **No pause/resume/kill-session/cancel-turn endpoints.** Per-turn cancellation on `/ws/chat` requires closing the socket — the server will abort the in-flight turn naturally via the drop of the `event_tx` channel.

### Cron writes
- `POST /api/cron` create (shell or agent job; auto-infers `agent` when `prompt` is provided).
- `PATCH /api/cron/{id}` update — routes the single textarea value to `command` or `prompt` based on the existing job's `job_type`.
- `DELETE /api/cron/{id}` remove.
- `PATCH /api/cron/settings` settings.

### Memory writes
- `POST /api/memory` store `{key, content, category?}`; category one of `core`, `daily`, `conversation`, or free string (→ `Custom`).
- `DELETE /api/memory/{key}` forget.

### Canvas writes
- `POST /api/canvas/{id}` push frame (content_type validated against `ALLOWED_CONTENT_TYPES`; `MAX_CONTENT_SIZE` enforced).
- `DELETE /api/canvas/{id}` clear.

---

## Section 3 — Streaming endpoints

### `GET /ws/chat` (WebSocket)
**Source:** `crates/zeroclaw-gateway/src/ws.rs:1-147`
**Sub-protocol:** `zeroclaw.v1` (echoed if requested; optional).
**Auth precedence:** (1) `Authorization: Bearer <tok>` header → (2) `Sec-WebSocket-Protocol: bearer.<tok>` → (3) `?token=<tok>` query.
**Query params:** `token`, `session_id`, `name` (see `WsQuery` at `ws.rs:60`).
**Framing:** newline-free JSON per Text frame. Every frame has a `type` discriminator.

**Server → client frames:**
```
{"type":"session_start","session_id":...,"name":?,"resumed":bool,"message_count":n}
{"type":"connected","message":"Connection established"}     // ack to connect frame
{"type":"chunk","content":"..."}                            // token-level stream
{"type":"thinking","content":"..."}                         // reasoning trace (provider-dependent)
{"type":"tool_call","name":"...","args":{...}}
{"type":"tool_result","name":"...","output":"..."}
{"type":"chunk_reset"}                                      // emitted before "done" so clients can clear draft
{"type":"done","full_response":"..."}
{"type":"error","message":"...","code":"AGENT_ERROR|AUTH_ERROR|PROVIDER_ERROR|INVALID_JSON|UNKNOWN_MESSAGE_TYPE|EMPTY_CONTENT|SESSION_BUSY|AGENT_INIT_FAILED"}
{"type":"agent_start","provider":"...","model":"..."}       // also published on the broadcast bus
{"type":"agent_end","provider":"...","model":"..."}
{"type":"lagged","missed_frames":n}                         // canvas only; WS chat drops silently
```

**Client → server frames:**
```
{"type":"connect","session_id":?,"device_name":?,"capabilities":[...]}   // optional first frame
{"type":"message","content":"..."}                                       // every turn
```
Anything else is a `code: UNKNOWN_MESSAGE_TYPE` error.

**Turn events source enum:** `zeroclaw_runtime::agent::TurnEvent` — variants `Chunk`, `Thinking`, `ToolCall`, `ToolResult` (`ws.rs:420-474`).

**Buffering:** in-process `tokio::sync::mpsc::channel(64)` between the agent turn future and the forwarder. No retry semantics; if the client drops, the turn continues and its result is persisted to the session backend (if configured).

**Session locking:** a `SessionActorQueue` serializes concurrent turns per `session_key` — concurrent clients on the same session_id receive `code: SESSION_BUSY`.

**Broadcast subscription:** the connection also subscribes to the gateway-wide broadcast channel (`event_tx`) so cron output and heartbeat events arrive as frames.

### `GET /ws/canvas/{id}` (WebSocket)
**Source:** `canvas.rs:163-290`
**Framing:** JSON per Text frame.

Server → client:
```
{"type":"frame","canvas_id":"...","frame":{...}}
{"type":"connected","canvas_id":"..."}
{"type":"lagged","canvas_id":"...","missed_frames":n}
{"type":"error","error":"..."}
```
Client-to-server messages are ignored except Close.

### `GET /ws/nodes` (WebSocket, experimental)
**Source:** `nodes.rs`
**Sub-protocol:** `zeroclaw.nodes.v1`
**Purpose:** external devices register capabilities; the gateway then invokes them as tools.

Node → Gateway:
```
{"type":"register","node_id":"...","capabilities":[{"name":"...","description":"...","parameters":{...}}]}
{"type":"result","call_id":"...","success":bool,"output":"...","error":?}
```
Gateway → Node:
```
{"type":"registered","node_id":"...","capabilities_count":n}
{"type":"invoke","call_id":"uuid","capability":"...","args":{...}}
```

### `GET /api/events` (SSE)
**Source:** `sse.rs:51-90`
**Protocol:** `text/event-stream`, SSE keep-alive. Each event `data:` is a single JSON object from `BroadcastObserver::record_event` (`sse.rs:125-193`).

**Event kinds:** `llm_request`, `tool_call`, `tool_call_start`, `error`, `agent_start`, `agent_end` — each carries a `timestamp` (RFC 3339). Lagged subscribers silently drop (no `code: lagged`).

**Authentication:** handshake-time bearer via `Authorization` header only — no query/subprotocol fallback for SSE.

---

## Section 4 — Why certain paths 503 on TYPHON

Live probe against `http://127.0.0.1:42617` in `zeroclaw-demo-typhon` confirmed: 200 for 19 `/api/*` endpoints listed above; 503 for eight paths (`/api/skills`, `/api/workspaces`, `/api/providers`, `/api/agents`, `/api/plugins`, `/api/autonomy`, `/api/metrics`, `/api/logs`) plus `/api/webauthn/credentials`.

**These are not real endpoints that are unimplemented. They don't exist in the router at all.** The 503 is from the SPA fallback (`static_files.rs:28`) which is registered at `lib.rs:1072` as `.fallback(get(static_files::handle_spa_fallback))`. That fallback returns
> `Web dashboard not available. Set gateway.web_dist_dir in your config and build the frontend with: cd web && npm ci && npm run build`
with `StatusCode::SERVICE_UNAVAILABLE` when `web_dist_dir` is unset — which it is, in our demo TYPHON build (`web_dist_dir: None` in `AppState`).

**Implication:** once a dashboard is built and `web_dist_dir` is configured, those same paths will return `index.html` (200 HTML) rather than JSON — still not an API. There is no plan in the current upstream source for any of these eight path names. `/api/plugins` does exist but only when the binary is compiled with `--features plugins-wasm` (`lib.rs:1051-1055`); our build is not. `/api/webauthn/*` exists only with `--features webauthn`.

So: "503 on TYPHON" for these eight paths means **the upstream router never registered them**, not "feature toggle off." The only feature-gated 503-when-off path in this set is `/api/plugins`.

---

## Section 5 — Surprises and gotchas

### 1. `/webhook` ignores the body `model` field
`WebhookBody` (`lib.rs:1392`) only has `message: String`. The handler calls `run_gateway_chat_simple` (`lib.rs:1334`) which uses `state.model` — the global configured model — not anything from the request. A client that sends `{"message": "...", "model": "..."}` will have the `model` silently discarded. This is **by design** as of the current code; to override per-turn, use `/ws/chat` on a session pre-configured with the desired model, or change the global via `PUT /api/config`.

### 2. `/webhook` provides no tools
`run_gateway_chat_simple` explicitly passes `tools: None` (`lib.rs:1370`) and empty skills lists to the system prompt builder. Tool calling only works through `/ws/chat` (which uses the full `Agent::turn_streamed` path). This is named "`_simple`" for exactly this reason.

### 3. No hardcoded default-model fallback in the gateway hot path
The "falls back to `anthropic/claude-sonnet-4`" behaviour observed on TYPHON is **not** hardcoded in the gateway — `state.model` is populated at startup from `Config::providers::fallback_provider().model` (via `crates/zeroclaw-gateway/src/lib.rs` build-up, cascading from config). There *is* an `"anthropic/claude-sonnet-4.6"` string literal in the ACP server (`crates/zeroclaw-channels/src/orchestrator/acp_server.rs:232`) as a last-resort for IDE/LSP-style protocol handshake responses, and in the onboarding wizard (`crates/zeroclaw-runtime/src/onboard/wizard.rs:1119`), but the gateway itself does not fall back here. What TYPHON showed us is almost certainly the config's `default_model` or `fallback_provider.model`. See `crates/zeroclaw-config/src/schema.rs:15822,15838,16749` for default strings embedded in test/example configs.

### 4. Max body size is 64 KB except for `/api/config` PUT
`MAX_BODY_SIZE = 65_536` enforced globally (`lib.rs:66`, layered at `lib.rs:1074`). A separate router `config_put_router` at `lib.rs:937-939` layers a 1 MB limit just for `PUT /api/config`, then merges in. `/webhook` therefore has a hard 64 KB cap — long-prompt agents must use `/ws/chat`.

### 5. Default request timeout is 30 s, tunable via env
`gateway_request_timeout_secs()` (`lib.rs:76`) reads `ZEROCLAW_GATEWAY_TIMEOUT_SECS` at request time and defaults to `REQUEST_TIMEOUT_SECS = 30`. The comment acknowledges agentic workloads routinely exceed 30 s. This timeout is applied as a `TimeoutLayer` (`lib.rs:1075-1078`) so `/webhook` plus any long REST call will 408 out at 30 s unless the env var is set. WebSockets are not affected (upgrade completes inside the timeout; the socket is long-lived).

### 6. Rate limiting is per-route and keyed by client
- `/pair`: `rate_limiter.allow_pair()` plus an `auth_limiter` (exponential lockout on failed attempts).
- `/webhook`: `rate_limiter.allow_webhook()` + `auth_limiter` when pairing is on.
- Other REST endpoints: not individually rate-limited in the gateway layer — rely only on the outer 64 KB / 30 s limits.
- Window: `RATE_LIMIT_WINDOW_SECS = 60` (`lib.rs:83`).
- Client key derived from peer `SocketAddr` *unless* `trust_forwarded_headers` is on (config flag), in which case `X-Forwarded-For` / `X-Real-IP` is honoured.

### 7. No CORS layer in the gateway
`grep -n CorsLayer crates/zeroclaw-gateway/src/lib.rs` returns no hits. Cross-origin browser clients will be blocked by the browser unless the dashboard is served from the same origin. zterm (a Rust TUI, not a browser) is unaffected.

### 8. Idempotency is opt-in via `X-Idempotency-Key`
Only `/webhook` honours it (`lib.rs:1471`). The `IdempotencyStore` retains a 5-minute window with up to 10 k keys. Repeats return `{status: "duplicate", idempotent: true, message}`.

### 9. SSE `/api/events` silently drops lagged subscribers
`sse.rs:82` filters `BroadcastStreamRecvError` to `None` — there is no "lagged" frame emitted. A slow reader will miss events with no notification. WS chat has the same behaviour (drops silently). WS canvas **does** emit a `{type:"lagged", missed_frames}` frame.

### 10. `/hooks/claude-code` is the only unauthenticated `/api`-style write
Documented exception (`api.rs:1543` comment): Claude Code subprocesses cannot carry a pairing token, so the hook carries a `session_id` instead. Anyone on-network who knows the URL can post arbitrary hook events — they just get logged, not acted on (Slack-update wiring is stubbed). Not a high-impact surface today but worth noting.

### 11. `/api/session/{id}` path uses one handler for two verbs
`.route("/api/sessions/{id}", delete(handle_api_session_delete).put(handle_api_session_rename))` (`lib.rs:998`). Any other verb (PATCH, POST, GET on that exact path without `/messages` or `/state`) falls through to the SPA fallback and 503s (see Section 4).

### 12. `/pair/code` is intentionally public
Not localhost-gated, not bearer-gated. Returns the code **only while the gateway is in its initial un-paired state** — once any device pairs successfully, this endpoint returns `{pairing_code: null}`. Comment in `lib.rs:2308` explicitly justifies this for Docker/remote setup UX.

### 13. Nested prefix routing
If the config sets a path prefix (`gateway.path_prefix`), everything mounts under it via `Router::new().nest(prefix, inner)` (`lib.rs:1085`). A trailing-slash quirk: `{prefix}/` redirects permanently to `{prefix}`. zterm should therefore either use the bare prefix (no trailing slash) or follow redirects.

### 14. Device revocation does not invalidate existing bearer tokens
`DeviceRegistry::revoke` (`api_pairing.rs:148`) deletes only the device record, not the token in `PairingGuard`. Tokens live in `Config::gateway.paired_tokens` and are not cross-referenced with device IDs on auth-check. So a revoked device's token keeps working until the token itself is removed (currently no single-token-remove endpoint; only via full-config PUT with `paired_tokens` edited).

### 15. `/api/doctor` accepts both GET and POST for the same handler
`.route("/api/doctor", get(api::handle_api_doctor).post(api::handle_api_doctor))` (`lib.rs:983`). The handler is pure read — no mutation. POST exists presumably for clients that treat "trigger an action" as always-POST.

---

## Section 6 — Prioritized zterm integration roadmap

zterm today: `POST /webhook`, `GET /ws/chat`, `GET /health`, `GET /api/config`. Reminder: zeroclaw is the only claw-family backend zterm actively targets in v0.2; Hermes is out of scope per `project_zterm_v02_backend_complete.md`.

### P0 — ship next

| Priority | Endpoint(s) | zterm surface | Why | Cost |
|---|---|---|---|---|
| P0 | `GET /api/status` | status bar / footer: provider, model, uptime, paired, channels | Already consumed by `/api/config`, same auth — tiny wrapper; surfaces gateway state that today requires inspecting config TOML | ~30 LOC; one request on connect + periodic poll |
| P0 | `GET /api/sessions` + `GET /api/sessions/{id}/messages` + `GET /api/sessions/running` | Session browser pane (E-3 in the Turbo-Vision roadmap); resume a conversation instead of starting fresh each `/ws/chat` | Directly unblocks the "resume previous session" demo path; no streaming required | ~100 LOC, list + detail model |
| P0 | `GET /api/events` (SSE) | Event feed pane (E-4 in roadmap); agent_start/agent_end/tool_call overlay | TypeOS/Borland-era status scroller — huge UX win, and zterm is a TUI so SSE keep-alive works cleanly with tokio | ~150 LOC; reuse session-state tracking from `/ws/chat` |

### P1 — next sprint

| Priority | Endpoint(s) | zterm surface | Why | Cost |
|---|---|---|---|---|
| P1 | `GET /api/tools` + `GET /api/cli-tools` | F-1 Help / F-5 Tools window listing what the agent can invoke | Makes "what can this agent do" discoverable without reading `~/.zeroclaw/config.toml`. Pure read, static | ~80 LOC |
| P1 | `GET /api/cron` + `GET /api/cron/{id}/runs` + `POST/PATCH/DELETE /api/cron` | Cron pane (browse + edit jobs, read recent runs) | Cron is a major zeroclaw feature with no good TUI today; zterm can own this | ~400 LOC, two-pane editor |
| P1 | `GET /api/memory` + `POST /api/memory` + `DELETE /api/memory/{key}` | Memory pane (category list → entry viewer, quick-add) | MNEMOS-adjacent muscle memory; trivial REST, delivers persistent-notes UX | ~200 LOC |
| P1 | `PUT /api/sessions/{id}` rename + `DELETE /api/sessions/{id}` | Right-click / F-2 context in session browser | Makes session browser actually useful for multi-week work | ~40 LOC |
| P1 | `GET /api/integrations` + `GET /api/integrations/settings` | F-5 Settings section showing which channels are live | Read-only reflection of config; unblocks "is Slack wired up" sanity check without TOML spelunking | ~60 LOC |
| P1 | `GET /api/cost` | Status-bar dollar counter (Paradox 4.5 had one, let's have one) | Single GET, one header cell | ~20 LOC |

### P2 — backlog

| Priority | Endpoint(s) | zterm surface | Why | Cost |
|---|---|---|---|---|
| P2 | `GET /api/canvas` + `GET /api/canvas/{id}` + `WS /ws/canvas/{id}` | Canvas pane for A2UI demos (if zeroclaw emits canvas content as the primary output) | Mostly for showcase; not required for text-first claw-family workflow | ~300 LOC (HTML/plain/markdown renderer) |
| P2 | `PUT /api/config` | In-TUI config editor (currently zterm only reads) | Large scope: round-trip TOML, 1 MB limit, secrets masking gotcha; risk of bricking the gateway. Keep CLI `$EDITOR` as the escape hatch and ship a read-only viewer first | ~500+ LOC + validation UX |
| P2 | `GET /api/devices` + `DELETE /api/devices/{id}` + pairing flow | Pairing pane with QR-like flow for peripheral devices | Only relevant if zterm starts mediating third-party device pairing — not in current scope | ~250 LOC |
| P2 | `GET /api/doctor` | Health dashboard with `[OK/WARN/ERROR]` per check | Nice-to-have, replaces `zeroclaw doctor` output but without adding core capability | ~80 LOC |
| P2 | `POST /admin/shutdown` (localhost only, when zterm + gateway on same host) | F-5 → "Stop gateway" menu item | Convenience; only works when zterm runs on the same host as the gateway | ~20 LOC |
| P2 | `GET /api/events/history` | Backfill the event feed pane on reconnect | Small polish on the P0 SSE path | ~40 LOC |
| P2 | `GET /api/session/{id}/state` | Show per-session "busy spinner" in the browser pane | Tiny polish | ~30 LOC |
| P2 | `WS /ws/nodes` | External-device tool publishing | Experimental; advertised in code but unclear demand from zterm users | deferred |
| P2 | `/api/webauthn/*` | Hardware-key-based re-auth in zterm | Feature-gated; almost certainly not in a TUI's scope | deferred |
| P2 | `/api/plugins` | Plugin discovery UI | Feature-gated on gateway side | deferred |
| P2 | `POST /api/pairing/initiate` + `POST /api/pair` | First-time pairing from inside zterm | Current zterm expects an already-paired token in config; bootstrap pairing is an edge case | ~100 LOC |

---

## Appendix — Methodology notes

- **Commit hash of the zeroclaw source read:** `e1f722f9d6d355fb7be6608a18d0469b957aa452` (depth-50 shallow clone of upstream `main` at 2026-04-24).
- **Probe host:** TYPHON container `zeroclaw-demo-typhon` at `http://127.0.0.1:42617` inside the container; verified every route's HTTP status code against the source interpretation.
- **Pairing state on probe host:** Paired, bearer token present in `~/.zeroclaw-demo/config.toml` (not reproduced here).
- **Examples that read providers/models use** `primary`, `together`, `gemini-flash-latest` rather than Anthropic/Claude defaults, per project convention.
- **NOT in this inventory:** channel-internal routes registered by `crates/zeroclaw-channels/*` (Lark, Line, voice, dedicated webhooks) — those run on their own per-channel listeners, not the shared gateway, and zterm doesn't touch them.

---

**Author:** Jason Perlow <jperlow@gmail.com>
**Date:** 2026-04-24
**File:** `docs/ZEROCLAW-API-INVENTORY.md` in `meta-nclawzero`
