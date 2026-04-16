# SPDX-FileCopyrightText: Copyright (c) 2026 Jason Perlow. All rights reserved.
# SPDX-License-Identifier: Apache-2.0
#
# NemoClaw — Node.js sandbox framework for AI agents
#
# Fetches upstream NemoClaw from GitHub, applies nclawzero patches
# (ZeroClaw agent support, security hardening, config fixes),
# and copies overlay files (ZeroClaw agent definition, test harness, scripts).

SUMMARY = "NemoClaw sandbox framework with ZeroClaw agent support"
DESCRIPTION = "NVIDIA NemoClaw provides sandboxed execution environments \
    for AI agent runtimes. This recipe adds ZeroClaw agent integration \
    via the nclawzero patchset."
HOMEPAGE = "https://github.com/NVIDIA/NemoClaw"
LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://LICENSE;md5=86d3f3a95c324c9479bd8986968f4327"

SRC_URI = " \
    git://github.com/NVIDIA/NemoClaw.git;protocol=https;branch=main \
    file://0001-fix-snapshot-symlink-protection.patch \
    file://0002-fix-config-file-permissions.patch \
    file://0003-feat-agent-defs-zeroclaw.patch \
    file://nemoclaw.service \
    file://nemoclaw.conf \
    file://overlay/ \
"

# Pin to a known-good upstream commit for reproducible builds.
# Update this when rebasing patches against new upstream.
SRCREV = "${AUTOREV}"
# Production: pin to specific commit
# SRCREV = "c333d96..."

PV = "1.0+git"
S = "${WORKDIR}/git"

DEPENDS = "nodejs-native"
RDEPENDS:${PN} = "nodejs zeroclaw-bin"

inherit systemd

SYSTEMD_SERVICE:${PN} = "nemoclaw.service"
SYSTEMD_AUTO_ENABLE = "enable"

do_compile() {
    cd ${S}
    # Production npm install — no dev deps, no optional native addons
    npm install --production --no-optional --ignore-scripts \
        --target_arch=${TARGET_ARCH} \
        --target_platform=linux

    # Build the nemoclaw plugin
    if [ -d "nemoclaw" ]; then
        cd nemoclaw
        npm install --production --no-optional --ignore-scripts
        cd ..
    fi
}

do_install() {
    # Install the NemoClaw application
    install -d ${D}/opt/nemoclaw
    cp -R ${S}/bin ${D}/opt/nemoclaw/
    cp -R ${S}/dist ${D}/opt/nemoclaw/ 2>/dev/null || true
    cp -R ${S}/src ${D}/opt/nemoclaw/
    cp -R ${S}/nemoclaw ${D}/opt/nemoclaw/
    cp -R ${S}/nemoclaw-blueprint ${D}/opt/nemoclaw/
    cp -R ${S}/agents ${D}/opt/nemoclaw/
    cp -R ${S}/node_modules ${D}/opt/nemoclaw/
    cp ${S}/package.json ${D}/opt/nemoclaw/

    # Remove dev artifacts
    rm -rf ${D}/opt/nemoclaw/.git
    rm -rf ${D}/opt/nemoclaw/test
    rm -rf ${D}/opt/nemoclaw/.github
    rm -rf ${D}/opt/nemoclaw/docs

    # Copy overlay files on top — ZeroClaw agent, scripts, configs
    if [ -d "${WORKDIR}/overlay" ]; then
        cp -R ${WORKDIR}/overlay/* ${D}/opt/nemoclaw/ 2>/dev/null || true
    fi

    # Configuration
    install -d ${D}${sysconfdir}/nemoclaw
    install -m 0600 ${WORKDIR}/nemoclaw.conf ${D}${sysconfdir}/nemoclaw/

    # Data directories
    install -d ${D}/var/lib/nemoclaw
    install -d ${D}/var/lib/nemoclaw/snapshots
    install -d ${D}/var/lib/nemoclaw/sandboxes

    # Systemd service
    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/nemoclaw.service ${D}${systemd_system_unitdir}/
}

FILES:${PN} = " \
    /opt/nemoclaw \
    ${sysconfdir}/nemoclaw \
    /var/lib/nemoclaw \
    ${systemd_system_unitdir}/nemoclaw.service \
"

CONFFILES:${PN} = "${sysconfdir}/nemoclaw/nemoclaw.conf"

# Node.js modules contain pre-built native addons
INSANE_SKIP:${PN} = "already-stripped ldflags file-rdeps"
