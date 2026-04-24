# Independent pi-gen vs meta-nclawzero structural audit (Codex)

Date: 2026-04-24

Scope note: outbound SSH to ARGOS was blocked in this Codex sandbox, so I could not inspect `/tmp/pi-gen/` in place. For pi-gen, I used the upstream primary sources that were accessible from this session: the official `RPi-Distro/pi-gen` repository README and `build.sh`. For `meta-nclawzero`, I audited the local checkout in this repo. I did not intentionally open the prior comparison reports before writing this one.

Sources:

- pi-gen upstream: <https://github.com/RPi-Distro/pi-gen>
- pi-gen README: <https://raw.githubusercontent.com/RPi-Distro/pi-gen/master/README.md>
- pi-gen build script: <https://raw.githubusercontent.com/RPi-Distro/pi-gen/master/build.sh>
- meta-nclawzero local files: [recipes-core/images/nclawzero-image.bb](/Users/jperlow/meta-nclawzero/recipes-core/images/nclawzero-image.bb), [recipes-core/images/nclawzero-image-desktop.bb](/Users/jperlow/meta-nclawzero/recipes-core/images/nclawzero-image-desktop.bb), [recipes-core/images/nclawzero-image-jetson.bb](/Users/jperlow/meta-nclawzero/recipes-core/images/nclawzero-image-jetson.bb), [recipes-core/images/nclawzero-image-jetson-dual.bb](/Users/jperlow/meta-nclawzero/recipes-core/images/nclawzero-image-jetson-dual.bb), [recipes-core/packagegroups/packagegroup-nclawzero.bb](/Users/jperlow/meta-nclawzero/recipes-core/packagegroups/packagegroup-nclawzero.bb), [recipes-core/packagegroups/packagegroup-nclawzero-desktop.bb](/Users/jperlow/meta-nclawzero/recipes-core/packagegroups/packagegroup-nclawzero-desktop.bb), [recipes-core/nclawzero-system-config/nclawzero-system-config_1.0.bb](/Users/jperlow/meta-nclawzero/recipes-core/nclawzero-system-config/nclawzero-system-config_1.0.bb), [recipes-core/nclawzero-ssh-keys/nclawzero-ssh-keys_1.0.bb](/Users/jperlow/meta-nclawzero/recipes-core/nclawzero-ssh-keys/nclawzero-ssh-keys_1.0.bb), [recipes-nemoclaw/nemoclaw/nemoclaw-firstboot_1.0.bb](/Users/jperlow/meta-nclawzero/recipes-nemoclaw/nemoclaw/nemoclaw-firstboot_1.0.bb), [recipes-nemoclaw/nemoclaw/files/nemoclaw-firstboot.sh](/Users/jperlow/meta-nclawzero/recipes-nemoclaw/nemoclaw/files/nemoclaw-firstboot.sh), [wic/nclawzero-rpi.wks.in](/Users/jperlow/meta-nclawzero/wic/nclawzero-rpi.wks.in), [wic/nclawzero-jetson-dual.wks](/Users/jperlow/meta-nclawzero/wic/nclawzero-jetson-dual.wks)

## Exec summary

Top 3, ranked by leverage:

1. Add boot-partition field provisioning via a tiny first-boot recipe that consumes operator-supplied sentinel files. This closes the largest operator UX gap immediately.
2. Consolidate operator-facing knobs into one layer-level config surface, but as Yocto variables in a dedicated config/include, not as a bash file cloned from pi-gen.
3. Refactor image hierarchy so “common system behavior” is actually shared. Right now Jetson carries some core logic that the base and desktop images do not.

Three explicit pattern calls:

- Boot-partition operator sentinel files: **Yes**
- Single root `config`: **Yes, but Yocto-native rather than a literal pi-gen-style bash fragment**
- Tiered image hierarchy via `require`: **Yes**

If I differ from another reviewer, it is likely on ranking: I place boot-partition provisioning above single-config consolidation by a wide margin. A scattered config surface is annoying; no field provisioning path is operationally expensive.

## 1. Patterns to steal from pi-gen

### 1.1 Boot-partition operator sentinel files

Recommendation: **Adopt first. Highest leverage.**

Why it matters:

