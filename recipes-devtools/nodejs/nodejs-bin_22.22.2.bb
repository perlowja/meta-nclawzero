# SPDX-FileCopyrightText: Copyright (c) 2026 Jason Perlow. All rights reserved.
# SPDX-License-Identifier: Apache-2.0
#
# Node.js 22 LTS — pre-built binary for aarch64.
# NemoClaw requires node >= 22.16.0.

SUMMARY = "Node.js 22 LTS JavaScript runtime (pre-built binary)"
HOMEPAGE = "https://nodejs.org"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "https://nodejs.org/dist/v${PV}/node-v${PV}-linux-arm64.tar.xz;name=node"
SRC_URI[node.sha256sum] = "e9e1930fd321a470e29bb68f30318bf58e3ecb4acb4f1533fb19c58328a091fe"

S = "${WORKDIR}/node-v${PV}-linux-arm64"

COMPATIBLE_HOST = "aarch64.*-linux"

do_install() {
    install -d ${D}${prefix}
    cp -R ${S}/bin ${D}${prefix}/
    cp -R ${S}/lib ${D}${prefix}/
    cp -R ${S}/include ${D}${prefix}/
    cp -R ${S}/share ${D}${prefix}/ 2>/dev/null || true

    # Symlink node and npm to standard paths
    install -d ${D}${bindir}
    ln -sf ../lib/node_modules/npm/bin/npm-cli.js ${D}${bindir}/npm 2>/dev/null || true
}

FILES:${PN} = " \
    ${prefix}/bin \
    ${prefix}/lib \
    ${prefix}/include \
    ${prefix}/share \
    ${bindir}/npm \
"

RDEPENDS:${PN} = "libstdc++"
INSANE_SKIP:${PN} = "already-stripped file-rdeps ldflags"
INHIBIT_PACKAGE_STRIP = "1"
INHIBIT_SYSROOT_STRIP = "1"
