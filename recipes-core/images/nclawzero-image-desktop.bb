# SPDX-FileCopyrightText: Copyright (c) 2026 Jason Perlow. All rights reserved.
# SPDX-License-Identifier: Apache-2.0
#
# nclawzero-image-desktop — nclawzero with Weston + RDP remote desktop
#
# Superset of nclawzero-image adding:
#   - Weston Wayland compositor (rdp backend)
#   - Self-signed TLS cert generated on first boot
#   - Accessible via Windows App / Microsoft Remote Desktop on port 3389
#
# Targets: Raspberry Pi 4 (8GB recommended for desktop), Jetson Orin Nano
# Flash:   bmaptool copy nclawzero-image-desktop-*.wic.gz /dev/sdX

require recipes-core/images/nclawzero-image.bb

SUMMARY = "nclawzero desktop image (Weston + RDP)"
DESCRIPTION = "nclawzero headless base plus Weston Wayland compositor \
    with RDP backend for remote desktop access via Windows App / \
    Microsoft Remote Desktop client."

IMAGE_INSTALL += " \
    packagegroup-nclawzero-desktop \
"

# Desktop needs wayland DISTRO_FEATURE — undo headless parent's remove.
# local.conf in build-rpi-desktop/ must also not remove wayland.
DISTRO_FEATURES:remove = "x11 vulkan"
DISTRO_FEATURES:append = " wayland opengl"

# Extra space for Weston + fonts + cert storage
IMAGE_ROOTFS_EXTRA_SPACE = "1048576"
