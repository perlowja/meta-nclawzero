#!/bin/sh
# nclawzero-update — unified update CLI for in-place + A/B updates.
# See docs/UPDATES.md for the operator runbook.
#
# Rules enforced (feedback_no_rollback_no_kernel_push.md +
# feedback_console_always_on.md):
#   - Every kernel swap leaves a primary-previous extlinux LABEL pointing
#     at /boot/Image.previous, so rollback is a 30s menu keypress away.
#   - Slot-install/switch preserve the *other* slot unchanged, so a bad
#     rootfs can be rolled back via slot-switch alone.
#   - Filesystem sync + fsync before any reboot-affecting rename.
set -e

die() { echo "nclawzero-update: $*" >&2; exit 1; }
usage() {
    cat >&2 << USAGE
nclawzero-update <command> [args]

  kernel <tarball>          in-place kernel+modules update (same vermagic).
                            also writes a 'primary-previous' extlinux LABEL
                            so rollback is a menu keypress — not an SD swap.
  overlay <tarball>         userspace rootfs overlay (tar starting at /).
  slot-init                 initialise slot B on the SD (one-time; destructive
                            to /dev/mmcblk0p2 if it exists — confirms first).
  slot-install <rootfs-tar> install a rootfs to the INACTIVE slot.
  slot-switch               flip extlinux DEFAULT to the other slot.
  slot-rollback             flip back to the last-booted slot (alias).
  status                    current slot, kernel sha, LABEL state.

USAGE
}

[ "$(id -u)" -eq 0 ] || die "must run as root"

CONF=/boot/extlinux/extlinux.conf
BOOT=/boot

# -------- helpers ----------------------------------------------------------

active_slot() {
    # Identify the slot currently mounted as / — returns 'a', 'b', or empty.
    active_dev=$(findmnt -n -o SOURCE /)
    case "$active_dev" in
    /dev/mmcblk0p1) echo a ;;
    /dev/mmcblk0p2) echo b ;;
    *)              echo "" ;;
    esac
}

inactive_slot() {
    case "$(active_slot)" in
    a) echo b ;;
    b) echo a ;;
    *) echo "" ;;
    esac
}

slot_dev() {
    case "$1" in
    a) echo /dev/mmcblk0p1 ;;
    b) echo /dev/mmcblk0p2 ;;
    *) die "unknown slot '$1'" ;;
    esac
}

extlinux_default() {
    awk '/^DEFAULT/ {print $2; exit}' "$CONF"
}

ensure_primary_previous_label() {
    # Inject a LABEL primary-previous entry that points at /boot/Image.previous
    # on the currently-active slot, if not already present. This is what
    # nclawzero-update kernel uses for in-place rollback.
    grep -q '^LABEL primary-previous' "$CONF" && return 0

    active_dev=$(findmnt -n -o SOURCE /)
    kargs=$(awk '/^LABEL[[:space:]]+(primary|primary-a)[[:space:]]*$/,/^LABEL/ {if (/^[[:space:]]*APPEND/) {sub(/^[[:space:]]*APPEND[[:space:]]+/,""); print; exit}}' "$CONF")
    [ -n "$kargs" ] || kargs="\${cbootargs} root=$active_dev rw rootwait rootfstype=ext4 console=tty0 console=ttyTCU0,115200 earlycon fbcon=map:0 nospectre_bhb splash"

    cat >> "$CONF" << EOF

LABEL primary-previous
	MENU LABEL nclawzero — previous kernel (rollback)
	LINUX /boot/Image.previous
	INITRD /boot/initrd
	APPEND $kargs
EOF
    sync
    echo "extlinux: added LABEL primary-previous pointing at /boot/Image.previous"
}

# -------- commands ---------------------------------------------------------

cmd="${1:-}"; shift || true
case "$cmd" in

kernel)
    tarball="${1:?usage: nclawzero-update kernel <tarball>}"
    [ -f "$tarball" ] || die "$tarball not found"
    tmp=$(mktemp -d)
    trap 'rm -rf "$tmp"' EXIT
    tar xzf "$tarball" -C "$tmp"
    [ -f "$tmp/Image" ]    || die "tarball missing /Image"
    [ -d "$tmp/modules" ]  || die "tarball missing /modules"

    # Back up current Image + modules so rollback stays possible
    [ -f "$BOOT/Image" ] && cp -a "$BOOT/Image" "$BOOT/Image.previous"
    sha_new=$(sha256sum "$tmp/Image" | cut -d' ' -f1)
    cp "$tmp/Image" "$BOOT/Image.new" && sync && mv "$BOOT/Image.new" "$BOOT/Image"

    kver=$(uname -r)
    if [ -d "/lib/modules/$kver" ]; then
        rm -rf "/lib/modules/$kver.previous"
        mv "/lib/modules/$kver" "/lib/modules/$kver.previous"
    fi
    mv "$tmp/modules" "/lib/modules/$kver" 2>/dev/null || mv "$tmp/modules/$kver" "/lib/modules/$kver"
    depmod -a "$kver"

    # CRITICAL (the gap that forced disassembly on TYDEUS 2026-04-24): ensure
    # an extlinux rollback label exists. Without this, a bad Image means the
    # only recovery is physical SD removal.
    ensure_primary_previous_label
    sync

    cat << DONE
