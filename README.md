# MB WAY Android Research Environment

Reproducible Android 11 research environment for local analysis of the MB WAY Android application.

The setup runs the Android emulator and analysis tooling in Docker, while using the host only for Docker/KVM and optional UI tools such as `scrcpy`.

## Requirements

Host system:

- Linux x86_64
- Docker
- Docker Compose
- KVM available at `/dev/kvm`
- ADB key available from `~/.android/adbkey`
- Optional: `scrcpy` for graphical interaction with the emulator

Fedora users may also need the local SELinux policy required by the Android emulator container.

Check KVM:

```bash
ls -l /dev/kvm
```

Check Docker:

```bash
docker --version
docker compose version
```

## Repository layout

Expected structure:

```text
mbway_emu_setup/
├── compose.yaml
├── Dockerfile.toolbox
├── Makefile
├── README.md
│
├── scripts/
│   ├── frida-setup.sh
│   └── verify-env.sh
│
├── apks/
│   ├── base.apk
│   ├── split_config.arm64_v8a.apk
│   ├── split_config.nl.apk
│   └── split_config.xxhdpi.apk
│
└── runtime/
    └── ...
```

The four MB WAY split APK files must be placed inside:

```text
./apks/
```

The expected files are:

```text
base.apk
split_config.arm64_v8a.apk
split_config.nl.apk
split_config.xxhdpi.apk
```

The `runtime/` directory can be used for files extracted from the running Android instance, JADX output, dynamically loaded DEX/ZIP files, Frida scripts, etc.

## Architecture

The environment consists of two Docker services:

```text
Host
│
├── Docker / KVM
│
├── optional scrcpy
│
└── Docker Compose
    │
    ├── emulator
    │   └── Android 11 / x86_64
    │       └── ARM translation via libndk_translation.so
    │
    └── toolbox
        ├── adb
        ├── Frida 17.18.0
        ├── frida-tools
        ├── JADX
        └── project scripts
```

The Android emulator runs as x86_64 but supports:

```text
x86_64
x86
arm64-v8a
armeabi-v7a
armeabi
```

ARM64 applications are executed using:

```text
libndk_translation.so
```

MB WAY itself runs as:

```text
arm64-v8a
```

## Initial setup

Build and start everything with:

```bash
make setup
```

This will:

1. Start the emulator container
2. Start the toolbox container
3. Wait for Android to become healthy
4. Connect ADB
5. Install the MB WAY split APKs
6. Install/start the matching Frida server
7. Verify the environment

A successful setup ends with:

```text
[+] Environment verified successfully.
[+] Environment ready.
```

## Useful commands

Start the environment:

```bash
make up
```

Stop it:

```bash
make down
```

Show container status:

```bash
make status
```

Open a shell inside the toolbox:

```bash
make shell
```

Install/reinstall MB WAY:

```bash
make install-app
```

Set up Frida:

```bash
make setup-frida
```

Verify the environment:

```bash
make verify
```

## ADB

Inside the toolbox, the emulator is available as:

```text
emulator:5555
```

Example:

```bash
docker compose exec toolbox adb -s emulator:5555 devices
```

Open a shell on Android:

```bash
docker compose exec toolbox adb -s emulator:5555 shell
```

Check Android version:

```bash
docker compose exec toolbox adb -s emulator:5555 \
  shell getprop ro.build.version.release
```

Expected:

```text
11
```

## Emulator UI

The emulator exposes ADB on host port `5555`.

Connect from the host:

```bash
adb connect localhost:5555
```

Then use:

```bash
scrcpy -s localhost:5555 --no-audio
```

`--no-audio` is recommended because the current Android container does not provide a working Opus audio encoder for scrcpy.

## Frida

Frida client tools run inside the toolbox container.

The Frida server runs inside Android as:

```text
/data/local/tmp/custom_loader
```

Verify that Frida can see MB WAY:

```bash
docker compose exec toolbox frida-ps -Uai
```

MB WAY package name:

```text
pt.sibs.android.mbway
```

Frida version currently used:

```text
17.18.0
```

The Frida client and server versions should remain matched.

## JADX

JADX is installed inside the toolbox image.

Example:

```bash
docker compose exec toolbox jadx \
  -d /workspace/runtime/jadx-output \
  /workspace/apks/base.apk
```

Because the repository is mounted as:

```text
/workspace
```

anything written under `/workspace` inside the toolbox also appears directly in the host repository.

## Notes for Fedora / SELinux

On Fedora, the Android emulator container may be blocked by SELinux with an error involving:

```text
qemu-system-x86
execheap
```

A narrow local policy was required during development.

If the emulator exits with code `139`, check:

```bash
sudo ausearch -m AVC,USER_AVC -ts recent
```

Do not globally disable SELinux.

## Expected environment

The verification script checks for:

```text
Android version: 11
System ABI:      x86_64
Native bridge:   libndk_translation.so
MB WAY ABI:      arm64-v8a
Frida tools:     available
Frida connection: working
```

## Important

This environment is intended for local academic security research.

Do not interact with production services or remote infrastructure unless explicitly permitted by the project scope.

The current setup is designed around analysis of the locally installed application and its runtime behavior.