# SPDX-FileCopyrightText: Copyright (c) 2026 Jason Perlow. All rights reserved.
# SPDX-License-Identifier: Apache-2.0
#
# NemoClaw core source — pre-cloned from upstream at Yocto build time.
#
# Why pre-clone rather than clone-at-first-boot:
#   1. Works offline on first boot (no network required)
#   2. Reproducible: the exact tree is pinned via SRCREV below
#   3. Faster first-boot provisioning (~5 min → ~30s)
#
# The first-boot script (nemoclaw-firstboot) will git fetch + reset --hard
# origin/main when network is reachable, so devices flashed weeks after build
# still pick up upstream changes when they first connect to the internet.
# If the device is offline, the Yocto-pinned version is used as-is.
#
# Patches are applied at build time via SRC_URI; the first-boot script no
# longer applies patches at runtime.

SUMMARY = "NemoClaw source tree (pre-cloned from github.com/NVIDIA/NemoClaw)"
DESCRIPTION = "NemoClaw CLI + plugin + blueprint tree, vendored from upstream \
    for offline-capable first-boot provisioning. Patches applied at build time."
HOMEPAGE = "https://github.com/NVIDIA/NemoClaw"
LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://LICENSE;md5=1f293dba04bfaa7b92e9480eed656511"

# Pin to upstream main HEAD at Yocto build time.
# First-boot will git fetch origin when online; this is the offline fallback.
SRCREV = "4e6508d0cbba95c04112421dd321dbc34188a3a5"
PV = "0.1+git${SRCPV}"

SRC_URI = "git://github.com/NVIDIA/NemoClaw.git;branch=main;protocol=https"

# Patches are NOT applied at Yocto build time — they drift against fast-moving
# upstream context lines and cause fragile builds. Instead, the patches ship
# as data files via nemoclaw-firstboot, and are applied at first boot with
# graceful fallback if a hunk fails to apply. See nemoclaw-firstboot.sh.

S = "${WORKDIR}/git"

# No compile step — we ship the source as-is; npm install happens at first boot.
do_configure[noexec] = "1"
do_compile[noexec] = "1"

do_install() {
    install -d ${D}/opt/nemoclaw
    # Copy source tree + .git so first-boot can `git fetch` against upstream.
    cp -a ${S}/. ${D}/opt/nemoclaw/
    # Make sure the tree is owned by root; the firstrun.sh later chowns
    # the data dir separately.
    chown -R root:root ${D}/opt/nemoclaw
    chmod -R u+rwX,go+rX,go-w ${D}/opt/nemoclaw
}

FILES:${PN} = "/opt/nemoclaw"

RDEPENDS:${PN} = "git"
# node_modules are materialised at first boot by nemoclaw-firstboot, so
# the recipe itself does not depend on nodejs-bin at install time.
