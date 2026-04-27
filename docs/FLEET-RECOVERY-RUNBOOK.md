# Fleet recovery runbook

When something goes wrong on a flashed nclawzero device, start with the
Raspberry Pi SD-card recovery path:

1. Confirm power, HDMI console, ping, and DHCP lease state.
2. If SSH still works, verify both `ncz` and backup operator access.
3. If SSH is broken but the boot partition is readable, repair
   `userconf.txt` or authorized keys from the FAT boot partition.
4. If the image is suspect, reflash with a known-good Pi SD image and verify
   the write before booting.

NVIDIA Jetson family recovery workflows are deferred pending hardware
validation. Older PXE, L4T, and device-mode recovery notes were removed because
they describe an unsupported path.
