#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
#
# tydeus-boot-diag.sh — non-destructive boot-state dumper for TYDEUS
# ===================================================================
#
# Runs on the currently-booted OS (whichever it is — JetPack or Yocto)
# and dumps everything we need to diagnose why the flashed Yocto image
# isn't the default boot target. Output is written to the USB stick
# (same dir as this script) so you can eject, bring it back to the
# workstation, and read the diagnosis offline.
#
# Usage (from the TYDEUS console, TTY login):
#   sudo mkdir -p /mnt/usb
#   sudo mount /dev/sda1 /mnt/usb          # or wherever USB enumerates
#   sudo bash /mnt/usb/tydeus-boot-diag.sh
#   sudo umount /mnt/usb
#
# No networking required. No partition modifications. Pure read-only.

set -u

USBDIR="$(cd "$(dirname "$0")" && pwd)"
STAMP="$(date -u +%Y%m%dT%H%M%SZ 2>/dev/null || date +%s)"
OUT="$USBDIR/tydeus-diag-${STAMP}.txt"

section() { echo ""; echo "================================================================"; echo "=== $*"; echo "================================================================"; }

{
  section "tydeus-boot-diag — $(date)"
  echo "script:   $0"
  echo "usbdir:   $USBDIR"
  echo "outfile:  $OUT"
  id

  section "OS identity (which distro is booted RIGHT NOW)"
  echo "--- /etc/os-release ---"
  cat /etc/os-release 2>&1
  echo ""
  echo "--- /etc/lsb-release (Ubuntu) ---"
  cat /etc/lsb-release 2>&1 || echo "(not present — likely not Ubuntu)"
  echo ""
  echo "--- /etc/nv_tegra_release (JetPack signature file) ---"
  cat /etc/nv_tegra_release 2>&1 || echo "(not present — not JetPack)"
  echo ""
  echo "--- hostname ---"
  hostname 2>&1

  section "Kernel + device tree"
  echo "--- uname -a ---"
  uname -a
  echo ""
  echo "--- /proc/device-tree/model ---"
  tr -d '\0' < /proc/device-tree/model 2>&1; echo ""
  echo "--- /proc/device-tree/chosen/bootargs (what the bootloader passed) ---"
  tr -d '\0' < /proc/device-tree/chosen/bootargs 2>&1; echo ""
  echo "--- /proc/cmdline (what the kernel saw) ---"
  cat /proc/cmdline 2>&1
  echo ""

  section "Block device layout"
  echo "--- lsblk -f ---"
  lsblk -f 2>&1
  echo ""
  echo "--- blkid ---"
  blkid 2>&1
  echo ""
  echo "--- mount ---"
  mount | grep -vE "^(proc|sysfs|cgroup|tmpfs|devpts|overlay|nsfs|autofs|bpf|pstore|mqueue|debugfs|tracefs|configfs|securityfs|hugetlbfs|fusectl)" 2>&1
  echo ""
  echo "--- findmnt / ---"
  findmnt / 2>&1
  echo ""

  section "Currently-booted rootfs /boot contents"
  echo "--- ls -la /boot/ ---"
  ls -la /boot/ 2>&1
  echo ""
  echo "--- ls -la /boot/extlinux/ (if present) ---"
  ls -la /boot/extlinux/ 2>&1 || echo "(no /boot/extlinux)"
  echo ""
  echo "--- /boot/extlinux/extlinux.conf on ACTIVE rootfs ---"
  cat /boot/extlinux/extlinux.conf 2>&1 || echo "(not present)"
  echo ""

  section "SD card probe (/dev/mmcblk0*)"
  for DEV in /dev/mmcblk0p1 /dev/mmcblk0p2 /dev/mmcblk0p3; do
    if [ -b "$DEV" ]; then
      echo "--- $DEV ---"
      blkid "$DEV" 2>&1
      MP="/mnt/diag-$(basename $DEV)"
      mkdir -p "$MP"
      if mount -o ro "$DEV" "$MP" 2>/dev/null; then
        echo "  mounted at $MP"
        echo "  /boot listing:"
        ls -la "$MP/boot/" 2>&1 | head -20
        echo "  /boot/extlinux/extlinux.conf:"
        cat "$MP/boot/extlinux/extlinux.conf" 2>&1 | sed 's/^/    /'
        echo "  /etc/os-release:"
        cat "$MP/etc/os-release" 2>&1 | sed 's/^/    /' | head -10
        umount "$MP"
      else
        echo "  could not mount (wrong fs? busy?)"
      fi
      rmdir "$MP" 2>/dev/null || true
      echo ""
    fi
  done

  section "NVMe probe (/dev/nvme0n1*)"
  for DEV in /dev/nvme0n1p1 /dev/nvme0n1p2 /dev/nvme0n1p3; do
    if [ -b "$DEV" ]; then
      echo "--- $DEV ---"
      blkid "$DEV" 2>&1
      MP="/mnt/diag-$(basename $DEV)"
      mkdir -p "$MP"
      if mount -o ro "$DEV" "$MP" 2>/dev/null; then
        echo "  mounted at $MP"
        echo "  /boot listing:"
        ls -la "$MP/boot/" 2>&1 | head -20
        echo "  /boot/extlinux/extlinux.conf:"
        cat "$MP/boot/extlinux/extlinux.conf" 2>&1 | sed 's/^/    /'
        echo "  /etc/os-release:"
        cat "$MP/etc/os-release" 2>&1 | sed 's/^/    /' | head -10
        umount "$MP"
      else
        echo "  could not mount (wrong fs? busy?)"
      fi
      rmdir "$MP" 2>/dev/null || true
      echo ""
    fi
  done

  section "UEFI boot variables (if exposed)"
  if [ -d /sys/firmware/efi/efivars ]; then
    echo "--- efibootmgr -v (boot order + entries) ---"
    if command -v efibootmgr >/dev/null; then
      efibootmgr -v 2>&1
    else
      echo "(efibootmgr not installed)"
    fi
    echo ""
    echo "--- /sys/firmware/efi listing ---"
    ls /sys/firmware/efi/ 2>&1 | head -10
  else
    echo "(no /sys/firmware/efi — not running under UEFI? or extlinux-direct?)"
  fi
  echo ""

  section "Kernel modules — loaded + available"
  echo "--- lsmod (top 30 by size) ---"
  lsmod 2>&1 | head -30
  echo ""
  echo "--- /lib/modules listing ---"
  ls -la /lib/modules/ 2>&1
  echo ""
  KVER="$(uname -r)"
  echo "--- modules dir for running kernel ($KVER) ---"
  ls -la "/lib/modules/$KVER/" 2>&1 | head -10
  echo ""
  echo "--- /lib/modules/$KVER/modules.dep line count ---"
  wc -l "/lib/modules/$KVER/modules.dep" 2>&1 || echo "(modules.dep missing)"
  echo ""

  section "PCI enumeration (for NIC + GPU identity)"
  if command -v lspci >/dev/null; then
    lspci -v 2>&1 | head -80
  else
    echo "(lspci not available)"
  fi
  echo ""

  section "systemd-boot / failed services / unit state"
  echo "--- systemctl --failed ---"
  systemctl --failed --no-pager 2>&1 | head -30 || true
  echo ""
  echo "--- systemctl list-units --state=failed --no-pager ---"
  systemctl list-units --state=failed --no-pager 2>&1 | head -30 || true
  echo ""

  section "dmesg — first 150 + last 150 lines"
  echo "--- HEAD (early boot) ---"
  dmesg 2>&1 | head -150
  echo ""
  echo "--- TAIL (late boot) ---"
  dmesg 2>&1 | tail -150
  echo ""

  section "UEFI capsule-update state (green bar every POST = stuck here)"
  echo "--- /opt/nvidia/l4t-bootloader-config/ ---"
  ls -la /opt/nvidia/l4t-bootloader-config/ 2>&1 | head -20 || echo "(not present)"
  echo ""
  echo "--- /opt/ota_package/ (OTA staging area) ---"
  ls -la /opt/ota_package/ 2>&1 | head -20 || echo "(not present)"
  echo ""
  echo "--- /var/lib/nvidia-l4t-bootloader-fallback/ ---"
  ls -la /var/lib/nvidia-l4t-bootloader-fallback/ 2>&1 | head -20 || echo "(not present)"
  echo ""
  echo "--- capsule + EFI files under /boot, /var, /opt ---"
  find /boot /var /opt -iname "*.cap" -o -iname "*.Cap" -o -iname "*capsule*" -o -iname "BOOTAA64.EFI*" 2>/dev/null | head -30
  echo ""
  echo "--- /sys/firmware/efi/efivars capsule/update/slot-related ---"
  ls /sys/firmware/efi/efivars/ 2>&1 | grep -iE "capsule|update|bootchain|slot|boot[0-9a-fA-F]+" | head -30 || true
  echo ""
  echo "--- nvbootctrl slot state ---"
  if command -v nvbootctrl >/dev/null; then
    echo "  dump-slots-info:"
    nvbootctrl dump-slots-info 2>&1 | sed 's/^/    /'
    echo "  get-current-slot:"
    nvbootctrl get-current-slot 2>&1 | sed 's/^/    /'
    echo "  get-active-boot-slot:"
    nvbootctrl get-active-boot-slot 2>&1 | sed 's/^/    /'
    for S in 0 1; do
      echo "  slot $S marked-successful:"
      nvbootctrl is-slot-marked-successful $S 2>&1 | sed 's/^/    /'
    done
    echo "  get-number-slots:"
    nvbootctrl get-number-slots 2>&1 | sed 's/^/    /'
  else
    echo "(nvbootctrl not installed — suggests broken JetPack or stripped Yocto)"
  fi
  echo ""
  echo "--- NVIDIA OTA / update service states ---"
  for SVC in nv_update_engine nvs-service nvphs ota_apply_package nv_bootloader_payload_updater nv-l4t-usb-device-mode; do
    STATUS="$(systemctl is-active ${SVC}.service 2>&1 || true)"
    ENABLED="$(systemctl is-enabled ${SVC}.service 2>&1 || true)"
    printf "  %-35s active=%-10s enabled=%s\n" "$SVC.service" "$STATUS" "$ENABLED"
  done
  echo ""
  echo "--- systemctl failed units related to update/boot/nvidia ---"
  systemctl --failed --no-pager 2>&1 | grep -iE "nv|update|boot|ota|capsule" | head -15 || true
  echo ""
  echo "--- dmesg lines mentioning capsule/update/slot/bootchain ---"
  dmesg 2>&1 | grep -iE "capsule|update|bootchain|boot slot|ota|fwupdate" | head -30 || true
  echo ""
  echo "--- recent journal entries re: update/ota/capsule ---"
  if command -v journalctl >/dev/null; then
    journalctl --no-pager -n 500 2>/dev/null | grep -iE "capsule|nv_update|ota_apply|bootloader_payload|bootchain" | tail -30 || true
  fi
  echo ""
  echo "--- /proc/device-tree/chosen contents ---"
  if [ -d /proc/device-tree/chosen ]; then
    ls /proc/device-tree/chosen/ 2>&1 | head -20
    for F in reset-reason nvidia,bootloader-capsule-update-state nvidia,boot-rescue; do
      if [ -f "/proc/device-tree/chosen/$F" ]; then
        echo "  $F:"
        tr -d '\0' < "/proc/device-tree/chosen/$F" 2>&1 | sed 's/^/    /'
        echo ""
      fi
    done
  fi
  echo ""

  section "Tegra-specific — NVIDIA power / boot state"
  for F in /sys/class/tegra_rcm/* /sys/power/state /sys/firmware/devicetree/base/chosen/plugin-manager/ids; do
    [ -e "$F" ] || continue
    echo "--- $F ---"
    if [ -d "$F" ]; then
      ls "$F" 2>&1 | head -5
    else
      cat "$F" 2>&1 | head -5
      echo ""
    fi
  done
  echo ""

  section "logind idle behavior (for auto-power-off diagnosis)"
  echo "--- /etc/systemd/logind.conf relevant lines ---"
  grep -E "^(Idle|HandleLidSwitch|HandlePowerKey|HandleSuspend)" /etc/systemd/logind.conf 2>&1 || echo "(no overrides in logind.conf)"
  echo "--- /etc/systemd/logind.conf.d/ overrides ---"
  ls /etc/systemd/logind.conf.d/ 2>&1 | head -10 || true
  echo ""

  section "end — $(date)"
} > "$OUT" 2>&1

echo ""
echo "Diagnostic complete."
echo "Output: $OUT"
echo "Size: $(du -h "$OUT" | awk '{print $1}')"
echo ""
echo "Next step:"
echo "  sudo umount $USBDIR"
echo "  Pull the stick and bring it back to the workstation."
