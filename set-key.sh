#!/usr/bin/env bash
# Send the API key from .env to the app on the TV. Usage: TV_HOST=192.168.1.73 ./set-key.sh
set -euo pipefail
cd "$(dirname "$0")"
: "${TV_HOST:?set TV_HOST to the TV IP address}"
CYT="${CLEANYOURTV:-$HOME/Code/cleanyourtv}"
KEY=$(sed -n 's/^ANTHROPIC_API_KEY=//p' .env | tr -d '[:space:]"')
[ -n "$KEY" ] || { echo "No key in .env yet." >&2; exit 1; }
"$CYT/tools/venv/bin/python" -I "$CYT/tools/tv.py" \
  "am broadcast -n com.askclaude.tv/.KeyReceiver --es key $KEY" | grep -oE 'data="[^"]*"' || true
