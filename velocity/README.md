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

The default image includes the REST-only permissions plugin. In Kubernetes,
Forge and the `grounds-velocity` chart provide `PERMISSIONS_SERVICE_URL` and
`PERMISSIONS_TOKEN_FILE` together with the projected workload token. Partial
configuration is rejected by the plugin during startup.

## Runtime environment variables

| Variable | Effect |
|----------|--------|
| `VELOCITY_FORWARDING_SECRET` | Written to `/app/forwarding.secret` at boot, byte-for-byte. Modern player-info forwarding needs the same secret on the backends. |
| `VELOCITY_ONLINE_MODE` | Set to the literal `false` to turn off Mojang authentication. Anything else (unset, empty, `true`, `False`) leaves the baked config alone. |
| `VELOCITY_LOGIN_RATELIMIT` | Milliseconds between logins from one source IP (`0` disables). Unset keeps Velocity's 3000. Load testing only. |

### `VELOCITY_ONLINE_MODE=false` — load testing only

Bot swarms have no Mojang account, so an online-mode proxy rejects them during
login and no load ever reaches the backends. This switch turns off `online-mode`
and `force-key-authentication` in `/app/velocity.toml` at boot, which is what
lets a load generator connect.

It also means **anyone can join as any username, including staff names**. A
proxy started this way must not be reachable from the public internet — give the
load-test environment its own proxy release and its own hostname, and leave the
player-facing one untouched. The container prints three `WARNING` lines at boot
so a proxy that has it on by accident is visible in the logs.

If the sed cannot find `online-mode` in the config (an upstream rename), the
container exits instead of quietly starting in online mode — a bot swarm being
rejected looks like a network fault and is expensive to debug.

### `VELOCITY_LOGIN_RATELIMIT` — load testing only

Velocity allows one login per source IP every `login-ratelimit` milliseconds,
3000 by default. A bot fleet shares one pod IP per worker, so that ceiling *is*
the ramp rate: one bot per 3 s per worker. 750 bots over four workers takes
eleven minutes before the test has started.

What makes it worth a knob is how it fails. It does not error — bots connect,
get kicked with "You are logging in too fast", the fleet replaces them, and the
whole run churns at a few hundred connected while looking like a server-side
problem.

Lowering it makes the proxy easier to flood with login attempts, so it belongs
on a load-test proxy and not on a player-facing one. Values below Velocity's
default print a warning at boot; a non-numeric value refuses to start rather
than silently falling back to 3000 and re-creating the symptom above.
