# TYDEUS (Jetson Orin Nano Super) — nclawzero system state

**Hostname:** `nclawzero`
**Model:** NVIDIA Jetson Orin Nano Engineering Reference Developer Kit Super (p3767-0005)
**OS:** Poky (Yocto Project Reference Distro) 5.0.17 scarthgap
**Kernel:** 5.15.148-l4t-r36.4.4-1012.12+gc8a82765359e (meta-tegra)
**Rootfs:** `/dev/mmcblk0p1` (SD card) — PARTUUID `365c9438-2077-4022-a86d-078cd42f5e1b`
**Last configured:** 2026-04-24 by rescue toolkit over SSH

## What works

- Yocto boots from SD via UEFI → L4TLauncher → extlinux (uses `boot.slot_suffix=_nclawzero`
  to bypass the meta-tegra initrd's `PARTLABEL=APP` scan that was picking NVMe)
- SSH: pi (NOPASSWD sudo) + root (password `Gumbo@Kona1b`, prohibit-password login)
- `authorized_keys` baked for pi and root
- `zeroclaw.service` running + `/health` 200 OK
- `nemoclaw-firstboot.service` completed
- USB-Ethernet adapter works out of the box (ax88179 driver present)
- Thermal: PWM fan running, zones at 60-63°C idle (fan step 1/3)

## Known issues on THIS flashed image (fixes applied in place)

1. **Onboard NIC (Tegra EQOS at `ethernet@2310000`) is `status="disabled"` in the DTB.**
   Fix applied: copied JetPack's `tegra234-p3768-0000+p3767-0005-nv-super.dtb` from
   NVMe to `/boot/` and added `FDT` line to `/boot/extlinux/extlinux.conf`. Takes
   effect on next reboot.

2. **zeroclaw creates workspace in `--config-dir` (not `[skills].workspace_dir` from config.toml).**
   Fix applied: symlinked `/etc/zeroclaw/workspace` → `/var/lib/zeroclaw/workspace` and
   `/etc/zeroclaw/skills` → `/var/lib/zeroclaw/skills`, chowned `/etc/zeroclaw` to
   zeroclaw:zeroclaw.

3. **pi user not in sudoers on baseline image.**
   Fix applied: pi added to sudo/wheel/docker groups; `/etc/sudoers.d/90-nclawzero-pi`
   NOPASSWD drop-in.

4. **Root account had empty password.**
   Fix applied: root password set to `Gumbo@Kona1b`. `PermitRootLogin prohibit-password`
   in sshd_config (key-only root SSH).

5. **IdleAction=poweroff default caused auto-shutdown under JetPack.**
   Fix applied (persistent): `/etc/systemd/logind.conf.d/99-nclawzero-no-idle-poweroff.conf`
   sets `IdleAction=ignore`, masks sleep/suspend/hibernate/hybrid-sleep targets.

6. **UEFI capsule update was retrying every boot (green bar at POST).**
   Fix applied: `/boot/efi/EFI/UpdateCapsule/*.cap` moved to `/var/backups/tydeus-capsule-*`,
   `nv_update_engine` + siblings disabled.

7. **Thermal tuning not persistent.**
   Fix applied: `nclawzero-thermal-tune.service` at multi-user.target sets CPU governor
   to conservative/powersave, runs `nvpmodel -m 1` if nvpmodel is present.

8. **systemd-networkd DHCP wasn't configured for USB-Ethernet.**
   Fix applied: `/etc/systemd/network/10-wired.network` with DHCP for `eth0 enP* enu* enx*`.

## Pending items for the meta-nclawzero image recipe

These should be baked in so fresh flashes don't need the rescue toolkit:

- Ship the correct `tegra234-p3768-0000+p3767-0005-nv-super.dtb` in the Yocto `/boot/` and
  reference via `FDT` in the generated extlinux.conf (so onboard NIC works out of the box)
- Generate extlinux.conf with `boot.slot_suffix=_nclawzero` + `root=PARTUUID=...` from start
- Bake pi in sudo+wheel+docker groups via EXTRA_USERS_PARAMS
- Ship `/etc/sudoers.d/90-nclawzero-pi` via a recipe file
- Set root password (or lock it to pubkey-only) via EXTRA_USERS_PARAMS  
- Ship `/etc/systemd/logind.conf.d/99-nclawzero-no-idle-poweroff.conf` as a file in a recipe
- Ship `/etc/systemd/network/10-wired.network` as a file in a recipe
- Ship the `nclawzero-thermal-tune.service` unit
- Symlink or correct the zeroclaw workspace/skills paths in the image rootfs
- Ship the nclawzero-ssh-keys recipe (authorized_keys for pi) in the image

## How to access

- `ssh pi@<ip>` (NOPASSWD sudo)
- `ssh root@<ip>` (pubkey only; `PermitRootLogin prohibit-password`)
- zeroclaw gateway: `http://<ip>:42617` (health at `/health`)

## 2026-04-24 follow-up — onboard NIC gap

Attempted fix (FDT line pointing at JetPack's super-variant DTB) did not work —
JetPack's DTB has the ethernet nodes at `status="disabled"` too, and the FDT
line in extlinux.conf didn't override what UEFI loaded anyway (UEFI pulls DTB
from the `A_kernel-dtb` partition at /dev/mmcblk0p3, not from /boot/).

JetPack enables onboard ethernet via a mechanism below extlinux — likely the
Tegra plugin-manager applying overlays based on board ID, or a kernel-command
that meta-tegra's Yocto image doesn't set up. Investigation needed in the
meta-nclawzero image recipe:

- Compare /dev/mmcblk0p3 DTB on a JetPack-booted system vs the one loaded under
  Yocto — content bytes may differ
- Check if meta-tegra's flash recipes produce dtbos that need plugin-manager
  wiring
- Check if UEFI boot-app supports an "OVERLAYS" directive we should populate
- Fallback: ship a ready-enabled DTB in the meta-nclawzero layer and write it
  to A_kernel-dtb at first boot

**Decision**: ship the image with the USB-Ethernet adapter path as the
canonical network route for now. Document as a known limitation; do not block
demo readiness on it.

## Demo-ready summary (post-2026-04-24 session)

- Plugable AX88179 USB-Ethernet works out of the box. SSH reachable at whatever
  DHCP assigns.
- zeroclaw runtime healthy at `http://<ip>:42617/health` (responds 200 OK).
- nemoclaw-firstboot ran to completion.
- Auto-power-off disabled; thermal tuning service installed.
- Root password set; sshd pubkey-only for root.
- pi user has NOPASSWD sudo.
- All rescue scripts on the NCLAWRESCUE USB for further triage.

## 2026-04-24 follow-up #2 — PCIe NIC + WiFi drivers are the actual missing piece

Deeper investigation after the DTB-swap attempt revealed that the DTB is a
red herring. The Orin Nano Super's onboard 1GbE is NOT the Tegra MGBE/EQOS —
it's a separate Realtek RTL8168 chip on the PCIe bus. And the M.2 Key-E slot
has a Realtek RTL8852CE WiFi+BT combo card present.

lspci evidence:
- `0008:01:00.0 [10ec:8168]` Class 0x020000 (Ethernet controller, RTL8168)
- `0001:01:00.0 [10ec:c822]` Class 0x0280 (WiFi, RTL8852CE combo)

Both devices enumerate cleanly at boot but have no driver to bind:
- `r8169.ko` — NOT in /lib/modules/<kver> on this Yocto image
- `rtw89*.ko` (+ rtw89_8852ce) — NOT in /lib/modules/<kver> on this Yocto image

### Recipe work required in meta-nclawzero

Add these kernel modules to the Yocto build:
- `kernel-module-r8169` (for RTL8168 onboard ethernet)
- `kernel-module-realtek` (PHY driver, already loadable but confirm it's shipped)
- `kernel-module-rtw89-pci` + `kernel-module-rtw89-8852ce` + `kernel-module-rtw89-core` (RTL8852CE WiFi)
- `wireless-regdb` (regulatory.db; silences cfg80211 warning)
- `linux-firmware-rtl-nic` (rtl8156b-2.fw for USB-Eth offloads, already hand-installed on this TYDEUS)
- `iw` + `wpa-supplicant` + `hostapd` (user-space tools for WiFi)
- `dtc` (device-tree-compiler, so on-device investigation is easier)

### Current TYDEUS status post-discovery

- USB-Ethernet (Plugable AX88179) at 192.168.207.64: WORKING
- Onboard RTL8168 PCIe ethernet: hardware present, no driver loaded — RECIPE GAP
- RTL8852CE WiFi+BT: hardware present, no driver loaded — RECIPE GAP
- rtl8156b-2.fw firmware hand-installed (silences dmesg warning)
- NetworkManager / xrdp / zram.service disabled (were failing every boot)
