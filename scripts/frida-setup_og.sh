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
if ! adb get-state &> /dev/null; then
    echo "No connected device found. Launching Pixel_4a emulator..."

    # Linux SDK default is ~/Android/Sdk; honor ANDROID_HOME/ANDROID_SDK_ROOT if set
    ANDROID_SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"

    echo "Android SDK path: $ANDROID_SDK"

    EMULATOR_PATH="$ANDROID_SDK/emulator/emulator"

    echo "Emulator binary path: $EMULATOR_PATH"

    if [ ! -f "$EMULATOR_PATH" ]; then
        echo "[!] Emulator binary not found at ${EMULATOR_PATH}" >&2
        exit 1
    fi

    # Launch emulator in background with specified flags
    "$EMULATOR_PATH" \
        -avd Pixel_4a \
        -writable-system \
        -no-snapshot > /dev/null 2>&1 &

    echo "Waiting for emulator to boot up..."
    adb wait-for-device

    # Wait until boot process is fully completed
    while [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]; do
        sleep 2
    done
    echo "Emulator booted successfully!"
else
    echo "Active device already detected."
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
adb shell "pkill -f custom_loader 2>/dev/null || true"
adb shell "nohup /data/local/tmp/custom_loader > /dev/null 2>&1 &"

sleep 2

echo "=== Verifying Frida connection ==="

frida-ps -U 

frida -D emulator-5554 -f pt.sibs.android.mbway -l frida_settings.java
