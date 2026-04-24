# nclawzero — Updating a live device

Three update paths, each with different blast radius and rollback story.
Pick the lightest one that fits your change.

| Path                    | What changes                              | Blast radius | Rollback     | Requires                 |
|-------------------------|-------------------------------------------|--------------|--------------|--------------------------|
| **In-place kernel+mods** | `/boot/Image` + `/lib/modules/<uname -r>/` | One reboot   | Swap backup  | Same kernel version on new+old |
| **Userspace overlay**   | Files under `/usr/`, `/etc/`, `/opt/`      | None / service restart | Keep tarball | —                    |
| **A/B slot flip**       | Full rootfs + kernel on new SD partition   | One reboot   | Re-flip slot | 2-partition SD layout    |

The SD card on a nclawzero Jetson devkit always has at least the running
rootfs partition (`/dev/mmcblk0p1`). Everything below happens **from the
running system** — no USB recovery mode, no physical REC-pin short.

## 1. In-place kernel + modules update

Use when the Yocto layer has a kernel-config change (driver added, LSM
toggled, etc.) but the kernel **version is the same** (same `SRCBRANCH` +
`SRCREV` in `linux-jammy-nvidia-tegra_5.15.bb`). Module `vermagic` must
match exactly or `modprobe` will refuse to load them.

### From operator side (on the Jetson)

```sh
# 1. Stage the new kernel + modules on the device
sudo nclawzero-update kernel /srv/nclaw/apps/kernel-update.tar.gz

# 2. Reboot
sudo reboot

# 3. After boot, verify new drivers are probing
lsmod | grep -E 'r8169|rtw88'
ip -br link show
```

The `nclawzero-update kernel` command:
- Backs up the current `/boot/Image` to `/boot/Image.previous`
- Unpacks the tarball (expects `Image` + `modules/`) into `/boot/` and
  `/lib/modules/<uname -r>/`
- Runs `depmod -a` so the new modules are discoverable
- Prints the new `/boot/Image` SHA256 for audit

### From build side (on ARGOS)

```sh
# Produce the update tarball after a kernel-config change
cd /mnt/argonas/nclawzero-yocto
source poky/oe-init-build-env build-jetson
bitbake virtual/kernel

# Extract the shippable artefacts
TMP=$(mktemp -d)
cd /home/jasonperlow/yocto-tmp/build-jetson-tmp/deploy/images/jetson-orin-nano-devkit
cp Image "$TMP/Image"
tar xzf modules-jetson-orin-nano-devkit.tgz -C "$TMP/"
tar czf kernel-update.tar.gz -C "$TMP" .

# Push to the device (any transport — scp / rsync / http)
scp kernel-update.tar.gz pi@<jetson-ip>:/srv/nclaw/apps/
```

### What can go wrong

- **Different kernel version** (wrong `uname -r` between build and device) →
  modules refuse to load with `"Invalid module format"`. The kernel will
  still boot but drivers that used to work won't. Rollback: boot menu at
  extlinux prompt (30s timeout — press any key) → pick `primary-previous`,
  which we pin in the extlinux generator.
- **Broken `/boot/Image`** (corrupt copy, wrong arch) → UEFI fails to hand
  off to the kernel. Fall through to extlinux prompt; pick `primary-previous`.
  Worst case, pull SD card and edit `extlinux.conf` from a workstation.

## 2. Userspace overlay update

Use when the change is to scripts, configs, or userspace binaries that
don't require a kernel change. Lowest-risk path; often avoids even a
reboot.

```sh
sudo nclawzero-update overlay /srv/nclaw/apps/userspace-v2.tar.gz
```

Unpacks the tarball starting at `/`, then:
- Runs `systemctl daemon-reload`
- Restarts the units listed in the tarball's top-level `RESTART-UNITS`
  file (one per line, e.g. `zeroclaw.service`, `llama-server-gemma.service`)
- Does NOT touch `/boot`, `/lib/modules`, or any mount config

## 3. A/B slot flip

