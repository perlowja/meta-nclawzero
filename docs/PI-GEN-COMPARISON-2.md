# pi-gen vs meta-nclawzero: Independent Structural Comparison
<!-- PI-GEN-COMPARISON-2.md — independent cross-check audit -->
<!-- Author: Claude Code (agent, cross-check pass) -->
<!-- Date: 2026-04-24 -->
<!-- Constraint: written before reading PI-GEN-COMPARISON.md -->

---

## Audit scope

- **pi-gen** at `/tmp/pi-gen/` on ARGOS (RPi-Distro/pi-gen, depth-1 HEAD)
- **meta-nclawzero** at `/mnt/argonas/nclawzero-yocto/meta-nclawzero/` (commit 8bd8d24)
- Focus: structural patterns, operator UX, image composition, field deployment

---

## 1. What patterns should we steal from pi-gen?

Ranked by operational leverage for demo/field-deployment.

### 1.1 Boot-partition operator sentinel files (HIGH — steal now)

**pi-gen approach:**
`stage2/04-cloud-init/` drops three files into `/boot/firmware/` at image-build time:
- `user-data` — cloud-init YAML, operator edits to set hostname, keys, WiFi, packages
- `network-config` — netplan YAML, operator edits for WiFi creds, static IP
- `meta-data` — instance identity for cloud-init NoCloud data-source

Additionally, `stage2/01-sys-tweaks/00-run.sh` handles:
- `PUBKEY_SSH_FIRST_USER` env var → writes to `${ROOTFS_DIR}/home/${FIRST_USER_NAME}/.ssh/authorized_keys` at build time
- `ENABLE_SSH` env var → `systemctl enable/disable ssh` at build time

The cloud-init path means the operator touches only the FAT partition (readable
from any OS, no Linux tools needed) and powers on. The NoCloud data-source
reads those YAML files on first boot, configures the system, and self-disables.

**Our current approach:**
`nclawzero-ssh-keys_1.0.bb` bakes a static `authorized_keys` into the image at
build time. This requires a rebuild cycle for every key rotation. There is no
mechanism for a field operator to drop an SSH key or WiFi credential onto the
FAT/boot partition before first power-on. The `wpa_supplicant-wlan0.conf.template`
in `nclawzero-system-config` is useful but requires SSH access to deploy — catch-22
on a device with no baked keys.

**Gap:** An operator handing an SD card to Phil Lawrence's team cannot currently
provision a new SSH key without a rebuild or console access. pi-gen's sentinel
pattern solves this with zero tooling beyond a file copy.

**Effort:** ~100 lines of a new `nclawzero-boot-sentinel` recipe + one systemd
oneshot unit (`nclawzero-sentinel.service`). Much less than a full cloud-init
adoption (see §2).

---

### 1.2 Single root `config` file with operator-editable knobs (MEDIUM-HIGH — steal the pattern)

**pi-gen approach:**
`/pi-gen/config` (sourced by `build.sh` at line ~60) exports all operator-facing
variables as a flat shell env file:

```sh
IMG_NAME
TARGET_HOSTNAME
FIRST_USER_NAME
FIRST_USER_PASS
ENABLE_SSH
PUBKEY_SSH_FIRST_USER
PUBKEY_ONLY_SSH
LOCALE_DEFAULT
KEYBOARD_KEYMAP
KEYBOARD_LAYOUT
TIMEZONE_DEFAULT
WPA_COUNTRY
ENABLE_CLOUD_INIT
DEPLOY_COMPRESSION
```

One file, no code reading required. CI pipelines override with `-c myconfig`.
The `build-docker.sh` wrapper also reads it and forwards the values to the
container. Every build artifact name and compression choice flows from this.

**Our current approach:**
Operator-facing knobs are scattered across:
- `conf/distro/nclawzero.conf` — distro-level feature flags
- Image recipe vars like `IMAGE_ROOTFS_EXTRA_SPACE`, `EXTRA_USERS_PARAMS`,
  `IMAGE_FSTYPES`, hardcoded hostnames in comments rather than variables
- `conf/local.conf` in each build workspace (not in the layer at all)
- Environment variables passed to `bitbake` ad-hoc

There is no single file an operator edits to change hostname, locale, timezone,
or first-user SSH key for a custom field image. The Yocto equivalent is a
`conf/site.conf` or a well-documented `conf/local.conf.sample` that gets copied
once. Neither exists in the layer.

