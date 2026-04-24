#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
#
# nclawzero-boot-provision — first-boot sentinel processor (pi-gen pattern).
#
# Operator drops files in /boot/firmware/ (or /boot/) on a workstation
# before flashing/booting. On first boot this service reads them and applies
# them, then renames them to <name>.applied-<timestamp> so they're not
# re-processed but operators can still see what landed.
#
# Sentinel files honored:
#
#   ssh                       — any presence enables sshd (oneshot).
#   userconf.txt              — single line "username:hashed-password".
#                                If the user doesn't exist and the username
#                                is valid (regex [a-z][a-z0-9_-]{0,30}), a
#                                home-shelled user is created and the
#                                password set from the hash. If a user with
#                                that name exists (e.g. the default ncz),
#                                only the password is updated.
#   wpa_supplicant.conf       — installed at
#                                /etc/wpa_supplicant/wpa_supplicant-wlan0.conf
#                                (mode 0600), wpa_supplicant@wlan0.service
#                                enabled.
#   authorized_keys           — installed at /home/<NCZ_USER>/.ssh/
#                                authorized_keys (mode 0600, chown).
#   firstrun.sh               — executed once with bash with NCZ_USER and
#                                NCZ_HOME exported. Combined stdout/stderr
#                                is appended to /var/log/nclawzero-firstrun.log.
#
# Robustness:
#   - Failure of one sentinel does NOT abort the others. Each is logged.
#   - Idempotent: rename-on-success means sentinels processed once.
#   - The unit ConditionPathExists guards on the dir existing; this script
#     additionally guards on at least one sentinel being present.
#   - On full success the boot-provision unit is masked so it doesn't fire
#     again on subsequent boots even if the directory is restored later.
#
# Env vars:
#   NCLAWZERO_BOOT_PROVISION_DIR  default: /boot/firmware (falls back to /boot)
#   NCZ_USER                      default: ncz
#
# References: pi-gen stage2/04-userconf and stage2/02-net-tweaks are the
# original prior art. We deviate from pi-gen by RENAMING (not deleting)
# applied sentinels so post-mortem inspection is possible without an SD
# rip-and-read.

set -u
LOG=/var/log/nclawzero-boot-provision.log
FIRSTRUN_LOG=/var/log/nclawzero-firstrun.log

log() {
    printf '%s %s\n' "$(date -Iseconds)" "$*" >> "$LOG"
}

# Pick the sentinel dir. /boot/firmware is the conventional pi-gen path
# on RPi (FAT32 partition mounted there); on Tegra and bare images the
# whole rootfs has /boot, so we fall back to that.
DIR="${NCLAWZERO_BOOT_PROVISION_DIR:-/boot/firmware}"
if [ ! -d "$DIR" ]; then
    if [ -d /boot ]; then
        DIR=/boot
    else
        log "no boot-provision directory found (tried /boot/firmware and /boot); skipping"
        exit 0
    fi
fi

NCZ_USER="${NCZ_USER:-ncz}"

# Determine target operator home for authorized_keys placement.
if id "$NCZ_USER" >/dev/null 2>&1; then
    NCZ_HOME="$(getent passwd "$NCZ_USER" | cut -d: -f6)"
else
    NCZ_HOME="/home/$NCZ_USER"
fi
export NCZ_USER NCZ_HOME

mkdir -p "$(dirname "$LOG")"
log "boot-provision starting; DIR=$DIR NCZ_USER=$NCZ_USER NCZ_HOME=$NCZ_HOME"

# Track whether anything was actually applied — only mask the unit if so.
APPLIED_ANY=0
TS="$(date +%Y%m%d-%H%M%S)"

mark_applied() {
    # rename "$1" -> "$1.applied-$TS"
    src="$1"
    dst="${src}.applied-${TS}"
    if mv -- "$src" "$dst" 2>>"$LOG"; then
        log "applied: ${src##*/} -> ${dst##*/}"
        APPLIED_ANY=1
        return 0
    else
        log "WARN: failed to rename $src; leaving in place"
        return 1
    fi
}

# ----- ssh sentinel ---------------------------------------------------------
if [ -e "$DIR/ssh" ]; then
    log "ssh sentinel present; enabling sshd"
    # ssh.service on most distros; sshd.service on a few. Try both.
    if systemctl list-unit-files ssh.service >/dev/null 2>&1; then
        systemctl enable --now ssh.service >>"$LOG" 2>&1 || log "WARN: enable ssh.service failed"
    elif systemctl list-unit-files sshd.service >/dev/null 2>&1; then
        systemctl enable --now sshd.service >>"$LOG" 2>&1 || log "WARN: enable sshd.service failed"
    else
        log "WARN: no ssh.service or sshd.service unit found; skipping enable"
    fi
    mark_applied "$DIR/ssh" || :
fi

