#!/bin/sh
# nclawzero-thermal-tune: set cpufreq + nvpmodel for sustained inference.
set -e

if [ ! -e /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor ]; then
    echo "nclawzero-thermal-tune: no cpufreq, skipping" >&2
    exit 0
fi

for gov in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do
    echo performance > "$gov" 2>/dev/null || true
done

if command -v nvpmodel >/dev/null 2>&1; then
    # MAXN_SUPER is mode 0 on Orin Nano Super. On older Orin Nano this is
    # also mode 0 (MAXN). No-op on non-Jetson.
    nvpmodel -m 0 || true
    nvpmodel -q | head -2 >&2
fi

if command -v jetson_clocks >/dev/null 2>&1; then
    jetson_clocks || true
fi

echo "nclawzero-thermal-tune: applied" >&2
