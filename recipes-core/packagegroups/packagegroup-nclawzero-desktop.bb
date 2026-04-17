# SPDX-FileCopyrightText: Copyright (c) 2026 Jason Perlow. All rights reserved.
# SPDX-License-Identifier: Apache-2.0
#
# Optional desktop layer for nclawzero — VNC + lightweight WM + browser.
# Only for devices with 4GB+ RAM. Not included in the base headless image.

SUMMARY = "nclawzero desktop — VNC, Openbox, browser for Claude Code"
LICENSE = "MIT"

inherit packagegroup

RDEPENDS:${PN} = " \
    xserver-xorg \
    xf86-video-fbdev \
    openbox \
    tigervnc \
    xterm \
    mesa \
    liberation-fonts \
    adwaita-icon-theme \
    bash \
"
