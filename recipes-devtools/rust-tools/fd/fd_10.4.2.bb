# SPDX-FileCopyrightText: Copyright (c) 2026 Jason Perlow. All rights reserved.
# SPDX-License-Identifier: MIT
#
# fd — simple, fast alternative to find. Rust binary, pre-built aarch64 release.

SUMMARY = "Simple, fast, user-friendly alternative to find"
HOMEPAGE = "https://github.com/sharkdp/fd"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

FD_VERSION = "10.4.2"
SRC_URI = "https://github.com/sharkdp/fd/releases/download/v${FD_VERSION}/fd-v${FD_VERSION}-aarch64-unknown-linux-gnu.tar.gz;name=bin"
SRC_URI[bin.sha256sum] = "6c51f7c5446b3338b1e401ff15dc194c590bb2fa64fd43ff3278300f073adec5"

S = "${WORKDIR}/fd-v${FD_VERSION}-aarch64-unknown-linux-gnu"

COMPATIBLE_HOST = "aarch64.*-linux"

do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${S}/fd ${D}${bindir}/fd
    install -d ${D}${mandir}/man1
    install -m 0644 ${S}/fd.1 ${D}${mandir}/man1/ 2>/dev/null || true
}

FILES:${PN} = "${bindir}/fd ${mandir}/man1/fd.1"

INSANE_SKIP:${PN} = "already-stripped ldflags"
