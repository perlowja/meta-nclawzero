# SPDX-FileCopyrightText: Copyright (c) 2026 Jason Perlow. All rights reserved.
# SPDX-License-Identifier: Apache-2.0
#
# nclawzero-image-jetson — Jetson Orin Nano / Super / Thor Yocto image
# with plymouth splash, clean systemd boot, and the CUDA/TensorRT/cuDNN
# stack needed for on-device AI inference.
#
# Target: jetson-orin-nano-devkit (SD boot). Flash with tegraflash bundle.
# Kernel: L4T R36.4.4 (Linux 5.15.148+git). CUDA 12.6.68 / cuDNN 9.3.0 / TRT 10.3.0.
#
# Baked-in fixes (see scripts/jetson-rescue/ for the rescue-script lineage):
#   - nclawzero-system-config: sudoers, logind, networkd, thermal, tmpfiles
#   - RTL8168 + RTW88-8822CE kernel configs + matching linux-firmware subpkgs
#   - Audio-graph-card DT overlay disables broken probe on devkit
#     (HDMI HDA + USB audio still fully available for future audio models)
#   - l4t-launcher-extlinux APPEND includes boot.slot_suffix=_nclawzero
#     to bypass platform-preboot PARTLABEL=APP scan, plus quiet+splash
#
# No xrdp (was failing on first boot — NoMachine is the preferred remote
# desktop path, installed post-first-boot via nemoclaw-firstboot).
# No NetworkManager (suppressed via BAD_RECOMMENDATIONS; we use
# systemd-networkd + wpa-supplicant).
# No zram (SysV-only init script in poky is broken under systemd).

SUMMARY = "nclawzero edge AI agent image — Jetson Orin Nano (XFCE + CUDA + plymouth)"
DESCRIPTION = "Jetson Orin Nano Yocto image with clean systemd boot, plymouth \
    splash, xrdp-free posture (NoMachine installed post-boot), XFCE desktop \
    for interactive sessions, full CUDA 12.6 / TensorRT 10.3 / cuDNN 9.3 \
    stack, and ZeroClaw + NemoClaw agent runtime."
LICENSE = "MIT"

COMPATIBLE_MACHINE = "(tegra)"

inherit core-image
inherit features_check
REQUIRED_DISTRO_FEATURES = "x11"

# --- Features ---------------------------------------------------------------

IMAGE_FEATURES += " \
    ssh-server-openssh \
    debug-tweaks \
    x11-base \
    splash \
    package-management \
"

# --- Core + XFCE ------------------------------------------------------------

IMAGE_INSTALL = " \
    packagegroup-core-boot \
    packagegroup-core-full-cmdline \
    packagegroup-core-x11 \
    packagegroup-xfce-base \
    packagegroup-xfce-extended \
    packagegroup-xfce-multimedia \
    kernel-modules \
"

IMAGE_INSTALL:append = " \
    mousepad \
    ristretto \
    thunar-archive-plugin \
    thunar-media-tags-plugin \
"

# --- nclawzero agent stack + system config ---------------------------------

IMAGE_INSTALL:append = " \
    packagegroup-nclawzero \
    nclawzero-system-config \
"
# nclawzero-dt-overlays intentionally NOT in IMAGE_INSTALL — the broken
# tegra-audio-graph-card driver is already disabled at kconfig level
# (nclawzero-jetson-hw.cfg) so no overlay is needed. The recipe stays
# in the layer as a reusable pattern for future codec HAT support.

# --- Plymouth boot splash ---------------------------------------------------

IMAGE_INSTALL:append = " \
    plymouth \
    plymouth-theme-nclawzero \
"

# --- First-boot provisioning (installs NoMachine, docker, optional tools) ---

IMAGE_INSTALL:append = " nemoclaw-firstboot"

# --- CUDA / TensorRT / cuDNN (JetPack 6.2.1 release set) --------------------

IMAGE_INSTALL:append = " \
    cuda-libraries \
    cuda-nvcc \
    cuda-cudart \
    cuda-command-line-tools \
    cudnn \
    tensorrt-core \
    tensorrt-plugins-prebuilt \
    tensorrt-trtexec-prebuilt \
"

# --- Firmware (no broken drivers: every enabled config has matching fw) -----

IMAGE_INSTALL:append = " \
    linux-firmware-rtl-nic \
    linux-firmware-rtl8822 \
    wireless-regdb-static \
"

# --- Baseline utilities ----------------------------------------------------

# Gemma 4 E4B demo stack — native llama-cpp binary w/ CUDA (sm_87 for
# Orin Ampere). See llama-cpp_git.bb for the build (inherits meta-tegra
# cuda class, DCMAKE_DISABLE_FIND_PACKAGE_OpenSSL to avoid native-sysroot
# libssl cross-link confusion). No Docker — per fleet doctrine
# (project_tydeus + user direction 2026-04-24: "go on the metal").
IMAGE_INSTALL:append = " llama-cpp nclawzero-demo-gemma nclawzero-storage-init"

IMAGE_INSTALL:append = " \
    curl wget git rsync \
    htop \
    vim nano \
    unzip zip tar \
    sudo \
    bash \
    tmux \
    tree \
    jq \
"

# --- Suppress unwanted recommends ------------------------------------------
#
# packagegroup-xfce-extended recommends NetworkManager + applet; we run
# systemd-networkd instead and NetworkManager fails at boot because
# /run/dbus/... service name is already claimed. Suppress the pull.
# xfce4-pulseaudio-plugin is fine; pulseaudio itself stays.
BAD_RECOMMENDATIONS += " \
    networkmanager \
    network-manager-applet \
    modemmanager \
"

# --- Distro features (also set in local.conf for parse-time eligibility) --

DISTRO_FEATURES_BACKFILL_CONSIDERED:append = " sysvinit"
VIRTUAL-RUNTIME_init_manager = "systemd"
VIRTUAL-RUNTIME_initscripts = "systemd-compat-units"

IMAGE_LINGUAS = ""

SYSTEMD_DEFAULT_TARGET = "multi-user.target"

# Reserve headroom for CUDA (~3GB) + NoMachine + skills + workspace.
IMAGE_ROOTFS_EXTRA_SPACE = "6291456"

# Service users + pi login shell.
# pi is in wheel group for sudoers-pi drop-in to apply.
inherit extrausers
EXTRA_USERS_PARAMS = " \
    useradd -r -d /var/lib/zeroclaw -s /usr/sbin/nologin zeroclaw; \
    useradd -r -d /var/lib/nemoclaw -s /usr/sbin/nologin nemoclaw; \
    useradd -m -s /bin/bash -G sudo,wheel,docker,video,audio,input,plugdev -p '!' pi; \
"

IMAGE_FSTYPES:remove:tegra = "wic wic.gz wic.bmap"
