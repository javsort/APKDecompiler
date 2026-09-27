.PHONY: up down connect status shell

up:
	@echo "[+] Starting containerized lab..."
	@docker compose up -d
	@echo "[+] Waiting for emulator health..."
	@until [ "$$(docker inspect -f '{{.State.Health.Status}}' mbway_emu_setup-emulator-1 2>/dev/null)" = "healthy" ]; do \
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