#!/usr/bin/env bash
# Build the app and install it on the TV. Usage: TV_HOST=192.168.1.73 ./install.sh
set -euo pipefail
cd "$(dirname "$0")"
: "${TV_HOST:?set TV_HOST to the TV IP address}"
CYT="${CLEANYOURTV:-$HOME/Code/cleanyourtv}"
TV="$CYT/tools/venv/bin/python -I $CYT/tools/tv.py"
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
./gradlew --no-daemon -q assembleRelease
"$CYT/tools/venv/bin/python" -I "$CYT/tools/b64push.py" app/build/outputs/apk/release/app-release.apk /data/local/tmp/ask-claude.apk
$TV 'pm install -r /data/local/tmp/ask-claude.apk; rm -f /data/local/tmp/ask-claude.apk'
