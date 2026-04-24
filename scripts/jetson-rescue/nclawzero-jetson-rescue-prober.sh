#!/bin/sh
# SPDX-License-Identifier: Apache-2.0
#
# nclawzero Jetson Orin Nano — rescue prober
# ===========================================
#
# Drop this script on a USB stick (FAT32 or ext4). When the Jetson's
# nclawzero image boots but kernel modules aren't loading (so no
# networking), plug the stick into the Jetson, let it auto-mount, then
# run this script as root from the console:
#
#   sudo sh /run/media/*/nclawzero-jetson-rescue-prober.sh
#
# What it does, in order:
#   1. Reports system state (kernel, model, rootfs) so we know what we're on
#   2. Checks /lib/modules/$(uname -r) — if missing, extracts a modules
#      tarball from the USB stick (bring modules-*.tgz too if you have it)
#   3. Runs depmod to regenerate modules.dep
#   4. Loads known Jetson Orin Nano devkit NIC drivers:
#        - r8169 + deps (onboard Realtek GbE)
#        - Plus common USB-Ethernet drivers as fallback (asix, cdc_ether)
#   5. Brings every non-loopback interface up
#   6. Runs DHCP (dhclient preferred, udhcpc fallback)
#   7. Generates sshd host keys if missing, starts sshd
#   8. Injects authorized_keys from the USB stick if present alongside
#      this script
#   9. Prints SSH-ready IP at the end
#
# Log file: written next to this script on the USB stick as
# rescue-<timestamp>.log — useful when there's no serial console and
# the Jetson reboots before you can read the TTY.
#
# Layout on the USB stick:
#   nclawzero-jetson-rescue-prober.sh    ← this file
#   authorized_keys                      ← optional; copied to ~pi/.ssh/
#   modules-<KVER>-<MACHINE>-<STAMP>.tgz ← optional; only if modules dir is broken

set -u

USBDIR="$(cd "$(dirname "$0")" && pwd)"
STAMP="$(date -u +%Y%m%dT%H%M%SZ 2>/dev/null || date +%s)"
LOG="$USBDIR/rescue-${STAMP}.log"

# Tee output to both the USB log and stdout so user can watch at console
exec > >(tee -a "$LOG") 2>&1

say() { echo ""; echo "=== $* ==="; }
ok()  { echo "[ok]   $*"; }
warn(){ echo "[warn] $*" >&2; }
err() { echo "[err]  $*" >&2; }

if [ "$(id -u)" -ne 0 ]; then
    err "must run as root (try: sudo sh $0)"
    exit 1
fi

say "nclawzero Jetson rescue prober — $(date)"
echo "hostname:    $(hostname 2>/dev/null || echo unknown)"
echo "uname -a:    $(uname -a)"
echo "model:       $(tr -d '\0' < /proc/device-tree/model 2>/dev/null || echo unknown)"
echo "rootfs:      $(findmnt / -n -o SOURCE,FSTYPE 2>/dev/null || echo unknown)"
echo "uptime:      $(uptime)"
echo "usb mount:   $USBDIR"
echo "log file:    $LOG"

KVER="$(uname -r)"
MODDIR="/lib/modules/$KVER"

say "Module tree check — $MODDIR"
if [ -f "$MODDIR/modules.dep" ]; then
    ok "modules.dep present"
    echo "     $(wc -l <"$MODDIR/modules.dep") dependency entries"
else
    warn "modules.dep missing — attempting recovery from USB"
    MOD_TGZ="$(ls "$USBDIR"/modules-*.tgz 2>/dev/null | head -1)"
    if [ -n "$MOD_TGZ" ] && [ -f "$MOD_TGZ" ]; then
        echo "     using $MOD_TGZ"
        mkdir -p /lib/modules
        if tar -xzf "$MOD_TGZ" -C /lib/modules/; then
            ok "modules extracted"
        else
            err "tar extract failed — wrong tarball shape?"
            exit 2
        fi
        if depmod -a "$KVER" 2>&1; then
            ok "depmod rebuilt"
        else
            err "depmod failed"
            exit 3
        fi
    else
        err "no modules-*.tgz on USB; stage one next to this script and rerun"
        echo ""
        echo "     Modules tarball for this image should be at:"
        echo "     ARGOS:/home/jasonperlow/yocto-tmp/build-jetson-tmp/deploy/images/jetson-orin-nano-devkit/"
        echo "     named modules-<KVER>-jetson-orin-nano-devkit-<STAMP>.tgz"
        exit 4
    fi
fi

say "PCI enumeration"
if command -v lspci >/dev/null; then
    lspci -v 2>&1 | head -60
else
    warn "lspci not installed; skipping"
fi

