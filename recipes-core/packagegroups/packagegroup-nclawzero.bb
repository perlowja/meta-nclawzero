# SPDX-FileCopyrightText: Copyright (c) 2026 Jason Perlow. All rights reserved.
# SPDX-License-Identifier: Apache-2.0

SUMMARY = "nclawzero agent stack — ZeroClaw + NemoClaw + Docker + skill-exec utilities"
LICENSE = "MIT"

inherit packagegroup

# Shared across both image variants.
RDEPENDS:${PN} = " \
    zeroclaw-bin \
    zeroclaw-env \
    nclawzero-cerberus-helper \
    nodejs-bin \
    nemoclaw-firstboot \
    ca-certificates \
    openssh-sftp-server \
    openssh-sshd \
    openssh-ssh \
    openssh-scp \
    docker-ce \
    docker-ce-cli \
    containerd-opencontainers \
    curl \
    wget \
    git \
    jq \
    rsync \
    tmux \
    vim \
    nano \
    htop \
    less \
    tree \
    lsof \
    strace \
    iperf3 \
    mtr \
    tcpdump \
    bind-utils \
    python3 \
    python3-pip \
"
