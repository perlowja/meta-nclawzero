# zeroclaw Windows setup doc — review against current master

**Doc URL:** https://singlerider.github.io/zeroclaw/en/setup/windows.html
**Cross-check target:** `zeroclaw-labs/zeroclaw:master` HEAD `4ab4998` (Cargo.toml `version = "0.7.3"`).
**Source-of-truth bits inspected:** `setup.bat`, `crates/zeroclaw-runtime/src/service/mod.rs` (1693 LOC), `dist/scoop/zeroclaw.json`, `src/main.rs` ServiceCommands.

Findings to confirm tomorrow on TYPHON (Windows reboot + InvestorClaw Windows test run):

---

## 1. Scoop manifest is severely stale — HIGH

`dist/scoop/zeroclaw.json` claims:
```json
"version": "0.5.9",
"url": "https://github.com/zeroclaw-labs/zeroclaw/releases/download/v0.5.9/zeroclaw-x86_64-pc-windows-msvc.zip"
```

Current Cargo.toml: `version = "0.7.3"`. Manifest is **23 patch releases behind** master. Anyone following the doc's *Option 2 — Scoop* path gets an ancient binary.

**Fix candidates:** (a) bump `dist/scoop/zeroclaw.json` to track release tags via release-time CI; (b) document the staleness in the doc with a "use Option 1 (--prebuilt) for current release" steer.

## 2. Doc claims Windows Service / LocalSystem path that isn't implemented — MEDIUM

Doc text:
> When run elevated, the installer registers a Windows Service under LocalSystem instead of a user-scoped scheduled task.

Master code in `service/mod.rs`:
- 47 `cfg!(target_os = "windows")` branches all flow into the scheduled-task path (line 1333: `"✅ Installed Windows scheduled task: {}"`)
- **No** `sc.exe`, `sc create`, `LocalSystem`, or `windows-service`-crate references anywhere in `crates/zeroclaw-runtime/src/service/`.
- No elevation-detection branch that re-routes to a Service installation.

**The "scheduled task" path works (extensively wired). The "Windows Service" path described in the doc is not implemented.**

**Fix candidates:** (a) implement the Windows Service path (`windows-service` crate + admin token check); (b) trim the doc to scheduled-task-only and reference Windows Service as a TODO.

## 3. Log path doc/code drift — LOW

Doc says:
> Logs go to `%LOCALAPPDATA%\ZeroClaw\logs\`.

Code at `service/mod.rs:444 fn logs_windows()`:
```rust
let logs_dir = config.config_path.parent().map_or_else(...).join("logs");
```
→ resolves to `<config_dir>/logs/`, which for the default install is `%USERPROFILE%\.zeroclaw\logs\` (not `%LOCALAPPDATA%\ZeroClaw\logs\`).

**Fix candidate:** doc edit to match code, OR code change to use `LOCALAPPDATA` (less invasive: doc edit).

## 4. setup.bat is current — OK

`setup.bat` at repo root, all four flags present (`--prebuilt`, `--minimal`, `--standard`, `--full`). Matches the doc's *Option 1*. Need to verify on TYPHON that `--prebuilt` actually fetches the v0.7.3 release binary (depends on the GitHub release artifact existing at the v0.7.3 tag).

## 5. SmartScreen / long-paths gotchas — verify on TYPHON

Doc lists three gotchas; nothing in master code references them, which is correct (these are Windows OS behaviors not zeroclaw bugs). Worth confirming on TYPHON during install:
- (a) Long path support — does `setup.bat --full` build cleanly on a path-260-capped install?
- (b) SmartScreen first-launch — does the unsigned `zeroclaw.exe` actually trip SmartScreen as documented?
- (c) Scheduled task stop-at-idle — verify the installed task has `Stop the task if it runs longer than: <unchecked>` and `Start only if on AC power: <unchecked>` per the doc.

## 6. InvestorClaw Windows test surface — separate scope

User has flagged that InvestorClaw Windows tests will run on TYPHON tomorrow. That's a different test path from zeroclaw setup; coordinate the boot sequence so both can run in one Windows reboot session.

---

## Recommended PR shape (if we want to land fixes upstream tomorrow)

| Fix | Surface | Effort |
|---|---|---|
| Scoop manifest version bump | `dist/scoop/zeroclaw.json` | trivial — bump version + add release-time CI hook |
| Doc edit: Windows Service is TODO, scheduled task is the supported path | `singlerider/zeroclaw` docs site | trivial |
| Doc edit: log path → `<config_dir>/logs/` | docs site | trivial |
| Implement Windows Service mode | `crates/zeroclaw-runtime/src/service/mod.rs` | medium — 200-400 LOC, needs `windows-service` crate, admin-token detection, TYPHON test |

---

*Review written 2026-04-25 evening, awaiting TYPHON Windows verification 2026-04-26.*
