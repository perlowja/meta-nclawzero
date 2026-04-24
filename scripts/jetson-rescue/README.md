# Jetson rescue toolkit

USB-deployable scripts for recovering from nclawzero Jetson Orin Nano boot / module-load failures. None of these are invoked by the build system or baked into the image — they're operator tooling, staged onto a FAT32 USB stick and run from the Jetson's TTY console.

## Why this exists

Recovery from a half-applied JetPack OTA or wedged capsule-update state on a Jetson Orin Nano devkit isn't fun. The REC button is internal (pin-short on the carrier board); networking often dies alongside modules; and the built-in UART requires a host-side TTL adapter some operators don't have at hand. A USB stick with known-good scripts plus a FAT32 filesystem (the only FS guaranteed to mount on Linux without kernel modules) is the most reliable recovery vector that doesn't require physical disassembly.

## Scripts in this directory

### `tydeus-boot-diag.sh`

**Non-destructive.** Run first whenever the Jetson is in an unexpected state.

Dumps:
- Which OS is currently booted (JetPack / Yocto signature files, kernel version, cmdline)
- Full block device layout (`lsblk`, `blkid`, mount points)
- SD + NVMe partition content probes — mounts each read-only, dumps `/boot/`, `/boot/extlinux/extlinux.conf`, `/etc/os-release`
- UEFI boot variables (`efibootmgr -v`)
- Kernel module state — loaded, available at `/lib/modules/$(uname -r)/`, `modules.dep` line count
- PCI enumeration (for NIC / GPU ID)
- systemd failed units
- `dmesg` (first + last 150 lines)
- **UEFI capsule-update state** — `nvbootctrl` slot info, `/opt/nvidia/l4t-bootloader-config/`, OTA staging areas, capsule file locations, NVIDIA update-service states, device-tree chosen flags
- Tegra power + rcm state
- `logind.conf` idle-action settings (for auto-power-off diagnosis)

Output: `tydeus-diag-<UTC-timestamp>.txt` written next to the script on the USB stick.

Read offline on a workstation to decide next steps.

### `fix-tydeus-yocto-boot.sh`

**Destructive — writes to `/boot/extlinux/extlinux.conf` on both SD and NVMe.**

Rewrites `extlinux.conf` on both partitions so:
- `yocto` label is listed first (workaround for Jetson UEFI extlinux loaders that ignore `DEFAULT` and boot the first `LABEL`)
- `primary` label (JetPack/Ubuntu) is kept as arrow-down fallback
- Timeout is 30 seconds (300 deciseconds — extlinux gotcha documented in fleet CLAUDE.md)

Only run after `tydeus-boot-diag.sh` confirms:
- `/boot/Image.yocto` exists on the currently-booted rootfs (kernel to load)
- `/dev/mmcblk0p1` is ext4 with Yocto contents
- `/dev/nvme0n1p1` is ext4 with JetPack

Backs up the prior `extlinux.conf` with a timestamp suffix before writing.

### `nclawzero-jetson-rescue-prober.sh`

**Destructive — loads modules, starts sshd, writes authorized_keys.**

For the state where Yocto IS booting on the Jetson but kernel modules aren't loading (so no networking). Runs after Yocto kernel is live.

Steps:
1. Dumps system state (kernel, model, rootfs)
2. Checks `/lib/modules/$(uname -r)` — if missing/empty, extracts a modules tarball named `modules-*.tgz` from the USB stick (stage one alongside this script if `/lib/modules/` is broken)
3. Loads known Jetson Orin Nano devkit NIC drivers (`r8169` + `phy`/`libphy` deps, plus common USB-ethernet fallbacks)
4. Brings interfaces up, runs DHCP (`dhclient` / `udhcpc` / `systemd-networkd`)
5. Generates sshd host keys if missing, starts sshd
6. Injects `authorized_keys` from the USB stick into `~pi/.ssh/` and `~root/.ssh/`
7. Prints the SSH command to use from the workstation

Log: `rescue-<timestamp>.log` on the USB stick.

## USB stick preparation

The stick must be **FAT32** (a.k.a. MS-DOS). Other filesystems (HFS+, exFAT, NTFS, ext4) require loadable kernel modules that a broken Yocto/JetPack instance can't load.

From macOS (erases the stick):

```sh
# Identify the device first, carefully
diskutil list external

# Reformat — DISK_ID is something like disk8; confirm from above
diskutil eraseDisk MS-DOS NCLAWRESCUE MBR <DISK_ID>
```

From Linux:

```sh
sudo mkfs.vfat -F 32 -n NCLAWRESCUE /dev/sdX1
```

Then copy all three scripts + an `authorized_keys` file (one line per operator SSH pub key) to the root of the stick:

```sh
cp scripts/jetson-rescue/*.sh /Volumes/NCLAWRESCUE/
cp recipes-core/nclawzero-ssh-keys/files/authorized_keys /Volumes/NCLAWRESCUE/
```

## Running order on the Jetson

From TTY console on the broken Jetson:

```sh
sudo mkdir -p /mnt/usb
sudo mount /dev/sda1 /mnt/usb                   # USB may enumerate as sdb1
sudo bash /mnt/usb/tydeus-boot-diag.sh          # ALWAYS first — read-only
sudo umount /mnt/usb
# Pull the stick, bring to workstation, read tydeus-diag-<ts>.txt
# Decide from output whether fix-tydeus-yocto-boot.sh or rescue-prober.sh applies
```

## What these scripts deliberately do NOT do

- **Trigger USB recovery mode.** Requires REC-pin shorting (physical case-open) on Orin Nano devkit — not a software operation we do. If the device needs a full `flash.sh` via USB recovery, that's a separate procedure.
- **Modify QSPI firmware.** Capsule updates are managed by NVIDIA's `nv_update_engine` service; we observe its state in diag but don't touch it.
- **Resolve a stuck capsule-update state.** If diag output shows the Jetson is in a wedged mid-update state (green bar every POST), the fix is typically `sudo nv_update_engine --enable-service` or a clean-rollback via `nvbootctrl mark-boot-successful` on the current slot — but decide from the specific diag output, not blindly.

## When to promote a diag finding into a recipe

Any environment-level condition that keeps biting us (e.g., `IdleAction=poweroff` in `logind.conf`, or a specific module-blocklist that breaks networking) should be fixed **in the Yocto recipe** so future images ship with the condition corrected rather than relying on an operator running a rescue script. Use these scripts to diagnose; fix in the recipe.
