# Project status

## TL;DR

- **Canary track (current `main`)**: tracks upstream HEAD, pre-alpha posture, no stability claims.
- **conservative (pinned) track (planned)**: future branch that pins upstream to judged-release-quality snapshots. Does not exist yet. Will be created when the maintainer pins a canary snapshot worth promoting.

## Why canary-first

This layer is a rolling investigation of what lands in upstream `NVIDIA/NemoClaw` and `zeroclaw-labs/zeroclaw` (plus supporting projects like `ggml-org/llama.cpp`). The whole point is to exercise upstream-HEAD behavior on real hardware — flash images built from the latest main/master commits, run skills, observe breakage.

Pinning upstream would defeat the purpose of that investigation. Until a release exists that's explicitly stamped "stable," the honest posture is: canary, track HEAD, expect breakage, report upstream when you find it.

### Prior art — the FreeBSD model

This is the same posture FreeBSD has run for 25+ years with its `-current` / `-stable` split:

- **`-current`** is the HEAD of main development. Users who track `-current` accept that it may break at any time and are expected to cooperate with developers when it does. No warranty, no SLA, just an honest statement that this is where development happens.
- **`-stable`** branches are cut from `-current` when the tree is judged release-quality. Releases come from `-stable`.

`main` on this layer is nclawzero's `-current`. Future `conservative/*` branches are nclawzero's `-stable`. The naming differs — "canary" vs "lts-equivalent pinned" — but the mental model is identical, and the FreeBSD community's ~three-decade track record of running this posture successfully is the prior art.

If you've run FreeBSD `-current` before, you already know what to expect here.

## What "canary" means operationally

- Upstream-sourced Yocto recipes use `SRCREV = "${AUTOREV}"` rather than hardcoded commit SHAs.
  - `recipes-nemoclaw/nemoclaw/nemoclaw-core_git.bb` — tracks `main` of `NVIDIA/NemoClaw`.
  - `recipes-ai/llama-cpp/llama-cpp_git.bb` — tracks `master` of `ggml-org/llama.cpp`.
- Pre-built-binary recipes (e.g., `recipes-zeroclaw/zeroclaw/zeroclaw-bin_*.bb`) are pinned to a named release tarball by necessity — binary releases don't have HEADs — but the version is treated as an arbitrary pin, not a stability claim.
- Every `bitbake` invocation pulls whatever upstream resolves to at that second. Two successive builds minutes apart can produce different images.
- Build failures caused by upstream breakage are expected and are not treated as regressions of this layer.

## What "no stability claims" means

- **Feature set** — features that work in one build may not work in the next, because upstream behaviour changed.
- **API / config schema** — upstream may rename, restructure, or deprecate without warning; this layer will re-flow those changes rather than insulate against them.
- **Boot reliability** — images are tested informally on the maintainer's Pi fleet; there is no automated regression harness on a canary image.
- **Security posture** — upstream advisories flow into the canary automatically (no vulnerable-version pin that needs bumping), but there is no formal SLA on triage speed.
- **Data loss** — skills that write state to `/var/lib/zeroclaw/` or `/var/lib/nemoclaw/` are subject to schema churn; upstream migrations may not always be non-destructive. Back up anything you care about.

## The conservative track — what the name means

**"Conservative" describes the upstream packaging choice, not the stability of this layer's integrated system.**

A `conservative/<year>.<month>` branch pins every upstream-sourced recipe to the SHA of the latest tagged upstream release as of the cutover date — whatever NVIDIA/NemoClaw, ggml-org/llama.cpp, and zeroclaw-labs/zeroclaw have stamped as their most recent production-release tag. That's the only thing "conservative" means.

It does **not** mean:

- That this layer's integrated system is stable. The integration of the upstream packages with this layer's overlays, patches, configs, and security choices is exercised only on the maintainer's Pi fleet, has no automated regression harness, and offers no guarantees.
- That breakage between two `conservative/*` branches is treated as a regression. If `conservative/2026.04` works and `conservative/2026.07` doesn't, the difference is whatever upstream shipped between tagged releases plus whatever maintainer changes landed in this layer — either could be the cause, and there's no promise to track it down.
- That there's a backport SLA for security or functionality fixes. There isn't. If something breaks upstream and upstream fixes it, the fix arrives on a conservative branch when the maintainer cuts the next one.
- That this is "long-term support" in any contractual sense. It is not. There are no release promises, no compatibility windows, no commitments.

It means exactly: every upstream-sourced recipe points at a production-tagged release commit, not a development HEAD. That's all.

### Mental model

Think of it as building a PC from off-the-shelf parts at a big computer store versus buying a certified, tested system from a vendor like Dell or HP. The parts themselves — CPU, board, SSD, PSU, RAM — are production-grade; every manufacturer has tested and warrantied the part they shipped. But the assembled *machine* — the combination of THIS board with THAT CPU cooler in THIS case with THAT BIOS revision, running your specific OS config — is one person's hand-built project. Newegg and Micro Center can honor warranties on the individual parts. Nobody warranties the machine as a whole. If it doesn't POST, you own the debugging.

Canary is the same PC build but with experimental-firmware board revisions and non-released driver builds in the mix. Conservative at least keeps you on the shipped-firmware, released-driver versions the part vendors themselves support.

Even then — and this is the point the analogy is load-bearing for — pinning every part to a tagged, released version doesn't prevent random incompatibilities, hardware-interaction quirks, or emergent behavior when six vendors' tested parts meet each other inside one chassis for the first time. Dell tests its own integrations before shipping a certified OptiPlex; nobody tests *this* layer's integrations except the maintainer on his own Pis. Conservative narrows the variance; it does not eliminate it.

## Pathway: canary → conservative

When the maintainer decides to cut a new conservative branch (typically: all three upstreams have cut new tagged releases since the last cut, or a security fix landed in one and the maintainer wants it pinned):

1. Pin upstream-sourced recipes to the SHAs of the latest **tagged upstream release** at cutover. This isn't AUTOREV, and it isn't a canary-snapshot SHA — it's the production-release tag's commit.
2. Cut a branch named `conservative/<year>.<month>` (e.g., `conservative/2026.04`). `main` stays on canary; `conservative/*` holds the pinned snapshot.
3. Tag the resulting image with a version number.

If you need LTS-grade guarantees, use a distro that actually has an LTS team. This isn't that.

## Current posture summary

| Component | Track | Pinning |
|---|---|---|
| `nemoclaw-core` source | canary | `SRCREV = "${AUTOREV}"` → `NVIDIA/NemoClaw` main HEAD |
| `llama-cpp` | canary | `SRCREV = "${AUTOREV}"` → `ggml-org/llama.cpp` master HEAD |
| `zeroclaw-bin` | pinned (necessarily — binary release) | `v0.7.3-beta.1051` tarball |
| Layer itself (this repo) | rolling | no release branches exist yet |

> **Note:** Jetson family BSP integration (kernel + L4T tooling via `meta-tegra`) is currently deferred pending hardware validation. Public `main` builds target Raspberry Pi only; Jetson recipe metadata is preserved on a private branch for resumption when validation hardware lands.

## If you're evaluating this for deployment

Don't — not yet. If you want to run it anyway: flash a canary image, note the git SHA of the layer and the SRCREV values `bitbake` resolved during the build (both go into the image's `installed-package-sizes` / recipe-info artifacts), and treat that tuple as your local pin. If it breaks, redo the tuple.
