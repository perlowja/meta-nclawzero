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

require recipes-core/images/nclawzero-image-common.inc

COMPATIBLE_MACHINE = "(tegra)"

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
    ${NCLAWZERO_COMMON_INSTALL} \
"

IMAGE_INSTALL:append = " \
    mousepad \
    ristretto \
    thunar-archive-plugin \
    thunar-media-tags-plugin \
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

# Firmware (no broken drivers: every enabled kernel CONFIG has matching fw)
#   - linux-firmware-rtl-nic:  rtl_nic/* (covers onboard R8169 + USB r8152/r8153 + r8156b)
#   - linux-firmware-rtl8822:  rtw88/rtw8822*.bin + rtl_bt/rtl8822*.bin + rtlwifi/*
#                              (WiFi firmware + Bluetooth combo firmware)
#   - wireless-regdb-static:   regulatory.db (required by any CFG80211 driver)
#   - bluez5:                  userspace BT stack (bluetoothctl, hciconfig, etc.)
IMAGE_INSTALL:append = " \
    linux-firmware-rtl-nic \
    linux-firmware-rtl8822 \
    wireless-regdb-static \
    bluez5 \
"

# --- Baseline utilities ----------------------------------------------------

# Gemma 4 E4B demo stack — native llama-cpp binary w/ CUDA (sm_87 for
# Orin Ampere). See llama-cpp_git.bb for the build (inherits meta-tegra
# cuda class, DCMAKE_DISABLE_FIND_PACKAGE_OpenSSL to avoid native-sysroot
# libssl cross-link confusion). No Docker — per fleet doctrine
# (project_tydeus + user direction 2026-04-24: "go on the metal").
# nclawzero-update + nclawzero-boot-provision come in via NCLAWZERO_COMMON_INSTALL.
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

# Reserve headroom for CUDA (~3GB) + NoMachine + skills + workspace.
IMAGE_ROOTFS_EXTRA_SPACE = "6291456"

# wic is removed by default for tegraflash-only single-slot images. The
# dual-slot variant (nclawzero-image-jetson-dual.bb) sets NCZ_NEEDS_WIC=1
# BEFORE the require to opt out of this removal, since it ships an A/B SD
# layout that needs the .wic + .wic.bmap artifacts.
IMAGE_FSTYPES:remove:tegra = "${@'' if d.getVar('NCZ_NEEDS_WIC') == '1' else 'wic wic.gz wic.bmap'}"
