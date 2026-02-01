A simple app that checks the prices of various Australian postage providers (currently AusPost only; Sendle is defunct) for a combination of customisable items and packaging from origin to destination.

For more details, check [`docs/api-contract.md`](docs/api-contract.md).

## Running with Docker

No need to install Node, npm, Maven, or Java locally. From the project root:

```bash
# Copy env template and add your API keys (optional)
cp .env.example .env

# Build and run
make docker-build
make docker-run
```

Then open http://localhost (frontend) or http://localhost:8080 (backend API).

### Pushing to Docker Hub

1. Add `DOCKER_HUB_USERNAME=your-dockerhub-username` to `.env` (project root)
2. Run `docker login` (if not already)
3. Run `make docker-push`

### Pulling on another device

**Option A – Without cloning the repo** (requires only Docker):

```bash
curl -sSL https://raw.githubusercontent.com/YOUR_USERNAME/YOUR_REPO/main/run-standalone.sh | bash -s YOUR_DOCKERHUB_USERNAME
```

Replace `YOUR_USERNAME/YOUR_REPO` with your GitHub repo path and `YOUR_DOCKERHUB_USERNAME` with your Docker Hub username. Then open http://localhost.

**Option B – With the repo cloned:**

1. Clone the repo and add `DOCKER_HUB_USERNAME=your-dockerhub-username` to `.env`
2. Run `make docker-pull` then `make docker-run`

- `make build-frontend` - Build frontend only
- `make build-backend` - Build backend only  
- `make docker-down` - Stop containers
