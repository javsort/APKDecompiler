ADB := docker compose exec -T toolbox adb -s emulator:5555
TOOLBOX := docker compose exec -T toolbox

.PHONY: setup install-app setup-frida verify
.PHONY: up down connect status shell

help:
	@echo "Available targets:"
	@echo "  up       - Start the containerized lab"
	@echo "  down     - Stop the containerized lab"
	@echo "  connect  - Connect to the emulator"
	@echo "  status   - Show the status of the containers"
	@echo "  shell    - Open a shell in the toolbox"
	@echo "  setup    - Set up the environment"
	@echo "  install-app - Install the MB WAY app"
	@echo "  setup-frida - Set up Frida"
	@echo "  verify   - Verify the environment"
	@echo
	@echo "  The following targets are available once the lab is running:"
	@echo "  get-ui   - Connect to the UI"
	@echo "  get-emulator-logs - Get emulator logs"
	@echo "  get-toolbox-logs - Get toolbox logs"

up:
	@echo "[+] Starting containerized lab..."
	@docker compose up -d
	@echo "[+] Waiting for emulator health..."
	@until [ "$$(docker inspect -f '{{.State.Health.Status}}' $$(docker compose ps -q emulator) 2>/dev/null)" = "healthy" ]; do \
		sleep 2; \
	done
	@$(MAKE) connect
	@echo "[+] Lab ready."

connect:
	@echo "[+] Connecting toolbox ADB..."
	@docker compose exec -T toolbox adb connect emulator:5555 >/dev/null
	@echo "[+] ADB connected."

status:
	@docker compose ps

shell:
	@docker compose exec toolbox bash

down:
	@echo "[+] Stopping lab..."
	@docker compose down

setup: up install-app setup-frida verify
	@echo
	@echo "[+] Environment ready."

install-app:
	@echo "[+] Installing MB WAY..."
	@$(ADB) install-multiple -r \
		/workspace/apks/base.apk \
		/workspace/apks/split_config.arm64_v8a.apk \
		/workspace/apks/split_config.nl.apk \
		/workspace/apks/split_config.xxhdpi.apk

setup-frida:
	@echo "[+] Setting up Frida..."
	@$(TOOLBOX) ./scripts/frida-setup.sh

verify:
	@echo "[+] Verifying environment..."
	@$(TOOLBOX) ./scripts/verify-env.sh


# Once running:
get-ui:
	@echo "[+] Getting UI..."
	@scrcpy -s localhost:5555 --no-audio
	@echo "[+] UI saved to /tmp/screen.png"

get-emulator-logs:
	@echo "[+] Getting emulator logs..."
	@docker compose logs -f emulator

get-toolbox-logs:
	@echo "[+] Getting toolbox logs..."
	@docker compose logs -f toolbox

launch-mbway:
	@echo "[+] Launching MB WAY..."
	@ docker compose exec toolbox adb -s emulator:5555 shell monkey \
  		-p pt.sibs.android.mbway \
  		-c android.intent.category.LAUNCHER 1

kill-mbway:
	@echo "[+] Killing MB WAY..."
	@docker compose exec toolbox adb -s emulator:5555 shell am force-stop pt.sibs.android.mbway