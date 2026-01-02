# Paper

A small Docker image for running a Paper Minecraft server. By using this image you agree to the [Minecraft EULA](https://aka.ms/MinecraftEULA).

## Plugins

Plugins can be configured via the `PLUGINS` build argument. By default, some plugins are pre-configured, but you can customize the plugin list during build. See the "Building with Custom Plugins" section below for details.

## Build & Run

### Basic Build

```sh
docker build -t paper .

docker run --rm -p 25565:25565 -e PAPER_VELOCITY_SECRET=123456789 paper
```

### Building with Custom Plugins

You can customize which plugins are installed by using the `PLUGINS` build argument. The format is: `repository:artifactId:version[,repository:artifactId:version,...]`

```sh
# Build with additional plugins
docker build --build-arg PLUGINS="plugin-server-discovery:plugin-server-discovery-paper:0.1.0,another-plugin:another-plugin-paper:1.2.3" -t paper .

# Build without default plugins (empty list)
docker build --build-arg PLUGINS="" -t paper .
```

### Building with GitHub Token (Local Builds)

When building locally, you need to provide a GitHub token to download plugins from GitHub Packages Maven Repository. The token is passed securely using Docker BuildKit secrets:

```sh
# Build with GitHub token
echo "your-github-token" | docker build --secret id=github_token,src=/dev/stdin -t paper .
```

Or using an environment variable:

```sh
export GITHUB_TOKEN="your-github-token"
echo "$GITHUB_TOKEN" | docker build --secret id=github_token,src=/dev/stdin -t paper .
```

**Note:** In CI/CD pipelines (GitHub Actions), the token is automatically provided and you don't need to set it manually.

## Plugin Installation

Plugins are downloaded from the GitHub Packages Maven Repository (`maven.pkg.github.com/groundsgg`) during the Docker build process. They are installed in the `/app/plugins` directory and will be automatically loaded when the server starts.

The plugin format follows Maven coordinates:
- **Repository**: GitHub repository name (e.g., `plugin-server-discovery`)
- **Artifact ID**: Maven artifact ID (e.g., `plugin-server-discovery-paper`)
- **Version**: Plugin version (e.g., `0.1.0`)
