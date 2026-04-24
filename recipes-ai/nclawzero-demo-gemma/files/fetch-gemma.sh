#!/bin/sh
# Fetch Gemma 4 Unsloth GGUF for the on-device inference demo.
# Runs once per first-boot; env overrides let operators swap the model.
set -e

MODEL_URL="${MODEL_URL:-https://huggingface.co/unsloth/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf}"
MODEL_PATH="${MODEL_PATH:-/var/lib/models/gemma.gguf}"

if [ -s "$MODEL_PATH" ]; then
    echo "fetch-gemma: model already present at $MODEL_PATH (size=$(stat -c%s "$MODEL_PATH"))"
    exit 0
fi

mkdir -p "$(dirname "$MODEL_PATH")"
tmp="${MODEL_PATH}.partial"

echo "fetch-gemma: downloading $MODEL_URL -> $MODEL_PATH"
# curl --fail so HTTP errors bubble up; -L follows HF redirects;
# --retry handles transient flakes. Operators should replace the URL
# with a specific Gemma 4 Unsloth build when published.
curl --fail -L --retry 5 --retry-delay 10 -o "$tmp" "$MODEL_URL"
mv "$tmp" "$MODEL_PATH"
chown root:root "$MODEL_PATH"
chmod 0644 "$MODEL_PATH"
echo "fetch-gemma: done ($(stat -c%s "$MODEL_PATH") bytes)"
