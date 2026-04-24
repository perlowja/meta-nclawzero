# pi-gen vs meta-nclawzero — Structural Pattern Audit

**Date**: 2026-04-24
**Author**: Jason Perlow (research conducted via Claude Opus 4.7)
**Scope**: Comparative audit of [RPi-Distro/pi-gen](https://github.com/RPi-Distro/pi-gen) (Raspberry Pi OS image-builder) against `meta-nclawzero` (this Yocto layer). Recommendation-only; no recipe changes in this commit.
**pi-gen revision**: `master` HEAD as of 2026-04-24 (BSD-3-Clause-style; copy of pi-gen LICENSE retained for any derivative text).

---

## 1. pi-gen architecture in 200 words

pi-gen builds Raspberry Pi OS images by chaining numbered stage directories (`stage0`-`stage5`) executed in lexical order by `build.sh`. Each stage is a chroot that **inherits the previous stage's rootfs** (via `copy_previous` in `prerun.sh`) and incrementally layers more software:

- **stage0** — `debootstrap` minimal base; configure apt + locale; install raspberrypi-bootloader.
- **stage1** — bootability (fstab, bootloader, raspi-config); installs `/boot/firmware/{config,cmdline}.txt`.
- **stage2** — "Lite" image; sets timezone, charmap, fake-hwclock, NTP, WLAN/BT, networking, optional cloud-init.
- **stage3** — Desktop base (X11, LXDE, browser, dev tools).
- **stage4** — Standard Raspberry Pi OS image (4 GB).
- **stage5** — Full image (LibreOffice, Mathematica, Scratch, Sonic Pi).

Each stage contains numbered sub-stages (`00-foo`, `01-bar`) holding plain-text inputs the orchestrator picks up by **convention**: `00-packages`, `00-packages-nr`, `00-debconf`, `00-patches/` (quilt), `00-run.sh` (host), `00-run-chroot.sh` (chroot). A `SKIP` file in any stage dir skips it; a `SKIP_IMAGES` file runs the stage but skips image generation. Stages with `EXPORT_IMAGE` get post-processed by `export-image/` (loop-back, partition, format, rsync, zerofree, bmaptool, compress).

The whole thing reads **one operator-facing file**: `./config` at the repo root, sourced as a bash fragment by `build.sh`.

---

## 2. Pattern-by-pattern comparison

| # | pi-gen pattern | meta-nclawzero equivalent | Gap / Verdict |
|---|---|---|---|
| 1 | **Numbered stage chain** (`stage0`-`stage5`) with `copy_previous` rsync between rootfs trees, `SKIP`/`SKIP_IMAGES` toggles | Three sibling images that mostly duplicate (`nclawzero-image.bb`, `-jetson.bb`, `-jetson-dual.bb`, `-desktop.bb`); only `-jetson-dual` uses `require` | **Adopt the inheritance idea, not the rsync-rootfs.** Yocto's `require` already does this cleanly — we just don't use it. See Recipe Sketch A. |
| 2 | **Plain-text package lists** (`00-packages`, `00-packages-nr`) — one package per line, comments allowed, parsed by sed | Single `RDEPENDS:${PN}` blob in `packagegroup-nclawzero.bb` with bash-style `\` continuations | **Adopt for ops ergonomics.** Recipe Sketch B introduces `package-lists/*.list` consumed at parse time. Massive readability win for non-Yocto operators. |
| 3 | **Debconf preseeding** (`00-debconf`) — answer-file fed to `debconf-set-selections` to suppress first-boot interactive prompts | No debconf (we use opkg/rpm); equivalent is hardcoded `IMAGE_LINGUAS=""` + machine.conf settings + tmpfiles | **DO NOT ADOPT directly** — apt/dpkg-only. The *idea* (operator-edit answer file) is captured by Pattern 10 (single config file). |
| 4 | **Boot-partition operator hooks** — `/boot/ssh`, `/boot/userconf.txt`, `/boot/wpa_supplicant.conf` sentinel files. Implemented by **`raspberrypi-sys-mods` + `userconf-pi`** Debian packages (which pi-gen *installs* but does not vendor). At first boot a `init_resize.sh` / `firstrun.sh` reads the FAT boot partition and acts on the sentinels | Nothing equivalent. `wpa_supplicant-wlan0.conf.template` is `/etc/...` (not `/boot/`); `nemoclaw-firstboot.service` runs unconditionally with no boot-partition input | **HIGH-VALUE ADOPT.** Recipe Sketch C: `nclawzero-boot-provision` reading sentinels from VFAT `/boot/firmware` (RPi) or `/boot/efi` (Jetson UEFI). |
| 5 | **`config.txt` / `cmdline.txt`** — Broadcom-firmware-readable text files in FAT boot partition. Operator edits from any workstation pre-flash | `extlinux.conf` (already FAT-resident on Jetson) + `nclawzero-rpi.wks.in` (RPi) | **Already covered** by Yocto's wic/extlinux. We could ship a **`config.txt`-style operator-readable** README in `/boot/firmware/README.md`. Low value. |
| 6 | **`firstboot` flow** — `userconf-pi` + `piwiz` GUI (desktop) + `rename-user` (lite); `init_setup.sh` runs once, then disables itself by editing `cmdline.txt` to remove its own kernel argument | `nemoclaw-firstboot.service` (oneshot, ConditionPathExists=!marker, runs `nemoclaw-firstboot.sh`); `nclawzero-system-config` is build-time (no first-boot phase) | **Partial parity.** Our marker-file pattern is correct. Gap: we do not act on **operator-supplied input** at first boot — we only do what the recipe baked in. Recipe Sketch C closes this. |
| 7 | **`export-image/` stage** — single source of partitioning, formatting, rsync, zerofree, **bmaptool**, **syft SBOM**, **per-image `.info` + `.sbom` + `.bmap` sidecars**, multi-format compression | `wic` produces `.wic.gz` + `.wic.bmap` (for RPi). No SBOM. No `.info`. No `zerofree` of ext4 reserved blocks. | **MEDIUM ADOPT** — three concrete additions: (a) `syft scan` post-`do_image_complete` for SPDX SBOM (already a Yocto class: `create-spdx`, but not enabled); (b) `IMAGE_POSTPROCESS_COMMAND` to emit `.info` text alongside; (c) `zerofree` on rootfs ext4 before final pack to cut compression size 30–60 %. See Recipe Sketch D. |
| 8 | **Docker-isolated build** (`build-docker.sh`) — `pigen_work` named container, `--volumes-from` for shell-into-failure, `CONTINUE=1` resumability | We use Yocto's sysroot/sstate isolation (already container-grade reproducibility); ARGOS is a fixed build host, not a per-developer container | **DO NOT ADOPT.** Yocto's sstate is strictly stronger. The one borrowable trick: pi-gen's **`PRESERVE_CONTAINER=1` for incremental development** maps cleanly to our existing sstate cache — no work needed. |
| 9 | **`#!/bin/bash -e` + `trap term EXIT INT TERM`** — every script fail-fast; `term()` does unmount cleanup; bind-mount lifecycle (`mount /proc /dev /dev/pts /sys /run /tmp` then unmount on exit) is centralised in `scripts/common`'s `on_chroot`/`unmount` helpers | Our shell scripts (`nemoclaw-firstboot.sh`, `nclawzero-thermal-tune.sh`) use `set -e` but **no consistent trap-based cleanup**. Lock files + marker files are ad hoc | **MEDIUM ADOPT** — write a `scripts/common.sh` helper that nclawzero shell scripts can `source` for consistent `log()`, `die()`, `cleanup()` trap registration. Low risk; high readability dividend. |
| 10 | **Single `./config` file** — every operator knob lives in one bash fragment sourced by `build.sh`. ~25 documented variables (`IMG_NAME`, `TARGET_HOSTNAME`, `FIRST_USER_NAME`, `WPA_COUNTRY`, `ENABLE_SSH`, `LOCALE_DEFAULT`, …) with sane defaults exported by `build.sh` itself | Spread across `conf/distro/nclawzero.conf` (does not exist yet — we're using poky's default), `conf/local.conf` per build-tree, image recipe `EXTRA_USERS_PARAMS`, `nclawzero-system-config` files, `nemoclaw.conf` | **HIGH-VALUE ADOPT.** Recipe Sketch E: a single `conf/distro/nclawzero.conf` with documented `NCLAWZERO_*` variables that the layer's recipes consume via `${@d.getVar('NCLAWZERO_HOSTNAME', True)}`. |

---

## 3. Concrete recipe sketches for the highest-value adoptions

### Sketch A — Tiered image hierarchy via `require`

Replace duplication between `nclawzero-image.bb`, `-jetson.bb`, `-jetson-dual.bb`, `-desktop.bb` with an **explicit ladder** mirroring pi-gen's stage0-5:

```
recipes-core/images/
├── nclawzero-image-base.bb        # stage1 equivalent: bootable minimal
├── nclawzero-image-lite.bb        # stage2: agent runtime, headless
├── nclawzero-image-full.bb        # stage4: + AI inference stack (CUDA/llama-cpp)
├── nclawzero-image-desktop.bb     # stage5: + XFCE + plymouth
└── machines/
    ├── nclawzero-image-rpi.bb     # require lite + RPi WIC kickstart
    ├── nclawzero-image-jetson.bb  # require full + tegraflash
    └── nclawzero-image-jetson-dual.bb  # require jetson + dual-slot wks
```

**`nclawzero-image-base.bb`** (new, replaces the bottom of `nclawzero-image.bb`):

```bitbake
SUMMARY = "nclawzero base image — bootable minimal (stage1 equivalent)"
LICENSE = "MIT"
inherit core-image

IMAGE_FEATURES += "ssh-server-openssh"
IMAGE_INSTALL = "packagegroup-core-boot kernel-modules nclawzero-system-config"
IMAGE_LINGUAS = ""
DISTRO_FEATURES:append = " systemd"
VIRTUAL-RUNTIME_init_manager = "systemd"
```

**`nclawzero-image-lite.bb`**:

```bitbake
require nclawzero-image-base.bb
SUMMARY = "nclawzero lite — base + agent runtime (stage2 equivalent)"
IMAGE_INSTALL:append = " packagegroup-nclawzero"
IMAGE_ROOTFS_EXTRA_SPACE = "524288"
```

**`nclawzero-image-jetson.bb`** drops to ~30 lines (only the tegra-specific bits) instead of the current ~150.

**Direct gain**: `-dual` already proves `require` works. Extending it kills the diff between `-jetson` and `-jetson-dual` to ~20 lines instead of ~150.

---

### Sketch B — Plain-text package list overlays (`00-packages` analogue)

A non-Yocto operator should be able to add a package without learning bbappends. Ship a **package-list parser** that reads from `recipes-core/packagegroups/lists/*.list`:

**`recipes-core/packagegroups/packagegroup-nclawzero.bb`** (modified):

```bitbake
SUMMARY = "nclawzero agent stack (driven by package-lists/*.list)"
LICENSE = "MIT"
inherit packagegroup

# Resolve a list of *.list files (one package per line, # comments)
# at parse time, expand into RDEPENDS. Operators add a new line to
# (e.g.) lists/agent.list and rebuild — no bbappend needed.
PACKAGE_LIST_DIR = "${THISDIR}/lists"

python __anonymous() {
    import os, glob
    pkgs = []
    listdir = d.expand("${PACKAGE_LIST_DIR}")
    for f in sorted(glob.glob(os.path.join(listdir, "*.list"))):
        with open(f) as fh:
            for line in fh:
                line = line.split("#", 1)[0].strip()
                if line:
                    pkgs.extend(line.split())
    if pkgs:
        d.appendVar("RDEPENDS:${PN}", " " + " ".join(pkgs))
}
```

**`recipes-core/packagegroups/lists/agent-core.list`** (new):

```text
# Core ZeroClaw + NemoClaw agent runtime
zeroclaw-bin
zeroclaw-env
nclawzero-cerberus-helper
nemoclaw-firstboot
nemoclaw-core
```

**`lists/diagnostics.list`**:

```text
# Network + system diagnostic tools
htop
iotop
mtr
tcpdump
```

A field operator hands `lists/site-extras.list` with their telemetry agent line — done. No `.bbappend`, no recipe surgery.

---

### Sketch C — Boot-partition operator hooks (`/boot/ssh` style)

The marquee adoption. New recipe `nclawzero-boot-provision` that **reads sentinel files from the VFAT boot partition at first boot**:

**`recipes-core/nclawzero-boot-provision/nclawzero-boot-provision_1.0.bb`**:

```bitbake
SUMMARY = "First-boot provisioner reading /boot/firmware sentinels"
DESCRIPTION = "Operator drops sentinel files onto the FAT boot partition \
    pre-flash. On first boot, this oneshot reads them and applies system \
    config (sshd enable, user creation, wpa_supplicant, hostname, \
    custom payload). Removes sentinels after consumption to avoid re-run."
LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/Apache-2.0;md5=89aea4e17d99a7cacdbeed46a0096b10"

SRC_URI = " \
    file://nclawzero-boot-provision.sh \
    file://nclawzero-boot-provision.service \
"

inherit systemd
SYSTEMD_SERVICE:${PN} = "nclawzero-boot-provision.service"
SYSTEMD_AUTO_ENABLE:${PN} = "enable"

do_install() {
    install -d ${D}${libexecdir}/nclawzero
    install -m 0755 ${WORKDIR}/nclawzero-boot-provision.sh \
        ${D}${libexecdir}/nclawzero/boot-provision.sh
    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/nclawzero-boot-provision.service \
        ${D}${systemd_system_unitdir}/
}

FILES:${PN} = " \
    ${libexecdir}/nclawzero/boot-provision.sh \
    ${systemd_system_unitdir}/nclawzero-boot-provision.service \
"

RDEPENDS:${PN} = "bash openssh-sshd wpa-supplicant systemd"
```

**`files/nclawzero-boot-provision.service`**:

```ini
[Unit]
Description=nclawzero first-boot sentinel provisioner
DefaultDependencies=no
After=local-fs.target systemd-tmpfiles-setup.service
Before=sshd.service network-pre.target shutdown.target
ConditionPathExists=!/var/lib/nclawzero/.boot-provisioned

[Service]
Type=oneshot
RemainAfterExit=yes
ExecStart=/usr/libexec/nclawzero/boot-provision.sh

[Install]
WantedBy=sysinit.target
```

**`files/nclawzero-boot-provision.sh`**:

```bash
#!/bin/bash
# Read operator-supplied sentinel files from the VFAT boot partition and
# apply system config. Inspired by Raspberry Pi OS userconf-pi /
# raspberrypi-sys-mods init_setup.sh, but Yocto-native.
#
# Sentinels (drop any subset onto the FAT boot partition pre-flash):
#   /boot/firmware/ssh                  empty file → enable sshd
#   /boot/firmware/hostname             one line, sets /etc/hostname
#   /boot/firmware/userconf.txt         "user:hashed-pw" → create user
#   /boot/firmware/wpa_supplicant.conf  → /etc/wpa_supplicant/wpa_supplicant-wlan0.conf
#   /boot/firmware/authorized_keys      → /home/<first-user>/.ssh/authorized_keys
#   /boot/firmware/firstrun.sh          executable → exec'd once, then deleted

set -euo pipefail

BOOT="/boot/firmware"
[ -d "$BOOT" ] || BOOT="/boot"            # fallback for non-RPi layouts
MARKER="/var/lib/nclawzero/.boot-provisioned"
LOG="/var/log/nclawzero-boot-provision.log"

mkdir -p "$(dirname "$MARKER")"
exec >>"$LOG" 2>&1
echo "=== boot-provision $(date -Is) ==="

# --- ssh enable ---------------------------------------------------------
if [ -e "$BOOT/ssh" ] || [ -e "$BOOT/ssh.txt" ]; then
    systemctl enable --now sshd.service
    rm -f "$BOOT/ssh" "$BOOT/ssh.txt"
fi

# --- hostname -----------------------------------------------------------
if [ -s "$BOOT/hostname" ]; then
    HN=$(head -n1 "$BOOT/hostname" | tr -d '[:space:]')
    [ -n "$HN" ] && hostnamectl set-hostname "$HN"
    rm -f "$BOOT/hostname"
fi

# --- user creation / pwhash ---------------------------------------------
if [ -s "$BOOT/userconf.txt" ]; then
    while IFS=: read -r U PWHASH; do
        [ -z "$U" ] && continue
        if id "$U" >/dev/null 2>&1; then
            echo "$U:$PWHASH" | chpasswd -e
        else
            useradd -m -s /bin/bash -G sudo "$U"
            echo "$U:$PWHASH" | chpasswd -e
        fi
    done < "$BOOT/userconf.txt"
    rm -f "$BOOT/userconf.txt"
fi

# --- wpa_supplicant -----------------------------------------------------
if [ -s "$BOOT/wpa_supplicant.conf" ]; then
    install -d -m 0755 /etc/wpa_supplicant
    install -m 0600 "$BOOT/wpa_supplicant.conf" \
        /etc/wpa_supplicant/wpa_supplicant-wlan0.conf
    rm -f "$BOOT/wpa_supplicant.conf"
    systemctl enable --now wpa_supplicant@wlan0.service || true
fi

# --- authorized_keys for first non-root user ---------------------------
if [ -s "$BOOT/authorized_keys" ]; then
    FIRST_USER=$(getent passwd 1000 | cut -d: -f1)
    if [ -n "$FIRST_USER" ]; then
        install -d -m 0700 -o "$FIRST_USER" -g "$FIRST_USER" \
            "/home/$FIRST_USER/.ssh"
        install -m 0600 -o "$FIRST_USER" -g "$FIRST_USER" \
            "$BOOT/authorized_keys" "/home/$FIRST_USER/.ssh/authorized_keys"
    fi
    rm -f "$BOOT/authorized_keys"
fi

# --- arbitrary one-shot operator script --------------------------------
if [ -x "$BOOT/firstrun.sh" ]; then
    "$BOOT/firstrun.sh" || echo "WARN: firstrun.sh exited $?"
    rm -f "$BOOT/firstrun.sh"
fi

# --- mark complete -----------------------------------------------------
touch "$MARKER"
sync
echo "=== boot-provision complete ==="
```

Wire it into the image by adding `nclawzero-boot-provision` to `packagegroup-nclawzero.bb` RDEPENDS.

**Operational shape for Phil Lawrence's team**: ship them an SD pre-flashed with a `nclawzero-image-jetson-dual.wic`. They mount the VFAT boot partition on a Mac/Windows laptop, drop in:

```
ssh                     (empty)
hostname                "phil-jet-001"
authorized_keys         (their public key)
wpa_supplicant.conf     (their site WiFi)
```

Insert SD, power on. 90 s later it's on the network with sshd up, no CLI interaction. **This is the highest-value pi-gen pattern for nclawzero's deployment shape.**

---

### Sketch D — Image SBOM + `.info` sidecar + zerofree (export-image polish)

Three small additions to `nclawzero-image.bb`:

```bitbake
# 1. SBOM via Yocto's create-spdx class (no external syft dep needed)
INHERIT += "create-spdx"
SPDX_INCLUDE_SOURCES = "0"   # we ship binary SBOMs, not source SBOMs
SPDX_PRETTY = "1"

# 2. Per-image .info sidecar (mirrors pi-gen's update_issue + INFO_FILE)
IMAGE_POSTPROCESS_COMMAND:append = " nclawzero_emit_info; "
nclawzero_emit_info() {
    local info="${IMGDEPLOYDIR}/${IMAGE_NAME}.info"
    {
        echo "${DISTRO_NAME} ${DISTRO_VERSION} (${IMAGE_NAME})"
        echo "Built: ${DATETIME}"
        echo "Yocto:  ${DISTRO_CODENAME}"
        echo ""
        echo "Packages:"
        cat "${IMAGE_MANIFEST}"
    } > "${info}"
}

# 3. zerofree the rootfs ext4 to cut .wic.gz size 30-60%
# (requires zerofree-native + the rootfs to be unmounted; runs inside
# image_types.bbclass image creation hook)
IMAGE_FSTYPES:append:raspberrypi4-64 = " ext4"
IMAGE_CMD:ext4:append = " && zerofree -v ${IMGDEPLOYDIR}/${IMAGE_NAME}.rootfs.ext4"
```

The `zerofree` line needs validation against meta-tegra's image_types_tegra (which generates ext4 differently), so keep it RPi-only initially.

---

### Sketch E — Single operator config file (`conf/distro/nclawzero.conf`)

Today there's no distro config; we ride poky's default. Create one:

**`conf/distro/nclawzero.conf`**:

```bitbake
# nclawzero distro — single source of truth for operator-tunable knobs
# (pi-gen's ./config equivalent). Recipes consume these via
# ${NCLAWZERO_HOSTNAME} etc. instead of hardcoding.

DISTRO = "nclawzero"
DISTRO_NAME = "nclawzero"
DISTRO_VERSION = "0.1.0"
DISTRO_CODENAME = "scarthgap"
MAINTAINER = "Jason Perlow <jperlow@gmail.com>"

# Default init / features ---------------------------------------------------
INIT_MANAGER = "systemd"
DISTRO_FEATURES:append = " systemd usrmerge wifi bluetooth"
DISTRO_FEATURES_BACKFILL_CONSIDERED:append = " sysvinit"

# Operator knobs (override in conf/local.conf or build-flag env) ------------
NCLAWZERO_HOSTNAME ??= "nclawzero"
NCLAWZERO_FIRST_USER ??= "pi"
NCLAWZERO_TIMEZONE ??= "America/New_York"
NCLAWZERO_LOCALE ??= "en_US.UTF-8"
NCLAWZERO_KEYMAP ??= "us"
NCLAWZERO_WPA_COUNTRY ??= "US"
NCLAWZERO_ENABLE_SSH ??= "1"
NCLAWZERO_PUBKEY_ONLY_SSH ??= "1"
NCLAWZERO_MODEL_URL ??= "https://huggingface.co/google/gemma-2-2b-it-GGUF"

# SSTATE / build accelerators ----------------------------------------------
SSTATE_MIRRORS ?= "file://.* http://192.168.207.22:8081/sstate-cache/PATH"
```

Then `nclawzero-system-config_1.0.bb` reads from these instead of hardcoding `pi`:

```bitbake
EXTRA_USERS_PARAMS = " \
    useradd -m -s /bin/bash -G sudo,wheel -p '!' ${NCLAWZERO_FIRST_USER}; \
"
```

And operator `conf/local.conf` becomes:

```bitbake
DISTRO = "nclawzero"
NCLAWZERO_HOSTNAME = "phil-jetson-002"
NCLAWZERO_TIMEZONE = "Europe/Berlin"
```

instead of editing 4 different recipes.

---

## 4. What we already do better than pi-gen

| Capability | pi-gen | meta-nclawzero | Why we win |
|---|---|---|---|
| **Reproducibility** | Docker isolates the host but not the package versions (apt floats) | Yocto SRCREV + sstate-cache + manifest.txt = bit-identical rebuilds across machines | sstate hashes everything that touched the build; pi-gen's `apt-get install -y foo` floats |
| **Cross-compile** | `binfmt_misc` + qemu-user-static (slow, fragile) | Native cross-toolchain per machine target | Yocto cross is mature; pi-gen's qemu chroot is a workaround |
| **Multi-arch from one tree** | Separate `master`/`arm64` branches | Single tree, MACHINE selects arch | Branch-per-arch is a structural smell |
| **Layer composition** | Custom stages by directory injection | Standard BSP/distro/oem layer model with priorities | Layer model is industry-standard; pi-gen reinvents it |
| **A/B rollback** | Not supported (rpi-clone is third-party) | `nclawzero-image-jetson-dual.bb` + `nclawzero-update slot-install` | pi-gen has no answer; ours is the TYDEUS-saga payoff |
| **Kernel customization** | `raspberrypi-kernel` Debian package only | Full kernel.bbclass with kernel-meta fragments + DT overlays | We ship our own kernel configs; pi-gen can't |
| **Patch lifecycle** | quilt + manual `.diff` files in `00-patches/` | Yocto `SRC_URI += "file://0001-foo.patch"` with auto-apply + auto-rebase via devtool | quilt edit-loop is the mid-2000s; devtool is 2020s |

---

## 5. What NOT to borrow + why

| pi-gen pattern | Why skip |
|---|---|
| **debconf preseeding** (`00-debconf`) | apt/dpkg-only. opkg/rpm have no equivalent. The *operator-friendly answer file* idea is captured by Sketch E (single distro config). |
| **`config.txt` Broadcom firmware file** | RPi-specific firmware-readable text format. Tegra's L4TLauncher reads `extlinux.conf` instead — already covered. RPi side already gets this from the bootimg-partition wic source. |
| **quilt `00-patches/EDIT`** (drop-into-bash mid-build) | Cute, but we have `devtool modify` + `devtool finish` which is strictly better and tracks the patch series correctly. |
| **bind-mount gymnastics** (`on_chroot` proc/dev/sys mount lifecycle) | Yocto's `do_rootfs` and `pseudo` handle this entirely under the hood. Adopting pi-gen's helpers would be regression. |
| **Stage-internal rsync of rootfs trees** (`copy_previous`) | Yocto's `IMAGE_INSTALL` + sstate is the rooted-graph equivalent; rsync-between-stages is ~10× slower and less correct. |
| **Docker isolation for build host** (`build-docker.sh`) | Yocto sysroot already isolates from the build host. ARGOS is a fixed Yocto host; we don't need per-developer build container. |
| **NOOBS export** (`export-noobs/`) | NOOBS is end-of-life; no analogue needed. |

---

## 6. Adoption priority + time estimate

| Priority | Sketch | Effort | Risk | Expected benefit |
|---|---|---|---|---|
| **P0** | C — Boot-partition operator hooks | 4 h | Low (new recipe, no recipe modifications) | Field-deployment ergonomics for Phil Lawrence's team; cuts SD-prep time per device from ~10 min to ~30 s |
| **P1** | E — Single `conf/distro/nclawzero.conf` | 2 h | Low (additive; existing local.conf overrides still work) | Operator can change hostname/locale/timezone without recipe editing |
| **P2** | A — Tiered image hierarchy via `require` | 6 h | Med (touches every image recipe; need to validate `-jetson-dual` still builds) | Eliminates ~80 % of duplication between image recipes |
| **P3** | B — Plain-text package list overlays | 3 h | Low (anonymous-python parser; falls back to current behavior on empty `lists/`) | Non-Yocto operator can add packages |
| **P4** | D — SBOM + `.info` + zerofree | 2 h | Low for SBOM/info; med for zerofree (needs RPi vs Tegra path-split) | Compliance + image-size win |
| | Total | ~17 h | | |

**Suggested first step**: implement Sketch C (boot-provisioner) standalone. It does not touch any existing recipe; it only adds a new one and a packagegroup line. Validate on the RPi 4 image first (where the FAT boot partition is the most natural fit), then port to Jetson UEFI's ESP partition.

---

## 7. License / attribution notes

pi-gen is BSD-3-Clause-style ("Raspberry Pi (Trading) Ltd." copyright). Any **substantial** script text we copy from pi-gen needs the upstream copyright + the BSD-3-Clause notice carried into our recipe. The sketches above are **inspired by** pi-gen patterns but written fresh — no verbatim copy. The sentinel-file *idea* (`/boot/ssh`, `/boot/userconf.txt`, etc.) is the public-protocol UX, not copyrightable.

Where we **do** want to lift code (e.g. if we later port `init_resize.sh` from `raspberrypi-sys-mods`), we will:

1. Vendor the file under `recipes-core/nclawzero-boot-provision/files/` with the upstream header preserved.
2. Add the upstream's BSD-3-Clause text alongside our Apache-2.0 (dual-license is fine; both permissive).
3. Cite the source git ref in the recipe `DESCRIPTION`.

---

## 8. Out-of-scope (deliberately not addressed)

- **Cloud-init integration** (pi-gen stage2/04-cloud-init): nclawzero is not aimed at cloud-style provisioning. The boot-partition sentinels (Sketch C) are a simpler, lower-overhead alternative for our edge use-case.
- **piwiz / GUI first-boot wizard** (pi-gen export-image/01-user-rename): nclawzero's primary form factor is headless edge; a GUI wizard would only matter for `nclawzero-image-desktop` and is low priority.
- **Mathematica EULA acceptance** (pi-gen stage2/03-accept-mathematica-eula): not relevant.
- **NOOBS bundle** (pi-gen export-noobs): EOL upstream.

---

## 9. Summary

pi-gen's design wisdom that maps cleanly to Yocto:

1. **Operator surface area should be one file** (`./config` → `conf/distro/nclawzero.conf`).
2. **Operator-supplied input belongs on the FAT boot partition** so a workstation can write it before the device ever boots.
3. **Convention beats configuration** for package lists (`00-packages` plain text → `lists/*.list`).
4. **Image stages should be additive layers**, not duplicated recipes (`stage1`→`stage2`→`stage5` → `require` chain).

pi-gen's design wisdom that does NOT map (and we should resist):

1. quilt-based patch management (devtool wins).
2. apt/debconf preseeding (different package ecosystem).
3. Docker-isolated host build (sstate already wins).
4. RPi-firmware-specific `config.txt` (Tegra has its own conventions).

**The single highest-value pattern to adopt is Sketch C** (boot-partition operator hooks). It is small, additive, low-risk, and unlocks field-deployment ergonomics nclawzero currently lacks.
