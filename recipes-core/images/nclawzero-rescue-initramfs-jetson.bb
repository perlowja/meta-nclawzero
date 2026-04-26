# SPDX-FileCopyrightText: Copyright (c) 2026 Jason Perlow. All rights reserved.
# SPDX-License-Identifier: Apache-2.0
#
# nclawzero-rescue-initramfs-jetson — PXE-bootable rescue initramfs for
# Jetson Orin Nano / Orin NX. Boots over the network, brings up the
# Tegra234 onboard ethernet via the nvethernet OOT module, starts dropbear,
# and waits for an operator to SSH in for install / repair / triage.
#
# WHY a Jetson-specific rescue initramfs:
#   The Tegra234 onboard ethernet (EQOS) is driven by NVIDIA's OOT
#   `nvethernet` module — neither mainline Linux nor any standard Debian /
#   Ubuntu netinstall initrd carries it. Booting Jetson over PXE with a
#   stock arm64 kernel + initrd produces a kernel that loads but cannot
#   bring up its NIC, so the PXE rescue dies silently after kernel handoff.
#
#   The fix is to use the L4T kernel + a Tegra-aware initramfs that
#   modprobes nvethernet (and its prerequisites) before any networking is
#   attempted.
#
# WHAT this image is:
#   - Wraps `tegra-minimal-initramfs` (already in meta-tegra) with the
#     networking + remote-access bits the original was missing.
#   - busybox + dropbear (sshd), no systemd, ~25-40 MB compressed cpio.gz.
#   - Pre-baked authorized_keys at /root/.ssh/authorized_keys so a
#     freshly-PXE-booted Jetson is reachable from any fleet host on day one.
#
# WHAT this image is NOT:
#   - Not a sovereign system. Job is install / repair, then chainload or
#     reboot into the production nclawzero-image-jetson on whatever target
#     storage we provisioned (NVMe / SD).
#   - Not CUDA-capable. Rescue does not need CUDA; the production image
#     does. Same kernel base (L4T 5.15.148+git) so the rescue's nvethernet
#     ABI matches what production expects — no kernel-pin surprises.
#
# Boot chain:
#   iPXE chainload → kernel (L4T Image) + this initramfs (.cpio.gz) →
#   /init → modprobe nvethernet → udhcpc on eth0 → dropbear -F -p 22 →
#   operator SSH in → run /usr/bin/nclawzero-install or repair tools.
#
# Build:
#   bitbake nclawzero-rescue-initramfs-jetson
#
# Deploy:
#   tmp/deploy/images/jetson-orin-nano-devkit/nclawzero-rescue-initramfs-jetson-*.cpio.gz
#     → /opt/netboot/http/initrd/jetson-rescue.cpio.gz on ARGOS

DESCRIPTION = "PXE rescue initramfs for nclawzero Jetson — Tegra ethernet + dropbear"
LICENSE = "MIT"

# busybox suffices for shell, ip, ifconfig, mount, modprobe, etc.
TEGRA_INITRD_BASEUTILS ?= "busybox"

PACKAGE_INSTALL = " \
    tegra-firmware-xusb \
    tegra-minimal-init \
    ${TEGRA_INITRD_BASEUTILS} \
    ${ROOTFS_BOOTSTRAP_INSTALL} \
    \
    nclawzero-rescue-init \
    \
    nv-kernel-module-nvethernet \
    kernel-module-nvme \
    kernel-module-pcie-tegra194 \
    kernel-module-phy-tegra194-p2u \
    kernel-module-tegra-xudc \
    kernel-module-ucsi-ccg \
    \
    dropbear \
    \
    nvme-cli \
    parted \
    e2fsprogs \
    e2fsprogs-mke2fs \
    util-linux-blkid \
    util-linux-lsblk \
    bmap-tools \
"

IMAGE_FEATURES = ""
IMAGE_LINGUAS = ""

COPY_LIC_MANIFEST = "0"
COPY_LIC_DIRS = "0"

COMPATIBLE_MACHINE = "(tegra)"

KERNELDEPMODDEPEND = ""

# 128 MB ceiling — the rescue initramfs must fit in HTTP/TFTP delivery
# without timing-out the Jetson UEFI iPXE chainload. Real measured size
# on first build will be well under this.
IMAGE_ROOTFS_SIZE = "131072"
IMAGE_ROOTFS_EXTRA_SPACE = "0"
IMAGE_NAME_SUFFIX = ""

FORCE_RO_REMOVE ?= "1"

inherit core-image

# cpio.gz is what iPXE / kernel initramfs expect.
IMAGE_FSTYPES = "${INITRAMFS_FSTYPES}"

# The mirror of tegra-minimal-initramfs: skip rm_work to keep the rootfs
# inspectable in deploy/, and dodge sstate sigil drift on the assembly task.
SSTATE_SKIP_CREATION:task-image-complete = "0"
SSTATE_SKIP_CREATION:task-image-qa = "0"
do_image_complete[vardepsexclude] += "rm_work_rootfs"
IMAGE_POSTPROCESS_COMMAND = ""

inherit nopackages

# Same sstate workaround as tegra-minimal-initramfs (PSEUDO_DISABLED at
# image_complete report).
python sstate_report_unihash() {
    report_unihash = getattr(bb.parse.siggen, 'report_unihash', None)

    if report_unihash:
        ss = sstate_state_fromvars(d)
        if ss['task'] == 'image_complete':
            os.environ['PSEUDO_DISABLED'] = '1'
        report_unihash(os.getcwd(), ss['task'], d)
}
