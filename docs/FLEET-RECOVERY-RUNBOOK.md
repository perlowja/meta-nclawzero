# Fleet recovery runbook — Pi + Jetson edge devices

When something goes wrong on a flashed nclawzero device, this is the playbook. Sequenced from quickest recovery to most invasive.

## 0. Confirm the failure mode

Before doing anything destructive:

- **Power cycle.** Not always the answer, but rules out transient stalls.
- **Ping the device.** `ping <device-ip>` (clawpi 192.168.207.54, zeropi 192.168.207.56, TYDEUS 192.168.207.62). If it pings, you have a working network stack — the problem is application-layer.
- **Check the LAN's DHCP lease table** (TrueNAS / OPNsense / whatever runs DHCP). If the device never asked for an IP, the issue is much further down: kernel didn't boot, NIC didn't enumerate, link is dead.
- **HDMI + USB keyboard if present.** Console is the ground truth. Look for `getty` login prompt vs. emergency shell vs. silent kernel panic.

Identify which level the failure is at before picking a recovery tier.

## Tier 1 — SSH still works, account problem

Symptom: `ssh ncz@<device>` rejects keys, or login lands but then `sudo` prompts for a password.

**Try the backup account first.** Every nclawzero image post-2026-04-26 ships a second sudo-NOPASSWD account named `jasonperlow` with the same authorized_keys baked in:

```bash
ssh jasonperlow@<device-ip>
sudo -n true && echo "sudo works"
```

If that gets you in, the operator account (`ncz`) has been disrupted somehow. Common causes:

- **Pi OS Trixie's `userconfig` service stripped the operator account.** Symptom: sshd prints "SSH may not work until a valid user has been set up" and refuses every login. The operator account literally doesn't exist anymore in `/etc/passwd`. Fix: bake `userconf.txt` (see Tier 2) and reboot. Or recreate the user manually:
  ```bash
  # As jasonperlow, sudo
  sudo useradd -m -s /bin/bash -G sudo,wheel,docker,video,audio,input,plugdev,netdev,render ncz
  echo 'ncz ALL=(ALL:ALL) NOPASSWD: ALL' | sudo tee /etc/sudoers.d/90-nclawzero-ncz
  sudo install -d -m 0700 -o ncz -g ncz /home/ncz/.ssh
  sudo install -m 0600 -o ncz -g ncz /home/jasonperlow/.ssh/authorized_keys /home/ncz/.ssh/authorized_keys
  ```

- **`/home/ncz/.ssh/authorized_keys` got corrupted or wrong-perm'd.** Reseed from the backup user's copy or from the build-time bake on ARGONAS at `/mnt/datapool/secrets/nclawzero-fleet-keys/authorized_keys`.

- **fleet-auth pulled a bad rotation.** `clawpi` runs the fleet-auth systemd timer; `zeropi` does not. Check `journalctl -u fleet-auth-update` on clawpi.

If both accounts reject your keys, jump to Tier 2.

## Tier 2 — SSH unreachable, device boots, has console access

Symptom: device pings (or DHCP lease shows up), HDMI shows a working login prompt, but SSH refuses keys for both ncz and jasonperlow.

If you have HDMI + USB keyboard:

```
# At the console, log in via Ctrl+Alt+F2 if XFCE auto-takes F1
# Try to log in as ncz with password (Gumbo@Kona1b for legacy, or
# whatever userconf.txt baked).  If that fails, try jasonperlow with
# the locked-password recovery flow:
sudo passwd jasonperlow      # set a temporary password
# log in as jasonperlow remotely, fix the issue, lock again with
sudo passwd -l jasonperlow
```

If no HDMI access, you're effectively in Tier 3 — pull the SD.

## Tier 3 — SD pull / userconf.txt bake / reflash

Symptom: device unreachable on every plane (ping, console, DHCP lease) OR the userconf hard-block has stripped both accounts.

### 3a. Drop a userconf.txt onto the existing SD

This is the right move when the SD itself is healthy and the issue is just that Pi OS Trixie's userconfig service stripped accounts. Pulls the SD, bakes `/boot/firmware/userconf.txt`, re-inserts. Boot brings the configured user back online.

1. Power off the device, pull the SD card.
2. Insert into a USB SD reader on TYPHON (or any Linux host):
   ```bash
   ssh jasonperlow@192.168.207.61
   lsblk    # confirm the device, e.g. /dev/sda
   ENC=$(openssl passwd -6 'Gumbo@Kona1b')
   sudo mkdir -p /mnt/sd-boot
   sudo mount /dev/sda1 /mnt/sd-boot         # FAT32 boot partition
   echo "ncz:${ENC}" | sudo tee /mnt/sd-boot/userconf.txt
   sudo chmod 644 /mnt/sd-boot/userconf.txt
   sudo umount /mnt/sd-boot
   sudo eject /dev/sda
   ```
3. Re-insert SD into device, power on. SSH should work as `ncz` with the password from the bake.

### 3b. Reflash with a known-good image

Use this when the SD itself is corrupt (silent write failures, filesystem damage) or when you want to test a freshly built image. The flash scripts on the Mac auto-resolve the latest image on ARGONAS:

```bash
# clawpi (Pi 4 8GB, 64GB SD):
bash ~/flash-clawpi-sd.sh

# zeropi (Pi 4 2GB, 16GB SD):
bash ~/flash-zeropi-sd.sh
```

