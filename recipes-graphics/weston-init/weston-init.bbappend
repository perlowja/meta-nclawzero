# SPDX-FileCopyrightText: Copyright (c) 2026 Jason Perlow. All rights reserved.
# SPDX-License-Identifier: Apache-2.0
#
# Override upstream weston.ini with an RDP-configured one.

FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

SRC_URI += "file://weston.ini"

# Do not let upstream do_install rewrite our weston.ini with sed-injected
# backend/xwayland lines — our override already has them.
DEFAULTBACKEND = ""
