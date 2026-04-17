#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# nclawzero first-boot RDP/TLS setup
#
# - Generates /etc/weston/tls.cert + tls.key if missing (self-signed)
# - Sets pi user password from /etc/nclawzero/initial-password
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

# Seed pi user password
if id pi >/dev/null 2>&1; then
    if [ -s "${PWFILE}" ]; then
        PW="$(tr -d '[:space:]' < "${PWFILE}")"
    else
        PW="zeroclaw"
    fi
    echo "pi:${PW}" | chpasswd
    echo "nclawzero-rdp-init: pi password set from ${PWFILE}"
fi

touch "${DONE}"
echo "nclawzero-rdp-init: done"
