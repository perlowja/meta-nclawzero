# SPDX-FileCopyrightText: Copyright (c) 2026 Jason Perlow. All rights reserved.
# SPDX-License-Identifier: Apache-2.0
#
# nclawzero-ssh-keys — pre-seed ~ncz/.ssh/authorized_keys at image build time.
#
# Why: on 2026-04-23 both Pis (zeropi/.56, clawpi/.54) were reflashed with the
# nclawzero image and lost their authorized_keys. The operator user password
# is locked (-p '!' in EXTRA_USERS_PARAMS), so there was no remote recovery
# path — required physical HDMI + keyboard access to seed a key per host.
#
# This recipe bakes workstation pub keys into the image at build time so the
# next reflash boots SSH-reachable from day one. No console step.
#
# 2026-04-24: operator user renamed from `pi` to `ncz` for new flashes;
# already-deployed devices retain pi until they are reflashed. This recipe
# now installs into /home/ncz/.ssh.
#
# Adding / revoking keys: edit files/authorized_keys, rebuild image. For
# live fleet updates on already-deployed hosts, clawpi has fleet-auth
# (systemd timer pulls from the central keys repo); zeropi does not and
# still needs manual key sync post-flash for anything added after its flash.
#
# Target user: ncz (interactive sudo-capable user, uid 1000).
# Mode: 0700 on .ssh, 0600 on authorized_keys. Owner: ncz:ncz.

SUMMARY = "Pre-seed authorized_keys for ncz user on nclawzero-flashed devices"
DESCRIPTION = "Bakes the nclawzero fleet's operator pub keys into \
    /home/ncz/.ssh/authorized_keys at image build so reflashed devices are \
    SSH-reachable without a console step. Companion to the locked ncz user \
    account created by extrausers in nclawzero-image.bb."
LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/Apache-2.0;md5=89aea4e17d99a7cacdbeed46a0096b10"

SRC_URI = "file://authorized_keys"

S = "${WORKDIR}"

# The ncz user is created by extrausers at image-assembly time, so this
# recipe must order after that. Image-assembly sequencing makes that
# already true — this is belt-and-suspenders documentation.
RDEPENDS:${PN} = "openssh-sshd"

do_install() {
    install -d -m 0700 ${D}/home/ncz/.ssh
    install -m 0600 ${WORKDIR}/authorized_keys ${D}/home/ncz/.ssh/authorized_keys
}

# Chown on target after the ncz user's uid/gid is resolved at first boot.
pkg_postinst_ontarget:${PN}() {
    chown -R ncz:ncz /home/ncz/.ssh
}

FILES:${PN} = "/home/ncz/.ssh"
