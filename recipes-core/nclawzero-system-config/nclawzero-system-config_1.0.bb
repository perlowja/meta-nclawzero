# SPDX-FileCopyrightText: Copyright (c) 2026 Jason Perlow. All rights reserved.
# SPDX-License-Identifier: Apache-2.0
#
# nclawzero-system-config — the "baked-in rescue" recipe.
#
# Bakes every in-place TYDEUS fix from scripts/jetson-rescue/ into the
# image recipe so fresh flashes come up correctly on the first boot:
#
#   - sudoers NOPASSWD for the ncz operator (renamed from pi 2026-04-24;
#     existing flashed devices retain pi until reflash)
#   - logind IdleAction=ignore (no silent auto-power-off)
#   - systemd-networkd DHCP defaults for eth* and wl*
#   - wpa_supplicant-wlan0 client template
#   - cpufreq + nvpmodel thermal-tune service
#   - tmpfiles.d for zeroclaw workspace path + legacy-path symlink
#   - udev rule for /dev/uinput (future Wayland input injection)
#
# Not Jetson-specific — these apply to Pi and Jetson alike (the thermal
# service is a no-op when nvpmodel/jetson_clocks are absent).

SUMMARY = "nclawzero system configuration (sudoers, logind, networkd, thermal)"
DESCRIPTION = "Bakes the TYDEUS in-place rescue fixes into the image."
LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/Apache-2.0;md5=89aea4e17d99a7cacdbeed46a0096b10"

SRC_URI = " \
    file://sudoers-ncz \
    file://sudoers-jasonperlow \
    file://99-no-idle-poweroff.conf \
    file://10-wired.network \
    file://20-wlan.network \
    file://wpa_supplicant-wlan0.conf.template \
    file://nclawzero-thermal-tune.sh \
    file://nclawzero-thermal-tune.service \
    file://nclawzero-irq-affinity.sh \
    file://nclawzero-irq-affinity.service \
    file://nclawzero-workspace.tmpfiles \
    file://99-uinput.rules \
"

S = "${WORKDIR}"

inherit systemd

SYSTEMD_PACKAGES = "${PN}"
SYSTEMD_SERVICE:${PN} = "nclawzero-thermal-tune.service nclawzero-irq-affinity.service"
SYSTEMD_AUTO_ENABLE:${PN} = "enable"

# Enable systemd-networkd + systemd-resolved so the dropped-in .network files
# actually drive DHCP on first boot. Without this the .network units sit
# unused and networking fails — this was the biggest triage finding from the
# GRAEAE/codex pass. Done via pkg_postinst_ontarget because the units are
# owned by the systemd package (not by this recipe), so SYSTEMD_SERVICE can't
# directly claim them.
pkg_postinst_ontarget:${PN} () {
    if [ -x /usr/bin/systemctl ]; then
        /usr/bin/systemctl enable systemd-networkd.service   || true
        /usr/bin/systemctl enable systemd-resolved.service   || true
        # systemd-resolved stub-resolv is the conventional /etc/resolv.conf
        # symlink target on systemd-networkd setups.
        ln -sf /run/systemd/resolve/stub-resolv.conf /etc/resolv.conf || true
    fi
}

do_install() {
    # sudoers drop-ins — operator (ncz) AND locked-password backup
    # user (jasonperlow) both get NOPASSWD so a key-only login can
    # actually do recovery work.
    install -d -m 0750 ${D}${sysconfdir}/sudoers.d
    install -m 0440 ${WORKDIR}/sudoers-ncz \
        ${D}${sysconfdir}/sudoers.d/90-nclawzero-ncz
    install -m 0440 ${WORKDIR}/sudoers-jasonperlow \
        ${D}${sysconfdir}/sudoers.d/91-nclawzero-jasonperlow

    # logind drop-in
    install -d -m 0755 ${D}${sysconfdir}/systemd/logind.conf.d
    install -m 0644 ${WORKDIR}/99-no-idle-poweroff.conf ${D}${sysconfdir}/systemd/logind.conf.d/

    # systemd-networkd
    install -d -m 0755 ${D}${sysconfdir}/systemd/network
    install -m 0644 ${WORKDIR}/10-wired.network ${D}${sysconfdir}/systemd/network/
    install -m 0644 ${WORKDIR}/20-wlan.network  ${D}${sysconfdir}/systemd/network/

    # wpa_supplicant client template (operator copies + fills in)
    install -d -m 0755 ${D}${sysconfdir}/wpa_supplicant
    install -m 0644 ${WORKDIR}/wpa_supplicant-wlan0.conf.template \
        ${D}${sysconfdir}/wpa_supplicant/wpa_supplicant-wlan0.conf.template

    # thermal-tune service + helper
    install -d -m 0755 ${D}${libexecdir}/nclawzero
    install -m 0755 ${WORKDIR}/nclawzero-thermal-tune.sh \
        ${D}${libexecdir}/nclawzero/thermal-tune.sh
    install -m 0755 ${WORKDIR}/nclawzero-irq-affinity.sh \
        ${D}${libexecdir}/nclawzero/irq-affinity.sh
    install -d -m 0755 ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/nclawzero-thermal-tune.service \
        ${D}${systemd_system_unitdir}/
    install -m 0644 ${WORKDIR}/nclawzero-irq-affinity.service \
        ${D}${systemd_system_unitdir}/

    # tmpfiles.d for zeroclaw workspace
    install -d -m 0755 ${D}${sysconfdir}/tmpfiles.d
    install -m 0644 ${WORKDIR}/nclawzero-workspace.tmpfiles \
        ${D}${sysconfdir}/tmpfiles.d/nclawzero-workspace.conf

    # udev rule
    install -d -m 0755 ${D}${sysconfdir}/udev/rules.d
    install -m 0644 ${WORKDIR}/99-uinput.rules ${D}${sysconfdir}/udev/rules.d/
}

FILES:${PN} = " \
    ${sysconfdir}/sudoers.d/90-nclawzero-ncz \
    ${sysconfdir}/sudoers.d/91-nclawzero-jasonperlow \
    ${sysconfdir}/systemd/logind.conf.d/99-no-idle-poweroff.conf \
    ${sysconfdir}/systemd/network/10-wired.network \
    ${sysconfdir}/systemd/network/20-wlan.network \
    ${sysconfdir}/wpa_supplicant/wpa_supplicant-wlan0.conf.template \
    ${libexecdir}/nclawzero/thermal-tune.sh \
    ${libexecdir}/nclawzero/irq-affinity.sh \
    ${systemd_system_unitdir}/nclawzero-thermal-tune.service \
    ${systemd_system_unitdir}/nclawzero-irq-affinity.service \
    ${sysconfdir}/tmpfiles.d/nclawzero-workspace.conf \
    ${sysconfdir}/udev/rules.d/99-uinput.rules \
"

RDEPENDS:${PN} = " \
    systemd \
    wpa-supplicant \
"