**Effort:** A `conf/nclawzero-operator.conf.sample` with 15-20 documented
variables (`NCLAWZERO_HOSTNAME`, `NCLAWZERO_TIMEZONE`, `NCLAWZERO_SSH_PUBKEY`,
`NCLAWZERO_WIFI_COUNTRY`, etc.) that feed into the image recipes via `?=`
defaults. ~50 lines of conf + recipe plumbing. This is documentation + convention,
not new infrastructure.

---

### 1.3 SKIP / SKIP_IMAGES sentinel files for stage composition (LOW-MEDIUM — adapt the idea)

**pi-gen approach:**
Operators create a `SKIP` file in any stage directory to omit it from the build.
`SKIP_IMAGES` suppresses image export without skipping the stage content. The
`EXPORT_IMAGE` marker in stage2, stage4, and stage5 triggers three distinct
image variants (`-lite`, full, `-full`) from the same accumulated rootfs tree.
The stage-copy mechanism (`copy_previous` rsync) lets each stage checkpoint
independently without re-running earlier stages.

This gives pi-gen "build to stage N and stop" reproducibility without any
external build system infrastructure. Operators can skip stages 3-5 to get a
lite image, skip stage 2's cloud-init substage for a minimal image, etc.

**Our current approach:**
We rely on Yocto's sstate cache for similar reproducibility. The image variants
(`nclawzero-image.bb`, `-jetson.bb`, `-jetson-dual.bb`, `-desktop.bb`) serve
the same purpose as pi-gen's stage2 vs stage4 vs stage5 export variants.
However, the relationship between the four images is ad-hoc:
- `nclawzero-image-desktop.bb` uses `require` to inherit the base image
- `nclawzero-image-jetson-dual.bb` uses `require nclawzero-image-jetson.bb`
- `nclawzero-image-jetson.bb` does NOT `require` from `nclawzero-image.bb`
  despite duplicating ~30% of its content (user creation block, `IMAGE_LINGUAS`,
  several `IMAGE_INSTALL` packages)

The pattern is inconsistent. This is the clearest structural weakness (see §3).

**Adapt:** Use `require`-based tiered hierarchy, not the sentinel-file mechanism.
The Yocto equivalent is cleaner — no filesystem state management needed.

---

### 1.4 SBOM and image provenance (LOW — worth adding)

**pi-gen approach:**
`export-image/05-finalise/01-run.sh` generates:
- `${IMG_NAME}.info` — dpkg -l snapshot of the rootfs + firmware/kernel git hashes
- `${IMG_NAME}.sbom` — SPDX JSON via `syft` if available
- `${IMG_NAME}.bmap` — block map via `bmaptool` for fast flashing

The info file includes the pi-gen git hash that produced the image, making
a deployed SD card self-describing. `rpi-issue` also embeds this in `/etc/`.

**Our current approach:**
Yocto automatically generates `image-manifest` (package list), and wic.bmap
is already in `IMAGE_FSTYPES`. We do not have an equivalent of the info file
with build git hash embedded into `/etc/` on the device. The `PI_GEN_HASH`
pattern (a text file at `/boot/firmware/issue.txt`) is valuable for field
diagnostics — an operator can `cat /boot/firmware/issue.txt` to know exactly
which layer commit produced this image.

**Effort:** One `do_image_complete:append` in a `nclawzero-image-common.inc`
(itself a recommendation — see §3) that writes the bitbake build metadata to
`/etc/nclawzero-issue`. ~20 lines.

---

## 2. What patterns should we NOT steal?

### 2.1 debootstrap + chroot + quilt patch stack

pi-gen builds by bootstrapping a Debian rootfs from scratch with `debootstrap`,
then running all customization inside `chroot` via `on_chroot()`. Quilt applies
patches to the live rootfs. This is unavoidable for a Debian-based tool but
creates:
- Non-reproducible builds (APT package versions drift unless pinned)
- Chroot escape risks without careful mount management (`/proc`, `/dev`, etc.)
- No cross-compilation: the build host must be ARM-capable or use QEMU

Yocto's sstate-cache + sysroot isolation + devtool cross-compilation is
structurally superior. We should not adopt any chroot-based customization
pattern. Our `pkg_postinst_ontarget` tasks are the correct Yocto equivalent
for operations that must run on-target, and they run in a controlled environment.

