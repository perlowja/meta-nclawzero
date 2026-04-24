# SPDX-License-Identifier: Apache-2.0
#
# Bake nclawzero-specific kernel boot args into the generated extlinux.conf
# so fresh flashes boot straight into Yocto without needing the
# fix-tydeus-all.sh USB rescue pass.
#
# boot.slot_suffix=_nclawzero
#   Tells meta-tegra's initrd /etc/platform-preboot to skip the
#   'blkid -t PARTLABEL=APP' scan that otherwise overrides root=
#   with whichever PARTLABEL=APP enumerates first (NVMe vs SD).
#   Root cause of the TYDEUS rescue saga — captured in
#   scripts/jetson-rescue/fix-tydeus-slot-suffix.sh.
#
# root=/dev/${TNSPEC_BOOTDEV}
#   Explicit root device for the cmdline. TNSPEC_BOOTDEV defaults to
#   mmcblk0p1 for orin-nano (SD) and nvme0n1p1 for orin-nx; both
#   deterministic on the devkit hardware.
#
# quiet splash loglevel=3
#   Clean boot for demo posture — plymouth handles progress display;
#   kernel only spills emerg/alert/crit/err to the console.
#   dmesg is still available for diagnostics post-boot.

UBOOT_EXTLINUX_KERNEL_ARGS:tegra = " \
boot.slot_suffix=_nclawzero \
root=/dev/${TNSPEC_BOOTDEV} \
rw \
rootwait \
rootfstype=ext4 \
console=ttyTCU0,115200 \
firmware_class.path=/etc/firmware \
fbcon=map:0 \
nospectre_bhb \
quiet \
splash \
loglevel=3 \
"
