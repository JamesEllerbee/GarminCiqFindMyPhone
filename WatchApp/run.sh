#!/usr/bin/env bash
# Build the app and run it in the Connect IQ simulator.
# Usage: ./run.sh [device]   (default: fr170)
set -euo pipefail

HERE="$(dirname "$(readlink -f "$0")")"
DEVICE="${1:-fr170}"
SDK="$(tr -d '\n' < "$HOME/.Garmin/ConnectIQ/current-sdk.cfg")"
SDK="${SDK%/}"
SHIM="$HERE/../connectiq-sdk-manager/jxl-compat"   # libjxl.so.0.11 -> 0.12 compat
PRG="$HERE/bin/FindMyPhone.prg"

cd "$HERE"
mkdir -p bin

echo "==> Building for $DEVICE"
"$SDK/bin/monkeyc" -d "$DEVICE" -f monkey.jungle -o "$PRG" -y developer_key.der -w

if ! pgrep -x simulator >/dev/null; then
    echo "==> Starting simulator"
    LD_LIBRARY_PATH="$SHIM${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}" \
        nohup "$SDK/bin/connectiq" >"$HERE/bin/simulator.log" 2>&1 &
    for _ in $(seq 1 30); do
        pgrep -x simulator >/dev/null && break
        sleep 1
    done
    sleep 3   # let it open its listening socket
fi

echo "==> Launching on $DEVICE"
exec "$SDK/bin/monkeydo" "$PRG" "$DEVICE"
