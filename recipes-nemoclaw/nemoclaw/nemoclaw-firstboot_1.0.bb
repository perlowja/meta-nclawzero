# SPDX-FileCopyrightText: Copyright (c) 2026 Jason Perlow. All rights reserved.
# SPDX-License-Identifier: Apache-2.0
#
# NemoClaw first-boot provisioning — clones and installs NemoClaw on first boot.
# Approach A: lightweight image, provisions from network on first boot.

SUMMARY = "NemoClaw first-boot provisioner"
LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/Apache-2.0;md5=89aea4e17d99a7cacdbeed46a0096b10"

SRC_URI = " \
    file://nemoclaw-firstboot.sh \
    file://nemoclaw-firstboot.service \
    file://0001-fix-snapshot-symlink-protection.patch \
    file://0002-fix-config-file-permissions.patch \
    file://0003-feat-agent-defs-zeroclaw.patch \
    file://nemoclaw.conf \
"

inherit systemd

SYSTEMD_SERVICE:${PN} = "nemoclaw-firstboot.service"
SYSTEMD_AUTO_ENABLE = "enable"

RDEPENDS:${PN} = "nodejs-bin git"

do_install() {
    # Provisioning script
    install -d ${D}${bindir}
    install -m 0755 ${WORKDIR}/nemoclaw-firstboot.sh ${D}${bindir}/nemoclaw-firstboot.sh

    # Systemd service
    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/nemoclaw-firstboot.service ${D}${systemd_system_unitdir}/

    # Patches for first-boot apply
    install -d ${D}${sysconfdir}/nemoclaw/patches
    for p in ${WORKDIR}/0*.patch; do
        [ -f "$p" ] && install -m 0644 "$p" ${D}${sysconfdir}/nemoclaw/patches/
    done

    # Config
    install -d ${D}${sysconfdir}/nemoclaw
    install -m 0600 ${WORKDIR}/nemoclaw.conf ${D}${sysconfdir}/nemoclaw/

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
