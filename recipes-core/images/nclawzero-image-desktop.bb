# SPDX-FileCopyrightText: Copyright (c) 2026 Jason Perlow. All rights reserved.
# SPDX-License-Identifier: Apache-2.0
#
# nclawzero-image-desktop — headless agent + VNC desktop for Claude Code.
# For 4GB+ RAM devices (clawpi). Access via VNC client on port 5901.
#
# NOT for the 2GB zeropi — use nclawzero-image (headless) for that.

require nclawzero-image.bb

SUMMARY = "nclawzero desktop image — Weston VNC + Claude Code demo box"

IMAGE_INSTALL:append = " \
    packagegroup-nclawzero-desktop \
"

# Wayland for Weston compositor with built-in VNC backend
DISTRO_FEATURES:append = " wayland opengl"
DISTRO_FEATURES:remove = "x11"

# Larger rootfs for desktop packages
IMAGE_ROOTFS_EXTRA_SPACE = "1048576"
