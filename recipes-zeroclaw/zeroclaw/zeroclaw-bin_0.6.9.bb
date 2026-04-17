# SPDX-FileCopyrightText: Copyright (c) 2026 Jason Perlow. All rights reserved.
# SPDX-License-Identifier: Apache-2.0
#
# ZeroClaw — pre-built Rust AI agent runtime binary
#
# Fetches the official aarch64 release binary from GitHub Releases.
# SHA256 verified. No Rust toolchain needed at build time.

SUMMARY = "ZeroClaw AI agent runtime (pre-built binary)"
DESCRIPTION = "Rust-based AI agent runtime with low memory footprint. \
    Runs at ~17MB RSS on Raspberry Pi 4. OpenAI-compatible API on port 42617."
HOMEPAGE = "https://github.com/zeroclaw-labs/zeroclaw"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

ZEROCLAW_VERSION = "0.6.9"

SRC_URI = " \
    https://github.com/zeroclaw-labs/zeroclaw/releases/download/v${ZEROCLAW_VERSION}/zeroclaw-aarch64-unknown-linux-gnu.tar.gz;name=bin \
    file://zeroclaw.service \
    file://zeroclaw.toml \
"

SRC_URI[bin.sha256sum] = "25e5a50a2870cfab14a2767d66650b188ca0ccbb38d9e895dd09b6d7399d73f6"

COMPATIBLE_HOST = "aarch64.*-linux"

inherit systemd

SYSTEMD_SERVICE:${PN} = "zeroclaw.service"
SYSTEMD_AUTO_ENABLE = "enable"

do_install() {
    # Binary
    install -d ${D}${bindir}
    install -m 0755 ${WORKDIR}/zeroclaw ${D}${bindir}/zeroclaw

    # Configuration
    install -d ${D}${sysconfdir}/zeroclaw
    install -m 0600 ${WORKDIR}/zeroclaw.toml ${D}${sysconfdir}/zeroclaw/

    # Data directories
    install -d ${D}/var/lib/zeroclaw
    install -d ${D}/var/lib/zeroclaw/skills
    install -d ${D}/var/lib/zeroclaw/workspace

    # Systemd service
    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/zeroclaw.service ${D}${systemd_system_unitdir}/
}

FILES:${PN} = " \
    ${bindir}/zeroclaw \
    ${sysconfdir}/zeroclaw \
    /var/lib/zeroclaw \
    ${systemd_system_unitdir}/zeroclaw.service \
"

CONFFILES:${PN} = "${sysconfdir}/zeroclaw/zeroclaw.toml"

# Pre-built binary is already stripped by upstream release process
INSANE_SKIP:${PN} = "already-stripped"
