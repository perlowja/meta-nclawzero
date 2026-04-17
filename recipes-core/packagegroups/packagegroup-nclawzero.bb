# SPDX-FileCopyrightText: Copyright (c) 2026 Jason Perlow. All rights reserved.
# SPDX-License-Identifier: Apache-2.0

SUMMARY = "nclawzero agent stack — ZeroClaw + NemoClaw + runtime deps"
LICENSE = "MIT"

inherit packagegroup

# Phase 1+2: Base OS + ZeroClaw binary + runtime deps
# Phase 3: Add nemoclaw once patches are validated against current upstream
RDEPENDS:${PN} = " \
    zeroclaw-bin \
    ca-certificates \
    curl \
    openssh-sftp-server \
    openssh-sshd \
    openssh-ssh \
    openssh-scp \
    htop \
    nano \
    less \
    python3 \
"
