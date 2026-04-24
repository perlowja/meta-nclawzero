#!/bin/sh
# nclawzero-init-storage — prep NVMe as /srv/nclaw (models + docker + apps)
#
# Safety posture:
#   - Does NOT partition/format if the partition is unrecognised
#   - Accepts an existing ext4 partition and just relabels + mounts it
#   - Honors NCLAWDATA_DEVICE env override (default /dev/nvme0n1p1)
#
# Use case: on a fresh Orin Nano flash the NVMe is either
#   (a) empty (user must pre-partition as ext4), or
#   (b) a leftover JetPack Ubuntu APP partition (1.8TB ext4) that we
#       happily reuse.
set -e

DEV="${NCLAWDATA_DEVICE:-/dev/nvme0n1p1}"
MNT="/srv/nclaw"

if [ ! -b "$DEV" ]; then
    echo "nclawzero-init-storage: $DEV not present — nothing to do" >&2
    exit 0
fi

FS=$(/usr/sbin/blkid -s TYPE -o value "$DEV" 2>/dev/null || true)
if [ "$FS" != "ext4" ]; then
    echo "nclawzero-init-storage: $DEV is not ext4 (got '$FS'); aborting to avoid data loss" >&2
    echo "  run 'mkfs.ext4 -L NCLAWDATA $DEV' manually if you want to format it" >&2
    exit 1
fi

# Relabel (uses tune2fs if available; silently no-op otherwise — fstab uses UUID)
if command -v tune2fs >/dev/null 2>&1; then
    tune2fs -L NCLAWDATA "$DEV" 2>/dev/null || true
elif command -v e2label >/dev/null 2>&1; then
    e2label "$DEV" NCLAWDATA 2>/dev/null || true
fi

mkdir -p "$MNT"
mountpoint -q "$MNT" || mount "$DEV" "$MNT"

UUID=$(/usr/sbin/blkid -s UUID -o value "$DEV")
FSTAB_LINE="UUID=${UUID} ${MNT} ext4 defaults,nofail,x-systemd.device-timeout=30 0 2"
if ! grep -q "${MNT}" /etc/fstab; then
    echo "$FSTAB_LINE" >> /etc/fstab
fi

mkdir -p "$MNT/models" "$MNT/docker" "$MNT/workspace" "$MNT/apps"
chmod 0755 "$MNT"

echo "nclawzero-init-storage: $DEV mounted at $MNT (UUID=$UUID)"
df -h "$MNT" | tail -1
