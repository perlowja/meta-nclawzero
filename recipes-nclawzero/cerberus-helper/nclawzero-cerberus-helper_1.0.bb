# SPDX-FileCopyrightText: Copyright (c) 2026 Jason Perlow. All rights reserved.
# SPDX-License-Identifier: Apache-2.0
#
# nclawzero-cerberus-helper — idempotent wrapper to query a local OpenAI-
# compatible inference endpoint (CERBERUS). Config-driven, re-runnable,
# no credentials. Demonstrates the tier-1 (local consultative) option
# alongside the default cloud-consultative path.

SUMMARY = "CERBERUS inference helper (idempotent curl wrapper)"
LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/Apache-2.0;md5=89aea4e17d99a7cacdbeed46a0096b10"

SRC_URI = " \
    file://cerberus.conf \
    file://ask-cerberus \
"

S = "${WORKDIR}"

RDEPENDS:${PN} = "curl python3"

do_install() {
    install -d ${D}${sysconfdir}/nclawzero
    install -m 0644 ${WORKDIR}/cerberus.conf ${D}${sysconfdir}/nclawzero/cerberus.conf

    install -d ${D}${bindir}
    install -m 0755 ${WORKDIR}/ask-cerberus ${D}${bindir}/ask-cerberus
}

FILES:${PN} = " \
    ${sysconfdir}/nclawzero/cerberus.conf \
    ${bindir}/ask-cerberus \
"

CONFFILES:${PN} = "${sysconfdir}/nclawzero/cerberus.conf"