say "Loading network drivers"
# Orin Nano devkit onboard GbE: Realtek RTL8111 → r8169 driver.
# Dep chain: mii -> libphy -> phy -> realtek -> r8169.
# Generic USB-Ethernet drivers included so any plugged-in adapter works.
MODS="mii libphy phylib realtek r8169 tg3 usbnet asix ax88179_178a cdc_ether cdc_ncm rndis_host"
LOADED=""
FAILED=""
for M in $MODS; do
    if modprobe "$M" 2>/dev/null; then
        LOADED="$LOADED $M"
    else
        FAILED="$FAILED $M"
    fi
done
[ -n "$LOADED" ] && ok "loaded:$LOADED"
[ -n "$FAILED" ] && warn "not loaded (may not apply to this HW):$FAILED"

say "Interface enumeration"
ip -br link 2>&1 | head -20

say "Bringing interfaces up"
for IF in $(ip -br link show 2>/dev/null | awk '$1!="lo" && $1!="" {print $1}'); do
    if ip link set "$IF" up 2>&1; then
        ok "$IF up"
    else
        warn "$IF could not be brought up"
    fi
done
sleep 2

say "DHCP"
# Only try DHCP on interfaces that went UP and have no IP yet
NEEDED_DHCP=""
for IF in $(ip -br link show up 2>/dev/null | awk '$1!="lo" {print $1}'); do
    if ! ip -br -4 addr show "$IF" 2>/dev/null | grep -qE '[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+'; then
        NEEDED_DHCP="$NEEDED_DHCP $IF"
    fi
done

if [ -z "$NEEDED_DHCP" ]; then
    ok "all UP interfaces already have IPs — skipping DHCP"
else
    for IF in $NEEDED_DHCP; do
        echo "[dhcp] $IF"
        if command -v dhclient >/dev/null; then
            dhclient -v -1 -timeout 10 "$IF" 2>&1 | head -10 || warn "dhclient on $IF timed out"
        elif command -v udhcpc >/dev/null; then
            udhcpc -i "$IF" -q -n -T 2 -t 5 2>&1 | head -10 || warn "udhcpc on $IF timed out"
        elif command -v systemctl >/dev/null; then
            systemctl restart systemd-networkd 2>&1 | head -5
            sleep 3
        else
            err "no DHCP client available (dhclient/udhcpc/systemd-networkd)"
        fi
    done
fi

say "Final IP state"
ip -br -4 addr 2>&1
ip -br -6 addr 2>&1 | grep -v "^lo" | head -10

say "sshd bring-up"
# Make sure host keys exist — fresh images and factory-reset boots often lack them
if command -v ssh-keygen >/dev/null; then
    ssh-keygen -A 2>&1 | head -5 || true
fi

if command -v systemctl >/dev/null; then
    if systemctl is-enabled sshd >/dev/null 2>&1 || systemctl is-enabled ssh >/dev/null 2>&1; then
        systemctl restart sshd 2>&1 || systemctl restart ssh 2>&1
    else
        systemctl start sshd 2>&1 || systemctl start ssh 2>&1 || warn "no sshd unit"
    fi
elif [ -x /usr/sbin/sshd ]; then
    pkill -f /usr/sbin/sshd 2>/dev/null || true
    /usr/sbin/sshd 2>&1 &
    sleep 1
else
    err "sshd not found in /usr/sbin"
fi

if ss -tlnp 2>/dev/null | grep -q ':22 '; then
    ok "sshd listening on :22"
elif netstat -tlnp 2>/dev/null | grep -q ':22 '; then
    ok "sshd listening on :22"
else
    warn "sshd not confirmed listening — check journalctl -u sshd"
fi

say "authorized_keys injection"
if [ -f "$USBDIR/authorized_keys" ]; then
    # Pi user first (nclawzero convention)
    if getent passwd pi >/dev/null; then
        mkdir -p -m 0700 /home/pi/.ssh
        cp "$USBDIR/authorized_keys" /home/pi/.ssh/authorized_keys
        chown -R pi:pi /home/pi/.ssh
        chmod 0600 /home/pi/.ssh/authorized_keys
        ok "injected to /home/pi/.ssh/authorized_keys"
    fi
    # Root too, belt-and-suspenders for this recovery session
    mkdir -p -m 0700 /root/.ssh
    cp "$USBDIR/authorized_keys" /root/.ssh/authorized_keys
    chmod 0600 /root/.ssh/authorized_keys
    ok "injected to /root/.ssh/authorized_keys"
else
    warn "no authorized_keys on USB stick — will rely on existing keys/passwords"
fi

say "SSH-ready summary"
IPS="$(ip -br -4 addr show 2>/dev/null | awk '$1!="lo" && $3!="" {gsub("/.*","",$3); print $3}')"
if [ -z "$IPS" ]; then
    err "NO IPv4 address acquired — check cable + DHCP server reachable"
    err "dmesg tail for diagnosis:"
    dmesg 2>&1 | tail -30
    exit 5
fi
echo "Try from your workstation:"
for IP in $IPS; do
    echo "    ssh pi@$IP"
done

say "Done — $(date)"
echo "Log saved on USB stick: $LOG"
exit 0
