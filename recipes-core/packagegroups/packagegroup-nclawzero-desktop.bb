# SPDX-FileCopyrightText: Copyright (c) 2026 Jason Perlow. All rights reserved.
# SPDX-License-Identifier: Apache-2.0

SUMMARY = "nclawzero desktop add-on — Weston + RDP + minimal Wayland UX"
LICENSE = "MIT"

inherit packagegroup

# Compositor + RDP wiring
RDEPENDS:${PN} = " \
    weston \
    weston-init \
    weston-examples \
    kbd \
    nclawzero-rdp-init \
    openssl-bin \
    shadow \
"

# Desktop utilities (Wayland-safe, no X11 dep)
RDEPENDS:${PN} += " \
    btop \
"

# Archives (desktop users often unzip downloads)
RDEPENDS:${PN} += " \
    p7zip \
    unzip \
    zip \
"

# Fonts — without these text renders as tofu in Weston
RDEPENDS:${PN} += " \
    liberation-fonts \
    ttf-dejavu-sans \
    ttf-dejavu-sans-mono \
    ttf-noto-emoji-color \
"

# TODO — Wayland-native tools that need a meta-wayland-utils / meta-sway layer:
#   foot (terminal), wofi (launcher), grim + slurp (screenshots),
#   wl-clipboard, imv (image viewer), mako (notifications),
#   zathura + zathura-pdf-poppler (PDF reader)
# For now: weston-terminal ships with weston-examples.
#
# TODO — GTK/Qt apps need x11 DISTRO_FEATURE (xwayland) or pure-Wayland
# builds not in scarthgap layers: pcmanfm (file manager), featherpad,
# mousepad, gedit (text editors), mesa-demos (GL debug).
# For now: use vim/nano in weston-terminal; no file manager GUI.

# --- Browser ----------------------------------------------------------
# Chromium with the Ozone/Wayland backend (matches our Weston compositor).
# Requires the meta-browser layer registered in bblayers.conf:
#   cd /mnt/argonas/nclawzero-yocto
#   git clone -b scarthgap https://github.com/OSSystems/meta-browser
#   bitbake-layers add-layer meta-browser/meta-chromium
#
# May also need in local.conf:
#   LICENSE_FLAGS_ACCEPTED:append = " commercial"
# (for proprietary codecs like H.264; skip if you're fine with web-safe only)
#
# Expect a ~4-8 hour build on ARGOS for the first compile; sstate-cached
# afterwards. This is the single biggest contributor to build time in the
# whole image.
RDEPENDS:${PN} += " \
    chromium-ozone-wayland \
"
