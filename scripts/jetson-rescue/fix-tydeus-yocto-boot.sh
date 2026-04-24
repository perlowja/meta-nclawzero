#!/usr/bin/env bash
#
# fix-tydeus-yocto-boot.sh
# ------------------------
# USB-loadable fix script for TYDEUS (Jetson Orin Nano devkit).
# Rewrites /boot/extlinux/extlinux.conf so the UEFI extlinux loader
# defaults to booting the Yocto image on /dev/mmcblk0p1 instead of the
# JetPack rootfs on /dev/nvme0n1p1. Yocto stanza is listed FIRST because
# some Jetson UEFI extlinux loaders ignore the DEFAULT directive and
# pick the first LABEL in file order.
#
# Usage (from the TYDEUS console, TTY login):
#   1. Insert USB stick containing this script
#   2. sudo mkdir -p /mnt/usb
#   3. sudo mount /dev/sda1 /mnt/usb          # or wherever the USB enumerates
#   4. sudo bash /mnt/usb/fix-tydeus-yocto-boot.sh
#   5. sudo umount /mnt/usb && sudo reboot
#
# Idempotent — safe to re-run. Backs up prior extlinux.conf each time.

set -euo pipefail

EXTLINUX=/boot/extlinux/extlinux.conf
YOCTO_KERNEL=/boot/Image.yocto
YOCTO_PART=/dev/mmcblk0p1
JETPACK_PART=/dev/nvme0n1p1
STAMP=$(date +%Y%m%d-%H%M%S)
BACKUP="${EXTLINUX}.bak-${STAMP}"

# --- pre-flight ------------------------------------------------------------

if [[ $EUID -ne 0 ]]; then
    echo "ERROR: must run as root (sudo)" >&2
    exit 1
fi

echo "=== TYDEUS Yocto boot fix ==="
echo "  extlinux  : $EXTLINUX"
echo "  yocto krn : $YOCTO_KERNEL"
echo "  yocto part: $YOCTO_PART"
echo "  jetpack   : $JETPACK_PART"
echo "  backup    : $BACKUP"
echo

# --- sanity checks ---------------------------------------------------------

if [[ ! -f "$EXTLINUX" ]]; then
    echo "ERROR: $EXTLINUX not found — aborting" >&2
    exit 2
fi

if [[ ! -f "$YOCTO_KERNEL" ]]; then
    echo "ERROR: $YOCTO_KERNEL not found — copy the Yocto kernel to /boot/Image.yocto first" >&2
    exit 3
fi

if ! blkid "$YOCTO_PART" | grep -qi ext4; then
    echo "ERROR: $YOCTO_PART is not ext4 (or not present); is the Yocto rootfs written?" >&2
    echo "  blkid output:"
    blkid "$YOCTO_PART" >&2 || true
    exit 4
fi

if ! blkid "$JETPACK_PART" | grep -qi ext4; then
    echo "ERROR: $JETPACK_PART is not ext4 (or not present); JetPack partition missing?" >&2
    exit 5
fi

# --- diagnostic dump (before changing anything) ----------------------------

echo "--- current extlinux.conf ---"
cat "$EXTLINUX"
echo
echo "--- Yocto partition (mmcblk0p1) /boot/extlinux check ---"
if mountpoint -q /mnt/yocto-probe; then umount /mnt/yocto-probe || true; fi
mkdir -p /mnt/yocto-probe
if mount -o ro "$YOCTO_PART" /mnt/yocto-probe 2>/dev/null; then
    if [[ -f /mnt/yocto-probe/boot/extlinux/extlinux.conf ]]; then
        echo "  Yocto partition ALSO has its own /boot/extlinux/extlinux.conf:"
        cat /mnt/yocto-probe/boot/extlinux/extlinux.conf | sed 's/^/    /'
        echo
        echo "  NOTE: if UEFI prefers this file over the NVMe one, our DEFAULT changes"
        echo "        on the NVMe copy have no effect. See end of script for mitigation."
    else
        echo "  (no extlinux.conf on Yocto rootfs — UEFI will fall through to NVMe)"
    fi
    umount /mnt/yocto-probe
else
    echo "  (could not mount $YOCTO_PART read-only — skipping probe)"
fi
rmdir /mnt/yocto-probe 2>/dev/null || true
echo

# --- backup + write new extlinux.conf --------------------------------------

cp -a "$EXTLINUX" "$BACKUP"
echo "Backup written: $BACKUP"

cat > "$EXTLINUX" <<EOF
TIMEOUT 300
DEFAULT yocto

MENU TITLE L4T boot options

# Yocto listed FIRST — some Jetson UEFI extlinux loaders ignore DEFAULT
# and boot the first LABEL they encounter.

LABEL yocto
      MENU LABEL Yocto nclawzero (scarthgap, internal SD mmcblk0p1)
      LINUX /boot/Image.yocto
      APPEND \${cbootargs} root=$YOCTO_PART rw rootwait rootfstype=ext4 mminit_loglevel=4 console=ttyTCU0,115200 firmware_class.path=/etc/firmware fbcon=map:0 video=efifb:off console=tty0

LABEL primary
      MENU LABEL JetPack (Ubuntu 22.04, NVMe) — fallback
      LINUX /boot/Image
      INITRD /boot/initrd
      APPEND \${cbootargs} root=$JETPACK_PART rw rootwait rootfstype=ext4 mminit_loglevel=4 console=ttyTCU0,115200 firmware_class.path=/etc/firmware fbcon=map:0 video=efifb:off console=tty0
EOF

chmod 644 "$EXTLINUX"
echo "New extlinux.conf written."
echo

# --- mitigation: neutralize Yocto-partition extlinux.conf if it exists ----
# If UEFI reads mmcblk0p1's /boot/extlinux/extlinux.conf INSTEAD of the NVMe
# one, our changes above are ignored. Overwrite the Yocto one with the same
# config so both paths converge.

echo "--- syncing Yocto-partition extlinux.conf (if present) ---"
mkdir -p /mnt/yocto-rw
if mount "$YOCTO_PART" /mnt/yocto-rw 2>/dev/null; then
    if [[ -d /mnt/yocto-rw/boot/extlinux ]]; then
        cp -a /mnt/yocto-rw/boot/extlinux/extlinux.conf \
              /mnt/yocto-rw/boot/extlinux/extlinux.conf.bak-${STAMP} 2>/dev/null || true
        cp -a "$EXTLINUX" /mnt/yocto-rw/boot/extlinux/extlinux.conf
        echo "  synced to Yocto partition."
        # Also make sure Yocto's /boot has its own kernel image available;
        # if not, copy the one we staged on NVMe.
        if [[ ! -f /mnt/yocto-rw/boot/Image.yocto ]] && [[ -f /mnt/yocto-rw/boot/Image ]]; then
            echo "  Yocto partition has its own /boot/Image — leaving as-is."
        fi
    else
        echo "  (Yocto partition has no /boot/extlinux directory — skipping)"
    fi
    umount /mnt/yocto-rw
else
    echo "  (could not mount $YOCTO_PART rw — skipping sync)"
fi
rmdir /mnt/yocto-rw 2>/dev/null || true
echo

# --- final verification ----------------------------------------------------

echo "--- final /boot/extlinux/extlinux.conf ---"
cat "$EXTLINUX"
echo
echo "=== DONE ==="
echo "Reboot when ready:  sudo reboot"
echo "At the UEFI extlinux menu (30s timeout), either:"
echo "  - do nothing → boots Yocto (new default)"
echo "  - arrow-down + Enter to pick 'primary' = Ubuntu/JetPack fallback"
