# SPDX-FileCopyrightText: Copyright (c) 2026 Jason Perlow. All rights reserved.
# SPDX-License-Identifier: Apache-2.0
#
# nclawzero-ssh-keys — pre-seed authorized_keys at image build time for the
# nclawzero operator account (ncz) AND the locked-password backup account
# (jasonperlow).
#
# Why: on 2026-04-23 both Pis (zeropi/.56, clawpi/.54) were reflashed with
# the nclawzero image and lost their authorized_keys. On 2026-04-26 the
# Trixie userconfig service stripped the operator account itself out from
# under us at first boot. In both cases the failure mode was "no remote
# recovery path" — operator account was the only SSH-able identity and
# required physical HDMI + keyboard access to recover.
#
# This recipe bakes workstation pub keys into the image at build time for
# BOTH accounts so the next reflash boots SSH-reachable from day one and
# stays SSH-reachable even if the ncz account is later disabled / locked
# / deleted by upstream (Trixie userconfig, fleet-auth misconfig, etc.).
# No console step.
#
# Operator vs backup:
#   ncz          — the canonical operator login. Renamed from `pi` 2026-04-24
#                  per `feedback_operator_user_ncz.md`. Used for day-to-day
#                  SSH and for sudoers NOPASSWD scoping.
#   jasonperlow  — defense-in-depth recovery account (matches user's identity
#                  on STUDIO/ULTRA/ARGOS/PYTHIA/CERBERUS so muscle-memory
#                  works). Locked password, same authorized_keys as ncz, exists
#                  purely to give a second SSH foothold if ncz is disrupted.
#
# Adding / revoking keys: edit files/authorized_keys, rebuild image. For
# live fleet updates on already-deployed hosts, clawpi has fleet-auth
# (systemd timer pulls from the central keys repo); zeropi does not and
# still needs manual key sync post-flash for anything added after its flash.
#
# Mode: 0700 on .ssh dir, 0600 on authorized_keys. Owners reconciled at
# first boot (pkg_postinst_ontarget — uid/gid for both users are only
# stable after extrausers has written passwd/group on target).

SUMMARY = "Pre-seed authorized_keys for ncz + jasonperlow on nclawzero-flashed devices"
DESCRIPTION = "Bakes the nclawzero fleet's operator pub keys into \
    /home/ncz/.ssh/authorized_keys AND /home/jasonperlow/.ssh/authorized_keys \
    at image build so reflashed devices are SSH-reachable without a \
    console step, and stay SSH-reachable through any disruption to the \
    primary ncz account. Companion to the two locked user accounts \
    created by extrausers in nclawzero-image-common.inc."
LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/Apache-2.0;md5=89aea4e17d99a7cacdbeed46a0096b10"

SRC_URI = "file://authorized_keys"

S = "${WORKDIR}"

# Both users are created by extrausers at image-assembly time, so this
# recipe must order after that. Image-assembly sequencing makes that
# already true — this is belt-and-suspenders documentation.
RDEPENDS:${PN} = "openssh-sshd"

