# Redis Server Setup for System Health Monitoring

This document describes the simplified Redis server setup for the System Health Monitoring application.

## Architecture

The Docker Compose configuration provides a Redis setup with:

- **Single Redis Instance**: Optimized for simplicity and reliability
- **Data Persistence**: Both RDB snapshots and AOF journaling
- **Resource Limits**: Configured for optimal performance
- **Web UI**: Redis Commander for easy monitoring and management

## Components

1. **Redis Server** (Port 6379)
   - Data persistence with RDB snapshots every 60 seconds
   - AOF persistence with fsync every second
   - 512MB memory limit with LRU eviction policy
   - Optimized for system health monitoring data

2. **Redis Commander** (Port 8081)
   - Web UI for Redis management
   - Access at http://localhost:8081
   - Connected to Redis instance

## Usage

### Starting the Redis Server

```bash
docker-compose up -d
```

### Connecting to Redis

From the application:

```properties
spring.redis.host=localhost
spring.redis.port=6379
```

For CLI access:

```bash
docker exec -it redis redis-cli
```

### Monitoring Redis

Open Redis Commander in your browser:

```
http://localhost:8081
```

### Stopping the Server

```bash
docker-compose down
```

To completely remove volumes and data:

```bash
docker-compose down -v
```

## Configuration Details

### Redis Configuration

- **Persistence**: 
  - RDB: Save every 60 seconds if at least 1 key changed
  - AOF: Enabled with fsync every second
- **Memory**: 512MB with LRU eviction
- **Container Resources**: 640MB limit

## Scaling Considerations

If your application grows beyond the initial requirements:

1. Increase memory limits in docker-compose.yml
2. Consider adding a replica for high availability
3. For very large deployments, look into Redis Sentinel or Redis Cluster 