kernel updated.
  /boot/Image                       = $sha_new  (new; backup: /boot/Image.previous)
  /lib/modules/$kver                (new; backup: .previous/)
  extlinux LABEL primary-previous   present (rollback via 30s menu)

reboot to activate. if new kernel fails to POST to framebuffer / console,
press any key at the extlinux menu (TIMEOUT 30 deciseconds = 3 seconds!
— press as soon as screen lights up) and pick 'primary-previous'.
DONE
    ;;

overlay)
    tarball="${1:?usage: nclawzero-update overlay <tarball>}"
    [ -f "$tarball" ] || die "$tarball not found"
    if tar tzf "$tarball" | grep -qE '^(\./)?boot/Image|^(\./)?lib/modules/'; then
        die "overlay must not contain /boot/Image or /lib/modules/ — use kernel subcommand"
    fi
    tar xzf "$tarball" -C /
    systemctl daemon-reload
    units=$(tar xzOf "$tarball" ./RESTART-UNITS 2>/dev/null || true)
    for unit in $units; do
        echo "restarting $unit"
        systemctl restart "$unit" || true
    done
    echo "overlay applied."
    ;;

slot-init)
    # Repartition the SD so there's a second rootfs slot of equal size to the
    # active one. Preserves the active slot and its data; wipes whatever is
    # after the active slot on the block device. Requires a confirmation.
    active=$(active_slot)
    [ "$active" = "a" ] || die "slot-init only runs from slot a right now (active=$active)"

    dev=/dev/mmcblk0
    [ -b "$dev" ] || die "$dev not present"

    if [ -b /dev/mmcblk0p2 ]; then
        fs=$(/usr/sbin/blkid -s TYPE -o value /dev/mmcblk0p2 2>/dev/null || true)
        if [ "$fs" = "ext4" ]; then
            echo "slot B already ext4 — nothing to do"
            exit 0
        fi
        die "/dev/mmcblk0p2 exists with fs='$fs'; refusing to overwrite — partition manually if you want to force"
    fi

    # Need parted to resize — check presence
    command -v parted >/dev/null 2>&1 || die "parted not in image; install it or add to IMAGE_INSTALL"
    command -v resize2fs >/dev/null 2>&1 || die "resize2fs not in image; add e2fsprogs-resize2fs to IMAGE_INSTALL"

    echo "PLAN:"
    echo "  1. shrink /dev/mmcblk0p1 to half its current ext4 size"
    echo "  2. create /dev/mmcblk0p2 with PARTLABEL=APP_B filling remaining space"
    echo "  3. mkfs.ext4 -L APP_B /dev/mmcblk0p2"
    echo
    echo "This operates on the LIVE rootfs partition. risk: low on modern ext4 +"
    echo "current meta-tegra layouts, but non-zero. ensure a backup exists before"
    echo "continuing. data on slot A is preserved; only unused tail-space moves"
    echo "to slot B."
    printf "type 'yes-i-have-a-backup' to continue: "
    read confirm
    [ "$confirm" = "yes-i-have-a-backup" ] || die "cancelled"

    # TODO: the actual shrink + repartition is nontrivial on a mounted rootfs
    # and is safer done from a recovery initrd. Leaving this as die-with-guide
    # until I wire up the proper offline-repartition path (or a dedicated
    # slot-init image that boots from USB, does the shrink, returns control).
    die "slot-init from the live rootfs is NOT yet implemented safely — build a dual-slot wic image on ARGOS (wic/nclawzero-jetson-dual.wks) and dd to a fresh SD card instead. See docs/UPDATES.md AB path."
    ;;

