# SPDX-FileCopyrightText: Copyright (c) 2026 Jason Perlow. All rights reserved.
# SPDX-License-Identifier: Apache-2.0

SUMMARY = "nclawzero desktop add-on — Weston compositor + RDP remote access"
LICENSE = "MIT"

inherit packagegroup

RDEPENDS:${PN} = \" \\
    weston \\
    weston-init \\
    weston-examples \\
    kbd \\
    nclawzero-rdp-init \\
    openssl-bin \\
    shadow \\
\"
