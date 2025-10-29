# dev-container

A Node.js container which is used for development in our DevSpace environment.

## Usage

This container is intended to be used within DevSpace. It offers a `devspace_start.sh` script that should be used as the
`command` of the container, e.g.:

```yaml
name: my-application
# ...
dev:
  my-application:
    # ...
    terminal:
      command: >-
        DEVSPACE_NAME=${DEVSPACE_NAME}
        INGRESS_URL=https://api.grounds.gg/my-application
        ./devspace_start.sh
```

Supported environment variables:

- `DEVSPACE_NAME` (required): The application name.
- `INGRESS_URL` (optional): The URL at which the service is exposed. If set, this is printed on startup.
