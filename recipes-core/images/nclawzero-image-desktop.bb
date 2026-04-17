# SPDX-FileCopyrightText: Copyright (c) 2026 Jason Perlow. All rights reserved.
# SPDX-License-Identifier: Apache-2.0
#
# nclawzero-image-desktop — headless agent + VNC desktop for Claude Code.
# For 4GB+ RAM devices (clawpi). Access via VNC client on port 5901.
#
# NOT for the 2GB zeropi — use nclawzero-image (headless) for that.

require nclawzero-image.bb

SUMMARY = "nclawzero desktop image — VNC + Openbox + Claude Code"

IMAGE_INSTALL:append = " \
    packagegroup-nclawzero-desktop \
"

# X11 needed for VNC + window manager
DISTRO_FEATURES:append = " x11"

# Larger rootfs for desktop packages + browser
IMAGE_ROOTFS_EXTRA_SPACE = "1048576"
