# SPDX-FileCopyrightText: Copyright (c) 2026 Jason Perlow. All rights reserved.
# SPDX-License-Identifier: Apache-2.0
#
# nclawzero-rescue-init — early-userspace shell script + authorized_keys
# bake for the Jetson PXE rescue initramfs.
#
# Lives at /init in the cpio.gz initramfs (replaces busybox's stub).
# Job:
#   1. mount /proc, /sys, /dev, /tmp
#   2. modprobe nvethernet (OOT NIC) + helpers
#   3. udhcpc on eth0 to get an IP from the LAN
#   4. seed /root/.ssh/authorized_keys with fleet pubkeys
#   5. dropbear -F -p 22 (foreground, blocks here until operator action)
#
# Companion image: nclawzero-rescue-initramfs-jetson.bb (PACKAGE_INSTALL
# pulls this recipe in).
#
# authorized_keys content kept in lockstep with
# recipes-core/nclawzero-ssh-keys/files/authorized_keys.  Both files
# should be edited together when keys rotate. (DRY-ing them via a single
# SRC_URI requires layer-shared file paths that BitBake parses as
# fragile; the duplication cost is one diff, twice, on rotation.)

SUMMARY = "Init script + authorized_keys for nclawzero Jetson PXE rescue initramfs"
DESCRIPTION = "Provides /init at initramfs root: mounts pseudo-fs, modprobes \
    Tegra234 nvethernet, runs udhcpc, seeds /root/.ssh/authorized_keys, \
    starts dropbear in foreground."
LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/Apache-2.0;md5=89aea4e17d99a7cacdbeed46a0096b10"

SRC_URI = " \
    file://init \
    file://authorized_keys \
"

S = "${WORKDIR}"

# Runtime deps. busybox supplies modprobe, ip, mount, udhcpc, etc.;
# dropbear is the foreground SSH server. The OOT nvethernet module is
# pulled in by the image recipe (PACKAGE_INSTALL there).
RDEPENDS:${PN} = "busybox dropbear"

# Fleet-internal authorized_keys is gitignored — validate at parse time
# so a missing file produces an actionable error before any work starts.
# Same pattern as nclawzero-ssh-keys; both files rotate together.
python () {
    import os
    keys = os.path.join(d.getVar('THISDIR'), 'files', 'authorized_keys')
    if not os.path.isfile(keys):
        example = keys + '.example'
        bb.fatal(
            "\n"
            "nclawzero-rescue-init: required fleet-internal file is missing:\n"
            "    %s\n"
            "\n"
            "This path is gitignored on purpose. Populate from the\n"
            "committed .example sibling, mirroring whatever is in\n"
            "recipes-core/nclawzero-ssh-keys/files/authorized_keys so\n"
            "production and rescue accept the same operator pubkeys:\n"
            "    cp %s %s\n"
            "    $EDITOR %s\n"
            "\n"
            "Then re-run bitbake.\n"
            % (keys, example, keys, keys)
        )
}

# Idempotent: install -m overwrites every time. Re-running the recipe
# against the same source produces an identical / tree.
do_install() {
    install -d -m 0755 ${D}
    install -m 0755 ${WORKDIR}/init ${D}/init

    install -d -m 0700 ${D}/root/.ssh
    install -m 0600 ${WORKDIR}/authorized_keys ${D}/root/.ssh/authorized_keys

    # Pseudo-fs mountpoints + writable runtime dirs.  busybox-init
    # (when used) and our /init both expect these to exist.
    install -d -m 0555 ${D}/proc ${D}/sys
    install -d -m 0755 ${D}/dev ${D}/tmp ${D}/run ${D}/var/log
    install -d -m 0700 ${D}/root
}

FILES:${PN} = " \
    /init \
    /root/.ssh/authorized_keys \
    /root/.ssh \
    /root \
    /proc \
    /sys \
    /dev \
    /tmp \
    /run \
    /var/log \
"
