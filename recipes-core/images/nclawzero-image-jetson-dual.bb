# SPDX-FileCopyrightText: Copyright (c) 2026 Jason Perlow. All rights reserved.
# SPDX-License-Identifier: Apache-2.0
#
# nclawzero-image-jetson-dual — same package set as nclawzero-image-jetson
# but packed as a dual-slot WIC for A/B rollback capability. This is the
# canonical TYDEUS image format going forward (per the disassembly-was-
# forced incident of 2026-04-24: single-slot + no primary-previous LABEL
# = bricked device + sealed enclosure = physical disassembly).
#
# Flash with `dd if=nclawzero-image-jetson-dual.wic of=/dev/sdX bs=4M`
# onto a fresh microSD, or via doflash.sh in USB recovery for in-eMMC
# devices. After first boot, slot B is empty ext4 ready for
# `nclawzero-update slot-install <newer-rootfs.tar.gz>`.

require nclawzero-image-jetson.bb

# Force-inherit the wic image class. The parent strips wic from
# IMAGE_FSTYPES via :remove:tegra at parse time, which prevents the
# conditional inherit inside image.bbclass from pulling image-wic.bbclass.
# Without this explicit inherit, do_image_wic is never defined; bitbake
# silently emits a "Function do_image_wic doesnt exist" warning and no
# .wic artifact is produced. The anonymous-python below restores
# IMAGE_FSTYPES at runtime, but by then it is too late for the inherit.
inherit image_types_wic

SUMMARY = "nclawzero Jetson image (dual-slot WIC — A/B capable)"
DESCRIPTION = "Dual-slot A/B-ready SD image. Shares all packages with \
    nclawzero-image-jetson; differs in partition layout (two rootfs slots \
    instead of one) to make bad-kernel rollback a keypress at extlinux, \
    not a device disassembly."

WKS_FILE = "nclawzero-jetson-dual.wks"

# IMAGE_FSTYPES gotcha: the parent recipe (nclawzero-image-jetson.bb) has
#   IMAGE_FSTYPES:remove:tegra = "wic wic.gz wic.bmap"
# BitBake's :remove operator is a list-subtractor that runs AFTER plain
# assignments AND after override resolution; multiple :remove statements
# stack and any :append we make is also subjected to the same removal at
# the very end, so neither :tegra= nor :forcevariable= nor :append nor
# :remove="" defeats the parent removal cleanly.
#
# The robust fix is an anonymous Python function that mutates the parsed
# data object AFTER the parent recipe has been fully expanded but BEFORE
# the wic packaging task runs — we strip the :remove flag in-place and
# set the value we want. This is the BitBake-supported escape hatch for
# exactly this kind of removal-conflict (used by poky's image bbclasses
# and meta-tegra's image_types in similar shapes).
#
# Without this fix, no .wic file lands in deploy/images/ — the dual-slot
# SD never gets built and we silently regress to the single-slot
# disassembly path that cost us the TYDEUS rescue.
python __anonymous() {
    # Drop the parent's :remove[tegra] flag for IMAGE_FSTYPES, then force
    # the value we want. delVarFlag is harmless if the flag doesn't exist;
    # we try both '_remove' (legacy varflag name) and 'remove' (current
    # BitBake) for portability across BitBake versions.
    d.delVarFlag('IMAGE_FSTYPES', '_remove')
    d.delVarFlag('IMAGE_FSTYPES', 'remove')
    d.setVar('IMAGE_FSTYPES', 'wic wic.bmap tegraflash')
}

# TEGRAFLASH_ROOTFS_EXTERNAL=1 tells image_types_tegra that rootfs lives
# outside the tegraflash-managed partitions (we want it on our wic-provisioned
# SD, not in QSPI). Note: the meta-tegra image_types_tegra.bbclass also
# computes this dynamically based on TNSPEC_BOOTDEV vs TNSPEC_BOOTDEV_DEFAULT,
# and since both are mmcblk0p1 for jetson-orin-nano-devkit the dynamic
# computation yields "0". Force it to "1" using the tegra override so we
# beat the bbclass default at expansion time.
TEGRAFLASH_ROOTFS_EXTERNAL:tegra = "1"
