# SPDX-FileCopyrightText: Copyright (c) 2026 Jason Perlow. All rights reserved.
# SPDX-License-Identifier: ISC
#
# starship — cross-shell prompt. Rust binary, pre-built aarch64 musl
# (static; runs on glibc systems without dependencies).

SUMMARY = "The minimal, blazing-fast, and infinitely customizable prompt"
HOMEPAGE = "https://starship.rs"
LICENSE = "ISC"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/ISC;md5=f3b90e78ea0cffb20bf5cca7947a896d"

STARSHIP_VERSION = "1.25.0"
SRC_URI = "https://github.com/starship/starship/releases/download/v${STARSHIP_VERSION}/starship-aarch64-unknown-linux-musl.tar.gz;name=bin"
SRC_URI[bin.sha256sum] = "68ffcb75582e5ed336b43598bb4d8ecc4ec994ea26eac7955d3d378f1375da34"

S = "${WORKDIR}"

COMPATIBLE_HOST = "aarch64.*-linux"

do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${S}/starship ${D}${bindir}/starship
}

FILES:${PN} = "${bindir}/starship"

INSANE_SKIP:${PN} = "already-stripped ldflags"
