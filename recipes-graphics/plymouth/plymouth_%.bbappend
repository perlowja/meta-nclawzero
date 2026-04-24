# SPDX-License-Identifier: Apache-2.0
#
# Plymouth configuration for nclawzero:
#   - Drop 'initrd' PACKAGECONFIG: Jetson Orin Nano boots via L4TLauncher
#     extlinux INITRD directive pointing at meta-tegra's purpose-built
#     initrd; plymouth-initrd would need dracut, which isn't in our layer
#     set and isn't wanted on Tegra anyway. Plymouth still starts
#     post-initrd via the systemd-integration unit.
#   - Add 'drm' PACKAGECONFIG on aarch64: upstream only flips DRM on for
#     x86/x86_64. Jetson uses nvidia-drm (KMS capable), so DRM rendering
#     is what we want for a polished splash — otherwise plymouth falls
#     back to a text/details renderer which defeats the splash effort.
#
# See plymouth-theme-nclawzero_1.0.bb for the theme itself.

PACKAGECONFIG:remove = "initrd"

PACKAGECONFIG:append:aarch64 = " drm"
PACKAGECONFIG[drm] = "-Ddrm=true,-Ddrm=false,libdrm"
