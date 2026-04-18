# SPDX-FileCopyrightText: Copyright (c) 2026 Jason Perlow. All rights reserved.
# SPDX-License-Identifier: Apache-2.0
#
# nclawzero-image — minimal embedded Linux image for AI agent edge deployment
#
# Headless console image with:
#   - ZeroClaw Rust agent runtime (~17MB RSS)
#   - NemoClaw sandbox framework
#   - systemd, SSH, networking
#   - No GUI, no desktop, no X11
#
# Targets: Raspberry Pi 4 (2GB/8GB), NVIDIA Jetson Orin Nano (future)
# Flash:   bmaptool copy nclawzero-image-raspberrypi4-64.wic.gz /dev/sdX

SUMMARY = "nclawzero edge AI agent image"
DESCRIPTION = "Minimal console image with ZeroClaw AI agent runtime \
    and NemoClaw sandbox framework for edge/embedded deployment."
LICENSE = "MIT"

inherit core-image

IMAGE_FEATURES += " \
    ssh-server-openssh \
    debug-tweaks \
"
# TODO: remove debug-tweaks for production images

IMAGE_INSTALL = " \
    packagegroup-core-boot \
    packagegroup-core-full-cmdline \
    packagegroup-nclawzero \
    kernel-modules \
"

# No GUI — headless
IMAGE_LINGUAS = ""

# systemd as init manager
DISTRO_FEATURES:append = " systemd virtualization"
DISTRO_FEATURES_BACKFILL_CONSIDERED:append = " sysvinit"
VIRTUAL-RUNTIME_init_manager = "systemd"
VIRTUAL-RUNTIME_initscripts = "systemd-compat-units"

# Remove desktop/graphics features
DISTRO_FEATURES:remove = "x11 wayland vulkan"

# Image output format — compressed WIC for SD card flashing
IMAGE_FSTYPES = "wic.gz wic.bmap"
WKS_FILE = "nclawzero-rpi.wks.in"

# Reserve extra rootfs space for skills, workspace, and npm cache
IMAGE_ROOTFS_EXTRA_SPACE = "524288"

# Create service users at image build time
inherit extrausers
EXTRA_USERS_PARAMS = " \
    useradd -r -d /var/lib/zeroclaw -s /usr/sbin/nologin zeroclaw; \
    useradd -r -d /var/lib/nemoclaw -s /usr/sbin/nologin nemoclaw; \
    useradd -m -s /bin/bash -G sudo pi; \
    usermod -p '\$6\$nclawzero\$eHgQYb4uuKNHMlaNzR.Y1Ot0iNmy2PYgTDVLoVzOOF2NZZnLu40cAoQlABPeSU14nWb6OWjzOQBGOT9QEkOO20' pi; \
"
