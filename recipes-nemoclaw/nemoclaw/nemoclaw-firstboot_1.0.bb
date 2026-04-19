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
