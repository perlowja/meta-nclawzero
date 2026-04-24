#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# nclawzero first-boot RDP/TLS setup
#
# - Generates /etc/weston/tls.cert + tls.key if missing (self-signed)
# - Sets ncz user password from /etc/nclawzero/initial-password
#   (was: pi; renamed 2026-04-24 — already-deployed pre-rename images
#   still ship the legacy account, this script falls back if pi exists
#   and ncz does not)
# - Creates /var/lib/nclawzero/rdp-init.done sentinel (idempotent)

set -euo pipefail

STATE_DIR=/var/lib/nclawzero
DONE="${STATE_DIR}/rdp-init.done"
CERT_DIR=/etc/weston
CERT="${CERT_DIR}/tls.cert"
KEY="${CERT_DIR}/tls.key"
PWFILE=/etc/nclawzero/initial-password

mkdir -p "${STATE_DIR}"
if [ -f "${DONE}" ]; then
    echo "nclawzero-rdp-init: already ran; skipping"
    exit 0
fi

# TLS cert for Weston RDP backend
mkdir -p "${CERT_DIR}"
if [ ! -f "${CERT}" ] || [ ! -f "${KEY}" ]; then
    echo "nclawzero-rdp-init: generating self-signed TLS cert"
    openssl req -x509 -nodes -newkey rsa:2048 \
        -keyout "${KEY}" -out "${CERT}" \
        -days 3650 \
        -subj "/CN=$(hostname)/O=nclawzero/OU=weston-rdp" \
        >/dev/null 2>&1
    chmod 600 "${KEY}"
    chmod 644 "${CERT}"
    chown weston:weston "${KEY}" "${CERT}" 2>/dev/null || true
fi

# Seed operator user password — prefer ncz (post-rename), fall back to pi
# for backward compatibility with already-deployed legacy images.
TARGET_USER=""
if id ncz >/dev/null 2>&1; then
    TARGET_USER=ncz
elif id pi >/dev/null 2>&1; then
    TARGET_USER=pi
fi

if [ -n "${TARGET_USER}" ]; then
    if [ -s "${PWFILE}" ]; then
        PW="$(tr -d '[:space:]' < "${PWFILE}")"
    else
        PW="zeroclaw"
    fi
    echo "${TARGET_USER}:${PW}" | chpasswd
    echo "nclawzero-rdp-init: ${TARGET_USER} password set from ${PWFILE}"
fi

touch "${DONE}"
echo "nclawzero-rdp-init: done"
