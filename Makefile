.PHONY: build-frontend build-backend build-all docker-build docker-push docker-pull docker-run docker-down docker-logs standalone-pull standalone-run bootstrap setup start stop restart status logs clean

# Bootstrap configuration (for other users)
# Publisher can override these here; other users can override via .env.
REPO_URL ?= https://github.com/FakhruddinNalawala/Postage-Comparator.git
APP_DIR ?= postage-comparator-app

# Load environment variables from .env (if present) before running commands
ENV_PREFIX = if [ -f .env ]; then set -a; . .env; set +a; fi;

# Default target
all: build-all

# Build frontend (npm install + vite build)
build-frontend:
	cd frontend && npm ci && npm run build

# Build backend (Maven package)
build-backend:
	cd backend && mvn package -DskipTests -q

# Build both frontend and backend
build-all: build-frontend build-backend

# Build Docker images
docker-build:
	@$(ENV_PREFIX) docker compose build

# Push images to Docker Hub (requires DOCKER_HUB_USERNAME in .env, run: docker login)
docker-push: docker-build
	@$(ENV_PREFIX) docker compose push

# Pull pre-built images from Docker Hub (requires DOCKER_HUB_USERNAME in .env)
docker-pull:
	@$(ENV_PREFIX) if [ -z "$$DOCKER_HUB_USERNAME" ]; then \
		echo "Add DOCKER_HUB_USERNAME=your-dockerhub-username to .env"; exit 1; fi; \
	docker compose pull

# Run the app in Docker (requires .env in same folder)
docker-run:
	@$(ENV_PREFIX) if [ ! -f .env ]; then \
		echo "Creating .env from .env.example (if exists)..."; \
		if [ -f .env.example ]; then cp .env.example .env; fi; \
	fi
	@$(ENV_PREFIX) docker compose up -d

# Stop Docker containers
docker-down:
	@$(ENV_PREFIX) docker compose down

# View Docker logs
docker-logs:
	@$(ENV_PREFIX) docker compose logs -f

# Standalone (pull-only) workflow for other users using docker-compose.standalone.yml
# Usage: DOCKER_HUB_USERNAME=your-dockerhub-username make standalone-run
standalone-pull:
	@$(ENV_PREFIX) if [ -z "$$DOCKER_HUB_USERNAME" ]; then \
		echo "Set DOCKER_HUB_USERNAME=your-dockerhub-username in .env or env"; exit 1; fi; \
	docker compose -f docker-compose.standalone.yml pull

standalone-run: standalone-pull
	@$(ENV_PREFIX) if [ -z "$$DOCKER_HUB_USERNAME" ]; then \
		echo "Set DOCKER_HUB_USERNAME=your-dockerhub-username in .env or env"; exit 1; fi; \
	docker compose -f docker-compose.standalone.yml up -d

# Bootstrap: clone or update repo, copy .env, pull images, and start app using standalone compose.
# Intended for other users who only have this Makefile and a .env file.
bootstrap:
	@$(ENV_PREFIX) \
	if [ -z "$$REPO_URL" ]; then \
		echo "REPO_URL must be set in .env or Makefile"; exit 1; fi; \
	if [ -z "$$DOCKER_HUB_USERNAME" ]; then \
		echo "DOCKER_HUB_USERNAME must be set in .env"; exit 1; fi; \
	if [ ! -d "$(APP_DIR)/.git" ]; then \
		echo "Cloning $$REPO_URL into $(APP_DIR)..."; \
		git clone "$$REPO_URL" "$(APP_DIR)"; \
	else \
		echo "Repo exists in $(APP_DIR), pulling latest..."; \
		git -C "$(APP_DIR)" pull --ff-only; \
	fi; \
	if [ -f .env ]; then \
		echo "Using .env from: $$(pwd)/.env -> copying into $(APP_DIR)/.env"; \
		cp .env "$(APP_DIR)/.env"; \
	else \
		echo "No .env in $$(pwd); containers will use $(APP_DIR)/.env if present"; \
	fi; \
	cd "$(APP_DIR)" && DOCKER_HUB_USERNAME="$$DOCKER_HUB_USERNAME" docker compose -f docker-compose.standalone.yml pull && \
	DOCKER_HUB_USERNAME="$$DOCKER_HUB_USERNAME" docker compose -f docker-compose.standalone.yml up -d

# ──────────────────────────────────────────────────────────────
# Quick-start for users who cloned the repo and have a .env ready.
#   1. git clone <repo-url> && cd postage-comparator
#   2. cp your-prepared.env .env
#   3. make setup        (pulls images & starts containers)
#
# After initial setup:
#   make start / make stop / make restart / make logs / make status
# ──────────────────────────────────────────────────────────────

# One-command setup: validate .env, pull pre-built images, and start all containers.
setup:
	@if [ ! -f .env ]; then \
		echo "ERROR: .env file not found."; \
		echo "Create a .env file with the required variables before running 'make setup'."; \
		echo "Required variables: DOCKER_HUB_USERNAME, AUSPOST_API_KEY, SHIPPIT_API_KEY, etc."; \
		exit 1; \
	fi
	@$(ENV_PREFIX) \
	if [ -z "$$DOCKER_HUB_USERNAME" ]; then \
		echo "ERROR: DOCKER_HUB_USERNAME is not set in .env."; \
		echo "This is needed to pull the pre-built Docker images."; \
		exit 1; \
	fi
	@echo "==> .env found and validated."
	@echo "==> Pulling pre-built images from Docker Hub..."
	@$(ENV_PREFIX) docker compose -f docker-compose.standalone.yml pull
	@echo "==> Starting containers..."
	@$(ENV_PREFIX) docker compose -f docker-compose.standalone.yml up -d
	@echo ""
	@echo "==> Setup complete!"
	@echo "    Frontend : http://localhost"
	@echo "    Backend  : http://localhost:8080"
	@echo ""
	@echo "    Useful commands:"
	@echo "      make logs     - tail container logs"
	@echo "      make stop     - stop all containers"
	@echo "      make restart  - restart all containers"
	@echo "      make status   - show container status"

# Start containers (assumes images are already pulled via 'make setup')
start:
	@$(ENV_PREFIX) docker compose -f docker-compose.standalone.yml up -d
	@echo "App started -> http://localhost"

# Stop containers
stop:
	@$(ENV_PREFIX) docker compose -f docker-compose.standalone.yml down

# Restart containers
restart: stop start

# Show container status
status:
	@$(ENV_PREFIX) docker compose -f docker-compose.standalone.yml ps

# Tail container logs (Ctrl+C to exit)
logs:
	@$(ENV_PREFIX) docker compose -f docker-compose.standalone.yml logs -f

# Clean build artifacts
clean:
	rm -rf frontend/dist frontend/node_modules
	cd backend && mvn clean -q
