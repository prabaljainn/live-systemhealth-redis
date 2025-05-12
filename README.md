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

- Linux/macOS operating system
- Root/sudo access for service installation
- Internet connection (for dependency installation)

The deployment script will automatically check for and install:
- Java 17 or newer
- Maven
- Redis server

## Deployment

For production deployment, use the included deployment script:

```bash
# Make the script executable
chmod +x deploy.sh

# Run the deployment script (requires root privileges)
sudo ./deploy.sh
```

The script will:
1. Check for and offer to install required dependencies (Java, Maven, Redis)
2. Ask for Redis and server configuration
3. Build the application (using Maven or the run-app script if available)
4. Create a systemd service
5. Start the service with proper restart policies

During deployment, you'll be prompted to:
- Install any missing dependencies
- Configure Redis connection settings
- Set up server identity information

## Quick Setup (Development)

For quick local testing:

```bash
# Option 1: Using Maven
mvn clean package
java -jar target/system-health-monitoring-0.0.1-SNAPSHOT.jar

# Option 2: Using the run-app script
chmod +x run-app
./run-app
```

The `run-app` script is a convenience wrapper that handles building and running the application. The deployment script will automatically use this script if available.

## Configuration

The application can be configured using environment variables:

| Variable | Description | Default |
|----------|-------------|---------|
| SERVER_PORT | Port the application listens on | 8080 |
| SERVER_ID | Unique server identifier | local-server |
| SERVER_DISPLAY_NAME | Display name for the UI | System Health Monitoring Server |
| SERVER_LOCATION | Location description | Production Environment |
| REDIS_HOST | Redis server hostname | localhost |
| REDIS_PORT | Redis server port | 6379 |
| REDIS_PASSWORD | Redis password (if any) | |
| REDIS_DATABASE | Redis database number | 0 |

## Accessing the Dashboard

Once deployed, the dashboard is available at:

```
http://your-server-ip:8080
```

## Service Management

### Using Systemd Commands

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

### Using the Stop Service Script

The `stop-service.sh` script provides a more controlled way to stop the service and perform additional tasks:

```bash
# Make the script executable
chmod +x stop-service.sh

# Run the script (requires root privileges)
sudo ./stop-service.sh
```

The stop script supports several command-line options:

```bash
# Show help message
sudo ./stop-service.sh --help

# Stop service, disable at boot and clear logs
sudo ./stop-service.sh --disable --clear-logs

# Stop service with force if needed, non-interactive
sudo ./stop-service.sh --yes --force
```

Available options:
- `-d, --disable`: Disable service from starting at boot
- `-c, --clear-logs`: Clear service logs
- `-f, --force`: Force kill service if graceful stop fails
- `-y, --yes`: Non-interactive mode (assume yes to all prompts)
- `-h, --help`: Show help message

## License

MIT

## Screenshots

<img width="1680" alt="Screenshot 2025-05-13 at 12 02 49 AM" src="https://github.com/user-attachments/assets/1536c627-0eb9-4a4c-b5f9-6394e7b38837" />
<img width="1680" alt="Screenshot 2025-05-13 at 12 02 56 AM" src="https://github.com/user-attachments/assets/687f3123-92fb-4152-a334-2b9d83e80436" />
<img width="1680" alt="Screenshot 2025-05-13 at 12 03 02 AM" src="https://github.com/user-attachments/assets/98955592-a6e9-4b55-b8ed-cf514d3edf0c" />
<img width="1680" alt="Screenshot 2025-05-13 at 12 03 09 AM" src="https://github.com/user-attachments/assets/324a86af-f60a-4a94-8091-31cb1046253b" />

