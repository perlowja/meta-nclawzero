# SPDX-FileCopyrightText: Copyright (c) 2026 Jason Perlow. All rights reserved.
# SPDX-License-Identifier: Apache-2.0
#
# nclawzero device-tree overlays — build and deploy .dtbo files into
# /boot alongside the base DTB so L4TLauncher's extlinux OVERLAYS
# directive can apply them at boot time.
#
# Current overlays:
#   - nclawzero-disable-audiograph.dtbo — disables broken
#     nvidia,tegra186-audio-graph-card on Orin Nano devkit (no on-board
#     codec). Prevents EBUSY probe noise without blocking HDMI HDA or
#     USB audio paths used by future audio models.

SUMMARY = "nclawzero Jetson device-tree overlays"
LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/Apache-2.0;md5=89aea4e17d99a7cacdbeed46a0096b10"

COMPATIBLE_MACHINE = "(tegra)"

SRC_URI = " \
    file://nclawzero-disable-audiograph.dts \
"

S = "${WORKDIR}"

DEPENDS = "dtc-native"

inherit deploy

do_compile() {
    for dts in ${S}/*.dts; do
        name=$(basename "$dts" .dts)
        dtc -I dts -O dtb -@ -o "${B}/${name}.dtbo" "$dts"
    done
}

do_deploy() {
    install -d ${DEPLOYDIR}
    for dtbo in ${B}/*.dtbo; do
        install -m 0644 "$dtbo" ${DEPLOYDIR}/
    done
}
addtask deploy after do_compile before do_build

do_install() {
    install -d ${D}/boot
    for dtbo in ${B}/*.dtbo; do
        install -m 0644 "$dtbo" ${D}/boot/
    done
}

FILES:${PN} = "/boot/*.dtbo"
PACKAGE_ARCH = "${MACHINE_ARCH}"
