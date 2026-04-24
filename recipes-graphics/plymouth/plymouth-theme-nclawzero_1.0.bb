# SPDX-FileCopyrightText: Copyright (c) 2026 Jason Perlow. All rights reserved.
# SPDX-License-Identifier: Apache-2.0

SUMMARY = "nclawzero plymouth boot splash theme (pure-text, script plugin)"
DESCRIPTION = "Centered 'nclawzero' wordmark + animated progress dots on \
near-black background. No PNG assets — uses plymouth's script-plugin text \
renderer for compatibility with Tegra DRM and any other framebuffer."
LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/Apache-2.0;md5=89aea4e17d99a7cacdbeed46a0096b10"

SRC_URI = " \
    file://nclawzero.plymouth \
    file://nclawzero.script \
"

S = "${WORKDIR}"

RDEPENDS:${PN} = "plymouth"

do_install() {
    install -d -m 0755 ${D}${datadir}/plymouth/themes/nclawzero
    install -m 0644 ${WORKDIR}/nclawzero.plymouth \
        ${D}${datadir}/plymouth/themes/nclawzero/nclawzero.plymouth
    install -m 0644 ${WORKDIR}/nclawzero.script \
        ${D}${datadir}/plymouth/themes/nclawzero/nclawzero.script
}

FILES:${PN} = "${datadir}/plymouth/themes/nclawzero"

# Default theme selection — plymouth-set-default-theme is invoked post-install.
pkg_postinst_ontarget:${PN} () {
    if [ -x /usr/sbin/plymouth-set-default-theme ]; then
        /usr/sbin/plymouth-set-default-theme nclawzero || true
    fi
}