- pi-gen’s strongest operator idea is not its shell implementation; it is the contract that the boot-visible partition is the handoff surface for last-mile provisioning.
- That directly matches your stated field scenario: hand an SD card to a third party, let them drop credentials and Wi-Fi config on the visible boot partition, then boot once.
- `meta-nclawzero` currently does not have an equivalent. Today the closest mechanism is static baked credentials in [recipes-core/nclawzero-ssh-keys/nclawzero-ssh-keys_1.0.bb](/Users/jperlow/meta-nclawzero/recipes-core/nclawzero-ssh-keys/nclawzero-ssh-keys_1.0.bb) plus an on-rootfs template in [recipes-core/nclawzero-system-config/files/wpa_supplicant-wlan0.conf.template](/Users/jperlow/meta-nclawzero/recipes-core/nclawzero-system-config/files/wpa_supplicant-wlan0.conf.template). Neither solves field provisioning.

Smallest recipe shape:

- New recipe: `recipes-core/nclawzero-boot-provision/nclawzero-boot-provision_1.0.bb`
- Installs:
  - `nclawzero-boot-provision.service`
  - `nclawzero-boot-provision.sh`
- Service ordering:
  - `After=local-fs.target`
  - `Before=sshd.service nemoclaw-firstboot.service`
  - one-shot
  - self-disabling by marker file or by deleting consumed sentinels
- Sentinel surface:
  - RPi: `/boot/nclawzero/authorized_keys`, `/boot/nclawzero/wpa_supplicant.conf`, `/boot/nclawzero/firstboot.env`
  - Jetson: same logical path, adjusted to the actual boot-mounted partition path used by the image

I would not mirror pi-gen’s exact filenames under `/boot/firmware/` unless you want maximum Raspberry Pi familiarity. A namespaced directory like `/boot/nclawzero/` avoids collisions with firmware-owned files.

### 1.2 Single operator config surface

Recommendation: **Adopt second, but not literally.**

What to steal:

- pi-gen’s one-file operator ergonomics
- clear defaults
- documented knobs
- ability to override a build without editing recipes

What not to steal:

- sourcing a bash fragment directly from `build.sh`

Yocto-native equivalent:

- Introduce a dedicated include or distro config that defines `NCLAWZERO_*` variables.
- Examples:
  - `conf/distro/include/nclawzero-config.inc`
  - or `conf/distro/nclawzero.conf` if you also want a real distro identity
- Consume those vars from recipes instead of hardcoding repeated values.

Initial knobs worth centralizing:

- `NCLAWZERO_FIRST_USER`
- `NCLAWZERO_FIRST_USER_GROUPS_PI`
- `NCLAWZERO_FIRST_USER_GROUPS_JETSON`
- `NCLAWZERO_ENABLE_DEBUG_TWEAKS`
- `NCLAWZERO_SSH_AUTHORIZED_KEYS_FILE`
- `NCLAWZERO_WIFI_COUNTRY`
- `NCLAWZERO_IMAGE_EXTRA_SPACE_BASE`
- `NCLAWZERO_IMAGE_EXTRA_SPACE_DESKTOP`
- `NCLAWZERO_IMAGE_EXTRA_SPACE_JETSON`
- `NCLAWZERO_ENABLE_NEMOCLAW_FIRSTBOOT`
- `NCLAWZERO_BOOT_PROVISION_ENABLE`

### 1.3 Tiered image hierarchy with stage-like intent

Recommendation: **Adopt/refine. Third.**

You already partly do this:

- [recipes-core/images/nclawzero-image-desktop.bb](/Users/jperlow/meta-nclawzero/recipes-core/images/nclawzero-image-desktop.bb) `require`s the base image
- [recipes-core/images/nclawzero-image-jetson-dual.bb](/Users/jperlow/meta-nclawzero/recipes-core/images/nclawzero-image-jetson-dual.bb) `require`s the Jetson image

But the hierarchy is incomplete:

- [recipes-core/images/nclawzero-image-jetson.bb](/Users/jperlow/meta-nclawzero/recipes-core/images/nclawzero-image-jetson.bb) is largely standalone rather than a refinement of a common base
- some “core system” behavior lives only in Jetson

The pi-gen lesson is less “copy stage0-stage5” and more “encode additive intent in the structure.” In Yocto, that means:

- common image base
- machine-family add-ons
- desktop add-ons
- deployment-layout add-ons

