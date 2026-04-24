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
    retained for emergency access."
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

# --- Core + XFCE (polished) -------------------------------------------------

IMAGE_INSTALL = " \
    packagegroup-core-boot \
    packagegroup-core-full-cmdline \
    packagegroup-core-x11 \
    packagegroup-xfce-base \
    packagegroup-xfce-extended \
    packagegroup-xfce-multimedia \
    kernel-modules \
"

# XFCE polish: themes, icons, plugins, desktop utilities.
IMAGE_INSTALL:append = " \
    xfce4-terminal \
    xfce4-whiskermenu-plugin \
    xfce4-clipman-plugin \
    xfce4-systemload-plugin \
    xfce4-cpufreq-plugin \
    xfce4-cpugraph-plugin \
    xfce4-netload-plugin \
    xfce4-pulseaudio-plugin \
    xfce4-screenshooter \
    xfce4-notifyd \
    xfce4-power-manager \
    mousepad \
    ristretto \
    file-roller \
    thunar-archive-plugin \
    thunar-media-tags-plugin \
    arc-icon-theme \
    papirus-icon-theme \
    plank \
"

# --- Fonts — good OpenType set ---------------------------------------------

IMAGE_INSTALL:append = " \
    fontconfig-utils \
    ttf-dejavu-common ttf-dejavu-sans ttf-dejavu-sans-mono ttf-dejavu-serif \
    ttf-liberation \
    noto-fonts noto-fonts-ui \
"

# --- nclawzero agent stack --------------------------------------------------

IMAGE_INSTALL:append = " packagegroup-nclawzero"

# --- Remote access ----------------------------------------------------------

# xrdp: built from source, starts on :3389 at boot.
# nemoclaw-firstboot: runs once at first boot to fetch+install NoMachine deb.
IMAGE_INSTALL:append = " \
    xrdp \
    nemoclaw-firstboot \
"

# --- CUDA / TensorRT / cuDNN (JetPack 6.2.1 release set) --------------------

IMAGE_INSTALL:append = " \
    cuda-libraries \
    cuda-runtime \
    cuda-nvcc \
    cuda-cudart \
    cuda-command-line-tools \
    cudnn \
    tensorrt-core \
    tensorrt-plugins-prebuilt \
    tensorrt-trtexec-prebuilt \
"

# --- JetPack-equivalent 3rd-party packages ----------------------------------

IMAGE_INSTALL:append = " \
    firefox \
    vlc \
    gstreamer1.0 \
    gstreamer1.0-plugins-base \
    gstreamer1.0-plugins-good \
    gstreamer1.0-plugins-bad \
    gstreamer1.0-libav \
    curl wget git rsync \
    htop iotop iftop \
    vim nano \
    unzip zip tar \
    sudo \
"

# --- NVIDIA container / docker integration ---------------------------------

IMAGE_INSTALL:append = " \
    docker-ce \
    nvidia-container-toolkit \
"

# --- Distro features --------------------------------------------------------

DISTRO_FEATURES:append = " x11 systemd opengl vulkan virtualization"
DISTRO_FEATURES_BACKFILL_CONSIDERED:append = " sysvinit"
VIRTUAL-RUNTIME_init_manager = "systemd"
VIRTUAL-RUNTIME_initscripts = "systemd-compat-units"

# Headless — no locales installed, but keep default terminal charset.
IMAGE_LINGUAS = ""

# Boot straight to multi-user; xrdp will spawn XFCE per RDP connection.
# Retain getty@tty1 for physical-console emergency login.
SYSTEMD_DEFAULT_TARGET = "multi-user.target"

# Reserve headroom for CUDA (~3GB), skills data, npm cache, workspace.
IMAGE_ROOTFS_EXTRA_SPACE = "6291456"

# Service users + pi login shell (pattern inherited from Pi images).
inherit extrausers
EXTRA_USERS_PARAMS = " \
    useradd -r -d /var/lib/zeroclaw -s /usr/sbin/nologin zeroclaw; \
    useradd -r -d /var/lib/nemoclaw -s /usr/sbin/nologin nemoclaw; \
    useradd -m -s /bin/bash -G sudo,docker,video,audio -p '!' pi; \
"
