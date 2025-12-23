# containers

A collection of Docker containers for development and deployment, published to GitHub Container Registry (ghcr.io).

## Available Containers

### dev-container-node
- **Description**: A Node.js container used for development in our DevSpace environment
- **Registry**: `ghcr.io/groundsgg/containers/dev-container-node`
- **Documentation**: [dev-container-node/README.md](./dev-container-node/README.md)
- **Features**:
  - Node.js 24.11.0 on Alpine Linux
  - PNPM package manager pre-installed
  - Development tools (wget, curl, nano, bash)
  - DevSpace integration with startup script

## Adding a New Container

Follow these steps to add a new container to this repository:

### 1. Create Container Directory
Create a new directory following the naming convention `{container-name}`:
```bash
mkdir your-container-name
cd your-container-name
```

### 2. Create Dockerfile
Create a `Dockerfile` with required OCI labels (use `dev-container-node/Dockerfile` as reference):

### 3. Create Container README
Create a `README.md` in your container directory with:
- Container description
- Usage instructions
- Environment variables
- Examples

### 4. Update Release Please Configuration
Add your container to `release-please-config.json`:

```json
{
  "packages": {
    "dev-container-node": {
      "release-type": "simple",
      "package-name": "dev-container-node"
    },
    "your-container-name": {
      "release-type": "simple",
      "package-name": "your-container-name"
    }
  }
}
```

### 5. Update GitHub Workflow
Update the container matrix in all workflows so the new container builds:
- `.github/workflows/docker-build-push.yml`: add your container to `strategy.matrix.container` (controls build/push and tag matching for `{container}@x.y.z`)
- `.github/workflows/ci.yml`: add your container to `strategy.matrix.container` (CI build validation)

### 6. Update Dependabot (Optional)
If your container has dependencies that need monitoring, update `.github/dependabot.yml` to include your container directory.
