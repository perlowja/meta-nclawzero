# SPDX-FileCopyrightText: Copyright (c) 2026 Jason Perlow. All rights reserved.
# SPDX-License-Identifier: BSD-3-Clause
#
# Tailscale — mesh VPN for remote access to nclawzero devices.
# Fetches the pre-built arm64 Go binary tarball from pkgs.tailscale.com.

SUMMARY = "Tailscale mesh VPN"
DESCRIPTION = "Tailscale daemon + CLI, pre-built from upstream. \
    Enables private mesh networking across home lab + remote Pi nodes."
HOMEPAGE = "https://tailscale.com"
LICENSE = "BSD-3-Clause"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/BSD-3-Clause;md5=550794465ba0ec5312d6919e203a55f9"

TAILSCALE_VERSION = "1.96.4"

SRC_URI = " \
    https://pkgs.tailscale.com/stable/tailscale_${TAILSCALE_VERSION}_arm64.tgz;name=bin \
    file://tailscaled.service \
"

SRC_URI[bin.sha256sum] = "a27249bc70d7b37a68f8be7f5c4507ea5f354e592dce43cb5d4f3e742b313c3c"

S = "${WORKDIR}/tailscale_${TAILSCALE_VERSION}_arm64"

COMPATIBLE_HOST = "aarch64.*-linux"

inherit systemd

SYSTEMD_SERVICE:${PN} = "tailscaled.service"
# Disabled by default — operator opts in via `systemctl enable --now tailscaled`
SYSTEMD_AUTO_ENABLE = "disable"

do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${S}/tailscale ${D}${bindir}/tailscale
    install -m 0755 ${S}/tailscaled ${D}${sbindir}/tailscaled 2>/dev/null || \
        install -d ${D}${sbindir} && install -m 0755 ${S}/tailscaled ${D}${sbindir}/tailscaled

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/tailscaled.service ${D}${systemd_system_unitdir}/

    install -d ${D}/var/lib/tailscale
}

FILES:${PN} = " \
    ${bindir}/tailscale \
    ${sbindir}/tailscaled \
    ${systemd_system_unitdir}/tailscaled.service \
    /var/lib/tailscale \
"

INSANE_SKIP:${PN} = "already-stripped ldflags"
