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
    # Try to extract APPEND from the active LABEL block (primary, primary-a,
    # or primary-b). Earlier versions used an awk range pattern, but ranges
    # of the form /^LABEL.../, /^LABEL/ close immediately because the start
    # line is itself matched by the end pattern — extracting nothing. Use
    # a flag-and-walk approach instead.
    kargs=$(awk '
        /^LABEL[[:space:]]+(primary|primary-a|primary-b)[[:space:]]*$/ { in_block=1; next }
        in_block && /^LABEL / { exit }
        in_block && /^[[:space:]]*APPEND/ { sub(/^[[:space:]]*APPEND[[:space:]]+/,""); print; exit }
    ' "$CONF")
    # L4TLauncher requires an APPEND line that begins with "${cbootargs} "
    # to actually concatenate the arg list onto the kernel command line
    # (see meta-tegra l4t-extlinux-config.bbclass header). The bbclass
    # auto-prepends ${cbootargs} when generating, so extracted APPEND lines
    # ALREADY contain it. Hand-emitted APPEND lines (this fallback path)
    # MUST include the literal token "${cbootargs}" at the start.
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

fixup_primary_previous_root() {
    # The bbappend-generated primary-previous LABEL bakes
    #   root=/dev/${TNSPEC_BOOTDEV}    -> e.g. root=/dev/mmcblk0p1
    # at build time. That's correct when we're booted from slot A, but if
    # we're on slot B and a kernel update goes wrong, the rollback label
    # would boot slot A's rootfs with slot B's /boot/Image.previous —
    # the wrong slot's old kernel paired with the wrong rootfs.
    #
    # Force the primary-previous APPEND root= to LABEL=APP_<active>, which
    # always matches the currently-running slot regardless of which
    # /dev/mmcblk0pN it landed on.
    grep -q '^LABEL primary-previous' "$CONF" || return 0

    active=$(active_slot)
    case "$active" in
    a) target_label="APP_A" ;;
    b) target_label="APP_B" ;;
    *) echo "warn: cannot determine active slot — skipping primary-previous root=fixup"; return 0 ;;
    esac

    # awk in-place edit: in the primary-previous LABEL block, rewrite the
    # root=... token of the APPEND line to root=LABEL=$target_label.
    tmp_conf=$(mktemp)
    awk -v tgt="root=LABEL=$target_label" '
        /^LABEL primary-previous[[:space:]]*$/ { in_block=1 }
        in_block && /^LABEL / && !/^LABEL primary-previous/ { in_block=0 }
        in_block && /^[[:space:]]*APPEND/ {
            # Replace any existing root=... token (LABEL=, UUID=, /dev/...,
            # PARTUUID=, PARTLABEL=) with the slot-correct one.
            if ($0 ~ /root=[^ ]+/) {
                sub(/root=[^ ]+/, tgt)
            } else {
                sub(/$/, " " tgt)
            }
        }
        { print }
    ' "$CONF" > "$tmp_conf" && cat "$tmp_conf" > "$CONF" && rm -f "$tmp_conf"
    sync
    echo "extlinux: primary-previous root=LABEL=$target_label (matches active slot $active)"
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

    # The new kernel's vermagic is whatever directory name the tarball ships
    # under modules/. Modules MUST land under /lib/modules/<NEW_KVER> — using
    # $(uname -r) (the running kernel) would put the new modules in the old
    # kver path, and after reboot the new kernel would find no modules in
    # /lib/modules/<NEW_KVER> → boot might POST but networking, USB, NVMe,
    # GPU all fail to bring up.
    new_kver=$(ls -1 "$tmp/modules" 2>/dev/null | head -n1)
    [ -n "$new_kver" ] || die "tarball modules/ is empty"
    [ -d "$tmp/modules/$new_kver" ] || die "tarball modules/$new_kver is not a directory"
    [ -d "$tmp/modules/$new_kver/kernel" ] || die "tarball modules/$new_kver/kernel missing — wrong layout"

    # An accompanying initrd in the tarball is encouraged. If present, we'll
    # swap it atomically after the kernel; if absent, we keep the existing
    # initrd (vermagic-bearing modules in initrd may then mismatch — but in
    # our build, initrd modules live in /lib/modules/<INITRD_KVER>/ inside
    # the cpio so they don't depend on the host kernel's modules dir).
    new_initrd=""
    [ -f "$tmp/initrd" ] && new_initrd="$tmp/initrd"

    # ----- stage everything to *.new sibling files first; rename at the end -----
    # Atomic-rename pattern: the only state where the system is unbootable
    # is between rename of Image (old → previous) and rename of Image.new → Image.
    # That window is two-rename-syscalls long; sync is forced before each.

    sha_new=$(sha256sum "$tmp/Image" | cut -d' ' -f1)
    cp "$tmp/Image" "$BOOT/Image.new"
    [ -n "$new_initrd" ] && cp "$new_initrd" "$BOOT/initrd.new"

    # Stage modules dir under its TRUE kver so a partial install + power loss
    # doesn't pollute the running kver's modules.
    if [ -d "/lib/modules/$new_kver" ]; then
        rm -rf "/lib/modules/$new_kver.previous"
        mv "/lib/modules/$new_kver" "/lib/modules/$new_kver.previous"
    fi
    mv "$tmp/modules/$new_kver" "/lib/modules/$new_kver"
    depmod -a "$new_kver"
    sync

    # Now flip kernel + initrd via atomic mv. Image.previous becomes the
    # rollback target; primary-previous extlinux LABEL points at it.
    [ -f "$BOOT/Image" ] && cp -a "$BOOT/Image" "$BOOT/Image.previous"
    sync
    mv "$BOOT/Image.new" "$BOOT/Image"
    if [ -n "$new_initrd" ]; then
        [ -f "$BOOT/initrd" ] && cp -a "$BOOT/initrd" "$BOOT/initrd.previous"
        mv "$BOOT/initrd.new" "$BOOT/initrd"
    fi
    sync

    # CRITICAL (the gap that forced disassembly on TYDEUS 2026-04-24): ensure
    # an extlinux rollback label exists. Without this, a bad Image means the
    # only recovery is physical SD removal.
    ensure_primary_previous_label
    # AND: ensure that label's root= matches whichever slot we're on. The
    # bbappend-generated primary-previous bakes root=/dev/${TNSPEC_BOOTDEV}
    # which is fixed to slot A at build time; rebooting into rollback from
    # slot B without this fixup would boot slot A's rootfs with slot B's
    # backup kernel. (Real-world: silent boot to a stale rootfs that thinks
    # it's the active slot — even worse than a panic, because nothing alerts
    # the operator.)
    fixup_primary_previous_root
    sync

    cat << DONE
