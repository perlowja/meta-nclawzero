# SPDX-FileCopyrightText: Copyright (c) 2026 Jason Perlow. All rights reserved.
# SPDX-License-Identifier: Apache-2.0

SUMMARY = "nclawzero agent stack — ZeroClaw + NemoClaw + runtime deps"
LICENSE = "MIT"

inherit packagegroup

# Phase 1: Base OS + runtime deps (no zeroclaw/nemoclaw yet — those need recipe fixes)
# Phase 2: Add zeroclaw-bin once SHA256 is verified
# Phase 3: Add nemoclaw once patches are validated against current upstream
RDEPENDS:${PN} = " \
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
