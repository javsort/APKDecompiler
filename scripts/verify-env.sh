#!/usr/bin/env bash

set -euo pipefail

PACKAGE="pt.sibs.android.mbway"
FRIDA_PS="frida-ps"
ADB="adb -s emulator:5555"

echo "=== Verifying research environment ==="

echo
echo "[1/6] Checking ADB..."

if ! command -v adb >/dev/null 2>&1; then
    echo "[!] adb not found."
    exit 1
fi

echo "[+] adb found."


echo
echo "[2/6] Checking emulator..."

if ! $ADB get-state >/dev/null 2>&1; then
    echo "[!] No Android device connected."
    exit 1
fi

echo "[+] Android device connected."


echo
echo "[3/6] Checking Android configuration..."

ABI="$($ADB shell getprop ro.product.cpu.abi | tr -d '\r')"
ABI_LIST="$($ADB shell getprop ro.product.cpu.abilist | tr -d '\r')"
BRIDGE="$($ADB shell getprop ro.dalvik.vm.native.bridge | tr -d '\r')"
ANDROID_VERSION="$($ADB shell getprop ro.build.version.release | tr -d '\r')"

echo "Android version: $ANDROID_VERSION"
echo "System ABI:      $ABI"
echo "Supported ABIs:  $ABI_LIST"
echo "Native bridge:   $BRIDGE"

[ "$ANDROID_VERSION" = "11" ] || {
    echo "[!] Expected Android 11."
    exit 1
}

[ "$ABI" = "x86_64" ] || {
    echo "[!] Expected x86_64 emulator."
    exit 1
}

[ "$BRIDGE" = "libndk_translation.so" ] || {
    echo "[!] ARM translation is unavailable."
    exit 1
}


echo
echo "[4/6] Checking MB WAY..."

if ! $ADB shell pm path "$PACKAGE" >/dev/null 2>&1; then
    echo "[!] MB WAY is not installed."
    exit 1
fi

APP_ABI="$(
    $ADB shell dumpsys package "$PACKAGE" |
        grep 'primaryCpuAbi=' |
        head -n1 |
        cut -d= -f2 |
        tr -d '\r '
)"

echo "MB WAY ABI: $APP_ABI"

[ "$APP_ABI" = "arm64-v8a" ] || {
    echo "[!] Expected MB WAY ABI arm64-v8a."
    exit 1
}


echo
echo "[5/6] Checking Frida installation..."

if ! command -v "$FRIDA_PS" >/dev/null 2>&1; then
    echo "[!] Frida tools are missing from the toolbox container."
    exit 1
fi

echo "[+] Frida tools installed."


echo
echo "[6/6] Checking Frida connection..."

if ! "$FRIDA_PS" -U >/dev/null 2>&1; then
    echo "[!] Frida cannot communicate with emulator."
    exit 1
fi

if "$FRIDA_PS" -Uai | grep -q "$PACKAGE"; then
    echo "[+] Frida can see MB WAY."
else
    echo "[!] Frida cannot see MB WAY."
    exit 1
fi


echo
echo "========================================"
echo "[+] Environment verified successfully."
echo "========================================"
