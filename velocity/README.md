# Velocity

A small Docker image for running a Velocity proxy.


## Build & Run
```sh
docker build -t velocity .

docker run --rm -p 25565:25565 -e VELOCITY_FORWARDING_SECRET=123456789 velocity
```
