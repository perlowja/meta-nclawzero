# SPDX-FileCopyrightText: Copyright (c) 2026 Jason Perlow. All rights reserved.
# SPDX-License-Identifier: Apache-2.0
#
# nclawzero-storage-init — operator helper to prep NVMe as /srv/nclaw
# for model storage + docker data-root + large-app data.
#
# Intentionally not a first-boot systemd auto-run: formatting / relabeling
# the wrong partition would be destructive. Operator runs once after flash:
#   sudo nclawzero-init-storage
# After that, /etc/fstab mounts /srv/nclaw automatically on every boot.

SUMMARY = "nclawzero NVMe storage initializer (operator-invoked)"
LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/Apache-2.0;md5=89aea4e17d99a7cacdbeed46a0096b10"

SRC_URI = "file://nclawzero-init-storage.sh"
S = "${WORKDIR}"

RDEPENDS:${PN} = "e2fsprogs-mke2fs e2fsprogs-tune2fs util-linux-blkid"

do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${WORKDIR}/nclawzero-init-storage.sh \
        ${D}${bindir}/nclawzero-init-storage
}
FILES:${PN} = "${bindir}/nclawzero-init-storage"
