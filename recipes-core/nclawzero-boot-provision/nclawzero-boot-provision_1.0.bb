# SPDX-FileCopyrightText: Copyright (c) 2026 Jason Perlow. All rights reserved.
# SPDX-License-Identifier: Apache-2.0
#
# nclawzero-boot-provision — first-boot sentinel processor (pi-gen pattern).
#
# Provides a oneshot systemd service that runs early in boot and applies
# operator-dropped sentinel files from /boot/firmware (or /boot fallback)
# such as `ssh`, `userconf.txt`, `wpa_supplicant.conf`, `authorized_keys`,
# and `firstrun.sh`. Mirrors the pi-gen / Raspberry Pi Imager first-boot
# pattern so workstation operators can pre-configure a device before
# flashing.
#
# Doctrine: applied sentinels are RENAMED to <name>.applied-<timestamp>
# (not deleted) so post-mortem inspection is possible without ripping the
# SD. On at least one successful application the service masks itself so
# subsequent boots are no-ops.
#
# See files/nclawzero-boot-provision.sh for the full processor logic and
# files/nclawzero-boot-provision.service for the unit ordering.

SUMMARY = "nclawzero first-boot sentinel processor (pi-gen-style)"
DESCRIPTION = "Systemd oneshot that applies operator-dropped sentinel \
    files (ssh, userconf.txt, wpa_supplicant.conf, authorized_keys, \
    firstrun.sh) from /boot/firmware on first boot. Mirrors pi-gen \
    stage2/04-userconf and raspi-config first-boot conventions."
LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/Apache-2.0;md5=89aea4e17d99a7cacdbeed46a0096b10"

SRC_URI = " \
    file://nclawzero-boot-provision.sh \
    file://nclawzero-boot-provision.service \
"

S = "${WORKDIR}"

inherit systemd allarch

SYSTEMD_SERVICE:${PN} = "nclawzero-boot-provision.service"
SYSTEMD_AUTO_ENABLE:${PN} = "enable"

# Hard runtime deps:
#   - bash for the script shebang
#   - shadow for useradd / chpasswd
#   - util-linux already present (install, getent come from coreutils/glibc)
#   - systemd for systemctl
RDEPENDS:${PN} = " \
    bash \
    shadow \
    systemd \
"

do_install() {
    install -d -m 0755 ${D}${libexecdir}/nclawzero
    install -m 0755 ${WORKDIR}/nclawzero-boot-provision.sh \
        ${D}${libexecdir}/nclawzero/boot-provision.sh

    install -d -m 0755 ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/nclawzero-boot-provision.service \
        ${D}${systemd_system_unitdir}/nclawzero-boot-provision.service
}

FILES:${PN} = " \
    ${libexecdir}/nclawzero/boot-provision.sh \
    ${systemd_system_unitdir}/nclawzero-boot-provision.service \
"
