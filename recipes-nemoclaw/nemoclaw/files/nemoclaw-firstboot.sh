#!/bin/bash
# nclawzero first-boot NemoClaw provisioning
# Runs once on first boot, clones NemoClaw, applies patches, installs deps.

set -e

MARKER="/var/lib/nemoclaw/.provisioned"
LOGFILE="/var/log/nemoclaw-firstboot.log"

if [ -f "$MARKER" ]; then
    echo "NemoClaw already provisioned, skipping." >> "$LOGFILE"
    exit 0
fi

echo "=== NemoClaw first-boot provisioning: $(date) ===" >> "$LOGFILE"

# Clone NemoClaw
echo "Cloning NemoClaw..." >> "$LOGFILE"
git clone --depth 1 https://github.com/NVIDIA/NemoClaw.git /opt/nemoclaw >> "$LOGFILE" 2>&1

# Apply nclawzero patches
echo "Applying nclawzero patches..." >> "$LOGFILE"
for patch in /etc/nemoclaw/patches/*.patch; do
    [ -f "$patch" ] || continue
    cd /opt/nemoclaw
    git apply "$patch" >> "$LOGFILE" 2>&1 || echo "WARN: patch failed: $patch" >> "$LOGFILE"
done

# Install npm deps
echo "Installing npm dependencies..." >> "$LOGFILE"
cd /opt/nemoclaw
npm install --production --no-optional >> "$LOGFILE" 2>&1

# Build plugin
if [ -d "nemoclaw" ]; then
    echo "Building NemoClaw plugin..." >> "$LOGFILE"
    cd nemoclaw
    npm install --production --no-optional >> "$LOGFILE" 2>&1
    npm run build >> "$LOGFILE" 2>&1 || echo "WARN: plugin build skipped" >> "$LOGFILE"
    cd ..
fi

# Install Claude Code CLI
echo "Installing Claude Code..." >> "$LOGFILE"
npm install -g @anthropic-ai/claude-code >> "$LOGFILE" 2>&1 || echo "WARN: Claude Code install failed" >> "$LOGFILE"

# Verify Claude Code
if command -v claude &>/dev/null; then
    echo "Claude Code: $(claude --version 2>/dev/null | head -1)" >> "$LOGFILE"
else
    echo "WARN: Claude Code not in PATH after install" >> "$LOGFILE"
fi

# Mark provisioned
mkdir -p /var/lib/nemoclaw
touch "$MARKER"
echo "=== NemoClaw + Claude Code provisioning complete: $(date) ===" >> "$LOGFILE"
