# SPDX-FileCopyrightText: Copyright (c) 2026 Jason Perlow. All rights reserved.
# SPDX-License-Identifier: Apache-2.0
#
# nclawzero-image-jetson — Jetson Orin Nano headless image with XFCE,
# remote access via xrdp (bundled) + NoMachine (installed by firstboot),
# and the CUDA/TensorRT/cuDNN stack needed for GPU inference.
#
# Target: jetson-orin-nano-devkit (SD card boot, flashed via USB recovery).
# Flash: bitbake produces tegraflash bundle; run ./doflash.sh with the
#        device in recovery mode to program QSPI and (optionally) SD.
#
# Kernel: L4T R36.4.4 (Linux 5.15.148+git) — the ceiling for Orin with CUDA.
# CUDA  : 12.6.68 (matches JetPack 6.2.1 release set)
# cuDNN : 9.3.0.75
# TRT   : 10.3.0.30

SUMMARY = "nclawzero edge AI agent image — Jetson Orin Nano (XFCE + CUDA)"
DESCRIPTION = "Headless Jetson Orin Nano image with polished XFCE desktop, \
    xrdp + NoMachine remote access, full CUDA 12.6 / TensorRT 10.3 / cuDNN 9.3 \
    stack, and ZeroClaw + NemoClaw agent runtime. No GNOME. TTY console login \
    retained for emergency access. Additional user-visible bits (themes, \
    firefox, vlc) installed post-boot by nemoclaw-firstboot."
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
#
# packagegroup-xfce-extended RRECOMMENDS all the XFCE goodies
# (cpufreq, cpugraph, netload, systemload, clipman, diskperf, places, xkb,
#  weather, fsguard, battery, mount, powermanager, timer, time-out, genmon,
#  wavelan, eyes, datetime) plus all base components.
# packagegroup-xfce-multimedia adds parole + pulseaudio-plugin.

IMAGE_INSTALL = " \
    packagegroup-core-boot \
    packagegroup-core-full-cmdline \
    packagegroup-core-x11 \
    packagegroup-xfce-base \
    packagegroup-xfce-extended \
    packagegroup-xfce-multimedia \
    kernel-modules \
"

# XFCE apps that packagegroup-xfce-base doesn't pull.
IMAGE_INSTALL:append = " \
    mousepad \
    ristretto \
    thunar-archive-plugin \
    thunar-media-tags-plugin \
"

# --- Fonts — OpenType + TrueType coverage ----------------------------------

IMAGE_INSTALL:append = " \
    ttf-dejavu \
    ttf-liberation \
    ttf-google-fira \
    ttf-inconsolata \
"

# --- nclawzero agent stack --------------------------------------------------

IMAGE_INSTALL:append = " packagegroup-nclawzero"

# --- Remote access ----------------------------------------------------------

IMAGE_INSTALL:append = " \
    xrdp \
    nemoclaw-firstboot \
"

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

# --- Docker + nvidia-container-toolkit (container runtime) -----------------

IMAGE_INSTALL:append = " \
    docker-moby \
    nvidia-container-toolkit \
"

# --- Baseline utilities ----------------------------------------------------

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

# --- Distro features (also set in local.conf for parse-time eligibility) --

DISTRO_FEATURES_BACKFILL_CONSIDERED:append = " sysvinit"
VIRTUAL-RUNTIME_init_manager = "systemd"
VIRTUAL-RUNTIME_initscripts = "systemd-compat-units"

IMAGE_LINGUAS = ""

# Boot straight to multi-user; xrdp spawns Xorg per RDP connection.
SYSTEMD_DEFAULT_TARGET = "multi-user.target"

# Reserve headroom for CUDA (~3GB) + NoMachine + skills + workspace.
IMAGE_ROOTFS_EXTRA_SPACE = "6291456"

# Service users + pi login shell.
inherit extrausers
EXTRA_USERS_PARAMS = " \
    useradd -r -d /var/lib/zeroclaw -s /usr/sbin/nologin zeroclaw; \
    useradd -r -d /var/lib/nemoclaw -s /usr/sbin/nologin nemoclaw; \
    useradd -m -s /bin/bash -G sudo,docker,video,audio -p '!' pi; \
"
