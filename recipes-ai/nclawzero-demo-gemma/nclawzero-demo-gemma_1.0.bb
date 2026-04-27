# SPDX-FileCopyrightText: Copyright (c) 2026 Jason Perlow. All rights reserved.
# SPDX-License-Identifier: Apache-2.0
#
# nclawzero-demo-gemma — first-boot provisioning of a Gemma Unsloth GGUF
# plus a matching llama-server systemd unit. Scope: optional local
# inference demos on supported Pi/container targets.
#
# Why first-boot fetch instead of SRC_URI:
#   - GGUF files are 2-5 GB. SRC_URI at Yocto build time needs a known
#     SHA256, which means every time upstream re-uploads a quant variant
#     we'd have to update + re-run every cached build. Operationally
#     painful.
#   - HuggingFace is fine to pull from on first boot with the device on
#     the demo network.
#   - /etc/nclawzero/gemma-model.env lets operators override URL / path
#     without rebuilding.
#
# Fits together: fetch service downloads → writes /var/lib/models/gemma.gguf
# → llama-server-gemma.service sees the ConditionPathExists fire, starts
# on localhost:8080 → zeroclaw [provider.local] (see zeroclaw-env) routes
# tool calls through this local endpoint.

SUMMARY = "First-boot Gemma GGUF fetch + llama-server systemd unit (demo)"
LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/Apache-2.0;md5=89aea4e17d99a7cacdbeed46a0096b10"

SRC_URI = " \
    file://fetch-gemma.sh \
    file://nclawzero-demo-gemma-fetch.service \
    file://llama-server-gemma.service \
    file://gemma-model.env \
"

S = "${WORKDIR}"

inherit systemd

SYSTEMD_PACKAGES = "${PN}"
SYSTEMD_SERVICE:${PN} = " \
    nclawzero-demo-gemma-fetch.service \
    llama-server-gemma.service \
"
SYSTEMD_AUTO_ENABLE:${PN} = "enable"

RDEPENDS:${PN} = "curl rsync sshpass llama-cpp"

do_install() {
    install -d -m 0755 ${D}${libexecdir}/nclawzero
    install -m 0755 ${WORKDIR}/fetch-gemma.sh ${D}${libexecdir}/nclawzero/fetch-gemma.sh

    install -d -m 0755 ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/nclawzero-demo-gemma-fetch.service \
        ${D}${systemd_system_unitdir}/
    install -m 0644 ${WORKDIR}/llama-server-gemma.service \
        ${D}${systemd_system_unitdir}/

    install -d -m 0755 ${D}${sysconfdir}/nclawzero
    install -m 0644 ${WORKDIR}/gemma-model.env \
        ${D}${sysconfdir}/nclawzero/gemma-model.env

    install -d -m 0755 ${D}/var/lib/models
}

FILES:${PN} = " \
    ${libexecdir}/nclawzero/fetch-gemma.sh \
    ${systemd_system_unitdir}/nclawzero-demo-gemma-fetch.service \
    ${systemd_system_unitdir}/llama-server-gemma.service \
    ${sysconfdir}/nclawzero/gemma-model.env \
    /var/lib/models \
"
