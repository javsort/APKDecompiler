.PHONY: help setup start install-app setup-frida verify run-frida stop reset

PACKAGE := pt.sibs.android.mbway
FRIDA := ./env/bin/frida

ANDROID_SDK ?= $(HOME)/Android/Sdk
EMULATOR := $(ANDROID_SDK)/emulator/emulator
AVD := Pixel_4a

help:
	@echo "MB WAY Research Environment"
	@echo
	@echo "make setup        Prepare the whole environment"
	@echo "make start        Start emulator"
	@echo "make install-app  Install MB WAY split APKs"
	@echo "make setup-frida  Install/start Frida server"
	@echo "make verify       Verify the environment"
	@echo "make run-frida    Launch Frida script"
	@echo "make stop         Stop emulator"
	@echo "make reset        Reinstall app and Frida"

setup: start install-app setup-frida verify
	@echo
	@echo "[+] Environment ready."

start:
	@echo "[+] Checking emulator..."
	@if adb get-state >/dev/null 2>&1; then \
		echo "[+] Emulator already running."; \
	else \
		echo "[+] Starting $(AVD)..."; \
		$(EMULATOR) \
			-avd $(AVD) \
			-writable-system \
			-no-snapshot \
			> /tmp/mbway-emulator.log 2>&1 & \
		echo "[+] Waiting for Android..."; \
		adb wait-for-device; \
		while [ "$$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]; do \
			sleep 2; \
		done; \
		echo "[+] Emulator booted."; \
	fi

install-app:
	@echo "[+] Installing MB WAY..."
	@adb install-multiple -r \
		base.apk \
		split_config.arm64_v8a.apk \
		split_config.nl.apk \
		split_config.xxhdpi.apk

setup-frida:
	@echo "[+] Setting up Frida..."
	@./scripts/frida-setup.sh

verify:
	@./scripts/verify-env.sh

run-frida:
	@$(FRIDA) -D emulator-5554 \
		-f $(PACKAGE) \
		-l ass_cracker.java

stop:
	@adb emu kill

reset:
	@-adb uninstall $(PACKAGE)
	@$(MAKE) install-app
	@$(MAKE) setup-frida
	@$(MAKE) verify