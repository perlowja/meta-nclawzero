# SPDX-FileCopyrightText: Copyright (c) 2026 Jason Perlow. All rights reserved.
# SPDX-License-Identifier: Apache-2.0
#
# ZeroClaw — pre-built Rust AI agent runtime binary
#
# Fetches the official aarch64 release binary + bundled web dashboard from
# GitHub Releases. SHA256 verified. No Rust toolchain needed at build time.
#
# Tracking the latest tagged beta from master (0.7.3) because the schema
# improvements on this track — first-class [runtime] section + clean
# SandboxBackend enum — are what nclawzero's config.toml depends on.
# Upgrade to stable 1.0 once released.

SUMMARY = "ZeroClaw AI agent runtime (pre-built binary + web dashboard)"
DESCRIPTION = "Rust-based AI agent runtime with low memory footprint. \
    Runs at ~22MB RSS on Raspberry Pi 4. OpenAI-compatible API + Web UI on port 42617."
HOMEPAGE = "https://github.com/zeroclaw-labs/zeroclaw"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

ZEROCLAW_VERSION = "0.7.3-beta.1051"

SRC_URI = " \
    https://github.com/zeroclaw-labs/zeroclaw/releases/download/v${ZEROCLAW_VERSION}/zeroclaw-aarch64-unknown-linux-gnu.tar.gz;name=bin \
    file://zeroclaw.service \
    file://config.toml \
"

SRC_URI[bin.sha256sum] = "2eb7fa9699e3e6064f7c882aa67f4f5fadc4002e1cdc588d924395d344e3bb3c"

COMPATIBLE_HOST = "aarch64.*-linux"

inherit systemd

SYSTEMD_SERVICE:${PN} = "zeroclaw.service"
SYSTEMD_AUTO_ENABLE = "enable"

do_install() {
    # Binary
    install -d ${D}${bindir}
    install -m 0755 ${WORKDIR}/zeroclaw ${D}${bindir}/zeroclaw

    # Web dashboard (bundled in the release tarball since 0.7.x)
    install -d ${D}${datadir}/zeroclaw
    cp -r ${WORKDIR}/web/dist ${D}${datadir}/zeroclaw/web-dist
    chmod -R a+rX ${D}${datadir}/zeroclaw/web-dist

    # Configuration
    install -d ${D}${sysconfdir}/zeroclaw
    install -m 0600 ${WORKDIR}/config.toml ${D}${sysconfdir}/zeroclaw/

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
    ${datadir}/zeroclaw \
    ${sysconfdir}/zeroclaw \
    /var/lib/zeroclaw \
    ${systemd_system_unitdir}/zeroclaw.service \
"

CONFFILES:${PN} = "${sysconfdir}/zeroclaw/config.toml"

# Pre-built binary is already stripped by upstream release process
INSANE_SKIP:${PN} = "already-stripped"

# Fix ownership on first-boot so the zeroclaw user (created via rootfs
# postprocess useradd) can read its config and write its state dir.
# pkg_postinst_ontarget runs on first boot via run-postinsts.service,
# which is ordered before multi-user.target (zeroclaw.service).
pkg_postinst_ontarget:${PN}() {
    chown -R zeroclaw:zeroclaw /var/lib/zeroclaw || true
    chown zeroclaw:zeroclaw /etc/zeroclaw/config.toml || true
    chmod 0600 /etc/zeroclaw/config.toml || true
}
