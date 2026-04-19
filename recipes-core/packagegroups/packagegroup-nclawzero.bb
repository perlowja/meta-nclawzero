# SPDX-FileCopyrightText: Copyright (c) 2026 Jason Perlow. All rights reserved.
# SPDX-License-Identifier: Apache-2.0

SUMMARY = "nclawzero agent stack — ZeroClaw + NemoClaw + Docker + terminal UX"
LICENSE = "MIT"

inherit packagegroup

# Core agent stack + runtime
RDEPENDS:${PN} = " \
    zeroclaw-bin \
    zeroclaw-env \
    nclawzero-cerberus-helper \
    nodejs-bin \
    nemoclaw-firstboot \
    nemoclaw-core \
    ca-certificates \
    openssh-sftp-server \
    openssh-sshd \
    openssh-ssh \
    openssh-scp \
    tailscale \
    curl \
    wget \
    bash-completion \
"

# Per-skill container sandbox runtime (ZeroClaw spawns ephemeral alpine
# containers per skill-tool execution — see crates/zeroclaw-runtime/src/
# security/docker.rs in zeroclaw upstream).
RDEPENDS:${PN} += " \
    docker-moby \
    containerd-opencontainers \
"

# AI/agent plumbing — JSON, git, session mgmt, local state, Python SDKs
RDEPENDS:${PN} += " \
    jq \
    git \
    bat \
    fd \
    starship \
    rsync \
    tmux \
    vim \
    nano \
    less \
    sqlite3 \
    python3 \
    python3-pip \
    python3-virtualenv \
    llama-cpp \
"

# Process / system monitoring
RDEPENDS:${PN} += " \
    zram \
    htop \
    iotop \
    iftop \
    mtr \
    lsof \
    strace \
    sysstat \
"

# Network diagnostics + MQTT client (inference event buses)
RDEPENDS:${PN} += " \
    netcat-openbsd \
    socat \
    nmap \
    tcpdump \
    bind-utils \
    iperf3 \
    mosquitto-clients \
"

# File / disk
RDEPENDS:${PN} += " \
    mc \
    tree \
    pv \
    file \
"

# TODO — not in poky/meta-oe/meta-python scarthgap; need extra layers or custom recipes:
#   Rust tools (need meta-rust-bin or custom cargo recipes):
#     ripgrep, fd, bat, websocat, starship, direnv, duf, procs
#   Go tools (need meta-virtualization or custom go recipes):
#     lazygit, fzf, grpcurl, git-lfs, tailscale
#   Python (not in meta-python scarthgap):
#     fail2ban, httpie
#   Miscellaneous not packaged:
#     ncdu, moreutils, inxi
#   AI/inference local (custom recipe needed):
#     llama-cpp