# Fleet-internal authorized_keys file is gitignored.  Validate at parse
# time — both presence AND content — so a missing-or-placeholder file
# gets a clear, actionable error before bitbake even starts building
# anything (and well before a multi-hour Yocto build + flash cycle that
# would otherwise produce an SSH-unreachable image with locked accounts).
python () {
    import os
    import subprocess

    keys = os.path.join(d.getVar('THISDIR'), 'files', 'authorized_keys')
    example = keys + '.example'

    if not os.path.isfile(keys):
        bb.fatal(
            "\n"
            "nclawzero-ssh-keys: required fleet-internal file is missing:\n"
            "    %s\n"
            "\n"
            "This path is gitignored on purpose — pubkey lists reveal\n"
            "fleet topology and stay out of public repos.  Populate it\n"
            "from the committed .example sibling:\n"
            "    cp %s %s\n"
            "    $EDITOR %s\n"
            "\n"
            "Then re-run bitbake.\n"
            % (keys, example, keys, keys)
        )

    # Reject obvious placeholder content. The .example ships a literal
    # AAAAREPLACEME line — if an operator did `cp .example authorized_keys`
    # without editing, the file is "non-empty" but unusable, and both
    # locked-password user accounts would ship with no functional SSH key.
    with open(keys, 'r') as fh:
        body = fh.read()

    if 'AAAAREPLACEME' in body or 'REPLACEME' in body:
        bb.fatal(
            "\n"
            "nclawzero-ssh-keys: %s still contains the .example placeholder\n"
            "(AAAAREPLACEME / REPLACEME).  An image baked with this content\n"
            "would be SSH-unreachable on first boot.  Edit the file and\n"
            "replace the placeholder lines with real fleet pubkeys, then\n"
            "re-run bitbake.\n"
            % keys
        )

    # Validate every non-blank, non-comment line as a real ssh pubkey.
    # ssh-keygen -l -f <file> parses authorized_keys and prints one
    # fingerprint per valid key; non-zero exit = at least one bad line.
    real_keys = [
        ln for ln in body.splitlines()
        if ln.strip() and not ln.lstrip().startswith('#')
    ]
    if not real_keys:
        bb.fatal(
            "\n"
            "nclawzero-ssh-keys: %s has no key lines (only comments / blanks).\n"
            "Image would ship with empty authorized_keys for both ncz and\n"
            "jasonperlow. Add real fleet pubkeys before re-running bitbake.\n"
            % keys
        )

    # Validate each non-comment line independently.  Whole-file
    # `ssh-keygen -l -f <file>` returns 0 when the file contains AT
    # LEAST ONE valid key, even with garbled lines mixed in (verified
    # empirically). A rotation with one good key + one typoed key
    # would pass that check and silently bake a partially-unreachable
    # set of recovery accounts.  Per-line validation closes the gap.
    #
    # Two-stage per-line check:
    #   (1) STRICT first-field grammar — the first whitespace-separated
    #       token must be a known sshd-supported key type. ssh-keygen
    #       fingerprints lines like `- ssh-ed25519 AAAA...` or
    #       `bad-option ssh-ed25519 AAAA...` (it scans for any valid
    #       token), but sshd rejects them as malformed options. Reject
    #       at build time so we don't ship lines sshd will silently
    #       ignore.
    #   (2) ssh-keygen on the full line — catches base64 corruption,
    #       wrong-length key blobs, etc.
    SSHD_KEY_TYPES = frozenset((
        'ssh-rsa', 'ssh-dss', 'ssh-ed25519', 'ssh-ed25519-cert-v01@openssh.com',
        'ssh-rsa-cert-v01@openssh.com', 'ssh-dss-cert-v01@openssh.com',
        'ecdsa-sha2-nistp256', 'ecdsa-sha2-nistp384', 'ecdsa-sha2-nistp521',
        'ecdsa-sha2-nistp256-cert-v01@openssh.com',
        'ecdsa-sha2-nistp384-cert-v01@openssh.com',
        'ecdsa-sha2-nistp521-cert-v01@openssh.com',
        'sk-ecdsa-sha2-nistp256@openssh.com', 'sk-ssh-ed25519@openssh.com',
    ))
    bad_lines = []  # only line numbers — line content is fleet-internal
    for line_no, line in enumerate(body.splitlines(), start=1):
        stripped = line.strip()
        if not stripped or stripped.startswith('#'):
            continue
        first = stripped.split(None, 1)[0] if stripped else ''
        if first not in SSHD_KEY_TYPES:
            bad_lines.append(line_no)
            continue
        try:
            subprocess.run(
                ['ssh-keygen', '-l', '-f', '/dev/stdin'],
                input=line + '\n',
                text=True,
                check=True,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.PIPE,
                timeout=5,
            )
        except (subprocess.CalledProcessError, FileNotFoundError, subprocess.TimeoutExpired):
            bad_lines.append(line_no)

    if bad_lines:
        # Diagnostic message redacts line content — pubkey + comment fields
        # reveal fleet topology and shouldn't end up in CI build logs.
        # Operator can read the lines themselves from %s.
        bb.fatal(
            "\nnclawzero-ssh-keys: malformed line(s) at:\n    %s\n  rejected on line %s.\n\n"
            "Each non-comment line must start with a sshd-supported key type\n"
            "(ssh-ed25519, ssh-rsa, ecdsa-sha2-nistp256, etc.) followed by\n"
            "the base64 blob and an optional comment. No bullet prefixes,\n"
            "no leading options-style fields. Fix each line locally (line\n"
            "content NOT echoed here to keep keys out of build logs) and\n"
            "re-run bitbake.\n"
            % (keys, ', '.join(str(n) for n in bad_lines))
        )
}

# do_install is idempotent — `install -m` overwrites every time, so a
# rebake against the same source produces an identical /home/<user>/.ssh
# tree. No accumulation across builds.
do_install() {
    install -d -m 0700 ${D}/home/ncz/.ssh
    install -m 0600 ${WORKDIR}/authorized_keys ${D}/home/ncz/.ssh/authorized_keys

    install -d -m 0700 ${D}/home/jasonperlow/.ssh
    install -m 0600 ${WORKDIR}/authorized_keys ${D}/home/jasonperlow/.ssh/authorized_keys
}

# Chown on target after each user's uid/gid is resolved at first boot.
pkg_postinst_ontarget:${PN}() {
    chown -R ncz:ncz /home/ncz/.ssh
    chown -R jasonperlow:jasonperlow /home/jasonperlow/.ssh
}

FILES:${PN} = "/home/ncz/.ssh /home/jasonperlow/.ssh"