Use when the change is large enough to warrant a full rootfs swap — a
`bitbake nclawzero-image-jetson` result, a distro upgrade, or any change
that touches initrd. Requires the SD card to have a **second rootfs
partition** (`/dev/mmcblk0p2`) sized similarly to the active one.

### One-time setup (per device)

```sh
# Operator: provision the B slot (wipes /dev/mmcblk0p2 — make sure nothing
# valuable is on it). Idempotent: no-op if slot B already set up.
sudo nclawzero-update slot-init
```

After setup you'll see:

```
lsblk
NAME        MAJ:MIN RM   SIZE RO TYPE MOUNTPOINTS
mmcblk0     179:0    0  58.2G  0 disk
├─mmcblk0p1 179:1    0    28G  0 part /          # slot A (active)
└─mmcblk0p2 179:2    0    28G  0 part              # slot B (staged)
```

### Install to inactive slot

```sh
# On ARGOS: build + ship the full rootfs tarball
bitbake nclawzero-image-jetson
scp /home/jasonperlow/yocto-tmp/build-jetson-tmp/deploy/images/jetson-orin-nano-devkit/nclawzero-image-jetson-*.rootfs.tar.gz \
    pi@<jetson>:/srv/nclaw/apps/nclawzero-rootfs-new.tar.gz

# On the Jetson: install to the inactive slot (does NOT reboot)
sudo nclawzero-update slot-install /srv/nclaw/apps/nclawzero-rootfs-new.tar.gz
```

### Switch slots + reboot

```sh
sudo nclawzero-update slot-switch     # flips extlinux DEFAULT to the other slot
sudo reboot
```

The extlinux menu gives you 30 seconds to intervene at each boot — press
any key and pick the old slot if the new one doesn't come up. The
`boot.slot_suffix=_nclawzero_a` / `_b` cmdline arg tells meta-tegra's
initrd to skip its PARTLABEL scan and honour the explicit `root=` we set
in `APPEND`.

### Rollback

```sh
# Works from either slot — flips DEFAULT back to whichever slot you
# booted from last successfully.
sudo nclawzero-update slot-rollback
sudo reboot
```

## When things really break: recovery

1. **Boot menu** is always your first stop. 30-second timeout at extlinux;
   press any key to see all labels. Pick `primary-previous` or the other
   slot.
2. **SD card read on workstation** — pull the SD, plug into any Linux
   workstation, edit `<mountpoint>/boot/extlinux/extlinux.conf`, change
   `DEFAULT`, save, eject, put back in Jetson, boot.
3. **USB recovery (nuclear)** — connect a host to the Jetson via the
   type-C port, short the REC pin, run meta-tegra's `doflash.sh`. Only
   needed if QSPI bootloader gets corrupted (our in-place + A/B paths
   never touch QSPI).

## Appendix: what's stored where

| Location                              | Survives updates?             |
|---------------------------------------|-------------------------------|
| `/srv/nclaw/` (NVMe)                  | Always — not touched by any update |
| `/srv/nclaw/models/`                  | Models live here; no re-download needed |
| `/srv/nclaw/apps/`                    | Update payloads staged here; operator-owned |
| `/srv/nclaw/workspace/`               | zeroclaw skill scratch; preserved across A/B flips |
| `/etc/zeroclaw/`                      | Slot-local (A/B will replace) |
| `/var/lib/zeroclaw/` (rootfs)         | Slot-local; use `/srv/nclaw/workspace` for work you want preserved |

## Appendix: tying to Yocto recipe changes

| Recipe / change                                           | Path to deploy              |
|-----------------------------------------------------------|-----------------------------|
| `recipes-kernel/linux/files/nclawzero-jetson-hw.cfg`       | **In-place kernel+mods**    |
| `recipes-core/nclawzero-system-config/*`                   | **Userspace overlay**       |
| `recipes-zeroclaw/zeroclaw/files/config.toml`              | **Userspace overlay**       |
| `recipes-graphics/plymouth/*`                              | **Userspace overlay**       |
| `recipes-bsp/uefi/l4t-launcher-extlinux.bbappend`          | **A/B slot flip** (initrd+extlinux gen changes) |
| Full image rebuild                                         | **A/B slot flip**           |
