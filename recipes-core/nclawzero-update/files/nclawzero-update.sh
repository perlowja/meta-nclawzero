#!/bin/sh
# nclawzero-update — unified update CLI for in-place + A/B updates
# See docs/UPDATES.md for the full operator runbook.
set -e

die() { echo "nclawzero-update: $*" >&2; exit 1; }
usage() {
    cat >&2 << USAGE
nclawzero-update <command> [args]

  kernel <tarball>         In-place kernel+modules update (same vermagic)
  overlay <tarball>        Userspace rootfs overlay (tar starting at /)
  slot-init                Initialise B slot (partitions /dev/mmcblk0p2)
  slot-install <rootfs-tar>  Install a rootfs to the inactive slot
  slot-switch              Flip extlinux DEFAULT to the other slot
  slot-rollback            Flip back to whichever slot last booted successfully
  status                   Show current slot, kernel SHA, module deltas

USAGE
}

[ "$(id -u)" -eq 0 ] || die "must run as root"

cmd="${1:-}"; shift || true
case "$cmd" in

kernel)
    tarball="${1:?usage: nclawzero-update kernel <tarball>}"
    [ -f "$tarball" ] || die "$tarball not found"
    tmp=$(mktemp -d)
    trap 'rm -rf "$tmp"' EXIT
    tar xzf "$tarball" -C "$tmp"
    [ -f "$tmp/Image" ] || die "tarball missing /Image"
    [ -d "$tmp/modules" ] || die "tarball missing /modules"

    # Backup + install
    [ -f /boot/Image ] && cp -a /boot/Image /boot/Image.previous
    sha_new=$(sha256sum "$tmp/Image" | cut -d' ' -f1)
    cp "$tmp/Image" /boot/Image.new && sync && mv /boot/Image.new /boot/Image

    kver=$(uname -r)
    # Merge the new modules — we replace the whole tree because partial updates
    # cause depmod weirdness. Preserve it as .previous for rollback.
    if [ -d "/lib/modules/$kver" ]; then
        rm -rf "/lib/modules/$kver.previous"
        mv "/lib/modules/$kver" "/lib/modules/$kver.previous"
    fi
    mv "$tmp/modules" "/lib/modules/$kver"
    depmod -a "$kver"

    echo "kernel updated."
    echo "  /boot/Image    = $sha_new  (backup: /boot/Image.previous)"
    echo "  /lib/modules/$kver  (backup: .previous/)"
    echo "reboot to activate. if it doesn't come up, pick the 'primary-previous'"
    echo "entry at the extlinux menu (30s timeout)."
    ;;

overlay)
    tarball="${1:?usage: nclawzero-update overlay <tarball>}"
    [ -f "$tarball" ] || die "$tarball not found"
    # Forbid overlays from shipping a kernel or module tree — use 'kernel' for that
    if tar tzf "$tarball" | grep -qE '^(\./)?boot/Image|^(\./)?lib/modules/'; then
        die "overlay must not contain /boot/Image or /lib/modules/ — use 'nclawzero-update kernel' for that"
    fi
    tar xzf "$tarball" -C /
    systemctl daemon-reload
    # Read RESTART-UNITS inside the tarball (if present) and restart each
    units=$(tar xzOf "$tarball" ./RESTART-UNITS 2>/dev/null || true)
    for unit in $units; do
        echo "restarting $unit"
        systemctl restart "$unit" || true
    done
    echo "overlay applied."
    ;;

slot-init)
    dev=/dev/mmcblk0
    part_b=/dev/mmcblk0p2
    [ -b "$dev" ] || die "$dev not present"
    if [ -b "$part_b" ]; then
        fs=$(/usr/sbin/blkid -s TYPE -o value "$part_b" 2>/dev/null || true)
        [ "$fs" = "ext4" ] && { echo "slot B ($part_b) already ext4 — ok"; exit 0; }
        die "$part_b exists with unexpected fs type '$fs' — not touching it"
    fi
    # TODO: sgdisk/parted is not guaranteed in the image; document that
    # operators may need to prepare the second partition before calling this.
    die "slot B not provisioned. add a second ext4 partition to $dev (parted/gparted on a workstation), then re-run"
    ;;

slot-install)
    rootfs="${1:?usage: nclawzero-update slot-install <rootfs-tar>}"
    [ -f "$rootfs" ] || die "$rootfs not found"
    # Identify inactive slot — whatever is NOT the current root
    active_dev=$(findmnt -n -o SOURCE /)
    case "$active_dev" in
    /dev/mmcblk0p1) target=/dev/mmcblk0p2 ;;
    /dev/mmcblk0p2) target=/dev/mmcblk0p1 ;;
    *) die "active root $active_dev not a known slot device" ;;
    esac
    [ -b "$target" ] || die "$target not present — run slot-init first"
    mnt=$(mktemp -d)
    trap 'umount "$mnt" 2>/dev/null; rmdir "$mnt"' EXIT
    mount "$target" "$mnt"
    # Wipe target + unpack
    find "$mnt" -mindepth 1 -maxdepth 1 ! -name 'lost+found' -exec rm -rf {} +
    tar xzf "$rootfs" -C "$mnt"
    sync
    echo "rootfs installed to $target. run 'nclawzero-update slot-switch' then reboot."
    ;;

slot-switch)
    # Flip DEFAULT label in extlinux.conf between 'primary-a' and 'primary-b'
    conf=/boot/extlinux/extlinux.conf
    [ -f "$conf" ] || die "$conf not found"
    cur=$(awk '/^DEFAULT/ {print $2; exit}' "$conf")
    case "$cur" in
    primary|primary-a) new=primary-b ;;
    primary-b)         new=primary-a ;;
    *) die "DEFAULT '$cur' isn't a known slot label; edit $conf manually" ;;
    esac
    grep -q "^LABEL $new" "$conf" || die "$conf has no LABEL $new entry; run slot-install first"
    sed -i "s/^DEFAULT .*/DEFAULT $new/" "$conf"
    sync
    echo "extlinux DEFAULT: $cur -> $new"
    ;;

slot-rollback)
    # Alias for slot-switch + reboot-expectation message
    "$0" slot-switch
    echo "rollback prepared. reboot to apply."
    ;;

status)
    echo "== nclawzero-update status =="
    echo "active root:  $(findmnt -n -o SOURCE /)"
    echo "uname -r:     $(uname -r)"
    [ -f /boot/Image ] && echo "kernel sha:   $(sha256sum /boot/Image | cut -d' ' -f1)"
    [ -f /boot/Image.previous ] && echo "backup sha:   $(sha256sum /boot/Image.previous | cut -d' ' -f1)"
    echo "slots:"
    for d in /dev/mmcblk0p1 /dev/mmcblk0p2; do
        if [ -b "$d" ]; then
            uuid=$(/usr/sbin/blkid -s UUID -o value "$d" 2>/dev/null)
            fs=$(/usr/sbin/blkid -s TYPE -o value "$d" 2>/dev/null)
            echo "  $d  fs=$fs uuid=$uuid"
        fi
    done
    [ -f /boot/extlinux/extlinux.conf ] && \
        echo "extlinux DEFAULT: $(awk '/^DEFAULT/ {print $2; exit}' /boot/extlinux/extlinux.conf)"
    ;;

""|help|--help|-h)
    usage; exit 0 ;;

*)
    usage; exit 2 ;;

esac
