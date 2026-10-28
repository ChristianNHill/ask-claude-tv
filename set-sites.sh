#!/usr/bin/env bash
# Send sites.txt to the app on the TV. Usage: TV_HOST=192.168.1.73 ./set-sites.sh
set -euo pipefail
cd "$(dirname "$0")"
: "${TV_HOST:?set TV_HOST to the TV IP address}"
CYT="${CLEANYOURTV:-$HOME/Code/cleanyourtv}"
B64=$(grep -vE '^\s*(#|$)' sites.txt | base64 | tr -d '\n')
"$CYT/tools/venv/bin/python" -I "$CYT/tools/tv.py" \
  "am broadcast -n com.askclaude.tv/.KeyReceiver --es sites_b64 '${B64:-}'" | grep -oE 'data="[^"]*"' || true
