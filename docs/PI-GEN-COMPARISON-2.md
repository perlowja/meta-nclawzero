# pi-gen vs meta-nclawzero: Independent Structural Comparison

This cross-check is retained only for Raspberry Pi image-builder lessons.

NVIDIA Jetson family support is deferred pending hardware validation, so older
recommendations for board-specific images, A/B layouts, CUDA packaging, and
L4T recovery were removed.

Relevant recommendations for the supported Pi/container project shape:

- Add boot-partition field provisioning for SSH keys, Wi-Fi, and hostname
- Keep operator-facing build knobs in one documented config surface
- Reduce duplication between base, desktop, and agent image recipes
- Preserve bmap/image metadata for safer SD-card flashing