Rough target:

- `nclawzero-image-common.inc`
- `nclawzero-image-rpi.bb`
- `nclawzero-image-rpi-desktop.bb`
- `nclawzero-image-jetson.bb`
- `nclawzero-image-jetson-dual.bb`

### 1.4 Export-stage mindset

Recommendation: **Steal the concept, not the mechanism.**

pi-gen cleanly separates “compose rootfs” from “export final artifact.” You already have some of this in WIC files and the dual-slot Jetson image. The useful lesson is to keep partition/export logic separate from package selection.

For this layer, the analog is:

- packagegroups decide software
- image recipe decides features
- WIC decides artifact layout
- update tooling decides lifecycle guarantees

That is already mostly your direction. Preserve it.

## 2. Patterns not to steal

### 2.1 Literal stage directories as the primary composition model

Recommendation: **Do not adopt.**

pi-gen’s stage tree is appropriate for a shell-driven debootstrap pipeline. Yocto already gives you stronger primitives:

- recipe dependencies
- packagegroups
- `require`
- machine overrides
- classes
- WIC partition descriptions

Recreating stage0-stage5 inside Yocto would be a second build system embedded in the first.

### 2.2 debconf preseeding

Recommendation: **Do not adopt directly.**

That is a Debian/apt-era answer-file mechanism. The useful idea is centralized defaults, which should instead land in Yocto variables, package config, and first-boot provisioning inputs.

### 2.3 pi-gen’s exact shell implementation

Recommendation: **Do not copy wholesale.**

Reasons:

- license attribution would be required for substantial copying
- the operational idea is portable, but the exact shell is tightly coupled to debootstrap/chroot/apt
- Yocto should own build-time composition; your runtime scripts should stay small and systemd-native

### 2.4 cloud-init as the main answer

Recommendation: **Mostly no.**

pi-gen exposes cloud-init as an option because Raspberry Pi OS is also a general-purpose image consumed in cloud-like workflows. For `meta-nclawzero`, adding cloud-init would likely be heavier than the problem requires.

Small conclusion:

- steal the “drop config onto the boot partition before first boot” UX
- do not introduce cloud-init unless you later decide this distro needs full declarative first-boot provisioning beyond SSH/Wi-Fi/basic env

## 3. Where meta-nclawzero is structurally weaker

### 3.1 Core system behavior is not actually in the shared base

This is the biggest structural weakness I found.

[recipes-core/nclawzero-system-config/nclawzero-system-config_1.0.bb](/Users/jperlow/meta-nclawzero/recipes-core/nclawzero-system-config/nclawzero-system-config_1.0.bb) contains cross-platform behavior:

- sudoers
- logind
- networkd
- Wi-Fi template
- tmpfiles
- udev

But it is explicitly added only in [recipes-core/images/nclawzero-image-jetson.bb](/Users/jperlow/meta-nclawzero/recipes-core/images/nclawzero-image-jetson.bb), not in [recipes-core/images/nclawzero-image.bb](/Users/jperlow/meta-nclawzero/recipes-core/images/nclawzero-image.bb) and not in [recipes-core/packagegroups/packagegroup-nclawzero.bb](/Users/jperlow/meta-nclawzero/recipes-core/packagegroups/packagegroup-nclawzero.bb).

Operational consequence:

- the “base” image is not actually the common behavioral base
- Raspberry Pi and desktop variants may diverge from Jetson in first-boot networking and operator access behavior
- future fixes can land in Jetson and silently miss the other images

This should be normalized before adding more variants.

### 3.2 User creation logic is duplicated and drifts by image

`EXTRA_USERS_PARAMS` is separately defined in:

- [recipes-core/images/nclawzero-image.bb](/Users/jperlow/meta-nclawzero/recipes-core/images/nclawzero-image.bb)
- [recipes-core/images/nclawzero-image-jetson.bb](/Users/jperlow/meta-nclawzero/recipes-core/images/nclawzero-image-jetson.bb)

Some divergence is legitimate because Jetson needs more groups, but the duplication is still brittle:

- same usernames repeated
- same locked-password posture repeated
- same service users repeated

This is a classic candidate for centralization in one include plus machine-specific append variables.

### 3.3 Package responsibility is blurred between image recipes and packagegroups