### 2.2 Full cloud-init stack adoption

pi-gen's `stage2/04-cloud-init` installs and enables the full `cloud-init` daemon
(meta-data, user-data, network-config). This is appropriate for a general-purpose
Debian image targeting cloud and VM deployments, where operators expect cloud-init.

For nclawzero:
- cloud-init is a heavy Python stack (~40MB) that conflicts with our "no web junk"
  doctrine and adds attack surface
- The `nemoclaw-firstboot.service` already handles our provisioning use case
- Our targets are embedded devices, not VMs. cloud-init's network/meta-data/user-data
  three-file model is designed for hypervisor IMDS, not SD cards

**What to steal instead:** the _concept_ of boot-partition sentinel files, implemented
as a lightweight bash + systemd oneshot, not cloud-init. See §5.

### 2.3 stage0's debootstrap + APT proxy model

pi-gen uses `APT_PROXY` and retry logic for network package installation. Yocto's
offline-build (DL_DIR + sstate) is strictly better — builds are reproducible without
network access after the first fetch. We should not introduce APT or runtime package
managers into our build path.

### 2.4 Root-level execution requirement

pi-gen requires `sudo` or root for the entire build (`"Please run as root" 1>&2`).
Yocto builds as a normal user. This is a meaningful deployment security difference
for CI/CD. Do not regress.

### 2.5 Stage-based rsync rootfs copy

pi-gen's `copy_previous` rsync between stages is a workaround for the fact that bash
scripts have no dependency tracking. Yocto's task graph + sstate achieves the same
incremental build property without full-rootfs copies. Do not model our composition
on the stage-rsync pattern.

---

## 3. Where is our current structure weaker than pi-gen's?

### 3.1 Image recipe duplication — the clearest structural gap

`nclawzero-image-jetson.bb` is 200+ lines of explicit `IMAGE_INSTALL:append`
blocks, user creation with `EXTRA_USERS_PARAMS`, `IMAGE_LINGUAS`, and feature
flags. `nclawzero-image.bb` (the RPi base) has a nearly identical user creation
block:

```
useradd -r -d /var/lib/zeroclaw -s /usr/sbin/nologin zeroclaw;
useradd -r -d /var/lib/nemoclaw -s /usr/sbin/nologin nemoclaw;
useradd -m -s /bin/bash ...  pi;
```

This appears in both `nclawzero-image.bb` and `nclawzero-image-jetson.bb`
independently. If the pi user's supplementary groups change (e.g., adding `docker`
to the RPi image), it requires editing two files.

Similarly, `debug-tweaks` appears in both base and jetson images as a hardcoded
`IMAGE_FEATURES += "... debug-tweaks"`. There is no central place to flip this
for production hardening across all images at once.

**pi-gen equivalent:** pi-gen has exactly one place (`FIRST_USER_NAME`,
`FIRST_USER_PASS`, `PASSWORDLESS_SUDO`) where user configuration lives.
Stage dependencies ensure this propagates to all image variants.

**Fix:** A `nclawzero-image-common.inc` pulled via `require` in all four image
recipes, containing:
- `EXTRA_USERS_PARAMS` canonical definition
- `IMAGE_LINGUAS = ""`
- `debug-tweaks` as a `?=`-defaulted variable (overridable for production)
- `ssh-server-openssh` feature
- Common monitoring packages

Estimated: ~60 lines of `.inc` + removal of ~80 lines of duplication across the
four image recipes.

### 3.2 No MACHINE-agnostic base image inheritance for jetson

`nclawzero-image-jetson.bb` has `COMPATIBLE_MACHINE = "(tegra)"` but does NOT
`require nclawzero-image.bb` — it independently re-specifies the entire package
set. Contrast with `nclawzero-image-desktop.bb` which correctly uses
`require recipes-core/images/nclawzero-image.bb`. The jetson image should have
a clear "is-a superset of" relationship to the base image, not a sibling
relationship. This would also fix the user-creation duplication in 3.1.

The only reason the jetson image does not inherit the base is probably the
`DISTRO_FEATURES:remove = "x11 wayland vulkan"` in the base image — which then
has to be un-removed in the jetson image. The right fix is to not have the base
image remove features at all; let image recipes configure their own features and
let the common `.inc` set neutral defaults.

### 3.3 No operator field-provisioning path (field-UX gap)

