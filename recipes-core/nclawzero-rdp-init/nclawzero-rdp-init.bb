# SPDX-FileCopyrightText: Copyright (c) 2026 Jason Perlow. All rights reserved.
# SPDX-License-Identifier: Apache-2.0
#
# First-boot service that:
#   - Generates self-signed TLS cert for Weston RDP backend
#   - Sets ncz user password from /etc/nclawzero/initial-password (seeded
#     at image build; default 'zeroclaw' if missing — CHANGE IN PROD)
#     Falls back to legacy pi user on already-deployed pre-rename images.
#   - Marks /var/lib/nclawzero/rdp-init.done to prevent re-run

SUMMARY = "nclawzero first-boot RDP/TLS setup"
LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/Apache-2.0;md5=89aea4e17d99a7cacdbeed46a0096b10"

SRC_URI = " \
    file://nclawzero-rdp-init.sh \
    file://nclawzero-rdp-init.service \
    file://initial-password \
"

S = "${WORKDIR}"

RDEPENDS:${PN} = "openssl-bin shadow bash"

inherit systemd allarch

SYSTEMD_SERVICE:${PN} = "nclawzero-rdp-init.service"
SYSTEMD_AUTO_ENABLE:${PN} = "enable"

do_install() {
    install -Dm0755 ${WORKDIR}/nclawzero-rdp-init.sh \
        ${D}${bindir}/nclawzero-rdp-init
    install -Dm0644 ${WORKDIR}/nclawzero-rdp-init.service \
        ${D}${systemd_system_unitdir}/nclawzero-rdp-init.service
    install -d ${D}${sysconfdir}/nclawzero
    install -Dm0600 ${WORKDIR}/initial-password \
        ${D}${sysconfdir}/nclawzero/initial-password
}

FILES:${PN} = " \
    ${bindir}/nclawzero-rdp-init \
    ${systemd_system_unitdir}/nclawzero-rdp-init.service \
    ${sysconfdir}/nclawzero/initial-password \
"
