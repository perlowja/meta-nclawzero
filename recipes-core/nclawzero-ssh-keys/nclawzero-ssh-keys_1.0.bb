# SPDX-FileCopyrightText: Copyright (c) 2026 Jason Perlow. All rights reserved.
# SPDX-License-Identifier: Apache-2.0
#
# nclawzero-ssh-keys — pre-seed ~pi/.ssh/authorized_keys at image build time.
#
# Why: on 2026-04-23 both Pis (zeropi/.56, clawpi/.54) were reflashed with the
# nclawzero image and lost their authorized_keys. The pi user password is
# locked (-p '!' in EXTRA_USERS_PARAMS), so there was no remote recovery path —
# required physical HDMI + keyboard access to seed a key per host.
#
# This recipe bakes workstation pub keys into the image at build time so the
# next reflash boots SSH-reachable from day one. No console step.
#
# Adding / revoking keys: edit files/authorized_keys, rebuild image. For
# live fleet updates on already-deployed hosts, clawpi has fleet-auth
# (systemd timer pulls from the central keys repo); zeropi does not and
# still needs manual key sync post-flash for anything added after its flash.
#
# Target user: pi (interactive sudo-capable user, uid 1000).
# Mode: 0700 on .ssh, 0600 on authorized_keys. Owner: pi:pi.

SUMMARY = "Pre-seed authorized_keys for pi user on nclawzero-flashed devices"
DESCRIPTION = "Bakes the nclawzero fleet's operator pub keys into \
    /home/pi/.ssh/authorized_keys at image build so reflashed devices are \
    SSH-reachable without a console step. Companion to the locked pi user \
    account created by extrausers in nclawzero-image.bb."
LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/Apache-2.0;md5=89aea4e17d99a7cacdbeed46a0096b10"

SRC_URI = "file://authorized_keys"

S = "${WORKDIR}"

# The pi user is created by extrausers at image-assembly time, so this
# recipe must order after that. Image-assembly sequencing makes that
# already true — this is belt-and-suspenders documentation.
RDEPENDS:${PN} = "openssh-sshd"

do_install() {
    install -d -m 0700 ${D}/home/pi/.ssh
    install -m 0600 ${WORKDIR}/authorized_keys ${D}/home/pi/.ssh/authorized_keys
}

# Chown on target after the pi user's uid/gid is resolved at first boot.
pkg_postinst_ontarget:${PN}() {
    chown -R pi:pi /home/pi/.ssh
}

FILES:${PN} = "/home/pi/.ssh"
