# SPDX-FileCopyrightText: Copyright (c) 2026 Jason Perlow. All rights reserved.
# SPDX-License-Identifier: Apache-2.0
#
# NemoClaw first-boot provisioner — materialises node_modules + Claude Code
# on top of the pre-cloned source tree that the nemoclaw-core recipe ships.
#
# Patches are now applied at Yocto build time via nemoclaw-core's SRC_URI,
# not at runtime.

SUMMARY = "NemoClaw first-boot provisioner"
LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/Apache-2.0;md5=89aea4e17d99a7cacdbeed46a0096b10"

SRC_URI = " \
    file://nemoclaw-firstboot.sh \
    file://nemoclaw-firstboot.service \
    file://nemoclaw.conf \
    file://0001-fix-snapshot-symlink-protection.patch;apply=no \
    file://0002-fix-config-file-permissions.patch;apply=no \
    file://0003-feat-agent-defs-zeroclaw.patch;apply=no \
"

PATCHFILES = " \
    0001-fix-snapshot-symlink-protection.patch \
    0002-fix-config-file-permissions.patch \
    0003-feat-agent-defs-zeroclaw.patch \
"

inherit systemd

SYSTEMD_SERVICE:${PN} = "nemoclaw-firstboot.service"
SYSTEMD_AUTO_ENABLE = "enable"

RDEPENDS:${PN} = "nemoclaw-core nodejs-bin git bash"

do_install() {
    # Provisioning script
    install -d ${D}${bindir}
    install -m 0755 ${WORKDIR}/nemoclaw-firstboot.sh ${D}${bindir}/nemoclaw-firstboot.sh

    # Systemd service
    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/nemoclaw-firstboot.service ${D}${systemd_system_unitdir}/

    # Config
    install -d ${D}${sysconfdir}/nemoclaw
    install -m 0600 ${WORKDIR}/nemoclaw.conf ${D}${sysconfdir}/nemoclaw/

    # Patches shipped for runtime apply by nemoclaw-firstboot.sh.
    # Applied with graceful failure — a hunk that does not apply against
    # upstream drift is logged but does not block provisioning.
    install -d ${D}${sysconfdir}/nemoclaw/patches
    for p in ${PATCHFILES}; do
        install -m 0644 ${WORKDIR}/$p ${D}${sysconfdir}/nemoclaw/patches/
    done

    # Data dir
    install -d ${D}/var/lib/nemoclaw
}

FILES:${PN} = " \
    ${bindir}/nemoclaw-firstboot.sh \
    ${systemd_system_unitdir}/nemoclaw-firstboot.service \
    ${sysconfdir}/nemoclaw \
    /var/lib/nemoclaw \
"

CONFFILES:${PN} = "${sysconfdir}/nemoclaw/nemoclaw.conf"
