# System Health Monitoring

A comprehensive system health monitoring application that collects various metrics from your server and displays them in a real-time dashboard.

## Features

- Real-time system metrics monitoring (CPU, Memory, Load)
- Storage monitoring (disk usage, I/O)
- Network interface monitoring
- RTSP stream monitoring
- Redis-based time-series data storage
- Web-based dashboard with real-time updates

## Requirements

- Java 11 or newer
- Maven
- Redis server
- Linux/macOS (for system metrics collection)

## Quick Setup

For quick local testing:

```bash
# Clone the repository
git clone https://github.com/yourusername/system-health-monitoring.git
cd system-health-monitoring

# Build the application
mvn clean package

# Run the application
java -jar target/system-health-monitoring-0.0.1-SNAPSHOT.jar
```

## Production Deployment

For production deployment, use the included deployment script:

```bash
# Make the script executable
chmod +x deploy.sh

# Run the deployment script (requires root privileges)
sudo ./deploy.sh
```

This will:
1. Create a systemd service for the application
2. Configure it to start automatically at boot
3. Set up proper logging and restart policies

## Configuration

The application can be configured using environment variables:

| Variable | Description | Default |
|----------|-------------|---------|
| SERVER_ID | Unique server identifier | local-server |
| SERVER_DISPLAY_NAME | Display name for the UI | Local Development Server |
| SERVER_LOCATION | Location description | Development Environment |
| REDIS_HOST | Redis server hostname | localhost |
| REDIS_PORT | Redis server port | 6379 |
| REDIS_PASSWORD | Redis password (if any) | |
| REDIS_DATABASE | Redis database number | 0 |

## Accessing the Dashboard

Once deployed, the dashboard is available at:

```
http://your-server-ip:8080
```

## Systemd Service Management

```bash
# Check service status
sudo systemctl status system-health-monitoring

# Start/stop/restart the service
sudo systemctl start system-health-monitoring
sudo systemctl stop system-health-monitoring
sudo systemctl restart system-health-monitoring

# View logs
sudo journalctl -u system-health-monitoring -f
```

## License

MIT