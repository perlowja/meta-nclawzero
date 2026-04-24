# SPDX-License-Identifier: Apache-2.0
#
# nclawzero extlinux generator — multi-LABEL for robust rollback.
#
# Emits THREE labels so a bad kernel / bad rootfs is a 30s menu keypress
# away from recovery, not a device-disassembly + SD-pull operation
# (see feedback_console_always_on.md + feedback_no_rollback_no_kernel_push.md —
# the rules that exist because this exact failure mode cost us a
# disassembly on TYDEUS 2026-04-24):
#
#   primary-a         -> /boot/Image on slot A (LABEL=APP_A)
#   primary-b         -> /boot/Image on slot B (LABEL=APP_B)   [A/B flip]
#   primary-previous  -> /boot/Image.previous on active slot        [in-place rollback]
#
# DEFAULT is primary-a on first build. nclawzero-update slot-switch flips
# DEFAULT between primary-a / primary-b. TIMEOUT 30 (3s) is deliberately
# short so happy-path boots are not slow, but 30 DS is enough for a
# keyboard-present operator to intervene. (Reminder: extlinux TIMEOUT is
# in DECISECONDS — a CLAUDE.md-noted gotcha.)
#
# boot.slot_suffix=_nclawzero_a / _b bypasses meta-tegra platform-preboot
# blkid PARTLABEL=APP scan (the original TYDEUS-rescue-saga root cause).
#
# console=tty0 + console=ttyTCU0,115200 + earlycon ensures framebuffer
# console AND UART both work from kernel handoff onwards (per the
# feedback_console_always_on.md rule). NO "quiet" flag — kernel diagnostics
# must always be visible so a boot failure is not a silent brick.
# plymouth still runs (splash flag) but only after its unit fires, so
# early panics fall through to the console.

UBOOT_EXTLINUX_LABELS = "primary-a primary-b primary-previous"
UBOOT_EXTLINUX_DEFAULT_LABEL = "primary-a"
UBOOT_EXTLINUX_TIMEOUT = "30"

UBOOT_EXTLINUX_MENU_DESCRIPTION:primary-a = "nclawzero slot A (active)"
UBOOT_EXTLINUX_MENU_DESCRIPTION:primary-b = "nclawzero slot B (staged / fallback)"
UBOOT_EXTLINUX_MENU_DESCRIPTION:primary-previous = "nclawzero — previous kernel (rollback)"

UBOOT_EXTLINUX_KERNEL_IMAGE:primary-a = "/boot/Image"
UBOOT_EXTLINUX_KERNEL_IMAGE:primary-b = "/boot/Image"
UBOOT_EXTLINUX_KERNEL_IMAGE:primary-previous = "/boot/Image.previous"

# Common kernel args. Per-slot root= set below.
NCLAWZERO_COMMON_KARGS = " \
earlycon \
console=tty0 \
console=ttyTCU0,115200 \
rw \
rootwait \
rootfstype=ext4 \
firmware_class.path=/etc/firmware \
fbcon=map:0 \
nospectre_bhb \
splash \
"

UBOOT_EXTLINUX_KERNEL_ARGS:primary-a = "${NCLAWZERO_COMMON_KARGS} boot.slot_suffix=_nclawzero_a root=LABEL=APP_A"
UBOOT_EXTLINUX_KERNEL_ARGS:primary-b = "${NCLAWZERO_COMMON_KARGS} boot.slot_suffix=_nclawzero_b root=LABEL=APP_B"
UBOOT_EXTLINUX_KERNEL_ARGS:primary-previous = "${NCLAWZERO_COMMON_KARGS} boot.slot_suffix=_nclawzero_prev root=/dev/${TNSPEC_BOOTDEV}"
