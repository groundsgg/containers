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

### 5. Update Release Please Configuration
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

### 6. Update GitHub Workflow
Update `.github/workflows/docker-build-push.yml`:

**a) Add to workflow_dispatch options** (around line 16):
```yaml
options:
  - dev-container-node
  - your-container-name
```

**b) Add to determine-matrix step** (around line 40):
```yaml
echo "matrix={\"include\":[{\"container\":\"dev-container-node\",\"tag_type\":\"edge\"},{\"container\":\"your-container-name\",\"tag_type\":\"edge\"}]}" \
  >> $GITHUB_OUTPUT
```

**c) Add tag pattern for your container** (around line 44):
```yaml
elif [[ "${{ github.ref }}" == refs/tags/your-container-name@* ]]; then
  echo "matrix={\"include\":[{\"container\":\"your-container-name\",\"tag_type\":\"release\"}]}" \
    >> $GITHUB_OUTPUT
```

**d) Add output variable** (around line 61):
```yaml
outputs:
  dev-container-node-tag: ${{ steps.set-output.outputs.dev-container-node-tag }}
  your-container-name-tag: ${{ steps.set-output.outputs.your-container-name-tag }}
```

**e) Update SBOM generation** (around line 130):
```yaml
image: >-
  ${{ matrix.tag_type == 'release' && needs.build-push.outputs.your-container-name-tag || format('ghcr.io/{0}/{1}:edge', github.repository, matrix.container) }}
```

### 8. Update Dependabot (Optional)
If your container has dependencies that need monitoring, update `.github/dependabot.yml` to include your container directory.
