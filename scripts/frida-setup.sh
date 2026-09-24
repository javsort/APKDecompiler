#!/usr/bin/env bash

# Exit immediately if a command exits with a non-zero status
set -e

echo "=== Creating virtual environment and installing Frida tools ==="
python3 -m venv env
source env/bin/activate

if ! pip install frida-tools; then
    echo "[!] Error installing frida-tools" >&2
    exit 1
fi

echo "=== Checking Frida version ==="
FRIDA_VERSION=$(frida --version)
echo "Installed Frida version: ${FRIDA_VERSION}"

echo "=== Verifying ADB installation ==="
if ! command -v adb &> /dev/null; then
    echo "[!] ADB is not installed. Installing platform tools..."
    if command -v dnf &> /dev/null; then
        sudo dnf install -y android-tools
    elif command -v apt-get &> /dev/null; then
        sudo apt-get update && sudo apt-get install -y android-tools-adb
    elif command -v brew &> /dev/null; then
        brew install android-platform-tools
    else
        echo "[!] Manual installation of ADB required." >&2
        exit 1
    fi
fi

echo "=== Checking for active device/emulator ==="
if ! adb get-state >/dev/null 2>&1; then
    echo "[!] No Android device connected. Run 'make start' first." >&2
    exit 1
fi

echo "=== Detecting device architecture ==="
RAW_ABI=$(adb shell getprop ro.product.cpu.abi | tr -d '\r')

case "$RAW_ABI" in
    arm64*)      ARCH="arm64" ;;
    armeabi*)    ARCH="arm" ;;
    x86_64*)     ARCH="x86_64" ;;
    x86*)        ARCH="x86" ;;
    *)
        echo "[!] Unknown or unsupported ABI: ${RAW_ABI}" >&2
        exit 1
        ;;
esac

echo "Detected target architecture: ${ARCH}"

echo "=== Downloading matching Frida server ==="
SERVER_NAME="frida-server-${FRIDA_VERSION}-android-${ARCH}"
ARCHIVE_NAME="${SERVER_NAME}.xz"
DOWNLOAD_URL="https://github.com/frida/frida/releases/download/${FRIDA_VERSION}/${ARCHIVE_NAME}"

if [ ! -f "$SERVER_NAME" ]; then
    echo "Downloading ${DOWNLOAD_URL}..."
    curl -L -o "$ARCHIVE_NAME" "$DOWNLOAD_URL"

    echo "Decompressing Frida server..."
    xz -d "$ARCHIVE_NAME"
fi

echo "=== Pushing Frida to device ==="
# Restart ADB as root to ensure writable system access on emulator
adb root > /dev/null 2>&1 || true
sleep 1

adb push "$SERVER_NAME" /data/local/tmp/custom_loader
adb shell "chmod 755 /data/local/tmp/custom_loader"


echo "=== Starting Frida server in background ==="

FRIDA_PID="$(adb shell pidof custom_loader 2>/dev/null | tr -d '\r' || true)"

if [ -n "$FRIDA_PID" ]; then
    echo "[+] Existing Frida server found (PID $FRIDA_PID). Stopping it..."
    adb shell kill "$FRIDA_PID"
    sleep 1
fi

adb shell "nohup /data/local/tmp/custom_loader \
    >/data/local/tmp/frida-server.log 2>&1 </dev/null &"

sleep 2

echo "=== Checking Frida server ==="

if ./env/bin/frida-ps -U >/dev/null 2>&1; then
    echo "[+] Frida server is reachable."
else
    echo "[!] Frida server is not reachable." >&2
    echo "[!] Frida server log:"
    adb shell cat /data/local/tmp/frida-server.log 2>/dev/null || true
    exit 1
fi

echo "[+] Frida setup complete."
