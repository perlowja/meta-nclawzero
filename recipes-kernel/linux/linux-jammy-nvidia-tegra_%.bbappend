# SPDX-License-Identifier: Apache-2.0
#
# Apply nclawzero kernel config fragment to the L4T kernel on Jetson.
# Enables RTL8168 onboard NIC, RTL8822CE onboard WiFi, HDMI/HDA/USB audio,
# plus Docker/Landlock baseline. Matches recipes-kernel/linux/files/nclawzero.cfg
# on the Raspberry Pi side.
#
# The meta-tegra layer already ships rtw8822ce-wifi.cfg but does NOT wire it
# into the recipe by default; we take ownership of the full config fragment
# here to make the set self-contained in meta-nclawzero.

FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

SRC_URI += "file://nclawzero-jetson-hw.cfg"