Examples:

- `nemoclaw-firstboot` is already in [recipes-core/packagegroups/packagegroup-nclawzero.bb](/Users/jperlow/meta-nclawzero/recipes-core/packagegroups/packagegroup-nclawzero.bb), yet [recipes-core/images/nclawzero-image-jetson.bb](/Users/jperlow/meta-nclawzero/recipes-core/images/nclawzero-image-jetson.bb) appends it again
- `nclawzero-system-config` is image-owned instead of clearly “always-on common config” or “Jetson-only config”
- utility package lists are large and partly image-local

BitBake can tolerate duplicate package names, but structurally this makes it harder to answer “where does this behavior come from?”

### 3.4 Operator-facing knobs are scattered across too many surfaces

Today the knobs live in at least these places:

- image recipes
- packagegroups
- static files under `files/`
- WIC files
- first-boot scripts
- likely local build config outside the layer

This is not fatal for a single maintainer. It is weak for reproducible operations, handoff, and future automation.

### 3.5 Hardcoded values are scattered and not obviously policy-backed

Examples:

- username `pi`
- Wi-Fi country default `US` in template
- rootfs extra space values per image
- groups for the operator account
- `debug-tweaks`
- `SYSTEMD_DEFAULT_TARGET`
- WIC partition sizes

Some should remain hardcoded. The issue is that the current code does not cleanly separate “fleet doctrine” from “build defaults.”

## 4. Where meta-nclawzero is structurally stronger

### 4.1 Yocto-native reproducibility and composition

This layer is stronger than pi-gen anywhere exact build graph control matters:

- recipe-level provenance
- packagegroups
- machine overrides
- controlled kernel config fragments
- WIC-defined layouts

That is a real advantage. Do not regress into shell-first composition.

### 4.2 Artifact and partition control

The WIC and Jetson layout work is significantly stronger than pi-gen’s generic consumer-image posture for your use case:

- [wic/nclawzero-rpi.wks.in](/Users/jperlow/meta-nclawzero/wic/nclawzero-rpi.wks.in) gives you explicit partition intent
- [wic/nclawzero-jetson-dual.wks](/Users/jperlow/meta-nclawzero/wic/nclawzero-jetson-dual.wks) gives you an A/B-ready layout
- [recipes-core/nclawzero-update/files/nclawzero-update.sh](/Users/jperlow/meta-nclawzero/recipes-core/nclawzero-update/files/nclawzero-update.sh) encodes rollback policy directly

pi-gen does not give you this level of embedded lifecycle control out of the box.

### 4.3 Deterministic first-boot via systemd oneshot

[recipes-nemoclaw/nemoclaw/files/nemoclaw-firstboot.service](/Users/jperlow/meta-nclawzero/recipes-nemoclaw/nemoclaw/files/nemoclaw-firstboot.service) is a cleaner contract than a pile of first-boot shell hooks scattered across a debootstrap stage tree. Its weakness is scope, not model.

The systemd oneshot approach is the right substrate for adding boot-partition provisioning.

### 4.4 Security and platform ownership

This repo is structurally stronger in areas pi-gen intentionally leaves generic:

- locked operator password posture
- owned kernel behavior
- owned systemd/network behavior
- controlled overlay and update mechanisms

The risk is not weakness here. The risk is accidentally bypassing these advantages by over-adopting pi-gen’s consumer-image conventions.

## 5. Operator UX field-provisioning gap

### Current answer

Can someone hand a pre-flashed SD to a third party with “drop your SSH key here, insert, power on”?

**No.**

Current blockers:

- SSH keys are baked statically at image build time in [recipes-core/nclawzero-ssh-keys/nclawzero-ssh-keys_1.0.bb](/Users/jperlow/meta-nclawzero/recipes-core/nclawzero-ssh-keys/nclawzero-ssh-keys_1.0.bb)
- the `pi` account is locked in the image recipes
- Wi-Fi config is only shipped as a rootfs template in [recipes-core/nclawzero-system-config/files/wpa_supplicant-wlan0.conf.template](/Users/jperlow/meta-nclawzero/recipes-core/nclawzero-system-config/files/wpa_supplicant-wlan0.conf.template)
- [recipes-nemoclaw/nemoclaw/files/nemoclaw-firstboot.sh](/Users/jperlow/meta-nclawzero/recipes-nemoclaw/nemoclaw/files/nemoclaw-firstboot.sh) provisions NemoClaw, not operator credentials

