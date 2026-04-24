#!/bin/sh
# Pin network IRQs to CPU cores 0-1 so inference cores (2-5, per the
# llama-server-gemma.service CPUAffinity) never get interrupted for DHCP
# renewals, fleet-auth pulls, MQTT traffic, etc.
#
# Safe no-op on systems without the expected /proc/irq paths or net devs.
set -e

mask_01="3"   # 0b0011 — cores 0 and 1

pin_irqs_for_dev() {
    local dev="$1"
    [ -d "/sys/class/net/$dev" ] || return 0
    local drv
    drv=$(readlink "/sys/class/net/$dev/device/driver" 2>/dev/null | awk -F/ '{print $NF}')
    [ -n "$drv" ] || return 0
    # Match any IRQ whose description contains the driver or netdev name
    for irq in /proc/irq/[0-9]*; do
        n=$(basename "$irq")
        if grep -q -E "(^| )$dev(\$| )" /proc/interrupts 2>/dev/null \
           | grep -q " $n:"; then
            echo "$mask_01" > "$irq/smp_affinity" 2>/dev/null || true
        fi
    done
}

for nic in /sys/class/net/*; do
    name=$(basename "$nic")
    case "$name" in
    lo|docker*|veth*|br-*) continue ;;
    esac
    pin_irqs_for_dev "$name"
done

echo "nclawzero-irq-affinity: pinned NIC IRQs to cores 0-1"