slot-install)
    rootfs="${1:?usage: nclawzero-update slot-install <rootfs-tar>}"
    [ -f "$rootfs" ] || die "$rootfs not found"

    tgt_slot=$(inactive_slot)
    [ -n "$tgt_slot" ] || die "cannot determine inactive slot (active=$(active_slot))"
    tgt_dev=$(slot_dev "$tgt_slot")
    [ -b "$tgt_dev" ] || die "$tgt_dev not present — run slot-init first"

    fs=$(/usr/sbin/blkid -s TYPE -o value "$tgt_dev" 2>/dev/null || true)
    [ "$fs" = "ext4" ] || die "$tgt_dev fs='$fs', expected ext4"

    mnt=$(mktemp -d)
    trap "umount '$mnt' 2>/dev/null; rmdir '$mnt'" EXIT
    mount "$tgt_dev" "$mnt"

    # Wipe the target slot (keep lost+found)
    find "$mnt" -mindepth 1 -maxdepth 1 ! -name 'lost+found' -exec rm -rf {} +
    tar xzf "$rootfs" -C "$mnt"
    sync

    # Validate the unpacked rootfs before letting slot-switch point boot at it.
    # A partial tarball or wrong-arch image would silently brick the slot.
    [ -f "$mnt/boot/Image" ]          || die "tarball missing /boot/Image — slot not usable"
    [ -f "$mnt/boot/extlinux/extlinux.conf" ] || echo "warn: tarball missing /boot/extlinux/extlinux.conf"
    [ -d "$mnt/lib/modules" ]         || die "tarball missing /lib/modules — slot not usable"

    # Ensure the ext4 filesystem label matches what our extlinux root=LABEL= expects.
    # Our wic ships slot A with LABEL=APP_A and slot B with LABEL=APP_B; if a
    # manually-created slot B came up blank-labelled, fix it here.
    target_label="APP_$(echo "$tgt_slot" | tr a-z A-Z)"
    cur_label=$(/usr/sbin/blkid -s LABEL -o value "$tgt_dev" 2>/dev/null)
    if [ "$cur_label" != "$target_label" ]; then
        umount "$mnt"
        /usr/sbin/tune2fs -L "$target_label" "$tgt_dev"             && echo "set filesystem label on $tgt_dev: $cur_label -> $target_label"             || die "could not relabel $tgt_dev to $target_label — install tune2fs"
        mount "$tgt_dev" "$mnt"
    fi

    umount "$mnt"
    rmdir "$mnt"
    trap - EXIT

    echo "rootfs installed to slot $tgt_slot ($tgt_dev)."
    echo "run 'nclawzero-update slot-switch' then reboot to activate."
    ;;

slot-switch)
    cur=$(extlinux_default)
    case "$cur" in
    primary|primary-a) new=primary-b ;;
    primary-b)         new=primary-a ;;
    *) die "DEFAULT '$cur' isn't a known slot label; edit $CONF manually" ;;
    esac
    grep -q "^LABEL $new" "$CONF" || die "$CONF has no LABEL $new entry; run slot-install first"
    sed -i "s/^DEFAULT .*/DEFAULT $new/" "$CONF"
    sync
    echo "extlinux DEFAULT: $cur -> $new"
    ;;

slot-rollback)
    "$0" slot-switch
    echo "rollback prepared. reboot to apply. if it fails to boot, the 30s"
    echo "extlinux menu gives you a keypress to pick the other slot again."
    ;;

status)
    echo "== nclawzero-update status =="
    echo "active slot:   $(active_slot)"
    echo "active root:   $(findmnt -n -o SOURCE /)"
    echo "uname -r:      $(uname -r)"
    [ -f /boot/Image ]          && echo "kernel sha:    $(sha256sum /boot/Image | cut -d' ' -f1)"
    [ -f /boot/Image.previous ] && echo "backup sha:    $(sha256sum /boot/Image.previous | cut -d' ' -f1)"
    echo
    echo "partitions:"
    for d in /dev/mmcblk0p1 /dev/mmcblk0p2; do
        if [ -b "$d" ]; then
            uuid=$(/usr/sbin/blkid -s UUID      -o value "$d" 2>/dev/null)
            label=$(/usr/sbin/blkid -s LABEL    -o value "$d" 2>/dev/null)
            partlbl=$(/usr/sbin/blkid -s PARTLABEL -o value "$d" 2>/dev/null)
            fs=$(/usr/sbin/blkid -s TYPE       -o value "$d" 2>/dev/null)
            echo "  $d  fs=$fs label=$label partlabel=$partlbl uuid=$uuid"
        fi
    done
    echo
    echo "extlinux:"
    echo "  DEFAULT: $(extlinux_default)"
    echo "  LABELs present:"
    awk '/^LABEL/ {print "    " $0}' "$CONF"
    ;;

""|help|--help|-h) usage; exit 0 ;;
*) usage; exit 2 ;;
esac