See §5 for full treatment. In summary: pi-gen ships with a documented mechanism
(`PUBKEY_SSH_FIRST_USER`, cloud-init sentinel files) for operators to personalize
an image without rebuilding it. We have none.

### 3.4 No single-config-file equivalent

See §1.2. Our knobs are scattered. There is no `nclawzero-operator.conf.sample`
that documents the 8-10 things a field operator needs to customize.

---

## 4. Where is our current structure stronger than pi-gen's?

### 4.1 Reproducible builds and sstate cache

Yocto's sstate means a second build of the same revision with the same inputs
produces a bitwise-identical image without re-running any task whose inputs
have not changed. pi-gen has no equivalent — APT packages can drift, and the
quilt patch stack runs unconditionally. This is a significant advantage for
regulatory certification (SBOM stability), CI/CD, and supply-chain provenance.

**Do not regress:** never add runtime APT operations, `pip install` at image
assembly, or any other step that fetches packages without a fixed hash.

### 4.2 Cross-compilation for multiple targets from one layer

pi-gen is armhf-only and requires native ARM hardware or QEMU binfmt_misc.
meta-nclawzero produces images for `raspberrypi4-64` (aarch64) and
`jetson-orin-nano-devkit` (also aarch64, but completely different BSP) from a
single layer, built with proper cross-compilation. Adding a new target (Jetson
AGX, Pi 5) requires a new image recipe and possibly a `.bbappend`, not a fork
of the build system.

### 4.3 Dual-slot A/B rootfs with extlinux rollback

`nclawzero-jetson-dual.bb` + `nclawzero-jetson-dual.wks` + the multi-label
`l4t-launcher-extlinux.bbappend` give us operator-recoverable A/B rollback
with a 3-second TIMEOUT menu. pi-gen has no upgrade or rollback concept at all.
The `nclawzero-update` CLI adds kernel and overlay in-place update paths on top.
This is substantially more sophisticated than anything in pi-gen.

### 4.4 Proper package layering via packagegroups

`packagegroup-nclawzero.bb` and `packagegroup-nclawzero-desktop.bb` give us
clean separation of "what is the agent stack" from "what goes in which image."
pi-gen's package lists (00-packages files) are flat, per-substage, with no
re-usable grouping across variants. Our packagegroup approach is correct and
should be extended, not simplified.

### 4.5 Declarative user creation with `extrausers`

`EXTRA_USERS_PARAMS` in Yocto's `extrausers` class creates users with locked
passwords (`-p '!'`) and correct group membership at image assembly, before any
target system runs. pi-gen's approach (`adduser` in `on_chroot`) runs at build
time but inside an ARM rootfs, requiring chroot. Our approach is host-native
and part of the reproducible build graph.

### 4.6 Kernel config fragments via SRC_URI

`linux-jammy-nvidia-tegra_%.bbappend` and `linux-raspberrypi_%.bbappend` apply
kernel config via `.cfg` fragments that are checksummed, version-controlled, and
applied before compile. pi-gen has no kernel build — it takes the pre-built
Raspberry Pi kernel as-is. The ability to gate on `dmesg -l err` being empty
(the no-broken-drivers rule) is only possible because we own the kernel config.

### 4.7 Tailscale, ZeroClaw, and custom binary recipes

`tailscale_1.96.4.bb`, `zeroclaw-bin_0.7.3.bb`, `nodejs-bin_22.22.2.bb` give
us pinned, hash-verified, cross-compiled binaries that pi-gen could only deliver
by downloading at build time (not reproducible) or by copying binary blobs with
no version tracking.

---

## 5. Operator UX gap: field provisioning

**The scenario:** Phil Lawrence's team receives an SD card, wants to add their
SSH public key, optionally configure WiFi, and power on the Jetson without any
console access and without a layer rebuild.

**Current state:** They cannot. `nclawzero-ssh-keys` bakes a single
`authorized_keys` at build time. Changing it requires a rebuild. The
`wpa_supplicant-wlan0.conf.template` requires SSH access to deploy, which
requires the SSH key catch-22. No FAT-accessible sentinel mechanism exists.

**pi-gen model:** `PUBKEY_SSH_FIRST_USER` bakes the key at build time, OR
the cloud-init `user-data` file on `/boot/firmware` (FAT, Windows-readable)
allows the operator to set an SSH key before first boot.

**Proposed nclawzero implementation:**

The design goal is to keep it lightweight (no cloud-init) and match our
systemd + bash posture.