### Smallest recipe to add

Recommendation:

- add `nclawzero-boot-provision`
- make it common to Pi and Jetson
- keep the scope intentionally narrow

Version 1 should only do:

1. If `/boot/nclawzero/authorized_keys` exists, install or append it to `/home/pi/.ssh/authorized_keys` with correct ownership and mode.
2. If `/boot/nclawzero/wpa_supplicant.conf` exists, install it to `/etc/wpa_supplicant/wpa_supplicant-wlan0.conf`, enable the unit, and remove or archive the sentinel.
3. If `/boot/nclawzero/firstboot.env` exists, source only a small allowlist of variables such as hostname or tailscale auth inputs. Do not make this an unrestricted shell execution surface.

That is enough to close the field gap without pulling in cloud-init.

## 6. Single-config-file consolidation sketch

### Recommendation

**Yes**, but implement it as a Yocto-native config contract, not a top-level bash script copied from pi-gen.

### Proposed shape

Add:

- `conf/distro/include/nclawzero-config.inc`

Document it as:

- “the one operator-facing layer config surface”
- intended for inclusion from build configs
- all `NCLAWZERO_*` variables documented in one place

Example categories:

- identity
  - `NCLAWZERO_HOSTNAME`
  - `NCLAWZERO_FIRST_USER`
- access
  - `NCLAWZERO_SSH_KEYS_FILE`
  - `NCLAWZERO_ENABLE_SSH`
  - `NCLAWZERO_LOCKED_PASSWORD`
- networking
  - `NCLAWZERO_WIFI_COUNTRY`
  - `NCLAWZERO_NETWORK_BACKEND`
- first boot
  - `NCLAWZERO_ENABLE_BOOT_PROVISION`
  - `NCLAWZERO_ENABLE_NEMOCLAW_FIRSTBOOT`
- image flavor
  - `NCLAWZERO_INCLUDE_DESKTOP`
  - `NCLAWZERO_INCLUDE_DEMO_GEMMA`
- sizing
  - `NCLAWZERO_ROOTFS_EXTRA_SPACE`
  - `NCLAWZERO_DATA_PARTITION_SIZE_MB`

### Rules for using it

- Recipes may read these variables.
- WIC may consume size-related variables.
- Machine-specific defaults may override them.
- `local.conf` may still override them per build tree.

That gives you pi-gen’s clarity without fighting Yocto’s configuration model.

## 7. Three specific patterns requested

### 7.1 Boot-partition operator sentinel files

Recommendation: **Yes**

Rank: **#1 overall**

Reason:

- biggest operational win
- smallest implementation
- directly closes the current field-provisioning failure mode

### 7.2 Single root `config`

Recommendation: **Yes, but adapted**

Rank: **#2 overall**

Reason:

- valuable for maintainability and reproducibility
- but should be a Yocto config/include surface, not a shell-sourced root file

### 7.3 Tiered image hierarchy via `require`

Recommendation: **Yes**

Rank: **#3 overall**

Reason:

- already partly present
- needs cleanup so common behavior is truly common
- lower urgency than field provisioning because today’s pain is operational, not purely structural

## 8. What I would rank differently than a peer reviewer might

I would rank these higher than a purely build-system-oriented reviewer might:

- boot-partition provisioning
- making `nclawzero-system-config` genuinely common

I would rank these lower than a “clean architecture first” reviewer might:

- a perfect single-file config abstraction
- heavy refactoring toward a grand image taxonomy before closing the field gap

Why:

- the present repo already has enough structural strength to keep building images
- it does not yet have a clean third-party handoff story for credentials and Wi-Fi
- that is the more expensive failure in the real world

## 9. Concrete next steps for follow-up recipe work

1. Add `nclawzero-boot-provision` as a tiny systemd oneshot recipe.
2. Move `nclawzero-system-config` into the common image path or the common packagegroup if it is truly cross-platform policy.
3. Centralize `EXTRA_USERS_PARAMS` and other repeated operator defaults in a new `nclawzero-config.inc`.
4. Refactor image recipes so Jetson extends a common base instead of redefining it wholesale where unnecessary.