# ----- userconf.txt sentinel ------------------------------------------------
if [ -f "$DIR/userconf.txt" ]; then
    log "userconf.txt sentinel present; processing"
    # Read first non-comment, non-empty line.
    line="$(grep -vE '^[[:space:]]*(#|$)' "$DIR/userconf.txt" | head -n1 || true)"
    if [ -z "$line" ]; then
        log "WARN: userconf.txt empty after comment-stripping"
    else
        # Split on FIRST colon only — the password hash itself contains colons.
        uname="${line%%:*}"
        uhash="${line#*:}"
        if [ -z "$uname" ] || [ -z "$uhash" ] || [ "$uname" = "$line" ]; then
            log "WARN: userconf.txt malformed (need username:hash)"
        elif ! printf '%s' "$uname" | grep -qE '^[a-z][a-z0-9_-]{0,30}$'; then
            log "WARN: userconf.txt username '$uname' does not match [a-z][a-z0-9_-]{0,30}"
        else
            if id "$uname" >/dev/null 2>&1; then
                log "userconf: user '$uname' already exists; updating password only"
            else
                log "userconf: creating user '$uname'"
                if ! useradd -m -s /bin/bash "$uname" >>"$LOG" 2>&1; then
                    log "WARN: useradd $uname failed"
                fi
            fi
            if id "$uname" >/dev/null 2>&1; then
                if printf '%s:%s\n' "$uname" "$uhash" | chpasswd -e >>"$LOG" 2>&1; then
                    log "userconf: password set for '$uname'"
                else
                    log "WARN: chpasswd -e for '$uname' failed (hash format?)"
                fi
            fi
            # If this is the operator account, refresh NCZ_USER/NCZ_HOME so
            # any subsequent authorized_keys install lands in the right place.
            if [ "$uname" = "$NCZ_USER" ]; then
                NCZ_HOME="$(getent passwd "$NCZ_USER" | cut -d: -f6)"
                export NCZ_HOME
            fi
        fi
    fi
    mark_applied "$DIR/userconf.txt" || :
fi

# ----- wpa_supplicant.conf sentinel -----------------------------------------
if [ -f "$DIR/wpa_supplicant.conf" ]; then
    log "wpa_supplicant.conf sentinel present; installing"
    install -d -m 0755 /etc/wpa_supplicant
    if install -m 0600 "$DIR/wpa_supplicant.conf" \
           /etc/wpa_supplicant/wpa_supplicant-wlan0.conf 2>>"$LOG"; then
        # systemd templated unit; the @wlan0 instance is what binds the iface.
        if systemctl list-unit-files 'wpa_supplicant@.service' >/dev/null 2>&1; then
            systemctl enable wpa_supplicant@wlan0.service >>"$LOG" 2>&1 || \
                log "WARN: enable wpa_supplicant@wlan0 failed"
        else
            log "WARN: wpa_supplicant@.service not present; configuration written but not enabled"
        fi
    else
        log "WARN: install wpa_supplicant.conf failed"
    fi
    mark_applied "$DIR/wpa_supplicant.conf" || :
fi

# ----- authorized_keys sentinel ---------------------------------------------
if [ -f "$DIR/authorized_keys" ]; then
    log "authorized_keys sentinel present; installing for $NCZ_USER"
    if id "$NCZ_USER" >/dev/null 2>&1; then
        install -d -m 0700 -o "$NCZ_USER" -g "$NCZ_USER" "$NCZ_HOME/.ssh" 2>>"$LOG" || \
            log "WARN: mkdir .ssh failed"
        if install -m 0600 -o "$NCZ_USER" -g "$NCZ_USER" \
               "$DIR/authorized_keys" "$NCZ_HOME/.ssh/authorized_keys" 2>>"$LOG"; then
            log "authorized_keys installed at $NCZ_HOME/.ssh/authorized_keys"
        else
            log "WARN: install authorized_keys failed"
        fi
    else
        log "WARN: operator user '$NCZ_USER' does not exist; skipping authorized_keys"
    fi
    mark_applied "$DIR/authorized_keys" || :
fi

# ----- firstrun.sh sentinel -------------------------------------------------
if [ -f "$DIR/firstrun.sh" ]; then
    log "firstrun.sh sentinel present; executing (log -> $FIRSTRUN_LOG)"
    {
        echo
        echo "=== nclawzero-boot-provision firstrun.sh @ $(date -Iseconds) ==="
        echo "NCZ_USER=$NCZ_USER NCZ_HOME=$NCZ_HOME"
    } >> "$FIRSTRUN_LOG"
    if NCZ_USER="$NCZ_USER" NCZ_HOME="$NCZ_HOME" \
           bash "$DIR/firstrun.sh" >>"$FIRSTRUN_LOG" 2>&1; then
        log "firstrun.sh exited 0"
    else
        rc=$?
        log "WARN: firstrun.sh exited $rc (output: $FIRSTRUN_LOG)"
    fi
    mark_applied "$DIR/firstrun.sh" || :
fi

# ----- self-disable on success ---------------------------------------------
if [ "$APPLIED_ANY" -eq 1 ]; then
    log "boot-provision: at least one sentinel applied; masking the unit"
    systemctl disable nclawzero-boot-provision.service >>"$LOG" 2>&1 || :
    # mask is stronger than disable — survives a re-enable attempt.
    systemctl mask nclawzero-boot-provision.service >>"$LOG" 2>&1 || :
else
    log "boot-provision: no sentinels processed; leaving unit enabled"
fi

log "boot-provision done"
exit 0
