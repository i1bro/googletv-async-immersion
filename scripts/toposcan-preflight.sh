#!/bin/sh
# Read-only device inspection. Does not install apps, grant permissions or change settings.
set -eu

if [ "$#" -ne 1 ]; then
    printf 'Usage: sh scripts/toposcan-preflight.sh ADB_SERIAL\n' >&2
    exit 2
fi

ADB=${ADB:-adb}
SERIAL=$1
"$ADB" -s "$SERIAL" get-state

read_device() {
    printf '\n--- %s ---\n' "$*"
    "$ADB" -s "$SERIAL" shell "$@" || printf 'Unavailable on this firmware\n'
}

read_device getprop ro.product.manufacturer
read_device getprop ro.product.model
read_device getprop ro.build.version.release
read_device getprop ro.build.version.sdk
read_device getprop ro.build.fingerprint
read_device wm size
read_device dumpsys display
read_device dumpsys thermalservice
read_device settings get secure screensaver_components
read_device settings get secure screensaver_enabled
read_device settings get system screen_off_timeout
read_device settings get secure sleep_timeout
read_device dumpsys package com.i1bro.googletv.asyncimmersion
read_device dumpsys package com.i1bro.googletv.asyncimmersion.debug

printf '\nInspection complete. No device settings were changed.\n'
