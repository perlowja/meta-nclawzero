# SPDX-FileCopyrightText: Copyright (c) 2026 Jason Perlow. All rights reserved.
# SPDX-License-Identifier: Apache-2.0

SUMMARY = "nclawzero agent stack — ZeroClaw + NemoClaw + runtime deps"
LICENSE = "MIT"

inherit packagegroup

# Phase 3: Full nclawzero stack — ZeroClaw + Node.js + Python + base OS
RDEPENDS:${PN} = " \
    zeroclaw-bin \
    nodejs-bin \
    ca-certificates \
    curl \
    git \
    openssh-sftp-server \
    openssh-sshd \
    openssh-ssh \
    openssh-scp \
    htop \
    nano \
    less \
    python3 \
    python3-pip \
"
