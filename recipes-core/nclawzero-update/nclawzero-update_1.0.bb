# SPDX-FileCopyrightText: Copyright (c) 2026 Jason Perlow. All rights reserved.
# SPDX-License-Identifier: Apache-2.0
#
# nclawzero-update — operator CLI for in-place + A/B rootfs updates.
# See docs/UPDATES.md in the meta-nclawzero repo for the full runbook.
#
# Three update paths, all driven from the running system (no USB recovery
# mode, no REC-pin short):
#
#   nclawzero-update kernel <tarball>    — swap /boot/Image + /lib/modules,
#                                           same-vermagic module reload
#   nclawzero-update overlay <tarball>   — rootfs file overlay for userspace
#   nclawzero-update slot-{init,install,switch,rollback}
#                                        — dual-partition A/B on SD card
#
# None of these touch QSPI firmware (UEFI/L4TLauncher live there and only
# change on a real tegraflash flash; we intentionally keep them out of
# the OTA surface).

SUMMARY = "nclawzero in-place + A/B updater CLI"
LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/Apache-2.0;md5=89aea4e17d99a7cacdbeed46a0096b10"

SRC_URI = "file://nclawzero-update.sh"
S = "${WORKDIR}"

RDEPENDS:${PN} = "bash util-linux-blkid util-linux-findmnt e2fsprogs-tune2fs e2fsprogs-e2fsck kmod tar gzip coreutils"

do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${WORKDIR}/nclawzero-update.sh ${D}${bindir}/nclawzero-update
}

FILES:${PN} = "${bindir}/nclawzero-update"
