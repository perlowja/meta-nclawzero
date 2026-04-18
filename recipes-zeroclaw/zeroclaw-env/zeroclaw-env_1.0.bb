# SPDX-FileCopyrightText: Copyright (c) 2026 Jason Perlow. All rights reserved.
# SPDX-License-Identifier: Apache-2.0
#
# zeroclaw-env — ships an empty /etc/zeroclaw/env on the image so operators
# can drop API keys in place at runtime without the daemon failing to start.
# zeroclaw.service already references it as EnvironmentFile=-/etc/zeroclaw/env.

SUMMARY = "ZeroClaw runtime environment file (empty stub)"
LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/Apache-2.0;md5=89aea4e17d99a7cacdbeed46a0096b10"

SRC_URI = "file://env.in"

S = "${WORKDIR}"

RDEPENDS:${PN} = "zeroclaw-bin"

do_install() {
    install -d ${D}${sysconfdir}/zeroclaw
    install -m 0600 ${WORKDIR}/env.in ${D}${sysconfdir}/zeroclaw/env
}

# Must be owned by zeroclaw:zeroclaw on the target so the daemon can read it.
pkg_postinst_ontarget:${PN}() {
    chown zeroclaw:zeroclaw /etc/zeroclaw/env || true
    chmod 0600 /etc/zeroclaw/env || true
}

FILES:${PN} = "${sysconfdir}/zeroclaw/env"
CONFFILES:${PN} = "${sysconfdir}/zeroclaw/env"