**FAT partition sentinel files** (drop into `/boot` or `/boot/firmware` on the
SD card before inserting into the device):

```
/boot/nclawzero/
    authorized_keys      # SSH pubkey(s), one per line. Appended to pi's authorized_keys.
    wpa_supplicant.conf  # Full wpa_supplicant.conf for WiFi. Copied into place and activated.
    hostname             # Single line, new hostname. Applied via hostnamectl.
    firstboot.env        # KEY=value pairs for any other operator knobs.
```

Using a subdirectory `/boot/nclawzero/` rather than root-level sentinels avoids
name collisions with the firmware layer files (`config.txt`, `cmdline.txt`, etc.)
and groups all nclawzero provisioning signals visually.

**New recipe:** `recipes-core/nclawzero-boot-sentinel/nclawzero-boot-sentinel_1.0.bb`

**New systemd unit:** `nclawzero-boot-sentinel.service`

Unit ordering:
```
[Unit]
After=local-fs.target
Before=sshd.service nemoclaw-firstboot.service
ConditionPathExists=/boot/nclawzero
```

Service type: `oneshot`, `RemainAfterExit=yes`

**Script outline** (`nclawzero-boot-sentinel.sh`):
1. Mount check: verify `/boot` is mounted (it should be via fstab, but guard)
2. If `/boot/nclawzero/authorized_keys` exists:
   - Append to `/home/pi/.ssh/authorized_keys` (create with correct perms if absent)
   - Remove the sentinel file (consume-once semantics)
3. If `/boot/nclawzero/wpa_supplicant.conf` exists:
   - Copy to `/etc/wpa_supplicant/wpa_supplicant-wlan0.conf`
   - `systemctl restart wpa_supplicant@wlan0.service`
   - Remove the sentinel file
4. If `/boot/nclawzero/hostname` exists:
   - `hostnamectl set-hostname $(cat /boot/nclawzero/hostname)`
   - Remove the sentinel file
5. If `/boot/nclawzero/firstboot.env` exists:
   - Source it and handle known keys (future extensibility hook)
   - Remove the sentinel file
6. Log all actions to `/var/log/nclawzero-boot-sentinel.log`
7. If `/boot/nclawzero/` is now empty, remove the directory

**Consume-once semantics** are critical: removing the sentinel after processing
prevents a password-less reboot from replaying WiFi credentials, and makes the
provisioning audit trail clear (sentinel gone = was applied).

**Wiring into existing image recipes:**
Add `nclawzero-boot-sentinel` to `packagegroup-nclawzero.bb`. This pulls it
into all four image recipes automatically. No image recipe changes needed.

**Estimated effort:** ~150 lines total (recipe + service unit + script). One
working day for a careful implementation with logging and error handling.

---

## 6. Single-config-file pattern: consolidation sketch

**Current state of scattered knobs:**

| Knob | Current location |
|------|-----------------|
| Distro name, version | `conf/distro/nclawzero.conf` |
| Image hostname | Hardcoded in comments; set by `extrausers` or `systemd-firstboot` |
| Timezone | Not set at image level; relies on systemd default |
| Locale | `IMAGE_LINGUAS = ""` (empty = C.UTF-8 default) |
| First user | `EXTRA_USERS_PARAMS` in each image recipe (duplicated) |
| SSH key | `recipes-core/nclawzero-ssh-keys/files/authorized_keys` (static file) |
| WiFi country | Not set |
| debug-tweaks | `IMAGE_FEATURES += "debug-tweaks"` in each image recipe |
| Deploy compression | Not specified (Yocto default) |
| Extra space | `IMAGE_ROOTFS_EXTRA_SPACE` per image recipe (different per target) |
| CUDA version | Hardcoded package names in jetson image recipe |

**Proposed consolidation: `conf/nclawzero-operator.conf`**

This file would live at `conf/nclawzero-operator.conf` in the layer and be
`include`d by `conf/layer.conf` (or documented for inclusion in
`conf/local.conf`). It sets all user-facing knobs with `?=` defaults so any
can be overridden without editing the file:

```bitbake
# nclawzero operator configuration — edit this file for your deployment
# All variables use ?= so they can be overridden in local.conf

# Image identity
NCLAWZERO_HOSTNAME          ?= "nclawzero"
NCLAWZERO_TIMEZONE          ?= "UTC"
NCLAWZERO_LOCALE            ?= "en_US.UTF-8"
NCLAWZERO_WIFI_COUNTRY      ?= "US"

# First user
NCLAWZERO_USER              ?= "pi"
NCLAWZERO_USER_GROUPS       ?= "sudo wheel docker video audio input plugdev"

# SSH key (leave empty to omit from image; use boot-sentinel for field provisioning)
NCLAWZERO_SSH_PUBKEY        ?= ""

# Debug / production toggle
# Set to "" to disable debug-tweaks (remove empty-root-password, disable debug shell)
NCLAWZERO_DEBUG_IMAGE       ?= "debug-tweaks"

# Deploy compression
NCLAWZERO_DEPLOY_COMPRESSION ?= "gz"

# Extra rootfs space (KB) — per-image recipes can override with their own ?=
NCLAWZERO_EXTRA_SPACE_BASE  ?= "524288"    # 512MB base for RPi
NCLAWZERO_EXTRA_SPACE_JETSON ?= "6291456"  # 6GB for Jetson (CUDA headroom)
NCLAWZERO_EXTRA_SPACE_DESKTOP ?= "1048576" # 1GB for Weston desktop
```

Image recipes would then reference `${NCLAWZERO_HOSTNAME}`, `${NCLAWZERO_USER}`,
etc. The user-creation block in `nclawzero-image-common.inc` (see §3.1)
would use these variables, eliminating the current duplication.

The `NCLAWZERO_SSH_PUBKEY` variable provides the build-time equivalent of pi-gen's
`PUBKEY_SSH_FIRST_USER`, feeding into the `nclawzero-ssh-keys` recipe via
`${NCLAWZERO_SSH_PUBKEY}` in `authorized_keys`. An empty value produces an image
with no baked keys, relying on the boot-sentinel mechanism at field time.

**Accompanying deliverable:** `conf/nclawzero-operator.conf.sample` — a fully
commented version shipped in the layer for operators to copy. The actual
`nclawzero-operator.conf` goes in `.gitignore` so site-specific values are never
accidentally committed. (~80 lines of conf + 20 lines of doc update)

---

## Cross-check criteria

### Top 3 recommendations (ranked by operational leverage)

**Rank 1: Boot-partition operator sentinel files**

The `nclawzero-boot-sentinel` mechanism (§5) is the highest-leverage single
addition. The ability to hand a pre-flashed SD card to a field team and have
them provision their SSH key by dropping a file on the FAT partition is the
difference between a professional deployment story and "requires a Linux laptop
and a rebuild." This is ~150 lines of new code, zero recipe restructuring,
and immediate demo value. Phil Lawrence's team can use it on day one.

Concretely: a new `recipes-core/nclawzero-boot-sentinel/` recipe with a
`nclawzero-boot-sentinel.service` unit and a ~100-line bash script, added to
`packagegroup-nclawzero.bb`.

**Rank 2: Tiered image hierarchy via `require` + `nclawzero-image-common.inc`**

The current four image recipes have diverged from a clean inheritance tree.
`nclawzero-image-jetson.bb` should `require nclawzero-image.bb` (or a shared
`.inc`) rather than re-specifying user creation, `IMAGE_LINGUAS`, and half the
`packagegroup-nclawzero` package set independently. The risk of divergence
already bit us with the user group list (`docker` in jetson, not in RPi base).
This is ~80 lines of new `.inc`, ~80 lines removed from existing recipes, and
produces a structure that is immediately more maintainable.

**Rank 3: `conf/nclawzero-operator.conf.sample` single-knob consolidation**

Centralizing the operator-facing variables into a documented file (§6) is the
lowest-code-change item but has outsized documentation value. A new contributor
or a field engineer should not have to read four image recipes to know that
`debug-tweaks` is on, or that timezone is unset, or that the SSH key is baked in
`recipes-core/nclawzero-ssh-keys/files/`. ~50 lines of conf + a comment block.

---

### Things I'd rank differently than a peer reviewer might

**Boot-sentinel over single-config-file consolidation, always.**

A peer reviewer familiar with Yocto tooling might rank the single-config-file
consolidation higher because it "cleans up the layer structure." I rank it third
because it is a developer-experience improvement, not a user-experience
improvement. The operator provisioning gap (rank 1) is a functional blocker for
field deployment: without it, you cannot hand a device to Phil's team. The
config-file consolidation can wait; a missing field-provisioning path cannot.

