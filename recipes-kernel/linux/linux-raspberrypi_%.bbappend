# SPDX-License-Identifier: Apache-2.0
# Apply nclawzero kernel config fragment to the Raspberry Pi kernel.

FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

SRC_URI += "file://nclawzero.cfg"
