# Paper

A small Docker image for running a Paper Minecraft server. By using this image you agree to the [Minecraft EULA](https://aka.ms/MinecraftEULA).

## Build & Run

```sh
docker build -t paper .

docker run --rm -p 25565:25565 -e PAPER_VELOCITY_SECRET=123456789  paper
```
