#!/bin/bash
# nclawzero first-boot NemoClaw provisioning
#
# Source tree is pre-cloned at /opt/nemoclaw by the nemoclaw-core recipe.
# This script:
#   1. If network reachable, fast-forward the pinned tree to origin/main
#      (graceful fallback to Yocto-pinned version if offline).
#   2. npm install --production (always runs; ~2-3 min on warm cache).
#   3. Build the plugin if present.
#   4. Install Claude Code CLI globally.
#   5. Mark provisioned.
#
# Patches are applied at Yocto build time — no runtime patch application.

set -e

MARKER="/var/lib/nemoclaw/.provisioned"
LOGFILE="/var/log/nemoclaw-firstboot.log"
NEMOCLAW_DIR="/opt/nemoclaw"

if [ -f "$MARKER" ]; then
    echo "NemoClaw already provisioned, skipping." >> "$LOGFILE"
    exit 0
fi

echo "=== NemoClaw first-boot provisioning: $(date) ===" >> "$LOGFILE"

# --- 1. Attempt online update, fall back silently ---------------------
if [ -d "$NEMOCLAW_DIR/.git" ]; then
    echo "Attempting online ff update of $NEMOCLAW_DIR ..." >> "$LOGFILE"
    if cd "$NEMOCLAW_DIR" \
        && git fetch --depth 1 origin main >> "$LOGFILE" 2>&1 \
        && git reset --hard origin/main >> "$LOGFILE" 2>&1; then
        echo "  updated to $(git rev-parse --short HEAD)" >> "$LOGFILE"
    else
        PINNED=$(git rev-parse --short HEAD 2>/dev/null || echo unknown)
        echo "  offline or fetch failed; running Yocto-pinned version ($PINNED)" >> "$LOGFILE"
    fi
else
    echo "WARN: $NEMOCLAW_DIR/.git missing — expected pre-cloned tree from nemoclaw-core recipe" >> "$LOGFILE"
fi

cd "$NEMOCLAW_DIR"

# --- 1b. Apply nclawzero patches (graceful — fuzz-tolerant, continues on failure) ---
if [ -d /etc/nemoclaw/patches ]; then
    echo "Applying nclawzero patches..." >> "$LOGFILE"
    for patch in /etc/nemoclaw/patches/*.patch; do
        [ -f "$patch" ] || continue
        name=$(basename "$patch")
        # Use --forward so already-applied patches are silently skipped;
        # --fuzz=3 tolerates small context drift.
        if git apply --check --3way "$patch" >/dev/null 2>&1; then
            git apply --3way "$patch" >> "$LOGFILE" 2>&1 &&                 echo "  applied $name" >> "$LOGFILE" ||                 echo "  WARN: 3way apply of $name failed — continuing" >> "$LOGFILE"
        elif patch -p1 -N --fuzz=3 --dry-run < "$patch" >/dev/null 2>&1; then
            patch -p1 -N --fuzz=3 < "$patch" >> "$LOGFILE" 2>&1 &&                 echo "  applied $name (with fuzz)" >> "$LOGFILE" ||                 echo "  WARN: fuzzed apply of $name failed — continuing" >> "$LOGFILE"
        else
            echo "  SKIP: $name does not apply to upstream HEAD (drift); continuing" >> "$LOGFILE"
        fi
    done
fi

# --- 2. Install uv (used by blueprint Python scripts) ----------------
echo "Installing uv..." >> "$LOGFILE"
if ! command -v uv >/dev/null 2>&1; then
    curl -LsSf https://astral.sh/uv/install.sh | sh >> "$LOGFILE" 2>&1 || \
        echo "WARN: uv install failed; continuing" >> "$LOGFILE"
fi
export PATH="$HOME/.local/bin:$PATH"

# --- 3. npm install deps ---------------------------------------------
echo "Installing npm dependencies..." >> "$LOGFILE"
npm install --production --no-optional >> "$LOGFILE" 2>&1 || {
    echo "ERROR: npm install failed" >> "$LOGFILE"
    exit 1
}

# --- 4. Build plugin if present --------------------------------------
if [ -d "$NEMOCLAW_DIR/nemoclaw" ]; then
    echo "Building NemoClaw plugin..." >> "$LOGFILE"
    cd "$NEMOCLAW_DIR/nemoclaw"
    npm install --production --no-optional >> "$LOGFILE" 2>&1 || \
        echo "WARN: plugin npm install had warnings" >> "$LOGFILE"
    npm run build >> "$LOGFILE" 2>&1 || \
        echo "WARN: plugin build skipped" >> "$LOGFILE"
    cd "$NEMOCLAW_DIR"
fi

# --- 5. Install Claude Code CLI --------------------------------------
echo "Installing Claude Code..." >> "$LOGFILE"
npm install -g @anthropic-ai/claude-code >> "$LOGFILE" 2>&1 || \
    echo "WARN: Claude Code install failed" >> "$LOGFILE"

if command -v claude >/dev/null 2>&1; then
    echo "Claude Code: $(claude --version 2>/dev/null | head -1)" >> "$LOGFILE"
fi

# --- 6. Mark provisioned ---------------------------------------------
mkdir -p /var/lib/nemoclaw
touch "$MARKER"
echo "=== NemoClaw + Claude Code provisioning complete: $(date) ===" >> "$LOGFILE"
