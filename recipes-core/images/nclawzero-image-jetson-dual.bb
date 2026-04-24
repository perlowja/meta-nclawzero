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

SUMMARY = "nclawzero Jetson image (dual-slot WIC — A/B capable)"
DESCRIPTION = "Dual-slot A/B-ready SD image. Shares all packages with \
    nclawzero-image-jetson; differs in partition layout (two rootfs slots \
    instead of one) to make bad-kernel rollback a keypress at extlinux, \
    not a device disassembly."

WKS_FILE = "nclawzero-jetson-dual.wks"
IMAGE_FSTYPES = "wic wic.bmap"

# TEGRAFLASH_ROOTFS_EXTERNAL=1 tells image_types_tegra that rootfs lives
# outside the tegraflash-managed partitions (we want it on our wic-provisioned
# SD, not in QSPI).
TEGRAFLASH_ROOTFS_EXTERNAL = "1"
