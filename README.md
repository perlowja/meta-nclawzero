# nclawzero/meta

Yocto layer for building minimal embedded Linux images with the ZeroClaw AI agent runtime and NemoClaw sandbox framework.

> **Repo path note (2026-04-26 reorg):** this layer lives at
> `gitlab.com/nclawzero/meta` (canonical) /
> `github.com/nclawzero/meta` (mirror) /
> `argonas:/mnt/datapool/git/nclawzero/meta.git` (fleet backup).
> The previous flat `perlowja/meta-nclawzero` URL auto-redirects on both
> forges. The on-disk directory name in working trees is still
> `meta-nclawzero/` — that's a working-tree convenience, the project
> path is `nclawzero/meta`.

> ## ⚠️ Canary track — no stability claims
>
> **Current `main` is the canary track.** Upstream-sourced recipes (`nemoclaw-core`, `llama-cpp`, etc.) use `SRCREV = "${AUTOREV}"` and pull whatever `main` / `master` of the upstream repo resolves to at the moment of the build. Every build is a snapshot of fast-moving upstreams; breakage between builds is expected.
>
> This layer makes **no claims about system stability, feature completeness, or fitness for any purpose**. Treat every build as pre-alpha — a rolling investigation of what's in upstream HEAD right now, not a deliverable product.
>
> A companion **conservative track** (see `conservative/*` branches) pins upstream-sourced recipes to the latest tagged upstream releases. Still not LTS-grade — no backport SLAs, no release promises — just pinned-to-releases rather than tracking HEAD, updated on a slower cadence than `main`. See [STATUS.md](./STATUS.md) for the full canary-vs-conservative model.

## What it does

Pulls upstream NemoClaw and ZeroClaw at build time. Applies a small patchset (~430 lines across 3 patches). Copies overlay files (ZeroClaw agent definition, security tests, deployment scripts). Produces a flashable SD card image.

No fork. No copied upstream code in this repo. Just patches, overlays, configs, and recipes.

## Targets

Current public support:

- x86_64 + macOS via Docker/Podman containers:
  `ghcr.io/perlowja/nclawzero-demo`,
  `ghcr.io/perlowja/nclawzero-agent`
- ARM Raspberry Pi family (Pi 4, Pi 5, Pi Zero 2 W, Pi 3 64-bit) via
  Yocto-built flashable images (`meta-nclawzero-base`) or pre-built SD
  images (`pi-gen-nclawzero`)

> **NVIDIA Jetson family support is deferred pending hardware validation.**
> A maintainer-tested Orin Nano dev kit suffered a brick-class firmware
> failure that has not been recovered through software. Public docs should
> not treat that workflow as supported until a known-good replacement unit
> validates it end to end.

## Performance

ZeroClaw on Raspberry Pi 4B (2GB):
- Daemon RSS: ~17MB
- Three daemon processes: <63MB total
- InvestorClaw skill (607 positions, 12 tools): passes full V7.1 test battery
- Available memory after boot: 1.4GB+

## Dependencies

- Yocto 5.0 Scarthgap (LTS)
- poky (scarthgap)
- meta-openembedded (scarthgap) — meta-oe, meta-python
- meta-raspberrypi (scarthgap) — for Pi targets

## Quick start

```bash
# Clone layers
git clone -b scarthgap git://git.yoctoproject.org/poky
git clone -b scarthgap git://git.openembedded.org/meta-openembedded
git clone -b scarthgap https://github.com/agherber/meta-raspberrypi.git

# Initialize build
source poky/oe-init-build-env build-rpi

# Add layers to conf/bblayers.conf, set MACHINE in conf/local.conf
# See conf/ directory for examples

# Build
bitbake nclawzero-image

# Flash
bmaptool copy tmp/deploy/images/raspberrypi4-64/nclawzero-image-raspberrypi4-64.wic.gz /dev/sdX
```

## Layer contents

```
meta-nclawzero/
  conf/layer.conf                      — layer configuration
  recipes-zeroclaw/zeroclaw/           — ZeroClaw binary + systemd service
  recipes-nemoclaw/nemoclaw/           — NemoClaw + patches + overlays
  recipes-core/images/                 — Raspberry Pi image recipes
  recipes-core/nclawzero-ssh-keys/     — production rootfs authorized_keys bake
  recipes-core/nclawzero-system-config/ — sudoers, networkd, logind, thermal
  recipes-core/packagegroups/          — package groups
  recipes-connectivity/                — network configuration
  wic/                                 — SD card partition layout
```

## Auth model

Two user accounts created via `extrausers` on every flashed image:

- **`ncz`** — operator (interactive sudo, NOPASSWD via sudoers drop-in). Locked password; SSH-only access via the baked authorized_keys.
- **`jasonperlow`** — defense-in-depth backup user. Same authorized_keys, same NOPASSWD sudo. Exists so a disrupted operator account (e.g., the 2026-04-26 Pi OS Trixie userconfig stripping incident) doesn't lock the fleet out.

`authorized_keys` content is **fleet-internal** (the comment fields disclose access topology). Real keys live at `/mnt/datapool/secrets/nclawzero-fleet-keys/authorized_keys` on ARGONAS and are pulled into the gitignored build-time path before each build via `~/sync-fleet-keys.sh`. The committed `.example` files document the format. Per-line strict validation rejects malformed lines (including `[options] key-type` shapes that ssh-keygen accepts but sshd refuses).

See `feedback_auth_local_only_keys.md` in the operator's MNEMOS for the full policy.

## Lineage

OpenZaurus (2002) -> OpenEmbedded -> Yocto Project

## License

Apache-2.0