kernel updated.
  running kernel:                   $(uname -r)
  staged kernel vermagic:           $new_kver
  /boot/Image                       = $sha_new  (new; backup: /boot/Image.previous)
  /lib/modules/$new_kver           (new; backup: $new_kver.previous/)
$( [ -n "$new_initrd" ] && echo "  /boot/initrd                      (new; backup: /boot/initrd.previous)" )
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
    [ -x "$mnt/sbin/init" ] || [ -x "$mnt/lib/systemd/systemd" ] \
        || die "tarball missing /sbin/init AND /lib/systemd/systemd — slot not usable"

    # Confirm /lib/modules/<kver> exists with at least kernel/ inside (otherwise
    # boot will produce a kernel that can't load any drivers — networking/usb/
    # nvme dead, no console-input recovery if the operator's at the keyboard).
    found_modules_kver=""
    for kdir in "$mnt"/lib/modules/*/; do
        if [ -d "$kdir/kernel" ]; then
            found_modules_kver=$(basename "$kdir")
            break
        fi
    done
    [ -n "$found_modules_kver" ] || die "tarball /lib/modules/* contains no kernel/ subdir — slot not bootable"

    # Drop a marker file so slot-switch can refuse to flip to a half-installed
    # slot. The marker is written ONLY at the end of a successful install,
    # which means a power-loss midway leaves no marker → slot-switch refuses.
    cat > "$mnt/.nclawzero-slot-ready" << MARKER
slot=$tgt_slot
installed_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)
modules_kver=$found_modules_kver
boot_image_sha256=$(sha256sum "$mnt/boot/Image" | cut -d' ' -f1)
MARKER
    sync

    # Ensure the ext4 filesystem label matches what our extlinux root=LABEL= expects.
    # Our wic ships slot A with LABEL=APP_A and slot B with LABEL=APP_B; if a
    # manually-created slot B came up blank-labelled, fix it here.
    target_label="APP_$(echo "$tgt_slot" | tr a-z A-Z)"
    cur_label=$(/usr/sbin/blkid -s LABEL -o value "$tgt_dev" 2>/dev/null || true)
    if [ "$cur_label" != "$target_label" ]; then
        umount "$mnt"
        # Some tune2fs versions refuse a relabel on a not-quite-clean
        # filesystem (Journal needs replay, last-mount-count exceeded,
        # etc.). Run a non-interactive fsck first so the tune2fs path
        # is robust. -f forces, -y answers yes to repairs.
        if command -v e2fsck >/dev/null 2>&1; then
            e2fsck -fy "$tgt_dev" || true
        fi
        if /usr/sbin/tune2fs -L "$target_label" "$tgt_dev"; then
            echo "set filesystem label on $tgt_dev: ${cur_label:-<unset>} -> $target_label"
        else
            mount "$tgt_dev" "$mnt"  # remount so trap can clean up
            die "could not relabel $tgt_dev to $target_label — verify tune2fs is installed and the filesystem is clean"
        fi
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
    primary|primary-a) new=primary-b; new_label="APP_B"; new_slot="b"; new_dev=/dev/mmcblk0p2 ;;
    primary-b)         new=primary-a; new_label="APP_A"; new_slot="a"; new_dev=/dev/mmcblk0p1 ;;
    *) die "DEFAULT '$cur' isn't a known slot label; edit $CONF manually" ;;
    esac
    grep -q "^LABEL $new" "$CONF" || die "$CONF has no LABEL $new entry; run slot-install first"

    # Sanity-check the target slot before flipping DEFAULT. Refuses to switch
    # if the target's filesystem doesn't exist OR the slot-install marker is
    # missing (= last install was interrupted or never happened on a fresh
    # dual wic, which ships slot B as empty ext4). Without this, a flip to a
    # blank slot B would brick — kernel would panic at rootwait + initrd
    # waits forever for /lib/modules/<kver>.
    [ -b "$new_dev" ] || die "$new_dev not present — slot $new_slot has no partition"
    fs=$(/usr/sbin/blkid -s TYPE -o value "$new_dev" 2>/dev/null || true)
    [ "$fs" = "ext4" ] || die "$new_dev fs='$fs', expected ext4 — slot $new_slot not usable"
    cur_label=$(/usr/sbin/blkid -s LABEL -o value "$new_dev" 2>/dev/null || true)
    [ "$cur_label" = "$new_label" ] \
        || die "$new_dev LABEL='$cur_label', expected '$new_label' — re-run slot-install"

    sw_mnt=$(mktemp -d)
    mount -o ro "$new_dev" "$sw_mnt" || die "could not mount $new_dev for sanity check"
    has_marker=0
    [ -f "$sw_mnt/.nclawzero-slot-ready" ] && has_marker=1
    has_image=0
    [ -f "$sw_mnt/boot/Image" ] && has_image=1
    has_init=0
    ([ -x "$sw_mnt/sbin/init" ] || [ -x "$sw_mnt/lib/systemd/systemd" ]) && has_init=1
    umount "$sw_mnt"; rmdir "$sw_mnt"

    if [ "$has_marker" -ne 1 ] || [ "$has_image" -ne 1 ] || [ "$has_init" -ne 1 ]; then
        die "slot $new_slot fails sanity (marker=$has_marker image=$has_image init=$has_init); run 'nclawzero-update slot-install <tar>' first"
    fi

    sed -i "s/^DEFAULT .*/DEFAULT $new/" "$CONF"

    # Also fixup primary-previous root= to point at the NEW intended-active
    # slot. After reboot, if the new slot fails and the operator picks
    # primary-previous from the menu, we want that label's root= to match
    # the same-slot's /boot/Image.previous — not the slot we just left.
    # (Without this, operator sees: slot B chosen, slot B fails, picks
    # primary-previous, lands on slot A's old rootfs — confusing.)
    if grep -q '^LABEL primary-previous' "$CONF"; then
        tmp_conf=$(mktemp)
        awk -v tgt="root=LABEL=$new_label" '
            /^LABEL primary-previous[[:space:]]*$/ { in_block=1 }
            in_block && /^LABEL / && !/^LABEL primary-previous/ { in_block=0 }
            in_block && /^[[:space:]]*APPEND/ {
                if ($0 ~ /root=[^ ]+/) {
                    sub(/root=[^ ]+/, tgt)
                } else {
                    sub(/$/, " " tgt)
                }
            }
            { print }
        ' "$CONF" > "$tmp_conf" && cat "$tmp_conf" > "$CONF" && rm -f "$tmp_conf"
        echo "extlinux primary-previous root= updated to LABEL=$new_label"
    fi
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