**Tiered hierarchy is maintenance hygiene, not elegance.**

The `require`-based hierarchy fix is not about making the layer look nice.
It is about ensuring that a change to the pi user's group membership (e.g.,
adding `plugdev` for USB device access) does not require editing two files and
still producing subtly different images for Jetson vs RPi. I rank this second
because the bug it prevents is realistic and has already bit us (the
`nclawzero-system-config` recipe was created precisely to consolidate fixes
that should have been in the base image).

**SBOM provenance (§1.4) is lower priority than it appears.**

pi-gen's `syft`-based SBOM output looks impressive. Yocto already generates
`image-manifest` (dpkg/opkg package list) as part of every build. The actual
gap is the embedded build hash in `/etc/nclawzero-issue`, which is a 20-line
addition with limited field-deployment leverage. I'd do it, but it's a day-5
item, not day-1.

---

### Absolute do-not-recommend (even if appealing)

**Do not adopt cloud-init.**

cloud-init's three-file boot-partition model is exactly what we are proposing
to adopt in the boot-sentinel design. But cloud-init itself (the daemon, the
Python stack, the module system) is inappropriate for nclawzero:

1. It is ~40MB of Python in a runtime that aims to be thin
2. It is designed for hypervisor metadata services (EC2 IMDS, Azure IMDS, GCP);
   the NoCloud data-source is an afterthought that happens to work for SD cards
3. It runs every boot and queries for metadata, adding latency to the boot path
4. Its failure modes (partial config application, module ordering bugs) are
   subtle and hard to diagnose in the field

The boot-sentinel design (§5) delivers the same operator UX with ~150 lines of
bash and a systemd oneshot unit. It runs once, self-disables, and leaves a clear
audit log. That is the right design for an embedded AI agent OS.

**Do not add APT or runtime package installation to the image build.**

pi-gen's `on_chroot apt-get install` pattern tempts you to add "just one more
package" at build time without a proper recipe. Every such shortcut undermines
sstate reproducibility and the supply-chain audit trail. If a package needs to
be in the image, it gets a recipe (even a trivial one) or goes into
`RDEPENDS` of an existing recipe. No exceptions.

---

## Appendix: File references

| File | What it showed |
|------|---------------|
| `/tmp/pi-gen/scripts/common` | `on_chroot()`, `copy_previous()`, `run_stage()` — the chroot execution model |
| `/tmp/pi-gen/build.sh` | The single `config` file pattern; all operator env vars in one place |
| `/tmp/pi-gen/stage2/01-sys-tweaks/00-run.sh` | `PUBKEY_SSH_FIRST_USER` → `authorized_keys` at build time |
| `/tmp/pi-gen/stage2/04-cloud-init/01-run.sh` | boot-partition sentinel file drops |
| `/tmp/pi-gen/stage2/04-cloud-init/files/user-data` | cloud-init YAML template with commented examples |
| `/tmp/pi-gen/export-image/05-finalise/01-run.sh` | SBOM, bmap, info file, machine-id reset, log cleanup |
| `/tmp/pi-gen/export-image/prerun.sh` | Dynamic image sizing with 20% margin |
| `recipes-core/images/nclawzero-image.bb` | RPi base image; extrausers; IMAGE_FSTYPES |
| `recipes-core/images/nclawzero-image-jetson.bb` | 200+ line CUDA+XFCE recipe; user-creation duplication |
| `recipes-core/images/nclawzero-image-jetson-dual.bb` | `require` + anonymous Python BitBake override |
| `recipes-core/images/nclawzero-image-desktop.bb` | Correct `require` pattern from base image |
| `recipes-core/nclawzero-ssh-keys/nclawzero-ssh-keys_1.0.bb` | Static authorized_keys; no runtime provisioning |
| `recipes-nemoclaw/nemoclaw/nemoclaw-firstboot_1.0.bb` | Oneshot systemd service model to emulate |
| `recipes-core/nclawzero-system-config/nclawzero-system-config_1.0.bb` | Consolidation recipe pattern |
| `wic/nclawzero-rpi.wks.in` | Three-partition RPi layout with data partition |
| `wic/nclawzero-jetson-dual.wks` | Dual-slot A/B Jetson layout |
| `recipes-bsp/uefi/l4t-launcher-extlinux.bbappend` | Multi-label extlinux rollback |
