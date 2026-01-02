# Velocity

A small Docker image for running a Velocity proxy.

## Default Plugins

The following plugins are installed by default:

- **plugin-server-discovery-velocity** (v0.1.0) - Server discovery plugin

## Build & Run

### Basic Build

```sh
docker build -t velocity .

docker run --rm -p 25565:25565 -e VELOCITY_FORWARDING_SECRET=123456789 velocity
```

### Building with Custom Plugins

You can customize which plugins are installed by using the `PLUGINS` build argument. The format is: `repository:artifactId:version[,repository:artifactId:version,...]`

```sh
# Build with additional plugins
docker build --build-arg PLUGINS="plugin-server-discovery:plugin-server-discovery-velocity:0.1.0,another-plugin:another-plugin-velocity:1.2.3" -t velocity .

# Build without default plugins (empty list)
docker build --build-arg PLUGINS="" -t velocity .
```

### Building with GitHub Token (Local Builds)

When building locally, you need to provide a GitHub token to download plugins from GitHub Packages Maven Repository. The token is passed securely using Docker BuildKit secrets:

```sh
# Build with GitHub token
echo "your-github-token" | docker build --secret id=github_token,src=/dev/stdin -t velocity .
```

Or using an environment variable:

```sh
export GITHUB_TOKEN="your-github-token"
echo "$GITHUB_TOKEN" | docker build --secret id=github_token,src=/dev/stdin -t velocity .
```

**Note:** In CI/CD pipelines (GitHub Actions), the token is automatically provided and you don't need to set it manually.

## Plugin Installation

Plugins are downloaded from the GitHub Packages Maven Repository (`maven.pkg.github.com/groundsgg`) during the Docker build process. They are installed in the `/app/plugins` directory and will be automatically loaded when the proxy starts.

The plugin format follows Maven coordinates:
- **Repository**: GitHub repository name (e.g., `plugin-server-discovery`)
- **Artifact ID**: Maven artifact ID (e.g., `plugin-server-discovery-velocity`)
- **Version**: Plugin version (e.g., `0.1.0`)
