# pi-gen vs meta-nclawzero: Structural Pattern Audit

This note is retained as a Raspberry Pi image-builder comparison.

Public support is currently:

- x86_64 + macOS via Docker/Podman containers:
  `ghcr.io/perlowja/nclawzero-demo`,
  `ghcr.io/perlowja/nclawzero-agent`
- ARM Raspberry Pi family via Yocto-built flashable images
  (`meta-nclawzero-base`) or pre-built SD images (`pi-gen-nclawzero`)

NVIDIA Jetson family support is deferred pending hardware validation. Prior
image hierarchy, boot, A/B update, and recovery recommendations for that family
were removed from this public comparison.

The useful pi-gen patterns for the supported Pi path remain:

- Boot-partition operator sentinel files for first-boot provisioning
- A single operator-facing config surface
- Clear image variants for base, desktop, and agent builds
- Sidecar metadata such as bmap files and build info
