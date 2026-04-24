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
# Targets:
#   - raspberrypi4-64        (SD card via wic.gz + bmap)
#   - jetson-orin-nano-devkit (tegraflash output via meta-tegra)
#
# Flash:
#   RPi:    bmaptool copy nclawzero-image-raspberrypi4-64.wic.gz /dev/sdX
#   Jetson: cd tmp/deploy/images/jetson-orin-nano-devkit && sudo ./doflash.sh

SUMMARY = "nclawzero edge AI agent image"
DESCRIPTION = "Minimal console image with ZeroClaw AI agent runtime \
    and NemoClaw sandbox framework for edge/embedded deployment."
LICENSE = "MIT"

inherit core-image

COMPATIBLE_MACHINE = "(raspberrypi4-64|tegra)"

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

# Headless — no locales / GUI
IMAGE_LINGUAS = ""

# systemd as init manager
DISTRO_FEATURES:append = " systemd"
DISTRO_FEATURES_BACKFILL_CONSIDERED:append = " sysvinit"
VIRTUAL-RUNTIME_init_manager = "systemd"
VIRTUAL-RUNTIME_initscripts = "systemd-compat-units"

# Remove desktop/graphics features
DISTRO_FEATURES:remove = "x11 wayland vulkan"

# Per-MACHINE image output + partitioning:
#   RPi  → SD card WIC, bmap side-car for bmaptool
#   Tegra → tegra-common.inc already appends "tegraflash" to IMAGE_FSTYPES;
#           do not clobber it here. Leave the default unset on tegra machines.
IMAGE_FSTYPES:raspberrypi4-64 = "wic.gz wic.bmap"
WKS_FILE:raspberrypi4-64 = "nclawzero-rpi.wks.in"

# Reserve headroom for workspace, skills, npm cache
IMAGE_ROOTFS_EXTRA_SPACE = "524288"

# Service users created at image build time
inherit extrausers
EXTRA_USERS_PARAMS = " \
    useradd -r -d /var/lib/zeroclaw -s /usr/sbin/nologin zeroclaw; \
    useradd -r -d /var/lib/nemoclaw -s /usr/sbin/nologin nemoclaw; \
    useradd -m -s /bin/bash -G sudo -p '!' pi; \
"
