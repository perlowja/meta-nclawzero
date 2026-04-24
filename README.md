# meta-nclawzero

Yocto layer for building minimal embedded Linux images with the ZeroClaw AI agent runtime and NemoClaw sandbox framework.

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

| Machine | Board | RAM | Status |
|---------|-------|-----|--------|
| `raspberrypi4-64` | Raspberry Pi 4 Model B | 2GB / 8GB | Primary |
| `jetson-orin-nano-devkit` | NVIDIA Jetson Orin Nano | 8GB | Planned |

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
- meta-tegra (scarthgap) — for Jetson targets (future)

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
  recipes-core/images/                 — image recipe
  recipes-core/packagegroups/          — package group
  recipes-connectivity/                — network configuration
  wic/                                 — SD card partition layout
```

## Lineage

OpenZaurus (2002) -> OpenEmbedded -> Yocto Project

## License

Apache-2.0
