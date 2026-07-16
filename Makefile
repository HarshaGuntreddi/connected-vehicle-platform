# ============================================================================
# Connected Vehicle Platform — helper commands
# ============================================================================
.DEFAULT_GOAL := help
COMPOSE := docker compose

.PHONY: help up down build rebuild logs ps clean config env test hardware-can

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-14s\033[0m %s\n", $$1, $$2}'

env: ## Create .env from .env.example if missing
	@test -f .env || (cp .env.example .env && echo "Created .env from .env.example")

up: env ## Build and start the entire stack in the background
	$(COMPOSE) up --build -d

build: ## Build all images without starting
	$(COMPOSE) build

rebuild: ## Force a clean rebuild of all images
	$(COMPOSE) build --no-cache

down: ## Stop and remove all containers
	$(COMPOSE) down

clean: ## Stop everything and remove volumes (DELETES DB DATA)
	$(COMPOSE) down -v --remove-orphans
	docker image prune -f

logs: ## Tail logs from all services
	$(COMPOSE) logs -f

ps: ## Show running containers
	$(COMPOSE) ps

config: ## Validate and render the compose configuration
	$(COMPOSE) config

test: ## Run all module unit tests inside a Maven container
	docker run --rm -v "$(PWD)":/app -w /app maven:3.9-eclipse-temurin-17 mvn -q test

hardware-can: ## Create a virtual CAN interface on the HOST (Linux only)
	sudo modprobe vcan
	sudo ip link add dev vcan0 type vcan
	sudo ip link set up vcan0
	@echo "vcan0 is up. Set CAN_MODE=hardware in .env and restart."
