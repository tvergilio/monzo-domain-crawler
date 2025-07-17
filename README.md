# Quick Start

## Prerequisites
- Docker
- Docker Compose

## Running the Crawler and Redis

```sh
docker-compose up --build
```

This will start both the crawler and Redis services. The crawler will connect to Redis at `redis:6379`.

## Stopping Services

```sh
docker-compose down
```