Both scripts:
1. Sanity-check the SD card size at `/dev/disk8` (mac-side; adjust if different).
2. Stream the latest matching `image_*-nclawzero-<profile>.img.xz` from ARGONAS (xz-decoded inline).
3. `dd` to `/dev/rdisk8` with `bs=4m conv=sync status=progress`.
4. **Byte-verify** at 6 sample regions (0, 2048, 16384, 1064960, 5000000, 8000000 sectors) by independently reading from ARGONAS and the SD, comparing MD5s. Catches silent SD/reader write failures (the 2026-04-26 corruption pattern: partition table OK but filesystem starts as zeros) **before** you insert the SD into the Pi.

If verify fails: try a different SD card OR a different USB reader. Don't insert a bad SD into the device — it won't boot.

### 3c. TYPHON-direct flash (when Mac path is suspect)

The Mac SD readers have shown silent-write-corruption patterns (2026-04-26 incident). If a Mac-flashed SD doesn't boot, retry on TYPHON which has a more reliable USB reader path:

```bash
ssh jasonperlow@192.168.207.61
lsblk   # confirm the SD device
# Then run the per-host flash script (e.g., /home/jasonperlow/typhon-flash-clawpi.sh)
```

The TYPHON-direct flow runs `dd if=- of=/dev/sda bs=4M conv=fsync` and does the same 6-region byte-verify against the source on ARGONAS.

## Tier 4 — Jetson-specific paths

### 4a. PXE rescue boot

For a Jetson (TYDEUS or any Orin Nano dev kit) where the SD/eMMC won't boot or you need to flash without physical SD access:

1. Connect Jetson to the LAN via the dev-kit ethernet port.
2. Power on holding F11 (or the dev kit's PXE-boot key) to reach the UEFI boot menu.
3. Select PXE boot. UEFI fetches `ipxe-arm64.efi` from ARGOS via TFTP.
4. iPXE chainloads HTTP from `http://192.168.207.22:8088/scripts/boot-jetson.ipxe`.
5. Kernel + the new rescue cpio.gz at `/opt/netboot/http/initrd/jetson-rescue.cpio.gz` get loaded.
6. After kernel handoff, the rescue init:
   - Mounts pseudo-fs.
   - Loads NIC drivers (RTL8168 r8169 for the dev-kit carrier; nvethernet for production carriers with Tegra234 EQOS).
   - Runs udhcpc on whichever interface comes up.
   - Starts dropbear in a setsid background loop.
   - Execs `/bin/sh` as PID 1 (operator console always available).
7. Rescue is reachable: `ssh root@<jetson-ip>` from any fleet host carrying a key in the baked authorized_keys.

What you can do from the rescue:
- Inspect / repair partitions: `parted`, `e2fsprogs`, `mkfs.ext4`, `resize2fs`.
- NVMe operations: `nvme-cli`.
- Re-flash: write a new rootfs to NVMe / SD via `dd` or pipe in over SSH.
- Mount and chroot into the on-disk rootfs to repair config files (e.g., a broken `/etc/fstab`).

The rescue is stateless — every PXE boot is fresh. Don't expect persistent identity (host keys are ephemeral; the operator verifies fingerprints out-of-band).

### 4b. Tegraflash (USB device-mode recovery)

When the Jetson can't even reach UEFI (corrupt QSPI bootloader, broken EFI variables, or the BootChainOsCurrent variable is in a state that won't boot any partition):

1. Hold the **Recovery** button on the dev kit while powering on.
2. Connect the USB-C device port to a Linux host.
3. `lsusb` shows the Jetson in NVIDIA recovery mode (vendor `0955`).
4. Use the tegraflash bundle on ARGONAS at `/mnt/datapool/backups/argos/argos/jetson-images-20260425/nclawzero-image-jetson-dual-jetson-orin-nano-devkit.rootfs.tegraflash.tar.gz` — extract it on a Linux host, `cd` in, run the included `flash.sh` or follow the README inside.

The dev kit's recovery flow does **not** require shorting any pins (that's a different procedure for the production module). REC button + POWER is sufficient.

## Tier 5 — Hardware failure

If the device doesn't even respond to power (no LED, no fan), you're past software recovery. Move to a known-good unit. The fleet has spares; the constraint isn't the unit, it's the time to reflash + provision. zeropi is the canonical 2GB-RAM proof point — don't repurpose it; pull a different unit if zeropi has hardware issues.

## After any recovery: re-verify auth posture

Whenever you've recovered a device, before declaring it healthy:

```bash
ssh ncz@<device>          # primary login
ssh jasonperlow@<device>  # backup login
sudo -n true && echo SUDO_OK   # via either account
```

If both accounts work and sudo is NOPASSWD, the device is operationally restored. Update the fleet inventory with the date of recovery.

## Where the fleet keys live (auth source-of-truth)

**Real fleet authorized_keys file:** `/mnt/datapool/secrets/nclawzero-fleet-keys/authorized_keys` on ARGONAS. Root-owned, mode 0600. Edit there for any rotation; sync to local working trees via `~/sync-fleet-keys.sh` before each image build.

**Per-repo gitignored paths (never committed):**
- `nclawzero/meta/recipes-core/nclawzero-ssh-keys/files/authorized_keys`
- `nclawzero/meta/recipes-core/nclawzero-rescue/files/authorized_keys`
- `nclawzero/pi-gen/stage-zeroclaw/04-bake-authorized-keys/files/authorized_keys`

**Per-repo committed `.example` files:** these are the format-doc placeholders. If git ever surfaces a real authorized_keys file as untracked, reset the .gitignore — pubkey content reveals fleet topology.

See `feedback_auth_local_only_keys.md` in MNEMOS for full policy details.
