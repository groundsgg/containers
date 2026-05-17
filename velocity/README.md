# Velocity

A small Docker image for running a Velocity proxy.

## Plugins

Plugins can be configured via the `PLUGINS` build argument. By default, some plugins are pre-configured, but you can customize the plugin list during build. See the "Building with Custom Plugins" section below for details.

The image always installs `plugin-grounds-runtime` for Velocity. Runtime-consumer plugins require this plugin in local, test, and production environments. The image writes runtime metadata to `/opt/grounds/runtime-manifest.json` and labels the image with the manifest path. Image tag semantics do not change; Forge ignores this runtime metadata in v1.

## Build & Run

### Building with GitHub Token (Local Builds)

When building locally, you need to provide a GitHub token to download plugins from GitHub Packages Maven Repository. The token is passed securely using Docker BuildKit secrets:

```sh
# Build with GitHub token
echo "your-github-token" | docker build -f velocity/Dockerfile --secret id=github_token,src=/dev/stdin -t velocity .
```

Or using an environment variable:

```sh
export GITHUB_TOKEN="your-github-token"
echo "$GITHUB_TOKEN" | docker build -f velocity/Dockerfile --secret id=github_token,src=/dev/stdin -t velocity .
```

**Note:** In CI/CD pipelines (GitHub Actions), the token is automatically provided and you don't need to set it manually.

## Plugin Installation

Plugins are downloaded from the GitHub Packages Maven Repository (`maven.pkg.github.com/groundsgg`) during the Docker build process. They are installed in the `/app/plugins` directory and will be automatically loaded when the proxy starts.

`plugin-grounds-runtime` is downloaded from the `plugin-grounds-runtime` GitHub Packages Maven repository using `GROUNDS_RUNTIME_PLUGIN_VERSION` and is installed as `/app/plugins/plugin-grounds-runtime.jar`.
The runtime manifest is generated from the `grounds-runtime-catalog` artifact with the same version.

The plugin format follows Maven coordinates:
- **Repository**: GitHub repository name (e.g., `plugin-server-discovery`)
- **Artifact ID**: Maven artifact ID (e.g., `plugin-server-discovery-velocity`)
- **Version**: Plugin version (e.g., `0.1.0`)
