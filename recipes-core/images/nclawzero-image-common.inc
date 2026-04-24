# SPDX-FileCopyrightText: Copyright (c) 2026 Jason Perlow. All rights reserved.
# SPDX-License-Identifier: Apache-2.0
#
# nclawzero-image-common.inc — shared bits across all flavors.
#
# Required by:
#   - nclawzero-image.bb            (Pi headless console)
#   - nclawzero-image-desktop.bb    (Pi + Weston/RDP)
#   - nclawzero-image-jetson.bb     (Jetson + XFCE + CUDA)
#   - nclawzero-image-jetson-dual.bb (require-chains via -jetson)
#
# Goal: DRY the recipe content that genuinely overlaps. Per-machine bits
# (CUDA, plymouth, XFCE, COMPATIBLE_MACHINE, IMAGE_FSTYPES quirks) stay
# in the per-flavor .bb where they belong.

LICENSE ?= "MIT"
inherit core-image

# --- systemd as init manager ------------------------------------------------

DISTRO_FEATURES:append = " systemd"
DISTRO_FEATURES_BACKFILL_CONSIDERED:append = " sysvinit"
VIRTUAL-RUNTIME_init_manager = "systemd"
VIRTUAL-RUNTIME_initscripts = "systemd-compat-units"

SYSTEMD_DEFAULT_TARGET ?= "multi-user.target"

# Headless — no auto-locale install. Per-flavor recipes can override.
IMAGE_LINGUAS ?= ""

# --- Common nclawzero stack -------------------------------------------------
#
# Bits that belong on every flavor:
#   - packagegroup-nclawzero       — agent runtime (zeroclaw, nemoclaw, etc.)
#   - nclawzero-system-config      — sudoers, logind, networkd, thermal, udev
#   - nclawzero-update             — A/B + in-place update CLI
#   - nclawzero-boot-provision     — first-boot sentinel processor
#                                     (pi-gen-style ssh/userconf/wpa/auth_keys)
#
# Per-flavor recipes still need to add their own machine-specific extras
# (kernel-modules, CUDA stack, plymouth, XFCE etc.).
NCLAWZERO_COMMON_INSTALL = " \
    packagegroup-nclawzero \
    nclawzero-system-config \
    nclawzero-update \
    nclawzero-boot-provision \
"

# --- Common BAD_RECOMMENDATIONS --------------------------------------------
#
# NetworkManager fights systemd-networkd; we always use the latter.
# modemmanager pulls in cellular plumbing nobody on this fleet uses.
BAD_RECOMMENDATIONS:append = " \
    networkmanager \
    network-manager-applet \
    modemmanager \
"

# --- Service users + operator login shell ----------------------------------
#
# Renamed from `pi` to `ncz` (2026-04-24). Existing flashed devices retain
# pi until they're reflashed; `feedback_operator_user_ncz.md` captures
# the rationale.
#
# Group memberships:
#   sudo, wheel       — sudoers-ncz drop-in NOPASSWD
#   docker            — docker socket access for nemoclaw firstboot
#   video, audio,
#   input, plugdev    — desktop / hardware access
#
# Note: netdev was intentionally removed during TYDEUS rootfs-create
# triage when the group did not exist in the build environment. Confirm
# before re-adding.
inherit extrausers
EXTRA_USERS_PARAMS = " \
    useradd -r -d /var/lib/zeroclaw -s /usr/sbin/nologin zeroclaw; \
    useradd -r -d /var/lib/nemoclaw -s /usr/sbin/nologin nemoclaw; \
    useradd -m -s /bin/bash -G sudo,wheel,docker,video,audio,input,plugdev -p '!' ncz; \
"
