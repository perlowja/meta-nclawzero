#!/bin/sh
# Fetch Gemma-4-E4B Unsloth GGUF for the on-device inference demo.
# Prefers rsync from TYPHON (fleet-local, fast); falls back to HuggingFace.
set -e

MODEL_NAME="${MODEL_NAME:-gemma-4-E4B-it-Q4_K_M.gguf}"
MODEL_DIR="${MODEL_DIR:-/srv/nclaw/models}"
MODEL_PATH="${MODEL_DIR}/${MODEL_NAME}"
TYPHON_SRC="${TYPHON_SRC:-jasonperlow@10.0.0.61:/home/jasonperlow/models/gemma-4-E4B-Q4/${MODEL_NAME}}"
MODEL_URL="${MODEL_URL:-}"  # HF fallback; operator sets if TYPHON unreachable

if [ -s "$MODEL_PATH" ]; then
    echo "fetch-gemma: model already present at $MODEL_PATH (size=$(stat -c%s "$MODEL_PATH"))"
    exit 0
fi

mkdir -p "$MODEL_DIR"
tmp="${MODEL_PATH}.partial"

echo "fetch-gemma: trying TYPHON rsync ($TYPHON_SRC)"
if command -v rsync >/dev/null 2>&1 && command -v sshpass >/dev/null 2>&1; then
    if sshpass -p "${TYPHON_PASS:-}" rsync -av --partial \
         -e 'ssh -o PubkeyAuthentication=no -o StrictHostKeyChecking=no' \
         "$TYPHON_SRC" "$tmp"; then
        mv "$tmp" "$MODEL_PATH"
        echo "fetch-gemma: TYPHON rsync ok ($(stat -c%s "$MODEL_PATH") bytes)"
        exit 0
    fi
    echo "fetch-gemma: TYPHON rsync failed, trying HF"
fi

if [ -n "$MODEL_URL" ]; then
    echo "fetch-gemma: downloading $MODEL_URL"
    curl --fail -L --retry 5 --retry-delay 10 -o "$tmp" "$MODEL_URL"
    mv "$tmp" "$MODEL_PATH"
    echo "fetch-gemma: done ($(stat -c%s "$MODEL_PATH") bytes)"
    exit 0
fi

echo "fetch-gemma: no source succeeded (set TYPHON_PASS or MODEL_URL)" >&2
exit 1
