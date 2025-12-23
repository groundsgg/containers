# Velocity

A small Docker image for running a Velocity proxy.


## Build & Run
Create your secret locally and mount it into the container.

```sh
cp config/forwarding.secret.example config/forwarding.secret

docker build -t velocity .

docker run --rm -p 25565:25565 -v "$PWD/config/forwarding.secret:/app/forwarding.secret:ro" velocity
```
