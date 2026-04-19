# SPDX-FileCopyrightText: Copyright (c) 2026 Jason Perlow. All rights reserved.
# SPDX-License-Identifier: MIT
#
# bat — cat clone with syntax highlighting. Rust binary, pre-built aarch64 release.

SUMMARY = "cat clone with syntax highlighting and Git integration"
HOMEPAGE = "https://github.com/sharkdp/bat"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

BAT_VERSION = "0.26.1"
SRC_URI = "https://github.com/sharkdp/bat/releases/download/v${BAT_VERSION}/bat-v${BAT_VERSION}-aarch64-unknown-linux-gnu.tar.gz;name=bin"
SRC_URI[bin.sha256sum] = "422eb73e11c854fddd99f5ca8461c2f1d6e6dce0a2a8c3d5daade5ffcb6564aa"

S = "${WORKDIR}/bat-v${BAT_VERSION}-aarch64-unknown-linux-gnu"

COMPATIBLE_HOST = "aarch64.*-linux"

do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${S}/bat ${D}${bindir}/bat
    install -d ${D}${mandir}/man1
    install -m 0644 ${S}/bat.1 ${D}${mandir}/man1/ 2>/dev/null || true
}

FILES:${PN} = "${bindir}/bat ${mandir}/man1/bat.1"

INSANE_SKIP:${PN} = "already-stripped ldflags"
