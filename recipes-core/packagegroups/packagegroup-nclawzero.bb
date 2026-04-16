# SPDX-FileCopyrightText: Copyright (c) 2026 Jason Perlow. All rights reserved.
# SPDX-License-Identifier: Apache-2.0

SUMMARY = "nclawzero agent stack — ZeroClaw + NemoClaw + runtime deps"
LICENSE = "MIT"

inherit packagegroup

RDEPENDS:${PN} = " \
    zeroclaw-bin \
    nemoclaw \
    nodejs \
    ca-certificates \
    curl \
    openssh-server \
    openssh-client \
    htop \
    nano \
    less \
"
